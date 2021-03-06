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

package com.avail.interpreter.primitive.integers;

import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerDescriptor.fromLong;
import static com.avail.descriptor.IntegerDescriptor.zero;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.*;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Compute the bitwise AND of the {@linkplain
 * IntegerDescriptor arguments}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_BitwiseAnd
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_BitwiseAnd().init(
			2, CannotFail, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Number a = interpreter.argument(0);
		final A_Number b = interpreter.argument(1);
		return interpreter.primitiveSuccess(a.bitwiseAnd(b, true));
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		assert argumentTypes.size() == 2;
		final A_Type aRange = argumentTypes.get(0);
		final A_Type bRange = argumentTypes.get(1);

		// If either value is constrained to a positive range, then at least
		// guarantee the bit-wise and can't be greater than or equal to the next
		// higher power of two of that range's upper bound.
		final long upper;
		if (aRange.lowerBound().greaterOrEqual(zero())
			&& aRange.upperBound().isLong())
		{
			if (bRange.lowerBound().greaterOrEqual(zero())
				&& bRange.upperBound().isLong())
			{
				upper = Math.min(
					aRange.upperBound().extractLong(),
					bRange.upperBound().extractLong());
			}
			else
			{
				upper = aRange.upperBound().extractLong();
			}
		}
		else if (bRange.lowerBound().greaterOrEqual(zero())
			&& bRange.upperBound().isLong())
		{
			upper = bRange.upperBound().extractLong();
		}
		else
		{
			// Give up, as the result may be negative or exceed a long.
			return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes);
		}
		// At least one value is positive, so the result is positive.
		// At least one is a long, so the result must be a long.
		final long highOneBit = Long.highestOneBit(upper);
		if (highOneBit == 0)
		{
			// One of the ranges is constrained to be exactly zero.
			return singleInt(0);
		}
		final long maxValue = (highOneBit - 1) | highOneBit;
		return integerRangeType(zero(), true, fromLong(maxValue), true);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				integers(),
				integers()),
			integers());
	}
}
