/**
 * L2_CREATE_FUNCTION.java
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

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.optimizer.Continuation1NotNullThrowsReification;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import java.util.List;

import static com.avail.descriptor.FunctionDescriptor.createExceptOuters;
import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * Synthesize a new {@link FunctionDescriptor function} from the provided
 * constant compiled code and the vector of captured ("outer") variables.
 */
public class L2_CREATE_FUNCTION extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_CREATE_FUNCTION().init(
			CONSTANT.is("compiled code"),
			READ_VECTOR.is("captured variables"),
			WRITE_POINTER.is("new function"));

	@Override
	public Continuation1NotNullThrowsReification<Interpreter> actionFor (
		final L2Instruction instruction)
	{
		final A_RawFunction code = instruction.constantAt(0);
		final List<L2ReadPointerOperand> outerRegs =
			instruction.readVectorRegisterAt(1);
		final int newFunctionRegIndex =
			instruction.writeObjectRegisterAt(2).finalIndex();

		final int numOuters = outerRegs.size();
		assert numOuters == code.numOuters();
		final int[] outerRegNumbers = new int[numOuters];
		for (int i = 0; i < numOuters; i++)
		{
			outerRegNumbers[i] = outerRegs.get(i).finalIndex();
		}
		return interpreter ->
		{
			final A_Function function = createExceptOuters(code, numOuters);
			for (int i = 0; i < numOuters; i++)
			{
				function.outerVarAtPut(
					i + 1, interpreter.pointerAt(outerRegNumbers[i]));
			}
			interpreter.pointerAtPut(newFunctionRegIndex, function);
		};
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final A_RawFunction code = instruction.constantAt(0);
		final List<L2ReadPointerOperand> outerRegs =
			instruction.readVectorRegisterAt(1);
		final L2WritePointerOperand newFunctionReg =
			instruction.writeObjectRegisterAt(2);

		registerSet.typeAtPut(
			newFunctionReg.register(), code.functionType(), instruction);
		if (registerSet.allRegistersAreConstant(outerRegs))
		{
			// This can be replaced with a statically constructed function
			// during regeneration, but for now capture the exact function that
			// will be constructed.
			final int numOuters = outerRegs.size();
			assert numOuters == code.numOuters();
			final A_Function function = createExceptOuters(code, numOuters);
			for (int i = 1; i <= numOuters; i++)
			{
				function.outerVarAtPut(
					i,
					registerSet.constantAt(outerRegs.get(i - 1).register()));
			}
			registerSet.constantAtPut(
				newFunctionReg.register(), function, instruction);
		}
		else
		{
			registerSet.removeConstantAt(newFunctionReg.register());
		}
	}

	@Override
	public boolean extractFunctionOuterRegister (
		final L2Instruction instruction,
		final L2ReadPointerOperand functionRegister,
		final int outerIndex,
		final L2WritePointerOperand targetRegisterWrite,
		final L1Translator translator)
	{
//		final A_RawFunction code = instruction.constantAt(0);
		final List<L2ReadPointerOperand> outerRegs =
			instruction.readVectorRegisterAt(1);
//		final int newFunctionRegIndex =
//			instruction.writeObjectRegisterAt(2).finalIndex();

		final L2ReadPointerOperand outerRegister =
			outerRegs.get(outerIndex - 1);
		translator.moveRegister(outerRegister, targetRegisterWrite);
		return true;
	}

	/**
	 * Extract the constant {@link A_RawFunction} from the given {@link
	 * L2Instruction}, which must have {@code L2_CREATE_FUNCTION} as its
	 * operation.
	 *
	 * @param instruction
	 *        The instruction to examine.
	 * @return The constant {@link A_RawFunction} extracted from the
	 *         instruction.
	 */
	public static A_RawFunction getConstantCodeFrom (
		final L2Instruction instruction)
	{
		assert instruction.operation == instance;
		return instruction.constantAt(0);
	}
}
