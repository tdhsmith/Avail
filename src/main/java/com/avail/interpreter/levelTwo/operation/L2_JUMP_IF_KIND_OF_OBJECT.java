/*
 * L2_JUMP_IF_KIND_OF_OBJECT.java
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
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED;
import static com.avail.interpreter.levelTwo.operation.L2ConditionalJump.BranchReduction.*;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.*;

/**
 * Jump to the target if the value is an instance of the type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_JUMP_IF_KIND_OF_OBJECT
extends L2ConditionalJump
{
	/**
	 * Construct an {@code L2_JUMP_IF_KIND_OF_OBJECT}.
	 */
	private L2_JUMP_IF_KIND_OF_OBJECT ()
	{
		super(
			READ_BOXED.is("value"),
			READ_BOXED.is("type"),
			PC.is("is kind", SUCCESS),
			PC.is("if not kind", FAILURE));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_JUMP_IF_KIND_OF_OBJECT instance =
		new L2_JUMP_IF_KIND_OF_OBJECT();

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand value = instruction.operand(0);
		final L2ReadBoxedOperand type = instruction.operand(1);
		final L2PcOperand ifKind = instruction.operand(2);
		final L2PcOperand ifNotKind = instruction.operand(3);

		// Ensure the new write ends up in the same synonym as the source.
		value.instructionWasAdded(instruction, manifest);
		type.instructionWasAdded(instruction, manifest);
		ifKind.instructionWasAdded(instruction, manifest);
		ifNotKind.instructionWasAdded(instruction, manifest);

		// Restrict the value to the type along the ifKind branch, but because
		// the provided type can be more specific at runtime, we can't restrict
		// the ifNotKind branch.
		ifKind.manifest().intersectType(
			value.semanticValue(),
			type.type().instance());
	}

	@Override
	public BranchReduction branchReduction (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator generator)
	{
		final L2ReadBoxedOperand value = instruction.operand(0);
		final L2ReadBoxedOperand type = instruction.operand(1);
//		final L2PcOperand ifKind = instruction.operand(2);
//		final L2PcOperand ifNotKind = instruction.operand(3);

		if (registerSet.hasConstantAt(type.register()))
		{
			// Replace this compare-and-branch with one that compares against
			// a constant.  This further opens the possibility of eliminating
			// one of the branches.
			final A_Type constantType =
				registerSet.constantAt(type.register());
			final A_Type valueType = registerSet.typeAt(value.register());
			if (valueType.isSubtypeOf(constantType))
			{
				return AlwaysTaken;
			}
			if (valueType.typeIntersection(constantType).isBottom())
			{
				return NeverTaken;
			}
		}
		return SometimesTaken;
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Generator generator)
	{
		final L2ReadBoxedOperand valueReg = instruction.operand(0);
		final L2ReadBoxedOperand typeReg = instruction.operand(1);
//		final L2PcOperand ifKind = instruction.operand(2);
//		final L2PcOperand ifNotKind = instruction.operand(3);

		assert registerSets.size() == 2;
		final RegisterSet isKindSet = registerSets.get(0);
//		final RegisterSet isNotKindSet = registerSets.get(1);

		final A_Type type;
		if (isKindSet.hasConstantAt(typeReg.register()))
		{
			// The type is statically known.
			type = isKindSet.constantAt(typeReg.register());
		}
		else
		{
			// The exact type being tested against isn't known, but the metatype
			// is.
			assert isKindSet.hasTypeAt(typeReg.register());
			final A_Type meta = isKindSet.typeAt(typeReg.register());
			assert meta.isInstanceMeta();
			type = meta.instance();
		}
		isKindSet.strengthenTestedTypeAtPut(
			valueReg.register(),
			type.typeIntersection(isKindSet.typeAt(valueReg.register())));
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand value = instruction.operand(0);
		final L2ReadBoxedOperand type = instruction.operand(1);
		final L2PcOperand ifKind = instruction.operand(2);
		final L2PcOperand ifNotKind = instruction.operand(3);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(value.registerString());
		builder.append(" ∈ ");
		builder.append(type.registerString());
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedOperand value = instruction.operand(0);
		final L2ReadBoxedOperand type = instruction.operand(1);
		final L2PcOperand ifKind = instruction.operand(2);
		final L2PcOperand ifNotKind = instruction.operand(3);

		// :: if (value.isInstanceOf(type)) goto isKind;
		// :: else goto isNotKind;
		translator.load(method, value.register());
		translator.load(method, type.register());
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_BasicObject.class),
			"isInstanceOf",
			getMethodDescriptor(BOOLEAN_TYPE, getType(A_Type.class)),
			true);
		emitBranch(translator, method, instruction, IFNE, ifKind, ifNotKind);
	}
}
