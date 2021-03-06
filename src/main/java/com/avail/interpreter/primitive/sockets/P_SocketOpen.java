/*
 * P_SocketOpen.java
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

import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.io.IOException;
import java.nio.channels.AsynchronousSocketChannel;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.SOCKET_KEY;
import static com.avail.descriptor.AtomDescriptor.createAtom;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ModuleDescriptor.currentModule;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.oneOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.descriptor.TypeDescriptor.Types.CHARACTER;
import static com.avail.exceptions.AvailErrorCode.E_IO_ERROR;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive:</strong> Open an {@linkplain
 * AsynchronousSocketChannel asynchronous socket channel}. Answer a
 * {@linkplain AtomDescriptor handle} that uniquely identifies the socket.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_SocketOpen
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_SocketOpen().init(
			1, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_String name = interpreter.argument(0);
		try
		{
			final A_Atom handle = createAtom(name, currentModule());
			final AsynchronousSocketChannel channel =
				currentRuntime().ioSystem().openSocket();
			final AvailObject pojo = identityPojo(channel);
			handle.setAtomProperty(SOCKET_KEY.atom, pojo);
			return interpreter.primitiveSuccess(handle);
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
			tuple(oneOrMoreOf(CHARACTER.o())),
			ATOM.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_IO_ERROR));
	}
}
