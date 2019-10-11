/*
 * P_BootstrapLexerKeywordBody.java
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

package com.avail.interpreter.primitive.bootstrap.lexing

import com.avail.descriptor.A_Number
import com.avail.descriptor.A_String
import com.avail.descriptor.A_Token
import com.avail.descriptor.A_Type
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import com.avail.descriptor.LexerDescriptor.lexerBodyFunctionType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TokenDescriptor.TokenType.KEYWORD
import com.avail.descriptor.TokenDescriptor.newToken
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail

/**
 * The `P_BootstrapLexerKeywordBody` primitive is used for parsing keyword
 * tokens.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_BootstrapLexerKeywordBody : Primitive(3, CannotFail, CanFold, CanInline, Bootstrap)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(3)
		val source = interpreter.argument(0)
		val sourcePositionInteger = interpreter.argument(1)
		val lineNumberInteger = interpreter.argument(2)

		val sourceSize = source.tupleSize()
		val startPosition = sourcePositionInteger.extractInt()
		var position = startPosition

		while (position <= sourceSize && Character.isUnicodeIdentifierPart(
				source.tupleCodePointAt(position)))
		{
			position++
		}
		val token = newToken(
			source.copyStringFromToCanDestroy(
				startPosition, position - 1, false),
			startPosition,
			lineNumberInteger.extractInt(),
			KEYWORD)
		return interpreter.primitiveSuccess(set(tuple(token)))
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return lexerBodyFunctionType()
	}

}