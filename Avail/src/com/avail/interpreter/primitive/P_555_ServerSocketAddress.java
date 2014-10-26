/**
 * P_555_ServerSocketAddress.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.io.IOException;
import java.net.*;
import java.nio.channels.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 567</strong>: Answer the {@linkplain InetSocketAddress
 * socket address} of the {@linkplain AsynchronousServerSocketChannel
 * asynchronous server socket channel} referenced by the specified {@linkplain
 * AtomDescriptor handle}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_555_ServerSocketAddress
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_555_ServerSocketAddress().init(
			1, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final AvailObject handle = args.get(0);
		final AvailObject pojo =
			handle.getAtomProperty(AtomDescriptor.serverSocketKey());
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				handle.isAtomSpecial()
				? E_SPECIAL_ATOM
				: E_INVALID_HANDLE);
		}
		final AsynchronousServerSocketChannel socket =
			(AsynchronousServerSocketChannel) pojo.javaObjectNotNull();
		final InetSocketAddress peer;
		try
		{
			peer = (InetSocketAddress) socket.getLocalAddress();
		}
		catch (final ClosedChannelException e)
		{
			return interpreter.primitiveFailure(E_INVALID_HANDLE);
		}
		catch (final IOException e)
		{
			return interpreter.primitiveFailure(E_IO_ERROR);
		}
		final InetAddress address = peer.getAddress();
		final byte[] addressBytes = address.getAddress();
		final AvailObject addressTuple =
			ByteArrayTupleDescriptor.forByteArray(addressBytes);
		final A_Number port = IntegerDescriptor.fromInt(peer.getPort());
		return interpreter.primitiveSuccess(
			TupleDescriptor.from(addressTuple, port));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ATOM.o()),
			TupleTypeDescriptor.forTypes(
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.inclusive(
						IntegerDescriptor.fromInt(4),
						IntegerDescriptor.fromInt(16)),
					TupleDescriptor.empty(),
					IntegerRangeTypeDescriptor.bytes()),
				IntegerRangeTypeDescriptor.unsignedShorts()));
	}


	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				E_INVALID_HANDLE.numericCode(),
				E_SPECIAL_ATOM.numericCode(),
				E_IO_ERROR.numericCode()
			).asSet());
	}
}
