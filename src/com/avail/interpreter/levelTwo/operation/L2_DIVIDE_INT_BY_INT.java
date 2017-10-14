/**
 * L2_DIVIDE_INT_BY_INT.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
 *   may be used to endorse or promote products derived set this software
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
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;

import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * If the divisor is zero, then jump to the zero divisor label.  Otherwise
 * divide the dividend int by the divisor int.  If the quotient and remainder
 * each fit in an {@code int}, then store them and continue, otherwise jump to
 * an out-of-range label.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_DIVIDE_INT_BY_INT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_DIVIDE_INT_BY_INT().init(
			READ_INT.is("dividend"),
			READ_INT.is("divisor"),
			WRITE_INT.is("quotient"),
			WRITE_INT.is("remainder"),
			PC.is("out of range"),
			PC.is("zero divisor"),
			PC.is("success"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ReadIntOperand dividendReg = instruction.readIntRegisterAt(0);
		final L2ReadIntOperand divisorReg = instruction.readIntRegisterAt(1);
		final L2WriteIntOperand quotientReg = instruction.writeIntRegisterAt(2);
		final L2WriteIntOperand remainderReg =
			instruction.writeIntRegisterAt(3);
		final int outOfRangeIndex = instruction.pcOffsetAt(4);
		final int zeroDivisorIndex = instruction.pcOffsetAt(5);
		final int successIndex = instruction.pcOffsetAt(6);

		final long divisor = divisorReg.in(interpreter);
		if (divisor == 0)
		{
			interpreter.offset(zeroDivisorIndex);
			return;
		}
		final long dividend = dividendReg.in(interpreter);
		final long quotient;
		if (divisor < 0)
		{
			// a/b for b<0:  use -(a/-b)
			quotient = -(dividend / -divisor);
		}
		else if (dividend < 0)
		{
			// a/b for a<0, b>0:  use -1-(-1-a)/b
			quotient = -1L - ((-1L - dividend) / divisor);
		}
		else
		{
			quotient = dividend / divisor;
		}
		// Remainder is always non-negative.
		final long remainder = dividend - (quotient * divisor);
		if (quotient == (int)quotient && remainder == (int)remainder)
		{
			quotientReg.set((int)quotient, interpreter);
			remainderReg.set((int)remainder, interpreter);
			interpreter.offset(successIndex);
		}
		else
		{
			interpreter.offset(outOfRangeIndex);
		}
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps for division by zero or out-of-range.
		return true;
	}
}
