/*
 * P_CreateDirectory.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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
import com.avail.descriptor.*
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.io.IOSystem

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.Collections
import java.util.EnumSet

import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FiberDescriptor.newFiber
import com.avail.descriptor.FiberTypeDescriptor.fiberType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.bytes
import com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType
import com.avail.descriptor.StringDescriptor.formatString
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_FILE_EXISTS
import com.avail.exceptions.AvailErrorCode.E_INVALID_PATH
import com.avail.exceptions.AvailErrorCode.E_IO_ERROR
import com.avail.exceptions.AvailErrorCode.E_PERMISSION_DENIED
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import java.util.Collections.singletonList

/**
 * **Primitive:** Create a directory with the indicated name
 * and permissions. Answer a new [fiber][A_Fiber] which, if creation
 * is successful, will be started to run the success [ function][A_Function].
 * If the creation fails, then the fiber will be started to apply the
 * failure function to the error code. The fiber runs at the specified priority.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_CreateDirectory : Primitive(5, CanInline, HasSideEffect)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(5)
		val directoryName = interpreter.argument(0)
		val ordinals = interpreter.argument(1)
		val succeed = interpreter.argument(2)
		val fail = interpreter.argument(3)
		val priority = interpreter.argument(4)

		val runtime = interpreter.runtime()
		val fileSystem = IOSystem.fileSystem
		val path: Path
		try
		{
			path = fileSystem.getPath(directoryName.asNativeString())
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
		) { formatString("Asynchronous create directory, %s", path) }
		newFiber.availLoader(current.availLoader())
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared())
		newFiber.textInterface(current.textInterface())
		newFiber.makeShared()
		succeed.makeShared()
		fail.makeShared()

		val permissions = permissionsFor(ordinals)
		val attr = PosixFilePermissions.asFileAttribute(permissions)
		runtime.ioSystem().executeFileTask(Runnable {
               try
               {
                   try
                   {
                       Files.createDirectory(path, attr)
                   }
                   catch (e: UnsupportedOperationException)
                   {
                       // Retry without setting the permissions.
                       Files.createDirectory(path)
                   }

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
		return functionType(tuple(stringType(),
		                          setTypeForSizesContentType(
			                          inclusive(0, 9),
			                          inclusive(1, 9)),
		                          functionType(
			                          emptyTuple(),
			                          TOP.o()), functionType(
			tuple(
				enumerationWith(
					set(E_FILE_EXISTS, E_PERMISSION_DENIED,
					    E_IO_ERROR))),
			TOP.o()), bytes()), fiberType(TOP.o()))
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(set(E_INVALID_PATH))
	}

	/**
	 * Convert the specified [set][SetDescriptor] of [ ] into the corresponding [set][Set]
	 * of [POSIX file permissions][PosixFilePermission].
	 *
	 * @param ordinals
	 * Some ordinals.
	 * @return The equivalent POSIX file permissions.
	 */
	private fun permissionsFor(
		ordinals: A_Set): Set<PosixFilePermission>
	{
		val allPermissions = IOSystem.posixPermissions
		val permissions = EnumSet.noneOf(PosixFilePermission::class.java)
		for (ordinal in ordinals)
		{
			permissions.add(allPermissions[ordinal.extractInt() - 1])
		}
		return permissions
	}
}