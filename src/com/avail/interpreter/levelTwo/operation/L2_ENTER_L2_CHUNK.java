/*
 * L2_ENTER_L2_CHUNK.java
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

import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.COMMENT;
import static com.avail.interpreter.levelTwo.L2OperandType.INT_IMMEDIATE;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.getDescriptor;
import static org.objectweb.asm.Type.getInternalName;
import static org.objectweb.asm.Type.getMethodDescriptor;

/**
 * This marks the entry point into optimized (level two) code.  At entry, the
 * arguments are expected to be in the {@link Interpreter#argsBuffer}.  Set up
 * fresh registers for this chunk, but do not write to them yet.
 *
 * <p>This instruction also occurs at places that a reified continuation can be
 * re-entered, such as returning into it, restarting it, or continuing it after
 * an interrupt has been handled.</p>
 */
public final class L2_ENTER_L2_CHUNK
extends L2Operation
{
	/**
	 * Construct an {@code L2_ENTER_L2_CHUNK}.
	 */
	private L2_ENTER_L2_CHUNK ()
	{
		super(
			INT_IMMEDIATE.is("entry point offset in default chunk"),
			COMMENT.is("chunk entry point name"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_ENTER_L2_CHUNK instance = new L2_ENTER_L2_CHUNK();

	@Override
	public boolean isEntryPoint (final L2Instruction instruction)
	{
		return true;
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
//		final int offsetInUnoptimizedChunk = instruction.intImmediateAt(0);

		assert this == instruction.operation();
		renderPreamble(instruction, builder);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final int offsetInUnoptimizedChunk = instruction.intImmediateAt(0);

		// :: if (!checkValidity()) {
		translator.loadInterpreter(method);
		translator.literal(method, offsetInUnoptimizedChunk);
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"checkValidity",
			getMethodDescriptor(
				BOOLEAN_TYPE,
				INT_TYPE),
			false);
		final Label isValidLabel = new Label();
		method.visitJumpInsn(IFNE, isValidLabel);
		// ::    return null;
		method.visitInsn(ACONST_NULL);
		method.visitInsn(ARETURN);
		// :: }
		method.visitLabel(isValidLabel);
	}
}
