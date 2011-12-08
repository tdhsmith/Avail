/**
 * L2LoadConstantInstruction.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.interpreter.levelTwo.instruction;

import static com.avail.interpreter.levelTwo.L2Operation.L2_doMoveFromConstant_destObject_;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@L2LoadConstantInstruction} stores a literal {@linkplain AvailObject object}
 * into the specified {@linkplain L2ObjectRegister register}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2LoadConstantInstruction
extends L2Instruction
{
	/** A literal {@linkplain AvailObject object}. */
	private final @NotNull AvailObject literal;

	/** The destination {@linkplain L2ObjectRegister register}. */
	private final @NotNull L2ObjectRegister destinationRegister;

	/**
	 * Construct a new {@link L2LoadConstantInstruction}.
	 *
	 * @param literal
	 *        A literal {@linkplain AvailObject object}.
	 * @param destinationRegister
	 *        The destination {@linkplain L2ObjectRegister register}.
	 */
	public L2LoadConstantInstruction (
		final @NotNull AvailObject literal,
		final @NotNull L2ObjectRegister destinationRegister)
	{
		this.literal = literal;
		this.destinationRegister = destinationRegister;
	}

	@Override
	public @NotNull List<L2Register> sourceRegisters ()
	{
		return Collections.emptyList();
	}

	@Override
	public @NotNull List<L2Register> destinationRegisters ()
	{
		return Collections.<L2Register>singletonList(destinationRegister);
	}

	@Override
	public void emitOn (final @NotNull L2CodeGenerator codeGenerator)
	{
		codeGenerator.emitL2Operation(
			L2_doMoveFromConstant_destObject_);
		codeGenerator.emitLiteral(literal);
		codeGenerator.emitObjectRegister(destinationRegister);
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		translator.registerTypeAtPut(destinationRegister, literal.kind());
		translator.registerConstantAtPut(destinationRegister, literal);
	}
}
