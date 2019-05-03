/*
 * L2FloatRegister.java
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

package com.avail.interpreter.levelTwo.register;

import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadFloatOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteFloatOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_MOVE_FLOAT;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.reoptimizer.L2Inliner;

import static com.avail.descriptor.TypeDescriptor.Types.DOUBLE;
import static com.avail.utility.Casts.cast;

/**
 * {@code L2FloatRegister} models the conceptual usage of a register that can
 * store a machine floating-point number.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2FloatRegister
extends L2Register
{
	@Override
	public RegisterKind registerKind ()
	{
		return RegisterKind.FLOAT;
	}

	/**
	 * Construct a new {@code L2FloatRegister}.
	 *
	 * @param debugValue
	 *        A value used to distinguish the new instance visually during
	 *        debugging of L2 translations.
	 * @param restriction
	 * 	      The {@link TypeRestriction}.
	 */
	public L2FloatRegister (
		final int debugValue,
		final TypeRestriction restriction)
	{
		super(debugValue, restriction);
	}

	@Override
	public L2ReadFloatOperand read (
		final TypeRestriction typeRestriction)
	{
		return new L2ReadFloatOperand(this, typeRestriction);
	}

	@Override
	public L2WriteFloatOperand write ()
	{
		return new L2WriteFloatOperand(this);
	}

	@Override
	public <R extends L2Register> R copyForTranslator (
		final L2Generator generator,
		final TypeRestriction typeRestriction)
	{
		return
			cast(new L2FloatRegister(generator.nextUnique(), typeRestriction));
	}

	@Override
	public L2FloatRegister copyAfterColoring ()
	{
		final L2FloatRegister result = new L2FloatRegister(
			finalIndex(), TypeRestriction.restriction(DOUBLE.o()));
		result.setFinalIndex(finalIndex());
		return result;
	}

	@Override
	public L2FloatRegister copyForInliner (final L2Inliner inliner)
	{
		return new L2FloatRegister(
			inliner.nextUnique(),
			restriction);
	}

	@Override
	public L2Operation phiMoveOperation ()
	{
		return L2_MOVE_FLOAT.instance;
	}

	@Override
	public String namePrefix ()
	{
		return "f";
	}
}
