/*
 * P_AcceptParsing.java
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
package com.avail.interpreter.primitive.phrases;

import com.avail.compiler.AvailAcceptedParseException;
import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FiberDescriptor.GeneralFlag.CAN_REJECT_PARSE;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.exceptions.AvailErrorCode.E_UNTIMELY_PARSE_ACCEPTANCE;
import static com.avail.interpreter.Primitive.Flag.Unknown;

/**
 * <strong>Primitive:</strong> Either an expression is having an applicable
 * semantic checked, a macro body is being executed for some invocation site, or
 * a {@link A_Definition#prefixFunctions() prefix function} for a macro is being
 * invoked for a tentative prefix of an invocation site.  The Avail code has
 * decided by invoking this primitive that the terms of the invocation are
 * acceptable.
 *
 * <p>By using this primitive in a semantic restriction rather than simply
 * returning the value ⊤, we are able to indicate statically that a particular
 * semantic restriction cannot strengthen the expression's type.  That's because
 * this primitive is ⊥-valued, and therefore the semantic restriction body can
 * itself be ⊥-valued.  If all semantic restrictions for a method are ⊥-valued,
 * and if all method definitions are ⊤-valued, we can be assured that a call
 * site can never produce a type stronger than ⊤.  Therefore it can never occur
 * as an argument of a send – other than of a macro that explicitly allows
 * ⊤-yielding expressions, such as "_!;", which is dealt with specially.  This
 * distinction allows less pointless parsing to take place, in theory yielding
 * both faster parsing and better diagnostics.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_AcceptParsing
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_AcceptParsing().init(
			0, Unknown);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(0);
		if (!interpreter.fiber().generalFlag(CAN_REJECT_PARSE))
		{
			return interpreter.primitiveFailure(E_UNTIMELY_PARSE_ACCEPTANCE);
		}
		throw new AvailAcceptedParseException();
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(emptyTuple(), bottom());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return
			enumerationWith(set(E_UNTIMELY_PARSE_ACCEPTANCE));
	}
}
