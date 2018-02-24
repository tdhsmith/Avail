/*
 * P_BootstrapSendAsStatementMacro.java
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

package com.avail.interpreter.primitive.bootstrap.syntax;

import com.avail.compiler.AvailRejectedParseException;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.ExpressionAsStatementPhraseDescriptor;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.ExpressionAsStatementPhraseDescriptor.newExpressionAsStatement;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.*;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER;
import static com.avail.interpreter.Primitive.Flag.Bootstrap;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * The {@code P_BootstrapSendAsStatementMacro} primitive is used to allow
 * message sends producing ⊤ to be used as statements, by wrapping them inside
 * {@linkplain ExpressionAsStatementPhraseDescriptor expression-as-statement
 * phrases}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_BootstrapSendAsStatementMacro
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_BootstrapSendAsStatementMacro().init(
			1, CanInline, Bootstrap);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_Phrase sendPhraseInLiteral = interpreter.argument(0);

		final @Nullable AvailLoader loader = interpreter.fiber().availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		final A_Phrase sendPhrase = sendPhraseInLiteral.token().literal();
		if (!sendPhrase.phraseKindIsUnder(SEND_PHRASE))
		{
			throw new AvailRejectedParseException(
				"statement to be a ⊤-valued send phrase, not a %s: %s",
				sendPhrase.phraseKind().name(),
				sendPhrase);
		}
		if (!sendPhrase.expressionType().isTop())
		{
			throw new AvailRejectedParseException(
				"statement's type to be ⊤, but it was %s.  Expression is: %s",
				sendPhrase.expressionType(),
				sendPhrase);
		}
		final A_Phrase sendAsStatement = newExpressionAsStatement(sendPhrase);
		return interpreter.primitiveSuccess(sendAsStatement);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				/* The send phrase to treat as a statement */
				LITERAL_PHRASE.create(SEND_PHRASE.mostGeneralType())),
			EXPRESSION_AS_STATEMENT_PHRASE.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return
			enumerationWith(set(E_LOADING_IS_OVER));
	}
}
