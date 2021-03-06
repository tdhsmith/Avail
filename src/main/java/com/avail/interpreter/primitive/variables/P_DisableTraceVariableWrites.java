/*
 * P_DisableTraceVariableWrites.java
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

package com.avail.interpreter.primitive.variables;

import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.FiberDescriptor;
import com.avail.descriptor.FiberDescriptor.TraceFlag;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.descriptor.SetDescriptor;
import com.avail.descriptor.VariableDescriptor;
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_ILLEGAL_TRACE_MODE;
import static com.avail.interpreter.Primitive.Flag.HasSideEffect;

/**
 * <strong>Primitive:</strong> Disable {@linkplain
 * TraceFlag#TRACE_VARIABLE_WRITES variable write tracing} for the {@linkplain
 * FiberDescriptor#currentFiber() current fiber}. For each {@linkplain
 * VariableDescriptor variable} that survived tracing, accumulate the variable's
 * {@linkplain VariableAccessReactor write reactor} {@linkplain
 * FunctionDescriptor functions} into a {@linkplain SetDescriptor set}. Clear
 * the write reactors for each variable written. Answer the set of functions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_DisableTraceVariableWrites
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_DisableTraceVariableWrites().init(
			0, HasSideEffect);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(0);
		final A_Fiber fiber = interpreter.fiber();
		if (!fiber.traceFlag(TraceFlag.TRACE_VARIABLE_WRITES))
		{
			return interpreter.primitiveFailure(E_ILLEGAL_TRACE_MODE);
		}
		interpreter.setTraceVariableWrites(false);
		final A_Set written = fiber.variablesWritten();
		A_Set functions = emptySet();
		for (final A_Variable var : written)
		{
			functions = functions.setUnionCanDestroy(
				var.validWriteReactorFunctions(),
				true);
		}
		return interpreter.primitiveSuccess(functions);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			emptyTuple(),
			setTypeForSizesContentType(
				wholeNumbers(),
				functionType(
					emptyTuple(),
					TOP.o())));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return
			enumerationWith(set(E_ILLEGAL_TRACE_MODE));
	}
}
