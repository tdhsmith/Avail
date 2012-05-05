/**
 * L2_LOOKUP_BY_TYPES.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.levelTwo.L2Interpreter.debugL1;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.RegisterSet;

/**
 * Look up the method to invoke.  Use the provided vector of argument
 * <em>types</em> to perform a polymorphic lookup.  Write the resulting
 * function into the specified destination register.
 */
public class L2_LOOKUP_BY_TYPES extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_LOOKUP_BY_TYPES();

	static
	{
		instance.init(
			SELECTOR.is("method"),
			READ_VECTOR.is("argument types"),
			WRITE_POINTER.is("looked up function"));
	}

	@Override
	public void step (final @NotNull L2Interpreter interpreter)
	{
		final int selectorIndex = interpreter.nextWord();
		final int argumentTypesIndex = interpreter.nextWord();
		final int resultingFunctionIndex = interpreter.nextWord();
		final AvailObject vect = interpreter.vectorAt(argumentTypesIndex);
		interpreter.argsBuffer.clear();
		final int numArgs = vect.tupleSize();
		for (int i = 1; i <= numArgs; i++)
		{
			interpreter.argsBuffer.add(
				interpreter.pointerAt(vect.tupleIntAt(i)));
		}
		final AvailObject selector =
			interpreter.chunk().literalAt(selectorIndex);
		if (debugL1)
		{
			System.out.printf(
				"  --- looking up: %s%n",
				selector.name().name());
		}
		final AvailObject signatureToCall =
			selector.lookupByTypesFromList(interpreter.argsBuffer);
		if (signatureToCall.equalsNull())
		{
			error("Unable to find unique implementation for call");
			return;
		}
		if (!signatureToCall.isMethod())
		{
			error("Attempted to call a non-implementation signature");
			return;
		}
		interpreter.pointerAtPut(
			resultingFunctionIndex,
			signatureToCall.bodyBlock());
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		// Find all possible implementations (taking into account the types
		// of the argument registers).  Then build an enumeration type over
		// those functions.
		final L2SelectorOperand selectorOperand =
			(L2SelectorOperand) instruction.operands[0];
		final L2ReadVectorOperand argsTypesOperand =
			(L2ReadVectorOperand) instruction.operands[1];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[2];
		final List<L2ObjectRegister> argTypeRegisters =
			argsTypesOperand.vector.registers();
		final int numArgs = argTypeRegisters.size();
		final List<AvailObject> argTypeBounds =
			new ArrayList<AvailObject>(numArgs);
		for (final L2ObjectRegister argTypeRegister : argTypeRegisters)
		{
			AvailObject type = registers.constantAt(argTypeRegister);
			if (type == null)
			{
				final AvailObject meta = registers.typeAt(argTypeRegister);
				if (meta != null && !meta.equals(TYPE.o()))
				{
					assert meta.instanceCount().equals(
						IntegerDescriptor.one());
					type = meta.instances().asTuple().tupleAt(1);
				}
				else
				{
					type = TOP.o();
				}
			}
			argTypeBounds.add(type);
		}
		// Figure out what could be invoked at runtime given these argument
		// type constraints.
		final List<AvailObject> possibleFunctions =
			new ArrayList<AvailObject>();
		final List<AvailObject> possibleSignatures =
			selectorOperand.method.implementationsAtOrBelow(argTypeBounds);
		for (final AvailObject signature : possibleSignatures)
		{
			if (signature.isMethod())
			{
				possibleFunctions.add(signature.bodyBlock());
			}
		}
		if (possibleFunctions.size() == 1)
		{
			// Only one function could be looked up (it's monomorphic for
			// this call site).  Therefore we know strongly what the
			// function is.
			registers.constantAtPut(
				destinationOperand.register,
				possibleFunctions.get(0));
		}
		else
		{
			final AvailObject enumType =
				AbstractEnumerationTypeDescriptor.withInstances(
					SetDescriptor.fromCollection(possibleFunctions));
			registers.typeAtPut(destinationOperand.register, enumType);
		}
	}
}