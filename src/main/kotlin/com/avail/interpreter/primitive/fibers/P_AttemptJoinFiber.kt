/*
 * P_AttemptJoinFiber.java
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

package com.avail.interpreter.primitive.fibers

import com.avail.descriptor.A_Fiber
import com.avail.descriptor.A_Function
import com.avail.descriptor.A_Type
import com.avail.descriptor.FiberDescriptor
import com.avail.descriptor.FiberDescriptor.ExecutionState
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FiberDescriptor.SynchronizationFlag.PERMIT_UNAVAILABLE
import com.avail.descriptor.FiberTypeDescriptor.mostGeneralFiberType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_FIBER_CANNOT_JOIN_ITSELF
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.Unknown
import com.avail.utility.Nulls.stripNull

/**
 * **Primitive:** If the [fiber][FiberDescriptor] has
 * already [terminated][ExecutionState.indicatesTermination], then
 * answer right away; otherwise, record the current fiber as a joiner of the
 * specified fiber, and attempt to [park][ExecutionState.PARKED].
 *
 *
 * To avoid potential deadlock from having multiple fiber locks held by the
 * same thread, we give best effort at removing this fiber from the joinee's set
 * of joining fibers in the event of an unpark.  Similarly, a termination of the
 * joinee may happen between adding this fiber to the set and transitioning this
 * fiber to a parked state.  That will simply be dealt with as a spurious
 * unpark.  Note that in this case, the unpark logic should expect that a
 * joining fiber
 *
 *
 * It may also be the case that
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_AttemptJoinFiber : Primitive(1, CanSuspend, Unknown)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(1)
		val joinee = interpreter.argument(0)
		val current = interpreter.fiber()
		// Forbid auto-joining.
		if (current.equals(joinee))
		{
			return interpreter.primitiveFailure(E_FIBER_CANNOT_JOIN_ITSELF)
		}
		val succeed = joinee.lock<Boolean> {
			if (joinee.executionState().indicatesTermination())
			{
				return@joinee.lock true
			}
			// Add to the joinee's set of joining fibers.  To avoid deadlock,
			// this step is done while holding only the joinee's lock, which
			// leads to the case where it's added as a joiner before attempting
			// to park.  In this case, a sudden termination of the joinee will
			// attempt to unpark the joiner, but it won't be in a parked state,
			// so it will just get a permit.
			//
			// A second scenario is if the joiner gets a termination request. To
			// avoid deadlock, there's a window between the unparking logic,
			// which requires a lock on the joiner, and removing the joiner from
			// the joinee, which requires a lock on the joinee.  To avoid both
			// locks needing to be held at once, we do these steps separately.
			// This leads to a rare case where the joinee terminates and unparks
			// its joiners, this one included, even though it's technically no
			// longer parked but still appears in the set.  This is treated as a
			// spurious unpark.
			//
			// In that case, the unparking logic may notice that the joiner is
			// no longer in the joinee's set – in fact, the set will have been
			// replaced by nil.  The attempt to remove the joiner from the set
			// is simply skipped in that case.
			joinee.joiningFibers(
				joinee.joiningFibers().setWithElementCanDestroy(
					current, false))
			false
		}
		return if (succeed)
		{
			interpreter.primitiveSuccess(nil)
		}
		else current.lock<Primitive.Result> {
			// If permit is not available, then park this fiber.
			if (current.getAndSetSynchronizationFlag(
					PERMIT_UNAVAILABLE, true))
			{
				return@current.lock interpreter . primitivePark stripNull<A_Function>(interpreter.function)
			}
			else
			{
				return@current.lock interpreter . primitiveSuccess nil
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				mostGeneralFiberType()),
			TOP.o())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(E_FIBER_CANNOT_JOIN_ITSELF))
	}

}