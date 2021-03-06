/*
 * P_FileWrite.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.avail.interpreter.primitive.files;

import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.io.IOSystem;
import com.avail.io.IOSystem.BufferKey;
import com.avail.io.IOSystem.FileHandle;
import com.avail.io.SimpleCompletionHandler;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.utility.Mutable;
import com.avail.utility.MutableLong;
import com.avail.utility.MutableOrNull;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.FILE_KEY;
import static com.avail.descriptor.FiberDescriptor.newFiber;
import static com.avail.descriptor.FiberTypeDescriptor.fiberType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.bytes;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.oneOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Interpreter.runOutermostFunction;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;
import static com.avail.utility.evaluation.Combinator.recurse;
import static java.lang.Math.min;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * <strong>Primitive:</strong> Write the specified {@linkplain
 * TupleDescriptor tuple} to the {@linkplain AsynchronousFileChannel file
 * channel} associated with the {@linkplain AtomDescriptor handle}. Writing
 * begins at the specified one-based position of the file.
 *
 * <p>
 * Answer a new fiber which, if the write is eventually successful, will be
 * started to run the success {@linkplain FunctionDescriptor function}.  If the
 * write is unsuccessful, the fiber will be started to apply the failure {@code
 * function} to the error code.  The fiber runs at the specified priority.
 * </p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_FileWrite
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_FileWrite().init(
			6, CanInline, HasSideEffect);

	/**
	 * The maximum transfer size when writing to a file.  Attempts to write
	 * more bytes than this may be broken down internally into transfers that
	 * are this small, possibly recycling the same buffer.
	 */
	public static final int MAX_WRITE_BUFFER_SIZE = 4_194_304;

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(6);
		final A_Number positionObject = interpreter.argument(0);
		final A_Tuple bytes = interpreter.argument(1);
		final A_Atom atom = interpreter.argument(2);
		final A_Function succeed = interpreter.argument(3);
		final A_Function fail = interpreter.argument(4);
		final A_Number priority = interpreter.argument(5);

		final A_BasicObject pojo =
			atom.getAtomProperty(FILE_KEY.atom);
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				atom.isAtomSpecial() ? E_SPECIAL_ATOM : E_INVALID_HANDLE);
		}
		final FileHandle handle = pojo.javaObjectNotNull();
		if (!handle.canWrite)
		{
			return interpreter.primitiveFailure(E_NOT_OPEN_FOR_WRITE);
		}
		final AsynchronousFileChannel fileChannel = handle.channel;
		if (!positionObject.isLong())
		{
			return interpreter.primitiveFailure(E_EXCEEDS_VM_LIMIT);
		}
		final int alignment = handle.alignment;
		final AvailRuntime runtime = currentRuntime();
		final IOSystem ioSystem = runtime.ioSystem();
		final long oneBasedPositionLong = positionObject.extractLong();
		// Guaranteed positive by argument constraint.
		assert oneBasedPositionLong > 0L;
		// Write the tuple of bytes, possibly split up into manageable sections.
		// Also update the buffer cache to reflect the modified file content.
		final A_Fiber current = interpreter.fiber();
		final A_Fiber newFiber = newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priority.extractInt(),
			() -> formatString("Asynchronous file write, %s", handle.filename));
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		newFiber.availLoader(current.availLoader());
		// Share and inherit any heritable variables.
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared());
		// Inherit the fiber's text interface.
		newFiber.textInterface(current.textInterface());
		// Share everything that will potentially be visible to the fiber.
		newFiber.makeShared();
		succeed.makeShared();
		fail.makeShared();

		// The iterator produces non-empty ByteBuffers, possibly the same one
		// multiple times, refilling it each time.
		final int totalBytes = bytes.tupleSize();
		final Iterator<ByteBuffer> bufferIterator;
		if (bytes.isByteBufferTuple())
		{
			final ByteBuffer buffer = bytes.byteBuffer().slice();
			bufferIterator = singletonList(buffer).iterator();
		}
		else if (bytes.isByteArrayTuple())
		{
			final ByteBuffer buffer = ByteBuffer.wrap(bytes.byteArray());
			bufferIterator = singletonList(buffer).iterator();
		}
		else
		{
			bufferIterator = new Iterator<ByteBuffer>()
			{
				/** The buffer to reuse for writing. */
				final ByteBuffer buffer = ByteBuffer.allocateDirect(
					min(totalBytes, MAX_WRITE_BUFFER_SIZE));

				/**
				 * The position in the bytes tuple corresponding with the
				 * current buffer start.
				 */
				int nextSubscript = 1;

				@Override
				public boolean hasNext ()
				{
					return nextSubscript <= totalBytes;
				}

				@Override
				public ByteBuffer next ()
				{
					if (!hasNext())
					{
						throw new NoSuchElementException();
					}
					buffer.clear();
					int count = nextSubscript + buffer.limit() - 1;
					if (count >= totalBytes)
					{
						// All the rest.
						count = totalBytes;
					}
					else
					{
						// It's not all the rest, so round down to the nearest
						// alignment boundary for performance.
						final long zeroBasedSubscriptAfterBuffer =
							oneBasedPositionLong + nextSubscript - 2 + count;
						final long modulus =
							zeroBasedSubscriptAfterBuffer % alignment;
						assert modulus == (int) modulus;
						if (modulus < count)
						{
							// Shorten this buffer so it ends at an alignment
							// boundary of the file, but only if it remains
							// non-empty.  Not only will this improve throughput
							// by allowing the operating system to avoid reading
							// a buffer so it can partially overwrite it, but it
							// also makes our own buffer overwriting code more
							// efficient.
							count -= (int) modulus;
						}
					}
					bytes.transferIntoByteBuffer(
						nextSubscript, nextSubscript + count - 1, buffer);
					buffer.flip();
					assert buffer.limit() == count;
					nextSubscript += count;
					return buffer;
				}

				@Override
				public void remove ()
				{
					throw new UnsupportedOperationException();
				}
			};
		}
		final MutableLong nextPosition =
			new MutableLong(oneBasedPositionLong - 1);
		final Mutable<ByteBuffer> currentBuffer =
			new Mutable<>(bufferIterator.next());
		recurse(continueWriting ->
		{
			if (!currentBuffer.value.hasRemaining())
			{
				if (bufferIterator.hasNext())
				{
					currentBuffer.value = bufferIterator.next();
					assert currentBuffer.value.hasRemaining();
				}
			}
			if (currentBuffer.value.hasRemaining())
			{
				fileChannel.write(
					currentBuffer.value,
					nextPosition.value,
					null,
					new SimpleCompletionHandler<Integer, Void>(
						bytesWritten ->
						{
							nextPosition.value += (long) bytesWritten;
							continueWriting.value();
						},
						throwable ->
						{
							// Invalidate *all* pages for this file to
							// ensure subsequent I/O has a proper
							// opportunity to re-encounter problems like
							// read faults and whatnot.
							for (final BufferKey key : new ArrayList<>(
								handle.bufferKeys.keySet()))
							{
								ioSystem.discardBuffer(key);
							}
							runOutermostFunction(
								runtime,
								newFiber,
								fail,
								singletonList(E_IO_ERROR.numericCode()));
						}));
			}
			else
			{
				// Just finished the entire write.  Transfer the data onto
				// any affected cached pages.
				assert nextPosition.value ==
					oneBasedPositionLong + totalBytes - 1;
				int subscriptInTuple = 1;
				long startOfBuffer = (oneBasedPositionLong - 1)
					/ alignment * alignment + 1;
				int offsetInBuffer =
					(int) (oneBasedPositionLong - startOfBuffer + 1);
				// Skip this if the file isn't also open for read access.
				if (!handle.canRead)
				{
					subscriptInTuple = totalBytes + 1;
				}
				while (subscriptInTuple <= totalBytes)
				{
					// Update one buffer.
					final int consumedThisTime = min(
						alignment - offsetInBuffer,
						totalBytes - subscriptInTuple) + 1;
					final BufferKey key = new BufferKey(
						handle, startOfBuffer);
					final MutableOrNull<A_Tuple> bufferHolder =
						ioSystem.getBuffer(key);
					@Nullable A_Tuple tuple = bufferHolder.value;
					if (offsetInBuffer == 1
						&& consumedThisTime == alignment)
					{
						tuple = bytes.copyTupleFromToCanDestroy(
							subscriptInTuple,
							subscriptInTuple + consumedThisTime - 1,
							false);
					}
					else if (tuple != null)
					{
						// Update the cached tuple.
						assert tuple.tupleSize() == alignment;
						final List<A_Tuple> parts = new ArrayList<>(3);
						if (offsetInBuffer > 1)
						{
							parts.add(
								tuple.copyTupleFromToCanDestroy(
									1, offsetInBuffer - 1, false));
						}
						parts.add(
							bytes.copyTupleFromToCanDestroy(
								subscriptInTuple,
								subscriptInTuple + consumedThisTime - 1,
								false));
						final int endInBuffer =
							offsetInBuffer + consumedThisTime - 1;
						if (endInBuffer < alignment)
						{
							parts.add(
								tuple.copyTupleFromToCanDestroy(
									endInBuffer + 1, alignment, false));
						}
						assert parts.size() > 1;
						tuple = parts.remove(0);
						while (!parts.isEmpty())
						{
							tuple = tuple.concatenateWith(
								parts.remove(0), true);
						}
						assert tuple.tupleSize() == alignment;
					}
					// Otherwise we're attempting to update a subregion of
					// an uncached buffer.  Just drop it in that case and
					// let the OS cache pick up the slack.
					if (tuple != null)
					{
						tuple = tuple.makeShared();
						synchronized (bufferHolder)
						{
							bufferHolder.value = tuple;
						}
					}
					subscriptInTuple += consumedThisTime;
					startOfBuffer += alignment;
					offsetInBuffer = 1;
				}
				assert subscriptInTuple == totalBytes + 1;
				runOutermostFunction(runtime, newFiber, succeed, emptyList());
			}
		});
		return interpreter.primitiveSuccess(newFiber);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tupleFromArray(
				naturalNumbers(),
				oneOrMoreOf(bytes()),
				ATOM.o(),
				functionType(
					emptyTuple(),
					TOP.o()),
				functionType(
					tuple(instanceType(E_IO_ERROR.numericCode())),
					TOP.o()),
				bytes()),
			fiberType(TOP.o()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_NOT_OPEN_FOR_WRITE,
				E_EXCEEDS_VM_LIMIT));
	}
}
