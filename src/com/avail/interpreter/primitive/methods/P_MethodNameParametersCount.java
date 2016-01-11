/**
 * P_MethodNameParametersCount.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.methods;

import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.annotations.Nullable;
import com.avail.compiler.MessageSplitter;
import com.avail.descriptor.*;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Treating the {@linkplain StringDescriptor
 * string} argument as a {@linkplain MethodDescriptor method} name, answer the
 * number of arguments expected for a valid send of the name.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_MethodNameParametersCount
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_MethodNameParametersCount().init(
			1, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_String name = args.get(0);
		@Nullable MessageSplitter splitter = null;
		try
		{
			splitter = new MessageSplitter(name);
		}
		catch (final MalformedMessageException e)
		{
			return interpreter.primitiveFailure(e);
		}
		assert splitter != null;
		return interpreter.primitiveSuccess(IntegerDescriptor.fromInt(
			splitter.numberOfArguments()));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.stringType()),
			IntegerRangeTypeDescriptor.wholeNumbers());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			MessageSplitter.possibleErrors);
	}
}