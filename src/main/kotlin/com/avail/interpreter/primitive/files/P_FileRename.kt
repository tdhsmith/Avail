/*
 * P_FileRename.java
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
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.io.IOSystem
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.CopyOption
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.ArrayList
import java.util.Collections

import com.avail.AvailRuntime.currentRuntime
import com.avail.descriptor.*
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.FiberDescriptor.newFiber
import com.avail.descriptor.FiberTypeDescriptor.fiberType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.bytes
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.StringDescriptor.formatString
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_FILE_EXISTS
import com.avail.exceptions.AvailErrorCode.E_INVALID_PATH
import com.avail.exceptions.AvailErrorCode.E_IO_ERROR
import com.avail.exceptions.AvailErrorCode.E_NO_FILE
import com.avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import java.util.Collections.singletonList

/**
 * **Primitive:** Rename the source [path][Path] to
 * the destination path. Try not to overwrite an existing destination. This
 * operation is only likely to work for two paths provided by the same
 * [file store][FileStore].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_FileRename : Primitive(6, CanInline, HasSideEffect)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(6)
		val source = interpreter.argument(0)
		val destination = interpreter.argument(1)
		val replaceExisting = interpreter.argument(2)
		val succeed = interpreter.argument(3)
		val fail = interpreter.argument(4)
		val priority = interpreter.argument(5)

		val runtime = currentRuntime()
		val sourcePath: Path
		val destinationPath: Path
		try
		{
			sourcePath = IOSystem.fileSystem.getPath(
				source.asNativeString())
			destinationPath = IOSystem.fileSystem.getPath(
				destination.asNativeString())
		}
		catch (e: InvalidPathException)
		{
			return interpreter.primitiveFailure(E_INVALID_PATH)
		}

		val priorityInt = priority.extractInt()
		val current = interpreter.fiber()
		val newFiber = newFiber(
			succeed.kind().returnType().typeUnion(fail.kind().returnType()),
			priorityInt
		) {
			formatString("Asynchronous file rename, %s → %s", sourcePath,
			             destinationPath)
		}
		newFiber.availLoader(current.availLoader())
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared())
		newFiber.textInterface(current.textInterface())
		newFiber.makeShared()
		succeed.makeShared()
		fail.makeShared()

		val replace = replaceExisting.extractBoolean()
		runtime.ioSystem().executeFileTask(Runnable {
               val options = ArrayList<CopyOption>()
               if (replace)
               {
                   options.add(StandardCopyOption.REPLACE_EXISTING)
               }
               try
               {
                   Files.move(
                       sourcePath,
                       destinationPath,
                       *options.toTypedArray())
               }
               catch (e: SecurityException)
               {
                   Interpreter.runOutermostFunction(
                       runtime,
                       newFiber,
                       fail,
                       listOf(E_PERMISSION_DENIED.numericCode()))
                   return@runtime.ioSystem().executeFileTask
               }
               catch (e: AccessDeniedException)
               {
                   Interpreter.runOutermostFunction(runtime, newFiber, fail, listOf(E_PERMISSION_DENIED.numericCode()))
                   return@runtime.ioSystem().executeFileTask
               }
               catch (e: NoSuchFileException)
               {
                   Interpreter.runOutermostFunction(
                       runtime,
                       newFiber,
                       fail,
                       listOf(E_NO_FILE.numericCode()))
                   return@runtime.ioSystem().executeFileTask
               }
               catch (e: FileAlreadyExistsException)
               {
                   Interpreter.runOutermostFunction(
                       runtime,
                       newFiber,
                       fail,
                       listOf(E_FILE_EXISTS.numericCode()))
                   return@runtime.ioSystem().executeFileTask
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
                   runtime,
                   newFiber,
                   succeed,
                   emptyList<A_BasicObject>())
           })
		return interpreter.primitiveSuccess(newFiber)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tupleFromArray(
				stringType(),
				stringType(),
				booleanType(),
				functionType(
					emptyTuple(),
					TOP.o()),
				functionType(
					tuple(
						enumerationWith(
							set(
								E_PERMISSION_DENIED,
								E_FILE_EXISTS,
								E_NO_FILE,
								E_IO_ERROR))),
					TOP.o()),
				bytes()),
			fiberType(TOP.o()))
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(set(E_INVALID_PATH))
	}

}