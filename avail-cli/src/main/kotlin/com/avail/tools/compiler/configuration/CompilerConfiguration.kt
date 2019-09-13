/*
 * CompilerConfiguration.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.tools.compiler.configuration

import com.avail.builder.*
import com.avail.descriptor.ModuleDescriptor
import com.avail.performance.StatisticReport
import com.avail.stacks.StacksGenerator
import com.avail.tools.compiler.Compiler
import com.avail.tools.compiler.configuration.VerbosityLevel.GLOBAL_LOCAL_PROGRESS
import com.avail.utility.configuration.Configuration
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * A `CompilerConfiguration` instructs a [compiler][Compiler] on
 * the building of a target Avail [module][ModuleDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
class CompilerConfiguration : Configuration
{
	/** The [Avail roots][ModuleRoots] path. */
	internal var availRootsPath = ""
		set (newValue)
		{
			field = newValue
			availRoots = null
		}

	/** The [Avail roots][ModuleRoots].  */
	internal var availRoots: ModuleRoots? = null
		get()
		{
			var roots = field
			if (roots == null)
			{
				roots = ModuleRoots(availRootsPath)
				availRoots = roots
			}
			return roots
		}


	/** The path to the [renames file][RenamesFileParser].  */
	internal var renamesFilePath: String? = null
		set(newValue)
		{
			field = newValue
			moduleNameResolver = null
		}

	/**
	 * The [module name resolver][ModuleNameResolver] correct for the current
	 * `CompilerConfiguration configuration`.
	 *
	 * @throws FileNotFoundException
	 *   If the [renames file path][RenamesFileParser] has been specified, but
	 *   is invalid.
	 * @throws RenamesFileParserException
	 *   If the renames file is invalid.
	 */
	internal var moduleNameResolver: ModuleNameResolver? = null
		@Throws(FileNotFoundException::class, RenamesFileParserException::class)
		get()
		{
			var resolver = field
			if (resolver == null)
			{
				val reader: Reader
				val path = renamesFilePath
				if (path == null)
				{
					reader = StringReader("")
				}
				else
				{
					val file = File(path)

					reader = BufferedReader(
						InputStreamReader(
							FileInputStream(file), StandardCharsets.UTF_8))
				}
				val renameParser = RenamesFileParser(
					reader, availRoots!!)
				resolver = renameParser.parse()
				try
				{
					reader.close()
				}
				catch (e: IOException)
				{
					throw RenamesFileParserException(e)
				}

				field = resolver
			}
			return resolver!!
		}

	/** The target [module][ModuleName] for compilation.  */
	internal var targetModuleName: ModuleName? = null

	/**
	 * `true` iff the compiler should compile the target module and its
	 * ancestors.
	 */
	internal var compileModules = false

	/**
	 * `true` iff the compiler should clear all repositories for which a valid
	 * source directory has been specified. `false` by default.
	 */
	internal var clearRepositories = false

	/**
	 * `true` iff Stacks documentation should be generated. `false` by default.
	 */
	internal var generateDocumentation = false

	/** The Stacks documentation path.  */
	internal var documentationPath = StacksGenerator.defaultDocumentationPath

	/**
	 * `true` iff the compiler should mute all output originating from user
	 * code. `false` by default.
	 */
	internal var quiet = false

	/**
	 * The [set][EnumSet] of reports the compiler should print following its
	 * run.
	 */
	internal var reports = EnumSet.noneOf(StatisticReport::class.java)

	/** The level of verbosity specified for the compiler. */
	internal var verbosityLevel = GLOBAL_LOCAL_PROGRESS

	override
	val isValid: Boolean
		get()
		{
			try
			{
				// Just try to create a module name resolver. If this fails,
				// then the configuration is invalid. Otherwise, it should be
				// okay.
				moduleNameResolver
			}
			catch (e: FileNotFoundException)
			{
				return false
			}
			catch (e: RenamesFileParserException)
			{
				return false
			}

			return compileModules || generateDocumentation || clearRepositories
		}
}