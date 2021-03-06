/*
 * P_SocketIPv4Connect.java
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

package com.avail.interpreter.primitive.sockets;

import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.io.SimpleCompletionHandler;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.AsynchronousSocketChannel;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.SOCKET_KEY;
import static com.avail.descriptor.FiberDescriptor.newFiber;
import static com.avail.descriptor.FiberTypeDescriptor.mostGeneralFiberType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.*;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Interpreter.runOutermostFunction;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * <strong>Primitive:</strong> Connect the {@linkplain
 * AsynchronousSocketChannel asynchronous socket} referenced by the specified
 * {@linkplain AtomDescriptor handle} to an {@linkplain Inet4Address IPv4
 * address} and port. Create a new {@linkplain FiberDescriptor fiber} to respond
 * to the asynchronous completion of the operation; the fiber will run at the
 * specified {@linkplain IntegerRangeTypeDescriptor#bytes() priority}. If the
 * operation succeeds, then eventually start the new fiber to apply the
 * {@linkplain FunctionDescriptor success function}. If the operation fails,
 * then eventually start the new fiber to apply the {@linkplain
 * FunctionDescriptor failure function} to the {@linkplain IntegerDescriptor
 * numeric} {@linkplain AvailErrorCode error code}. Answer the new fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_SocketIPv4Connect
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_SocketIPv4Connect().init(
			6, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(6);
		final A_Atom handle = interpreter.argument(0);
		final A_Tuple addressTuple = interpreter.argument(1);
		final A_Number port = interpreter.argument(2);
		final A_Function succeed = interpreter.argument(3);
		final A_Function fail = interpreter.argument(4);
		final A_Number priority = interpreter.argument(5);
		final A_BasicObject pojo = handle.getAtomProperty(SOCKET_KEY.atom);
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				handle.isAtomSpecial()
				? E_SPECIAL_ATOM
				: E_INVALID_HANDLE);
		}
		final AsynchronousSocketChannel socket = pojo.javaObjectNotNull();
		// Build the big-endian address byte array.
		final byte[] addressBytes = new byte[4];
		for (int i = 0; i < addressBytes.length; i++)
		{
			final A_Number addressByte = addressTuple.tupleAt(i + 1);
			addressBytes[i] = (byte) addressByte.extractUnsignedByte();
		}
		final SocketAddress address;
		try
		{
			final Inet4Address inetAddress = (Inet4Address)
				InetAddress.getByAddress(addressBytes);
			address = new InetSocketAddress(
				inetAddress, port.extractUnsignedShort());
		}
		catch (final IllegalStateException e)
		{
			return interpreter.primitiveFailure(E_INVALID_HANDLE);
		}
		catch (final UnknownHostException e)
		{
			// This shouldn't actually happen, since we carefully enforce the
			// range of addresses.
			assert false;
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
		final A_Fiber current = interpreter.fiber();
		final A_Fiber newFiber = newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priority.extractInt(),
			() -> formatString(
				"Socket IPv4 connect, %s:%d",
				addressTuple.toString(),
				port.extractInt()));
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
		// Now start the asynchronous connect.
		final AvailRuntime runtime = currentRuntime();
		try
		{
			socket.connect(
				address,
				null,
				new SimpleCompletionHandler<>(
					unused -> runOutermostFunction(
						runtime,
						newFiber,
						succeed,
						emptyList()),
					killer -> runOutermostFunction(
						runtime,
						newFiber,
						fail,
						singletonList(
							E_IO_ERROR.numericCode()))));
		}
		catch (final IllegalArgumentException e)
		{
			return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE);
		}
		catch (final IllegalStateException e)
		{
			return interpreter.primitiveFailure(E_INVALID_HANDLE);
		}
		catch (final SecurityException e)
		{
			return interpreter.primitiveFailure(E_PERMISSION_DENIED);
		}
		return interpreter.primitiveSuccess(newFiber);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return
			functionType(
				tupleFromArray(
					ATOM.o(),
					tupleTypeForSizesTypesDefaultType(
						singleInt(4),
						emptyTuple(),
						bytes()),
					unsignedShorts(),
					functionType(
						emptyTuple(),
						TOP.o()),
					functionType(
						tuple(instanceType(E_IO_ERROR.numericCode())),
						TOP.o()),
					bytes()),
				mostGeneralFiberType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_INCORRECT_ARGUMENT_TYPE,
				E_IO_ERROR,
				E_PERMISSION_DENIED));
	}
}
