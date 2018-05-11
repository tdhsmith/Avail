/*
 * L2ReadIntOperand.java
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.descriptor.A_Number;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.register.L2IntRegister;

import javax.annotation.Nullable;

/**
 * An {@code L2ReadIntOperand} is an operand of type {@link
 * L2OperandType#READ_INT}. It holds the actual {@link L2IntRegister} that
 * is to be accessed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2ReadIntOperand
extends L2ReadOperand<L2IntRegister, A_Number>
{
	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.READ_INT;
	}

	/**
	 * Construct a new {@code L2ReadIntOperand} for the specified {@link
	 * L2IntRegister} and optional restriction.
	 *
	 * @param register
	 *        The register.
	 * @param restriction
	 *        The further {@link TypeRestriction} to apply to this particular
	 *        read.
	 */
	public L2ReadIntOperand (
		final L2IntRegister register,
		final @Nullable TypeRestriction<A_Number> restriction)
	{
		super(register, restriction);
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}
}
