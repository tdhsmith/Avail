/*
 * L2_LOOKUP_BY_VALUES.java
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Method;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.exceptions.AvailException;
import com.avail.exceptions.MethodDefinitionException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2SelectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import static com.avail.AvailRuntimeSupport.captureNanos;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.SetDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.BOXED;
import static com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * Look up the method to invoke. Use the provided vector of arguments to
 * perform a polymorphic lookup. Write the resulting function into the
 * specified destination register. If the lookup fails, then branch to the
 * specified {@linkplain Interpreter#offset(int) offset}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_LOOKUP_BY_VALUES
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_LOOKUP_BY_VALUES}.
	 */
	private L2_LOOKUP_BY_VALUES ()
	{
		super(
			SELECTOR.is("message bundle"),
			READ_BOXED_VECTOR.is("arguments"),
			WRITE_BOXED.is("looked up function"),
			WRITE_BOXED.is("error code"),
			PC.is("lookup succeeded", SUCCESS),
			PC.is("lookup failed", FAILURE));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_LOOKUP_BY_VALUES instance =
		new L2_LOOKUP_BY_VALUES();

	/**
	 * The error codes that can be produced by a failed lookup.
	 */
	public static final A_Type lookupErrorsType = enumerationWith(
		set(
			E_NO_METHOD,
			E_NO_METHOD_DEFINITION,
			E_AMBIGUOUS_METHOD_DEFINITION,
			E_ABSTRACT_METHOD_DEFINITION,
			E_FORWARD_METHOD_DEFINITION));

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		assert this == instruction.operation();
		final L2SelectorOperand bundle = instruction.operand(0);
		final L2ReadBoxedVectorOperand argRegs = instruction.operand(1);
		final L2WriteBoxedOperand functionReg = instruction.operand(2);
		final L2WriteBoxedOperand errorCodeReg = instruction.operand(3);
		final L2PcOperand lookupSucceeded = instruction.operand(4);
		final L2PcOperand lookupFailed = instruction.operand(5);

		bundle.instructionWasAdded(instruction, manifest);
		argRegs.instructionWasAdded(instruction, manifest);
		functionReg.instructionWasAdded(instruction, manifest);
		errorCodeReg.instructionWasAdded(instruction, manifest);
		lookupSucceeded.instructionWasAdded(instruction, manifest);
		lookupFailed.instructionWasAdded(instruction, manifest);

		// If the lookup failed, it supplies the reason to the errorCodeReg.
		lookupFailed.manifest().setRestriction(
			errorCodeReg.semanticValue(),
			errorCodeReg.restriction());

		// If the lookup succeeds, the functionReg will be set, and we can also
		// conclude that the arguments satisfied at least one of the found
		// function types.
		lookupSucceeded.manifest().setRestriction(
			functionReg.semanticValue(),
			functionReg.restriction());
		// The function type should be an enumeration, so we know that each
		// argument satisfied at least one of the functions' corresponding
		// argument types.
		final List<L2ReadBoxedOperand> arguments = argRegs.elements();
		final A_Type functionType = functionReg.restriction().type;
		if (functionType.isEnumeration())
		{
			final int numArgs = arguments.size();
			final Set<A_Function> functions = toSet(functionType.instances());
			final A_Type argumentTupleUnionType = functions.stream()
				.map(f -> f.code().functionType().argsTupleType())
				.reduce(A_Type::typeUnion)
				.orElse(bottom());  // impossible
			for (int i = 1; i <= numArgs; i++)
			{
				final A_Type argumentUnion =
					argumentTupleUnionType.typeAtIndex(i);
				lookupSucceeded.manifest().intersectType(
					arguments.get(i - 1).semanticValue(),
					argumentUnion);
			}
			// If only one argument wasn't strongly typed enough to prove
			// statically, we could subtract the functions' argument types from
			// that argument register's restriction.  We won't bother here,
			// since this is the failed slow lookup case.
		}
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Generator generator)
	{
		// Find all possible definitions (taking into account the types of the
		// argument registers).  Then build an enumeration type over those
		// functions.
		final L2SelectorOperand bundleOperand = instruction.operand(0);
		final L2ReadBoxedVectorOperand argRegs = instruction.operand(1);
		final L2WriteBoxedOperand functionReg = instruction.operand(2);
		final L2WriteBoxedOperand errorCodeReg = instruction.operand(3);
//		final L2PcOperand lookupSucceeded = instruction.operand(4);
//		final L2PcOperand lookupFailed = instruction.operand(5);

		// If the lookup fails, then only the error code register changes.
		registerSets.get(0).typeAtPut(
			errorCodeReg.register(), lookupErrorsType, instruction);
		// If the lookup succeeds, then the situation is more complex.
		final RegisterSet registerSet = registerSets.get(1);
		final int numArgs = argRegs.elements().size();
		final List<TypeRestriction> argRestrictions = new ArrayList<>(numArgs);
		for (final L2ReadBoxedOperand argRegister : argRegs.elements())
		{
			final A_Type type = registerSet.hasTypeAt(argRegister.register())
				? registerSet.typeAt(argRegister.register())
				: ANY.o();
			argRestrictions.add(restrictionForType(type, BOXED));
		}
		// Figure out what could be invoked at runtime given these argument
		// type constraints.
		final List<A_Function> possibleFunctions = new ArrayList<>();
		final List<A_Definition> possibleDefinitions =
			bundleOperand.bundle.bundleMethod().definitionsAtOrBelow(
				argRestrictions);
		for (final A_Definition definition : possibleDefinitions)
		{
			if (definition.isMethodDefinition())
			{
				possibleFunctions.add(definition.bodyBlock());
			}
		}
		if (possibleFunctions.size() == 1)
		{
			// Only one function could be looked up (it's monomorphic for
			// this call site).  Therefore we know strongly what the
			// function is.
			registerSet.constantAtPut(
				functionReg.register(),
				possibleFunctions.get(0),
				instruction);
		}
		else
		{
			final A_Type enumType =
				enumerationWith(setFromCollection(possibleFunctions));
			registerSet.typeAtPut(
				functionReg.register(), enumType, instruction);
		}
	}

	/**
	 * Perform the lookup.
	 *
	 * @param interpreter
	 *        The {@link Interpreter}.
	 * @param bundle
	 *        The {@link A_Bundle}.
	 * @param values
	 *        The {@linkplain AvailObject values} for the lookup.
	 * @return The unique {@linkplain A_Function function}.
	 * @throws MethodDefinitionException
	 *         If the lookup did not resolve to a unique executable function.
	 */
	@ReferencedInGeneratedCode
	public static A_Function lookup (
			final Interpreter interpreter,
			final A_Bundle bundle,
			final AvailObject[] values)
		throws MethodDefinitionException
	{
		if (Interpreter.debugL2)
		{
			Interpreter.log(
				Interpreter.loggerDebugL2,
				Level.FINER,
				"{0}Lookup {1}",
				interpreter.debugModeString,
				bundle.message().atomName());
		}

		final List<AvailObject> valuesList = new ArrayList<>(values.length);
		Collections.addAll(valuesList, values);

		final A_Method method = bundle.bundleMethod();
		final long before = captureNanos();
		final A_Definition definitionToCall;
		try
		{
			definitionToCall = method.lookupByValuesFromList(valuesList);
		}
		finally
		{
			final long after = captureNanos();
			interpreter.recordDynamicLookup(bundle, after - before);
		}
		if (definitionToCall.isAbstractDefinition())
		{
			throw MethodDefinitionException.abstractMethod();
		}
		if (definitionToCall.isForwardDefinition())
		{
			throw MethodDefinitionException.forwardMethod();
		}
		return definitionToCall.bodyBlock();
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2SelectorOperand bundleOperand = instruction.operand(0);
		final L2ReadBoxedVectorOperand argRegs = instruction.operand(1);
		final L2WriteBoxedOperand functionReg = instruction.operand(2);
		final L2WriteBoxedOperand errorCodeReg = instruction.operand(3);
		final L2PcOperand lookupSucceeded = instruction.operand(4);
		final L2PcOperand lookupFailed = instruction.operand(5);

		// :: try {
		final Label tryStart = new Label();
		final Label catchStart = new Label();
		method.visitTryCatchBlock(
			tryStart,
			catchStart,
			catchStart,
			getInternalName(MethodDefinitionException.class));
		method.visitLabel(tryStart);
		// ::    function = lookup(interpreter, bundle, types);
		translator.loadInterpreter(method);
		translator.literal(method, bundleOperand.bundle);
		translator.objectArray(method, argRegs.elements(), AvailObject.class);
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(L2_LOOKUP_BY_VALUES.class),
			"lookup",
			getMethodDescriptor(
				getType(A_Function.class),
				getType(Interpreter.class),
				getType(A_Bundle.class),
				getType(AvailObject[].class)),
			false);
		translator.store(method, functionReg.register());
		// ::    goto lookupSucceeded;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// MethodDefinitionException to be pushed onto the stack. So always do
		// the jump.
		method.visitJumpInsn(
			GOTO, translator.labelFor(lookupSucceeded.offset()));
		// :: } catch (MethodDefinitionException e) {
		method.visitLabel(catchStart);
		// ::    errorCode = e.numericCode();
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(AvailException.class),
			"numericCode",
			getMethodDescriptor(getType(A_Number.class)),
			false);
		method.visitTypeInsn(
			CHECKCAST,
			getInternalName(AvailObject.class));
		translator.store(method, errorCodeReg.register());
		// ::    goto lookupFailed;
		translator.jump(method, instruction, lookupFailed);
		// :: }
	}
}
