/*
 * P_CurrentWorkingDirectory.java
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

import com.avail.descriptor.A_String
import com.avail.descriptor.A_Type
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.LinkOption
import java.nio.file.Path

import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.StringDescriptor.stringFrom
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail

/**
 * **Primitive:** Answer the [ ][Path.toRealPath] of the current working directory.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_CurrentWorkingDirectory : Primitive(0, CannotFail, CanInline, CanFold)
{
	/**
	 * The current working directory of the Avail virtual machine. Because Java
	 * does not permit the current working directory to be changed, it is safe
	 * to cache the answer at class-loading time.
	 */
	private val currentWorkingDirectory: A_String

	// Obtain the current working directory. Try to resolve this location to its
	// real path. If resolution fails, then just use the value of the "user.dir"
	// system property.
	init
	{
		val userDir = System.getProperty("user.dir")
		val fileSystem = FileSystems.getDefault()
		val path = fileSystem.getPath(userDir)
		var realPathString: String
		try
		{
			realPathString = path.toRealPath().toString()
		}
		catch (e: IOException)
		{
			realPathString = userDir
		}
		catch (e: SecurityException)
		{
			realPathString = userDir
		}

		currentWorkingDirectory = stringFrom(realPathString).makeShared()
	}

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(0)
		return interpreter.primitiveSuccess(currentWorkingDirectory)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(emptyTuple(), stringType())
	}

}