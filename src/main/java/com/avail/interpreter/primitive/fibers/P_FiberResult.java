/*
 * P_FiberResult.java
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

package com.avail.interpreter.primitive.fibers;

import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FiberDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.utility.MutableOrNull;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FiberTypeDescriptor.mostGeneralFiberType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.exceptions.AvailErrorCode.E_FIBER_PRODUCED_INCORRECTLY_TYPED_RESULT;
import static com.avail.exceptions.AvailErrorCode.E_FIBER_RESULT_UNAVAILABLE;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Answer the result of the specified
 * {@linkplain FiberDescriptor fiber}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_FiberResult
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_FiberResult().init(
			1, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_Fiber fiber = interpreter.argument(0);
		final MutableOrNull<Result> result = new MutableOrNull<>();
		fiber.lock(() ->
		{
			if (!fiber.executionState().indicatesTermination()
				|| fiber.fiberResult().equalsNil())
			{
				result.value = interpreter.primitiveFailure(
					E_FIBER_RESULT_UNAVAILABLE);
			}
			else
			{
				final AvailObject fiberResult = fiber.fiberResult();
				if (!fiberResult.isInstanceOf(fiber.kind().resultType()))
				{
					result.value = interpreter.primitiveFailure(
						E_FIBER_PRODUCED_INCORRECTLY_TYPED_RESULT);
				}
				else
				{
					result.value = interpreter.primitiveSuccess(
						fiber.fiberResult());
				}
			}
		});
		return result.value();
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return
			functionType(tuple(mostGeneralFiberType()), ANY.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(
			E_FIBER_RESULT_UNAVAILABLE,
			E_FIBER_PRODUCED_INCORRECTLY_TYPED_RESULT));
	}
}
