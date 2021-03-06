/*
 * P_RaiseException.java
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
package com.avail.interpreter.primitive.controlflow;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ObjectDescriptor.objectFromMap;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTypeDescriptor.exceptionType;
import static com.avail.descriptor.ObjectTypeDescriptor.stackDumpAtom;
import static com.avail.interpreter.Primitive.Flag.CanSuspend;
import static com.avail.interpreter.Primitive.Flag.CanSwitchContinuations;
import static com.avail.utility.Nulls.stripNull;

/**
 * <strong>Primitive:</strong> Raise an exception. Scan the stack of
 * {@linkplain ContinuationDescriptor continuations} until one is found for
 * a {@linkplain FunctionDescriptor function} whose {@link A_RawFunction code}
 * is {@link P_CatchException}. Get that continuation's second argument (a
 * handler block of one argument), and check if that handler block will accept
 * {@code exceptionValue}. If not, keep looking. If it will accept it, unwind
 * the stack so that the {@link P_CatchException} continuation is the top entry,
 * and invoke the handler block with {@code exceptionValue}. If there is no
 * suitable handler block, then fail this primitive (with the unhandled
 * exception).
 */
public final class P_RaiseException
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_RaiseException().init(
			1, CanSuspend, CanSwitchContinuations);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_BasicObject exception = interpreter.argument(0);
		// Attach the current continuation to the exception, so that a stack
		// dump can be obtained later.
		final A_Map fieldMap = exception.fieldMap();
		final A_Map newFieldMap = fieldMap.mapAtPuttingCanDestroy(
			stackDumpAtom(),
			stripNull(interpreter.reifiedContinuation).makeImmutable(),
			false);
		final AvailObject newException = objectFromMap(newFieldMap);
		// Search for an applicable exception handler, and invoke it if found.
		return interpreter.searchForExceptionHandler(newException);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return
			functionType(
				tuple(
					exceptionType()),
				bottom());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return exceptionType();
	}
}
