/*
 * P_FileSync.java
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
import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.io.IOSystem.FileHandle;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.FILE_KEY;
import static com.avail.descriptor.FiberDescriptor.newFiber;
import static com.avail.descriptor.FiberTypeDescriptor.fiberType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.bytes;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Interpreter.runOutermostFunction;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * <strong>Primitive:</strong> Force all system buffers associated with the
 * {@linkplain FileHandle#canWrite writable} {@linkplain AsynchronousFileChannel
 * file channel} associated with the {@linkplain AtomDescriptor handle} to
 * synchronize with the underlying device.
 *
 * <p>
 * Answer a new fiber which, if the sync is eventually successful, will be
 * started to run the success {@linkplain FunctionDescriptor function}.  If the
 * sync fails with an {@link IOException}, the fiber will be started to apply
 * the failure function to the error code.  The fiber runs at the specified
 * priority.
 * </p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_FileSync
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_FileSync().init(
			4, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(4);
		final A_Atom atom = interpreter.argument(0);
		final A_Function succeed = interpreter.argument(1);
		final A_Function fail = interpreter.argument(2);
		final A_Number priority = interpreter.argument(3);

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

		// Don't block an execution thread - use the runtime's file executor
		// pool instead.  That keeps all the execution threads humming, even if
		// there are several pending blocking I/O operations (like sync).  Note
		// that the current (2014.06.11) implementation of the file executor
		// specifies an unbounded queue, so the fiber execution threads will
		// never be blocked waiting for I/O.
		final int priorityInt = priority.extractInt();
		final A_Fiber current = interpreter.fiber();
		final A_Fiber newFiber = newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priorityInt,
			() -> formatString("Asynchronous file sync, %s", handle.filename));
		newFiber.availLoader(current.availLoader());
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared());
		newFiber.textInterface(current.textInterface());
		newFiber.makeShared();
		succeed.makeShared();
		fail.makeShared();

		final AvailRuntime runtime = interpreter.runtime();
		runtime.ioSystem().executeFileTask(() ->
		{
			try
			{
				handle.channel.force(true);
			}
			catch (final IOException e)
			{
				runOutermostFunction(
					runtime,
					newFiber,
					fail,
					singletonList(
						E_IO_ERROR.numericCode()));
				return;
			}
			runOutermostFunction(
				runtime,
				newFiber,
				succeed,
				emptyList());
		});
		return interpreter.primitiveSuccess(newFiber);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				ATOM.o(),
				functionType(emptyTuple(), TOP.o()),
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
			set(E_INVALID_HANDLE, E_SPECIAL_ATOM, E_NOT_OPEN_FOR_WRITE));
	}
}
