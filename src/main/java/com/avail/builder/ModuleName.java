/*
 * ModuleName.java
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

package com.avail.builder;

import com.avail.descriptor.ModuleDescriptor;

import javax.annotation.Nullable;

/**
 * A {@code ModuleName} represents the canonical name of an Avail {@linkplain
 * ModuleDescriptor module}. A canonical name is specified relative to an
 * Avail {@linkplain ModuleRoots module root} and has the form
 * <strong>/R/X/Y/Z</strong>, where <strong>R</strong> is a module root on the
 * Avail module path, <strong>X</strong> is a package within
 * <strong>R</strong>, <strong>Y</strong> is a package within
 * <strong>X</strong>, and <strong>Z</strong> is a module or package within
 * <strong>Y</strong>.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class ModuleName
{
	/** The fully-qualified module name. */
	private final String qualifiedName;

	/**
	 * Answer the fully-qualified module name.
	 *
	 * @return The fully-qualified module name.
	 */
	public String qualifiedName ()
	{
		return qualifiedName;
	}

	/** The logical root name of the {@linkplain ModuleName module name}. */
	private final String rootName;

	/**
	 * Answer the logical root name of the {@linkplain ModuleName module name}.
	 *
	 * @return the rootName
	 *         The logical root name of the {@linkplain ModuleName module name}.
	 */
	public String rootName ()
	{
		return rootName;
	}

	/**
	 * The fully-qualified package name of the {@linkplain ModuleName module
	 * name}.
	 */
	private final String packageName;

	/**
	 * Answer the fully-qualified package name of the {@linkplain ModuleName
	 * module name}.
	 *
	 * @return The fully-qualified package name of the {@linkplain ModuleName
	 *         module name}.
	 */
	public String packageName ()
	{
		return packageName;
	}

	/**
	 * The local name of the {@linkplain ModuleDescriptor module} referenced by
	 * this {@linkplain ModuleName module name}.
	 */
	private final String localName;

	/**
	 * Answer the local name of the {@linkplain ModuleDescriptor module}
	 * referenced by this {@linkplain ModuleName module name}.
	 *
	 * @return The local name of the {@linkplain ModuleDescriptor module}
	 *         referenced by this {@linkplain ModuleName module name}.
	 */
	public String localName ()
	{
		return localName;
	}

	/**
	 * The lazily-initialized root-relative {@linkplain ModuleName module name}.
	 * This is the {@linkplain #qualifiedName() fully-qualified name} minus the
	 * #rootName() module root}.
	 */
	private @Nullable String rootRelativeName;

	/**
	 * Answer the root-relative {@linkplain ModuleName module name}. This is the
	 * {@linkplain #qualifiedName() fully-qualified name} minus the {@linkplain
	 * #rootName() module root}.
	 *
	 * @return The root-relative name.
	 */
	public String rootRelativeName ()
	{
		@Nullable String name = rootRelativeName;
		if (name == null)
		{
			final String[] components = qualifiedName.split("/");
			final StringBuilder builder = new StringBuilder(50);
			for (int index = 2; index < components.length; index++)
			{
				if (index > 2)
				{
					builder.append('/');
				}
				builder.append(components[index]);
			}
			name = builder.toString();
			rootRelativeName = name;
		}
		return name;
	}

	/**
	 * Whether this module name was transformed via a rename rule.
	 */
	private final boolean isRename;

	/**
	 * Answer whether this module name was transformed via a rename rule.
	 *
	 * @return True if the module was renamed, otherwise false.
	 */
	public boolean isRename ()
	{
		return isRename;
	}

	/**
	 * Construct a new {@link ModuleName} from the specified fully-qualified
	 * module name.
	 *
	 * @param qualifiedName A fully-qualified module name.
	 * @throws IllegalArgumentException
	 *         If the argument was malformed.
	 */
	public ModuleName (final String qualifiedName)
	throws IllegalArgumentException
	{
		this(qualifiedName, false);
	}

	/**
	 * Construct a new {@link ModuleName} from the specified fully-qualified
	 * module name.
	 *
	 * @param qualifiedName A fully-qualified module name.
	 * @param isRename Whether module resolution followed a renaming rule.
	 * @throws IllegalArgumentException
	 *         If the argument was malformed.
	 */
	public ModuleName (final String qualifiedName, final boolean isRename)
		throws IllegalArgumentException
	{
		this.qualifiedName = qualifiedName;
		this.isRename = isRename;

		final String[] components = qualifiedName.split("/");
		if (components.length < 3 || !components[0].isEmpty())
		{
			throw new IllegalArgumentException(
				"invalid fully-qualified module name (" + qualifiedName + ")");
		}

		// Handle the easy ones first.
		this.rootName = components[1];
		this.localName  = components[components.length - 1];

		// Now determine the package.
		final StringBuilder builder = new StringBuilder(50);
		for (int index = 1; index < components.length - 1; index++)
		{
			builder.append("/");
			builder.append(components[index]);
		}
		this.packageName = builder.toString();
	}

	/**
	 * Construct a new {@link ModuleName} from the specified canonical module
	 * group name and local name.
	 *
	 * @param packageName A canonical package name.
	 * @param localName A local module name.
	 * @throws IllegalArgumentException
	 *         If the argument was malformed.
	 */
	public ModuleName (
		final String packageName,
		final String localName)
	throws IllegalArgumentException
	{
		this(packageName, localName, false);
	}

	/**
	 * Construct a new {@link ModuleName} from the specified canonical module
	 * group name and local name.
	 *
	 * @param packageName A canonical package name.
	 * @param localName A local module name.
	 * @param isRename Whether module resolution followed a renaming rule.
	 * @throws IllegalArgumentException
	 *         If the argument was malformed.
	 */
	public ModuleName (
		final String packageName,
		final String localName,
		final boolean isRename)
	throws IllegalArgumentException
	{
		this(packageName + "/" + localName, isRename);
	}

	@Override
	public boolean equals (final @Nullable Object obj)
	{
		return this == obj
			|| ((obj instanceof ModuleName)
				    && qualifiedName.equals(((ModuleName) obj).qualifiedName));
	}

	@Override
	public int hashCode ()
	{
		// The magic number is a prime.
		return 345533 * qualifiedName.hashCode() ^ 0x5881271A;
	}

	@Override
	public String toString ()
	{
		return qualifiedName;
	}
}
