/*
 * P_SimpleMacroDefinitionForAtom.java
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

package com.avail.interpreter.primitive.methods;

import com.avail.AvailTask;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.descriptor.PhraseDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.exceptions.MalformedMessageException;
import com.avail.exceptions.SignatureException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import static com.avail.compiler.splitter.MessageSplitter.possibleErrors;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.*;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanSuspend;
import static com.avail.interpreter.Primitive.Flag.Unknown;
import static com.avail.interpreter.Primitive.Result.FIBER_SUSPENDED;
import static com.avail.utility.Nulls.stripNull;

/**
 * <strong>Primitive:</strong> Simple macro definition.  The first argument
 * is the macro name, and the second argument is a {@linkplain TupleDescriptor
 * tuple} of {@linkplain FunctionDescriptor functions} returning ⊤, one for each
 * occurrence of a {@linkplain Metacharacter#SECTION_SIGN section sign} (§)
 * in the macro name.  The third argument is the function to invoke for the
 * complete macro.  It is constrained to answer a {@linkplain
 * PhraseDescriptor phrase}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_SimpleMacroDefinitionForAtom
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_SimpleMacroDefinitionForAtom().init(
			3, CanSuspend, Unknown);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
		final A_Atom atom = interpreter.argument(0);
		final A_Tuple prefixFunctions = interpreter.argument(1);
		final A_Function function = interpreter.argument(2);

		final A_Fiber fiber = interpreter.fiber();
		final @Nullable AvailLoader loader = fiber.availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		if (!loader.phase().isExecuting())
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION);
		}
		for (final A_Function prefixFunction : prefixFunctions)
		{
			final int numArgs = prefixFunction.code().numArgs();
			final A_Type kind = prefixFunction.kind();
			final A_Type argsKind = kind.argsTupleType();
			for (int argIndex = 1; argIndex <= numArgs; argIndex++)
			{
				if (!argsKind.typeAtIndex(argIndex).isSubtypeOf(
					PARSE_PHRASE.mostGeneralType()))
				{
					return interpreter.primitiveFailure(
						E_MACRO_PREFIX_FUNCTION_ARGUMENT_MUST_BE_A_PARSE_NODE);
				}
			}
			if (!kind.returnType().isTop())
			{
				return interpreter.primitiveFailure(
					E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP);
			}
		}
		try
		{
			final MessageSplitter splitter =
				atom.bundleOrCreate().messageSplitter();
			if (prefixFunctions.tupleSize()
				!= splitter.numberOfSectionCheckpoints)
			{
				return interpreter.primitiveFailure(
					E_MACRO_PREFIX_FUNCTION_INDEX_OUT_OF_BOUNDS);
			}
		}
		catch (final MalformedMessageException e)
		{
			return interpreter.primitiveFailure(e.errorCode());
		}
		final int numArgs = function.code().numArgs();
		final A_Type kind = function.kind();
		final A_Type argsKind = kind.argsTupleType();
		for (int argIndex = 1; argIndex <= numArgs; argIndex++)
		{
			if (!argsKind.typeAtIndex(argIndex).isSubtypeOf(
				PARSE_PHRASE.mostGeneralType()))
			{
				return interpreter.primitiveFailure(
					E_MACRO_ARGUMENT_MUST_BE_A_PARSE_NODE);
			}
		}
		if (!kind.returnType().isSubtypeOf(PARSE_PHRASE.mostGeneralType()))
		{
			return interpreter.primitiveFailure(
				E_MACRO_MUST_RETURN_A_PARSE_NODE);
		}
		final A_Function primitiveFunction = stripNull(interpreter.function);
		assert primitiveFunction.code().primitive() == this;
		final List<AvailObject> copiedArgs =
			new ArrayList<>(interpreter.argsBuffer);
		interpreter.primitiveSuspend(primitiveFunction);
		interpreter.runtime().whenLevelOneSafeDo(
			fiber.priority(),
			AvailTask.forUnboundFiber(
				fiber,
				() ->
				{
					try
					{
						loader.addMacroBody(
							atom, function, prefixFunctions);
						int counter = 1;
						for (final A_Function prefixFunction
							: prefixFunctions)
						{
							prefixFunction.code().setMethodName(
								formatString("Macro prefix #%d of %s",
									counter, atom.atomName()));
							counter++;
						}
						function.code().setMethodName(
							formatString("Macro body of %s",
								atom.atomName()));
						Interpreter.resumeFromSuccessfulPrimitive(
							currentRuntime(),
							fiber,
							this,
							nil);
					}
					catch (
						final MalformedMessageException
							| SignatureException e)
					{
						Interpreter.resumeFromFailedPrimitive(
							currentRuntime(),
							fiber,
							e.numericCode(),
							primitiveFunction,
							copiedArgs);
					}
				}));
		return FIBER_SUSPENDED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				ATOM.o(),
				zeroOrMoreOf(mostGeneralFunctionType()),
				functionTypeReturning(PARSE_PHRASE.mostGeneralType())),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(E_LOADING_IS_OVER, E_CANNOT_DEFINE_DURING_COMPILATION,
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_REDEFINED_WITH_SAME_ARGUMENT_TYPES,
				E_MACRO_PREFIX_FUNCTION_ARGUMENT_MUST_BE_A_PARSE_NODE,
				E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP,
				E_MACRO_ARGUMENT_MUST_BE_A_PARSE_NODE,
				E_MACRO_MUST_RETURN_A_PARSE_NODE,
				E_MACRO_PREFIX_FUNCTION_INDEX_OUT_OF_BOUNDS)
				.setUnionCanDestroy(possibleErrors, true));
	}
}
