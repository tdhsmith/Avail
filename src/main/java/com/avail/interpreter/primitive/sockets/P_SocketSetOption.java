/*
 * P_SocketSetOption.java
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

import com.avail.descriptor.A_Type;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.MapDescriptor.Entry;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.AsynchronousSocketChannel;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.SOCKET_KEY;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.MapTypeDescriptor.mapTypeForSizesKeyTypeValueType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;
import static java.net.StandardSocketOptions.*;

/**
 * <strong>Primitive:</strong> Set the socket options for the
 * {@linkplain AsynchronousSocketChannel asynchronous socket channel} referenced
 * by the specified {@linkplain AtomDescriptor handle}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_SocketSetOption
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_SocketSetOption().init(
			2, CanInline, HasSideEffect);

	/**
	 * A one-based list of the standard socket options.
	 */
	@SuppressWarnings("rawtypes")
	private static final SocketOption[] socketOptions =
		{null, SO_RCVBUF, SO_REUSEADDR, SO_SNDBUF, SO_KEEPALIVE, TCP_NODELAY};

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final AvailObject handle = interpreter.argument(0);
		final AvailObject options = interpreter.argument(1);
		final AvailObject pojo =
			handle.getAtomProperty(SOCKET_KEY.atom);
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				handle.isAtomSpecial() ? E_SPECIAL_ATOM : E_INVALID_HANDLE);
		}
		final AsynchronousSocketChannel socket = pojo.javaObjectNotNull();
		try
		{
			for (final Entry entry : options.mapIterable())
			{
				final AvailObject key = entry.key();
				final AvailObject entryValue = entry.value();
				@SuppressWarnings("rawtypes")
				final SocketOption option = socketOptions[key.extractInt()];
				final Class<?> type = option.type();
				if (type.equals(Boolean.class) && entryValue.isBoolean())
				{
					@SuppressWarnings("unchecked")
					final SocketOption<Boolean> booleanOption = option;
					socket.setOption(
						booleanOption, entryValue.extractBoolean());
				}
				else if (type.equals(Integer.class) && entryValue.isInt())
				{
					final Integer value = entryValue.extractInt();
					@SuppressWarnings("unchecked")
					final SocketOption<Integer> intOption = option;
					socket.setOption(intOption, value);
				}
				else
				{
					return interpreter.primitiveFailure(
						E_INCORRECT_ARGUMENT_TYPE);
				}
			}
			return interpreter.primitiveSuccess(nil);
		}
		catch (final IllegalArgumentException e)
		{
			return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE);
		}
		catch (final IOException e)
		{
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				ATOM.o(),
				mapTypeForSizesKeyTypeValueType(
					inclusive(0, socketOptions.length - 1),
					inclusive(1, socketOptions.length - 1),
					ANY.o())),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_SPECIAL_ATOM,
				E_INCORRECT_ARGUMENT_TYPE,
				E_IO_ERROR));
	}
}
