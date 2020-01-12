/*
 * L2_JUMP_BACK.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.values.L2SemanticValue;
import org.objectweb.asm.MethodVisitor;

import java.util.HashSet;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED_VECTOR;

/**
 * Unconditionally jump to the level two offset in my {@link L2PcOperand}, while
 * also limiting that edge's {@link L2ValueManifest} to the
 * {@link L2ReadVectorOperand}'s reads.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_JUMP_BACK
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_JUMP_BACK}.
	 */
	private L2_JUMP_BACK ()
	{
		super(
			PC.is("target", SUCCESS),
			READ_BOXED_VECTOR.is("registers to keep"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_JUMP_BACK instance = new L2_JUMP_BACK();

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}

	@Override
	public boolean isUnconditionalJump ()
	{
		return true;
	}

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		final L2PcOperand target = instruction.operand(0);
		final L2ReadBoxedVectorOperand preservedReads = instruction.operand(1);

		// Play the reads against the old manifest, which is then filtered.
		preservedReads.instructionWasAdded(manifest);
		final Set<L2SemanticValue> semanticValuesToKeep = new HashSet<>();
		final Set<L2Register> registersToKeep = new HashSet<>();
		preservedReads.elements().forEach(readOperand ->
		{
			semanticValuesToKeep.add(readOperand.semanticValue());
			registersToKeep.add(readOperand.register());
		});
		manifest.retainSemanticValues(semanticValuesToKeep);
		manifest.retainRegisters(registersToKeep);

		target.instructionWasAdded(manifest);
	}

	/**
	 * Extract the target of the given jump-back instruction.
	 *
	 * @param instruction
	 *        The {@link L2Instruction} to examine.  Its {@link L2Operation}
	 *        must be an {@code L2_JUMP_BACK}.
	 * @return The {@link L2PcOperand} to which the instruction jumps.
	 */
	public static L2PcOperand jumpTarget (final L2Instruction instruction)
	{
		assert instruction.operation() == instance;
		return instruction.operand(0);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2PcOperand target = instruction.operand(0);

		// :: goto offset;
		translator.jump(method, instruction, target);
	}
}
