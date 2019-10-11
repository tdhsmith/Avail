/*
 * P_BootstrapVariableDeclarationMacro.java
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
import com.avail.descriptor.A_String
import com.avail.descriptor.A_Token
import com.avail.descriptor.A_Type
import com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind
import com.avail.descriptor.FiberDescriptor
import com.avail.descriptor.TokenDescriptor.TokenType
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.STRONG
import com.avail.descriptor.DeclarationPhraseDescriptor.newVariable
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.DECLARATION_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE
import com.avail.descriptor.TypeDescriptor.Types.TOKEN
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail

/**
 * The `P_BootstrapVariableDeclarationMacro` primitive is used
 * for bootstrapping declaration of a [ local variable][DeclarationKind.LOCAL_VARIABLE] (without an initializing expression).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_BootstrapVariableDeclarationMacro : Primitive(2, CanInline, CannotFail, Bootstrap)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(2)
		val variableNameLiteral = interpreter.argument(0)
		val typeLiteral = interpreter.argument(1)

		val nameToken = variableNameLiteral.token().literal()
		val nameString = nameToken.string()
		if (nameToken.tokenType() != TokenType.KEYWORD)
		{
			throw AvailRejectedParseException(
				STRONG,
				"new variable name to be alphanumeric, not %s",
				nameString)
		}
		val type = typeLiteral.token().literal()
		if (type.isTop || type.isBottom)
		{
			throw AvailRejectedParseException(
				STRONG,
				"variable's declared type to be something other than %s",
				type)
		}
		val variableDeclaration = newVariable(nameToken, type, typeLiteral, nil)
		val conflictingDeclaration = FiberDescriptor.addDeclaration(variableDeclaration)
		if (conflictingDeclaration != null)
		{
			throw AvailRejectedParseException(
				STRONG,
				"local variable %s to have a name that doesn't shadow an " + "existing %s (from line %d)",
				nameString,
				conflictingDeclaration.declarationKind().nativeKindName(),
				conflictingDeclaration.token().lineNumber())
		}
		return interpreter.primitiveSuccess(variableDeclaration)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				/* Variable name phrase. */
				LITERAL_PHRASE.create(TOKEN.o()),
				/* Variable type's literal phrase. */
				LITERAL_PHRASE.create(anyMeta())),
			DECLARATION_PHRASE.mostGeneralType())
	}

}