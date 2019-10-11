/*
 * P_Sleep.java
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

import com.avail.AvailRuntime
import com.avail.descriptor.A_Fiber
import com.avail.descriptor.A_Function
import com.avail.descriptor.A_Type
import com.avail.descriptor.AvailObject
import com.avail.descriptor.FiberDescriptor
import com.avail.descriptor.FiberDescriptor.ExecutionState
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import java.util.TimerTask

import com.avail.descriptor.FiberDescriptor.ExecutionState.ASLEEP
import com.avail.descriptor.FiberDescriptor.ExecutionState.SUSPENDED
import com.avail.descriptor.FiberDescriptor.InterruptRequestFlag.TERMINATION_REQUESTED
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InfinityDescriptor.positiveInfinity
import com.avail.descriptor.IntegerDescriptor.zero
import com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.Primitive.Flag.Unknown
import com.avail.utility.Nulls.stripNull

/**
 * **Primitive:** Put the [ current][FiberDescriptor.currentFiber] [fiber][FiberDescriptor] to [ ][ExecutionState.ASLEEP] for at least the specified number of
 * milliseconds. If the sleep time is zero (`0`), then return immediately.
 * If the sleep time is too big (i.e., greater than the maximum delay supported
 * by the operating system), then sleep forever.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_Sleep : Primitive(1, CannotFail, CanSuspend, Unknown)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(1)
		val sleepMillis = interpreter.argument(0)
		// If the requested sleep time is 0 milliseconds, then return
		// immediately. We could have chosen to yield here, but it was better to
		// make sleep and yield behave differently.
		if (sleepMillis.equalsInt(0))
		{
			return interpreter.primitiveSuccess(nil)
		}
		val fiber = interpreter.fiber()
		// If the requested sleep time isn't colossally big, then arrange for
		// the fiber to resume later. If the delay is too big, then the fiber
		// will only awaken due to interruption.
		val runtime = interpreter.runtime()
		val primitiveFunction = stripNull(interpreter.function)
		if (sleepMillis.isLong)
		{
			// Otherwise, delay the resumption of this task.
			val task = object : TimerTask()
			{
				override fun run()
				{
					fiber.lock {
						// Only resume the fiber if it's still asleep. A
						// termination request may have already woken the
						// fiber up, but so recently that it didn't manage
						// to cancel this timer task.
						if (fiber.executionState() === ASLEEP)
						{
							fiber.wakeupTask(null)
							fiber.executionState(SUSPENDED)
							Interpreter.resumeFromSuccessfulPrimitive(
								runtime,
								fiber,
								this@P_Sleep,
								nil)
						}
					}
				}
			}
			// Once the fiber has been unbound, transition it to sleeping and
			// start the timer task.
			interpreter.postExitContinuation {
				fiber.lock {
					// If termination has been requested, then schedule
					// the resumption of this fiber.
					if (fiber.interruptRequestFlag(TERMINATION_REQUESTED))
					{
						assert(fiber.executionState() === SUSPENDED)
						Interpreter.resumeFromSuccessfulPrimitive(
							runtime,
							fiber,
							this,
							nil)
						return@fiber.lock
					}
					fiber.wakeupTask(task)
					fiber.executionState(ASLEEP)
					runtime.timer.schedule(
						task,
						sleepMillis.extractLong())
				}
			}
		}
		else
		{
			// Once the fiber has been unbound, transition it to sleeping.
			interpreter.postExitContinuation {
				fiber.lock {
					// If termination has been requested, then schedule
					// the resumption of this fiber.
					if (fiber.interruptRequestFlag(TERMINATION_REQUESTED))
					{
						assert(fiber.executionState() === SUSPENDED)
						Interpreter.resumeFromSuccessfulPrimitive(
							runtime,
							fiber,
							this,
							nil)
						return@fiber.lock
					}
					fiber.executionState(ASLEEP)
				}
			}
		}// The delay was too big, so put the fiber to sleep forever.
		// Don't actually transition the fiber to the sleeping state, which
		// can only occur at task-scheduling time. This happens after the
		// fiber is unbound from the interpreter. Instead, suspend the fiber.
		return interpreter.primitiveSuspend(primitiveFunction)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(tuple(inclusive(
			zero(),
			positiveInfinity())), TOP.o())
	}

}