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

package com.avail.interpreter.primitive.bootstrap.syntax

import com.avail.compiler.AvailRejectedParseException
import com.avail.descriptor.A_Phrase
import com.avail.descriptor.A_Type
import com.avail.descriptor.ExpressionAsStatementPhraseDescriptor
import com.avail.interpreter.AvailLoader
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.WEAK
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.ExpressionAsStatementPhraseDescriptor.newExpressionAsStatement
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE
import com.avail.descriptor.SetDescriptor.set
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * The `P_BootstrapSendAsStatementMacro` primitive is used to allow
 * message sends producing ⊤ to be used as statements, by wrapping them inside
 * [expression-as-statement][ExpressionAsStatementPhraseDescriptor].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_BootstrapSendAsStatementMacro : Primitive(1, CanInline, Bootstrap)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(1)
		val sendPhraseInLiteral = interpreter.argument(0)

		val loader = interpreter.fiber().availLoader()
		             ?: return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		val sendPhrase = sendPhraseInLiteral.token().literal()
		if (!sendPhrase.phraseKindIsUnder(SEND_PHRASE))
		{
			throw AvailRejectedParseException(
				WEAK,
				"statement to be a ⊤-valued send phrase, not a %s: %s",
				sendPhrase.phraseKind().name,
				sendPhrase)
		}
		if (!sendPhrase.expressionType().isTop)
		{
			throw AvailRejectedParseException(
				WEAK,
				"statement to yield ⊤, but it yields %s.  Expression is: %s",
				sendPhrase.expressionType(),
				sendPhrase)
		}
		val sendAsStatement = newExpressionAsStatement(sendPhrase)
		return interpreter.primitiveSuccess(sendAsStatement)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				/* The send phrase to treat as a statement */
				LITERAL_PHRASE.create(SEND_PHRASE.mostGeneralType())),
			EXPRESSION_AS_STATEMENT_PHRASE.mostGeneralType())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(set(E_LOADING_IS_OVER))
	}

}