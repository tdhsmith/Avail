/*
 * P_BootstrapPrefixEndOfBlockBody.java
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

import com.avail.descriptor.A_Atom
import com.avail.descriptor.A_Fiber
import com.avail.descriptor.A_Map
import com.avail.descriptor.A_Tuple
import com.avail.descriptor.A_Type
import com.avail.descriptor.BlockPhraseDescriptor
import com.avail.descriptor.FunctionDescriptor
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY
import com.avail.descriptor.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_MAP_KEY
import com.avail.descriptor.AtomDescriptor.SpecialAtom.COMPILER_SCOPE_STACK_KEY
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.InstanceMetaDescriptor.topMeta
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.STATEMENT_PHRASE
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleTypeDescriptor.oneOrMoreOf
import com.avail.descriptor.TupleTypeDescriptor.tupleTypeForTypes
import com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.descriptor.TupleTypeDescriptor.zeroOrOneOf
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.descriptor.TypeDescriptor.Types.TOKEN
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_INCONSISTENT_PREFIX_FUNCTION
import com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER
import com.avail.interpreter.Primitive.Flag.Bootstrap
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * The `P_BootstrapPrefixEndOfBlockBody` primitive is used for
 * bootstrapping the [block][BlockPhraseDescriptor] syntax for defining
 * [functions][FunctionDescriptor].
 *
 *
 * It ensures that declarations introduced within the block body end scope
 * when the close bracket ("]") is encountered.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object P_BootstrapPrefixEndOfBlockBody : Primitive(5, CanInline, Bootstrap)
{

	/** The key to the client parsing data in the fiber's environment.  */
	internal val clientDataKey = CLIENT_DATA_GLOBAL_KEY.atom

	/** The key to the variable scope map in the client parsing data.  */
	internal val scopeMapKey = COMPILER_SCOPE_MAP_KEY.atom

	/** The key to the tuple of scopes to pop as blocks complete parsing.  */
	internal val scopeStackKey = COMPILER_SCOPE_STACK_KEY.atom

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(5)
		//		final A_Phrase optionalArgumentDeclarations = interpreter.argument(0);
		//		final A_Phrase optionalPrimitive = interpreter.argument(1);
		//		final A_Phrase optionalLabel = interpreter.argument(2);
		//		final A_Phrase statements = interpreter.argument(3);
		//		final A_Phrase optionalReturnExpression = interpreter.argument(4);

		val fiber = interpreter.fiber()
		var fiberGlobals = fiber.fiberGlobals()
		if (!fiberGlobals.hasKey(clientDataKey))
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER)
		}
		var clientData: A_Map = fiberGlobals.mapAt(clientDataKey)
		if (!clientData.hasKey(scopeMapKey))
		{
			// It looks like somebody removed all the scope information.
			return interpreter.primitiveFailure(E_INCONSISTENT_PREFIX_FUNCTION)
		}

		// Save the current scope map to a temp, pop the scope stack to replace
		// the scope map, then push the saved scope map onto the stack.  This
		// just exchanges the top of stack and current scope map.  The block
		// macro body will do its local declaration lookups in the top of stack,
		// then discard it when complete.
		val currentScopeMap = clientData.mapAt(scopeMapKey)
		var stack: A_Tuple = clientData.mapAt(scopeStackKey)
		val poppedScopeMap = stack.tupleAt(stack.tupleSize())
		stack = stack.tupleAtPuttingCanDestroy(
			stack.tupleSize(), currentScopeMap, true)
		clientData = clientData.mapAtPuttingCanDestroy(
			scopeMapKey, poppedScopeMap, true)
		clientData = clientData.mapAtPuttingCanDestroy(
			scopeStackKey, stack, true)
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			clientDataKey, clientData, true)
		fiber.fiberGlobals(fiberGlobals.makeShared())
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional arguments section. */
					zeroOrOneOf(
						/* Arguments are present. */
						oneOrMoreOf(
							/* An argument. */
							tupleTypeForTypes(
								/* Argument name, a token. */
								TOKEN.o(),
								/* Argument type. */
								anyMeta())))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional primitive declaration. */
					zeroOrOneOf(
						/* Primitive declaration */
						tupleTypeForTypes(
							/* Primitive name. */
							TOKEN.o(),
							/* Optional failure variable declaration. */
							zeroOrOneOf(
								/* Primitive failure variable parts. */
								tupleTypeForTypes(
									/* Primitive failure variable name token */
									TOKEN.o(),
									/* Primitive failure variable type */
									anyMeta()))))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Optional label declaration. */
					zeroOrOneOf(
						/* Label parts. */
						tupleTypeForTypes(
							/* Label name */
							TOKEN.o(),
							/* Optional label return type. */
							zeroOrOneOf(
								/* Label return type. */
								topMeta())))),
				/* Macro argument is a phrase. */
				LIST_PHRASE.create(
					/* Statements and declarations so far. */
					zeroOrMoreOf(
						/* The "_!" mechanism wrapped each statement inside a
						 * literal phrase, so expect a phrase here instead of
						 * TOP.o().
						 */
						STATEMENT_PHRASE.mostGeneralType())),
				/* Optional return expression */
				LIST_PHRASE.create(
					zeroOrOneOf(
						PARSE_PHRASE.create(ANY.o())))),
			TOP.o())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_INCONSISTENT_PREFIX_FUNCTION))
	}

}