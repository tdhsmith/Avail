/*
 * P_BitwiseAnd.java
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

package com.avail.interpreter.primitive.integers

import com.avail.descriptor.A_Number
import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Type
import com.avail.descriptor.IntegerDescriptor
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerDescriptor.fromLong
import com.avail.descriptor.IntegerDescriptor.zero
import com.avail.descriptor.IntegerRangeTypeDescriptor.integerRangeType
import com.avail.descriptor.IntegerRangeTypeDescriptor.integers
import com.avail.descriptor.IntegerRangeTypeDescriptor.singleInt
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail

/**
 * **Primitive:** Compute the bitwise AND of the [ ].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_BitwiseAnd : Primitive(2, CannotFail, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(2)
		val a = interpreter.argument(0)
		val b = interpreter.argument(1)
		return interpreter.primitiveSuccess(a.bitwiseAnd(b, true))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		assert(argumentTypes.size == 2)
		val aRange = argumentTypes[0]
		val bRange = argumentTypes[1]

		// If either value is constrained to a positive range, then at least
		// guarantee the bit-wise and can't be greater than or equal to the next
		// higher power of two of that range's upper bound.
		val upper: Long
		if (aRange.lowerBound().greaterOrEqual(zero()) && aRange.upperBound().isLong)
		{
			if (bRange.lowerBound().greaterOrEqual(zero()) && bRange.upperBound().isLong)
			{
				upper = Math.min(
					aRange.upperBound().extractLong(),
					bRange.upperBound().extractLong())
			}
			else
			{
				upper = aRange.upperBound().extractLong()
			}
		}
		else if (bRange.lowerBound().greaterOrEqual(zero()) && bRange.upperBound().isLong)
		{
			upper = bRange.upperBound().extractLong()
		}
		else
		{
			// Give up, as the result may be negative or exceed a long.
			return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		}
		// At least one value is positive, so the result is positive.
		// At least one is a long, so the result must be a long.
		val highOneBit = java.lang.Long.highestOneBit(upper)
		if (highOneBit == 0L)
		{
			// One of the ranges is constrained to be exactly zero.
			return singleInt(0)
		}
		val maxValue = highOneBit - 1 or highOneBit
		return integerRangeType(zero(), true, fromLong(maxValue), true)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				integers(),
				integers()),
			integers())
	}

}