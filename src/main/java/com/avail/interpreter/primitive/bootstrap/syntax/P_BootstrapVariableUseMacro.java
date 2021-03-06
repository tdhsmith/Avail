/*
 * P_BootstrapVariableUseMacro.java
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
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.descriptor.VariableUsePhraseDescriptor;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.avail.compiler.problems.CompilerDiagnostics.ParseNotificationLevel.*;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_MAP_KEY;
import static com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind.LOCAL_CONSTANT;
import static com.avail.descriptor.DeclarationPhraseDescriptor.newModuleConstant;
import static com.avail.descriptor.DeclarationPhraseDescriptor.newModuleVariable;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.TypeDescriptor.Types.TOKEN;
import static com.avail.descriptor.VariableUsePhraseDescriptor.newUse;
import static com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER;
import static com.avail.interpreter.Primitive.Flag.*;
import static java.util.Comparator.comparing;

/**
 * The {@code P_BootstrapVariableUseMacro} primitive is used to create
 * {@link VariableUsePhraseDescriptor variable use} phrases.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@SuppressWarnings("unused")
public final class P_BootstrapVariableUseMacro
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_BootstrapVariableUseMacro().init(
			1, CannotFail, CanInline, Bootstrap);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_Phrase variableNameLiteral = interpreter.argument(0);

		final @Nullable AvailLoader loader = interpreter.availLoaderOrNull();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		assert variableNameLiteral.isInstanceOf(
			LITERAL_PHRASE.mostGeneralType());
		final A_Token literalToken = variableNameLiteral.token();
		assert literalToken.tokenType() == TokenType.LITERAL;
		final A_Token actualToken = literalToken.literal();
		assert actualToken.isInstanceOf(TOKEN.o());
		final A_String variableNameString = actualToken.string();
		if (actualToken.tokenType() != TokenType.KEYWORD)
		{
			throw new AvailRejectedParseException(
				STRONG,
				"variable %s to be alphanumeric",
				variableNameString);
		}
		final A_Map fiberGlobals = interpreter.fiber().fiberGlobals();
		final A_Map clientData = fiberGlobals.mapAt(
			CLIENT_DATA_GLOBAL_KEY.atom);
		final A_Map scopeMap = clientData.mapAt(COMPILER_SCOPE_MAP_KEY.atom);
		if (scopeMap.hasKey(variableNameString))
		{
			final A_Phrase localDeclaration =
				scopeMap.mapAt(variableNameString);
			// If the local constant is initialized by a literal, then treat a
			// mention of that constant as though it were the literal itself.
			if (localDeclaration.declarationKind() == LOCAL_CONSTANT
				&& localDeclaration
					.initializationExpression()
					.phraseKindIsUnder(LITERAL_PHRASE))
			{
				return interpreter.primitiveSuccess(
					localDeclaration.initializationExpression());
			}

			final AvailObject variableUse = newUse(
				actualToken, scopeMap.mapAt(variableNameString));
			variableUse.makeImmutable();
			return interpreter.primitiveSuccess(variableUse);
		}
		// Not in a block scope. See if it's a module variable or module
		// constant...
		final A_Module module = loader.module();
		if (module.variableBindings().hasKey(variableNameString))
		{
			final A_BasicObject variableObject =
				module.variableBindings().mapAt(variableNameString);
			final A_Phrase moduleVarDecl =
				newModuleVariable(
					actualToken,
					variableObject,
					nil,
					nil);
			final A_Phrase variableUse = newUse(actualToken, moduleVarDecl);
			variableUse.makeImmutable();
			return interpreter.primitiveSuccess(variableUse);
		}
		if (!module.constantBindings().hasKey(variableNameString))
		{
			throw new AvailRejectedParseException(
				// Almost any theory is better than guessing that we want the
				// value of some variable that doesn't exist.
				scopeMap.mapSize() == 0 ? SILENT : WEAK,
				() ->
				{
					final StringBuilder builder = new StringBuilder();
					builder.append("variable ");
					builder.append(variableNameString);
					builder.append(" to be in scope (local scope is: ");
					final List<A_String> scope = new ArrayList<>(
						toList(scopeMap.keysAsSet().asTuple()));
					scope.sort(comparing(A_String::asNativeString));
					boolean first = true;
					for (final A_String eachVar : scope)
					{
						if (!first)
						{
							builder.append(", ");
						}
						builder.append(eachVar.asNativeString());
						first = false;
					}
					builder.append(")");
					return stringFrom(builder.toString());
				});
		}
		final A_BasicObject variableObject =
			module.constantBindings().mapAt(variableNameString);
		final A_Phrase moduleConstDecl =
			newModuleConstant(actualToken, variableObject, nil);
		final A_Phrase variableUse = newUse(actualToken, moduleConstDecl);
		return interpreter.primitiveSuccess(variableUse);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				/* Variable name */
				LITERAL_PHRASE.create(TOKEN.o())),
			EXPRESSION_PHRASE.mostGeneralType());
	}
}
