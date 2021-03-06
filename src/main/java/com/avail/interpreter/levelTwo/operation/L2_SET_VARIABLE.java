/*
 * L2_SET_VARIABLE.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.VariableDescriptor;
import com.avail.exceptions.VariableSetException;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Set;

import static com.avail.descriptor.VariableTypeDescriptor.mostGeneralVariableType;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.OFF_RAMP;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * Assign a value to a {@linkplain VariableDescriptor variable}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_SET_VARIABLE
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_SET_VARIABLE}.
	 */
	private L2_SET_VARIABLE ()
	{
		super(
			READ_BOXED.is("variable"),
			READ_BOXED.is("value to write"),
			PC.is("write succeeded", SUCCESS),
			PC.is("write failed", OFF_RAMP));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_SET_VARIABLE instance =
		new L2_SET_VARIABLE();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Generator generator)
	{
		final L2ReadBoxedOperand variable = instruction.operand(0);
//		final L2ReadBoxedOperand value = instruction.operand(1);
//		final L2PcOperand success = instruction.operand(2);
//		final L2PcOperand failure = instruction.operand(3);

		// The two register sets are clones, so only cross-check one of them.
		final RegisterSet registerSet = registerSets.get(0);
		assert registerSet.hasTypeAt(variable.register());
		final A_Type varType = registerSet.typeAt(variable.register());
		assert varType.isSubtypeOf(mostGeneralVariableType());
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2ReadBoxedOperand variable = instruction.operand(0);
		final L2ReadBoxedOperand value = instruction.operand(1);
//		final L2PcOperand success = instruction.operand(2);
//		final L2PcOperand failure = instruction.operand(3);

		final A_Type varType = registerSet.typeAt(variable.register());
		final A_Type valueType = registerSet.typeAt(value.register());
		if (valueType.isSubtypeOf(varType.writeType()))
		{
			// Type propagation has strengthened the value's type enough to
			// be able to avoid the check.
			generator.addInstruction(
				L2_SET_VARIABLE_NO_CHECK.instance,
				instruction.operand(0),
				instruction.operand(1),
				instruction.operand(2),
				instruction.operand(3));
			return true;
		}
		return super.regenerate(instruction, registerSet, generator);
	}

	@Override
	public boolean isVariableSet ()
	{
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand variable = instruction.operand(0);
		final L2ReadBoxedOperand value = instruction.operand(1);
//		final int successIndex = instruction.pcOffsetAt(2);
//		final L2PcOperand failure = instruction.operand(3);

		renderPreamble(instruction, builder);
		builder.append(" ↓");
		builder.append(variable.registerString());
		builder.append(" ← ");
		builder.append(value.registerString());
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedOperand variable = instruction.operand(0);
		final L2ReadBoxedOperand value = instruction.operand(1);
		final L2PcOperand success = instruction.operand(2);
		final L2PcOperand failure = instruction.operand(3);

		// :: try {
		final Label tryStart = new Label();
		final Label catchStart = new Label();
		method.visitTryCatchBlock(
			tryStart,
			catchStart,
			catchStart,
			getInternalName(VariableSetException.class));
		method.visitLabel(tryStart);
		// ::    variable.setValue(value);
		translator.load(method, variable.register());
		translator.load(method, value.register());
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_Variable.class),
			"setValue",
			getMethodDescriptor(VOID_TYPE, getType(A_BasicObject.class)),
			true);
		// ::    goto success;
		// Note that we cannot potentially eliminate this branch with a
		// fall through, because the next instruction expects a
		// VariableSetException to be pushed onto the stack. So always do the
		// jump.
		method.visitJumpInsn(GOTO, translator.labelFor(success.offset()));
		// :: } catch (VariableSetException) {
		method.visitLabel(catchStart);
		method.visitInsn(POP);
		// ::    goto failure;
		translator.jump(method, instruction, failure);
		// :: }
	}
}
