/**
 * CompilerConfiguration.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.tools.compiler.configuration;

import static com.avail.tools.compiler.configuration.VerbosityLevel.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.EnumSet;
import com.avail.annotations.Nullable;
import com.avail.builder.ModuleName;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.ModuleRoots;
import com.avail.builder.RenamesFileParser;
import com.avail.builder.RenamesFileParserException;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.tools.configuration.Configuration;
import com.avail.tools.compiler.Compiler;

/**
 * A {@code CompilerConfiguration} instructs a {@linkplain Compiler compiler} on
 * the building of a target Avail {@linkplain ModuleDescriptor module}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public class CompilerConfiguration
implements Configuration
{
	/**
	 * The {@linkplain ModuleRoots Avail roots} path.
	 */
	private String availRootsPath = "";

	/** The {@linkplain ModuleRoots Avail roots}. */
	private transient @Nullable ModuleRoots availRoots;

	/**
	 * Answer the {@linkplain ModuleRoots Avail roots} path.
	 *
	 * @return The Avail roots path.
	 */
	public String availRootsPath ()
	{
		return availRootsPath;
	}

	/**
	 * Set the {@linkplain ModuleRoots Avail roots} path.
	 *
	 * @param newPath
	 *        The replacement Avail roots path.
	 */
	public void setAvailRootsPath (final String newPath)
	{
		availRootsPath = newPath;
		availRoots = null;
	}

	/**
	 * Answer the {@linkplain ModuleRoots Avail roots}.
	 *
	 * @return The Avail roots.
	 */
	public ModuleRoots availRoots ()
	{
		ModuleRoots roots = availRoots;
		if (roots == null)
		{
			roots = new ModuleRoots(availRootsPath);
			availRoots = roots;
		}
		return roots;
	}

	/** The path to the {@linkplain RenamesFileParser renames file}. */
	private @Nullable String renamesFilePath;

	/** The {@linkplain ModuleNameResolver module name resolver}. */
	private transient @Nullable ModuleNameResolver moduleNameResolver;

	/**
	 * Answer the path to the {@linkplain RenamesFileParser renames file}.
	 *
	 * @return The renames file path.
	 */
	public String renamesFilePath ()
	{
		return renamesFilePath;
	}

	/**
	 * Set the path to the {@linkplain RenamesFileParser renames file}.
	 *
	 * @param newPath The renames file path.
	 */
	public void setRenamesFilePath (final String newPath)
	{
		renamesFilePath = newPath;
		moduleNameResolver = null;
	}

	/**
	 * Answer the {@linkplain ModuleNameResolver module name resolver} correct
	 * for the current {@linkplain CompilerConfiguration configuration}.
	 *
	 * @return A module name resolver.
	 * @throws FileNotFoundException
	 *         If the {@linkplain RenamesFileParser renames file path} has been
	 *         specified, but is invalid.
	 * @throws RenamesFileParserException
	 *         If the renames file is invalid.
	 */
	public ModuleNameResolver moduleNameResolver ()
		throws FileNotFoundException, RenamesFileParserException
	{
		ModuleNameResolver resolver = moduleNameResolver;
		if (resolver == null)
		{
			final Reader reader;
			final String path = renamesFilePath;
			if (path == null)
			{
				reader = new StringReader("");
			}
			else
			{
				final File file = new File(path);
				reader = new BufferedReader(new FileReader(file));
			}
			final RenamesFileParser renameParser = new RenamesFileParser(
				reader, availRoots());
			resolver = renameParser.parse();
			moduleNameResolver = resolver;
		}
		return resolver;
	}

	/** The target {@linkplain ModuleName module} for compilation. */
	private @Nullable ModuleName targetModuleName;

	/**
	 * Answer the {@linkplain ModuleName module} that is the target for
	 * compilation.
	 *
	 * @return The module name.
	 */
	public ModuleName targetModuleName()
	{
		return targetModuleName;
	}

	/**
	 * Set the {@linkplain ModuleName module} that is to be the target for
	 * compilation.
	 *
	 * @param target The new module name.
	 */
	public void setTargetModuleName(final ModuleName target)
	{
		targetModuleName = target;
	}

	/**
	 * The flag indicating whether the compiler should clear all repositories
	 * for which a valid source directory has been specified. This option is
	 * false by default and is changed to true upon inclusion of the option
	 * keyword in the arguments invoking the compiler.
	 */
	private boolean clearRepositories = false;

	/**
	 * Answer whether the compiler is set to clear the repositories.
	 *
	 * @return The status of the clearRepositories flag.
	 */
	public boolean clearRepositories()
	{
		return clearRepositories;
	}

	/**
	 * Instruct the compiler to clear all repositories for which a valid source
	 * directory has been specified.
	 */
	public void setClearRepositoriesFlag()
	{
		clearRepositories = true;
	}

	/**
	 * The flag indicating whether the compiler should mute all output
	 * originating from user code.
	 */
	private boolean quiet = false;

	/**
	 * Answer whether the compiler is set to mute user output.
	 *
	 * @return The status of the quiet flag.
	 */
	public boolean quiet()
	{
		return quiet;
	}

	/**
	 * Instruct the compiler to mute all output originating from user code.
	 */
	public void setQuietFlag()
	{
		quiet = true;
	}

	/**
	 * The {@linkplain EnumSet set} of reports the compiler should print
	 * following its run.
	 */
	private EnumSet<StatisticReport> reports;

	/**
	 * Answer the {@linkplain EnumSet set} of reports the compiler should print
	 * following its run.
	 *
	 * @return The set of report names.
	 */
	public EnumSet<StatisticReport> reports()
	{
		return reports;
	}

	/**
	 * Configure the compiler to display the appropriate reports following its
	 * run.
	 *
	 * @param reports The set of report names.
	 */
	public void setReports(final EnumSet<StatisticReport> reports)
	{
		this.reports = reports;
	}

	/**
	 * @return True if the configuration has been set to output any {@linkplain
	 *         StatisticReport reports}, false otherwise;
	 */
	public boolean hasReports()
	{
		return (reports != null) && !(reports.isEmpty());
	}

	/**
	 * The flag indicating whether the compiler should show the time elapsed
	 * for the process.
	 */
	private boolean showTiming = false;

	/**
	 * Answer whether the compiler is set to show timing.
	 *
	 * @return The status of the showTiming flag.
	 */
	public boolean showTiming()
	{
		return showTiming;
	}

	/**
	 * Instruct the compiler to show the time elapsed for the process.
	 */
	public void setShowTimingFlag()
	{
		showTiming = true;
	}

	/**
	 * The level of verbosity specified for the compiler.
	 */
	private VerbosityLevel verbosityLevel = ERROR_ONLY;

	/**
	 * Answer the current verbosity level.
	 *
	 * @return The verbosity level.
	 */
	public VerbosityLevel verbosityLevel()
	{
		return verbosityLevel;
	}

	/**
	 * Set the compiler's verbosity level.
	 *
	 * @param level The requested verbosity level.
	 */
	public void setVerbosityLevel(final VerbosityLevel level)
	{
		verbosityLevel = level;
	}

	@Override
	public boolean isValid ()
	{
		// Just try to create a module name resolver. If this fails, then the
		// configuration is invalid. Otherwise, it should be okay.
		try
		{
			moduleNameResolver();
		}
		catch (final FileNotFoundException|RenamesFileParserException e)
		{
			return false;
		}
		return true;
	}
}