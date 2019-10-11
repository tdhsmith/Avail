/*
 * P_FileTruncate.java
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

package com.avail.interpreter.primitive.files

import com.avail.AvailRuntime
import com.avail.descriptor.A_Atom
import com.avail.descriptor.A_BasicObject
import com.avail.descriptor.A_Fiber
import com.avail.descriptor.A_Function
import com.avail.descriptor.A_Number
import com.avail.descriptor.A_Type
import com.avail.descriptor.AtomDescriptor
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.io.IOSystem.FileHandle
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import java.io.IOException
import java.nio.channels.AsynchronousFileChannel
import java.util.Collections

import com.avail.AvailRuntime.currentRuntime
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.AtomDescriptor.SpecialAtom.FILE_KEY
import com.avail.descriptor.FiberDescriptor.newFiber
import com.avail.descriptor.FiberTypeDescriptor.fiberType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceTypeDescriptor.instanceType
import com.avail.descriptor.IntegerRangeTypeDescriptor.bytes
import com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.StringDescriptor.formatString
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_INVALID_HANDLE
import com.avail.exceptions.AvailErrorCode.E_IO_ERROR
import com.avail.exceptions.AvailErrorCode.E_NOT_OPEN_FOR_WRITE
import com.avail.exceptions.AvailErrorCode.E_SPECIAL_ATOM
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import java.util.Collections.singletonList

/**
 * **Primitive:** If the specified size is less than the size
 * of the indicated [writable][FileHandle.getCanWrite] [ ] associated with the [ ], then reduce its size as indicated, discarding any
 * data beyond the new file size.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_FileTruncate : Primitive(5, CanInline, HasSideEffect)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(5)
		val atom = interpreter.argument(0)
		val sizeObject = interpreter.argument(1)
		val succeed = interpreter.argument(2)
		val fail = interpreter.argument(3)
		val priority = interpreter.argument(4)

		val pojo = atom.getAtomProperty(FILE_KEY.atom)
		if (pojo.equalsNil())
		{
			return interpreter.primitiveFailure(
				if (atom.isAtomSpecial) E_SPECIAL_ATOM else E_INVALID_HANDLE)
		}
		val handle = pojo.javaObjectNotNull<FileHandle>()
		if (!handle.canWrite)
		{
			return interpreter.primitiveFailure(E_NOT_OPEN_FOR_WRITE)
		}
		val fileChannel = handle.channel
		// Truncating to something beyond the file size has no effect, so use
		// Long.MAX_VALUE if the newSize is bigger than that.
		val size = if (sizeObject.isLong)
			sizeObject.extractLong()
		else
			java.lang.Long.MAX_VALUE
		val runtime = currentRuntime()
		// Guaranteed non-negative by argument constraint.
		assert(size >= 0L)

		val priorityInt = priority.extractInt()
		val current = interpreter.fiber()
		val newFiber = newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priorityInt
		) {
			formatString("Asynchronous truncate, %s",
			             handle.filename)
		}
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		newFiber.availLoader(current.availLoader())
		// Share and inherit any heritable variables.
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared())
		// Inherit the fiber's text interface.
		newFiber.textInterface(current.textInterface())
		// Share everything that will potentially be visible to the fiber.
		newFiber.makeShared()
		succeed.makeShared()
		fail.makeShared()

		runtime.ioSystem().executeFileTask(Runnable {
	           try
	           {
	               fileChannel.truncate(size)
	           }
	           catch (e: IOException)
	           {
	               Interpreter.runOutermostFunction(
	                   runtime,
	                   newFiber,
	                   fail,
	                   listOf(E_IO_ERROR.numericCode()))
	               return@runtime.ioSystem().executeFileTask
	           }

	           Interpreter.runOutermostFunction(
	               runtime, newFiber, succeed, emptyList())
	       })

		return interpreter.primitiveSuccess(newFiber)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				ATOM.o(),
				wholeNumbers(),
				functionType(
					emptyTuple(),
					TOP.o()),
				functionType(
					tuple(instanceType(E_IO_ERROR.numericCode())),
					TOP.o()),
				bytes()),
			fiberType(TOP.o()))
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(
				E_INVALID_HANDLE,
				E_NOT_OPEN_FOR_WRITE,
				E_SPECIAL_ATOM))
	}

}