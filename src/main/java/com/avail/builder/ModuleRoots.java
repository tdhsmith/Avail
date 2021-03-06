/*
 * ModuleRoots.java
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

import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.avail.persistence.IndexedRepositoryManager.isIndexedRepositoryFile;

/**
 * {@code ModuleRoots} encapsulates the Avail {@linkplain ModuleDescriptor
 * module} path. The Avail module path specifies bindings between
 * <em>logical root names</em> and {@linkplain ModuleRoot locations} of Avail
 * modules. A logical root name should typically belong to a vendor of Avail
 * modules, ergo a domain name or registered trademark suffices nicely.
 *
 * <p>The format of an Avail module path is described by the following
 * simple grammar:</p>
 *
 * <pre>
 * modulePath ::= binding ++ ";" ;
 * binding ::= logicalRoot "=" objectRepository ("," sourceDirectory) ;
 * logicalRoot ::= [^=;]+ ;
 * objectRepository ::= [^;]+ ;
 * sourceDirectory ::= [^;]+ ;
 * </pre>
 *
 * <p>{@code logicalRoot} represents a logical root name. {@code
 * objectRepository} represents the absolute path of a binary module repository.
 * {@code sourceDirectory} represents the absolute path of a package, i.e., a
 * directory containing source modules, and may be sometimes be omitted (e.g.,
 * when compilation is not required).</p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@ThreadSafe
public final class ModuleRoots
implements Iterable<ModuleRoot>
{
	/**
	 * Answer the Avail {@linkplain ModuleDescriptor module} path.
	 *
	 * @return The Avail {@linkplain ModuleDescriptor module} path.
	 */
	public String modulePath ()
	{
		final StringBuilder builder = new StringBuilder(200);
		boolean first = true;
		for (final Entry<String, ModuleRoot> entry : rootMap.entrySet())
		{
			final ModuleRoot root = entry.getValue();
			if (!first)
			{
				builder.append(";");
			}
			builder.append(root.name());
			builder.append("=");
			builder.append(root.repository().fileName().getPath());
			final @Nullable File sourceDirectory = root.sourceDirectory();
			if (sourceDirectory != null)
			{
				builder.append(",");
				builder.append(sourceDirectory.getPath());
			}
			first = false;
		}
		return builder.toString();
	}

	/**
	 * A {@linkplain Map map} from logical root names to {@linkplain ModuleRoot
	 * module root}s.
	 */
	private final Map<String, ModuleRoot> rootMap = new LinkedHashMap<>();

	/**
	 * Parse the Avail {@linkplain ModuleDescriptor module} path into a
	 * {@linkplain Map map} of logical root names to {@linkplain ModuleRoot
	 * module root}s.
	 *
	 * @param modulePath The module roots path string.
	 * @throws IllegalArgumentException
	 *         If any component of the Avail {@linkplain ModuleDescriptor
	 *         module} path is invalid.
	 */
	private void parseAvailModulePath (final String modulePath)
	throws IllegalArgumentException
	{
		clearRoots();
		// Root definitions are separated by semicolons.
		String [] components = modulePath.split(";");
		if (modulePath.isEmpty())
		{
			components = new String[0];
		}
		for (final String component : components)
		{
			// An equals separates the root name from its paths.
			final String[] binding = component.split("=");
			if (binding.length != 2)
			{
				throw new IllegalArgumentException();
			}

			// A comma separates the repository path from the source directory
			// path.
			final String rootName = binding[0];
			final String[] paths = binding[1].split(",");
			if (paths.length > 2)
			{
				throw new IllegalArgumentException();
			}

			// All paths must be absolute.
			for (final String path : paths)
			{
				final File file = new File(path);
				if (!file.isAbsolute())
				{
					throw new IllegalArgumentException();
				}
			}

			// If only one path is supplied, then it must reference a valid
			// repository.
			final File repositoryFile = new File(paths[0]);
			try
			{
				if (paths.length == 1
					&& !isIndexedRepositoryFile(repositoryFile))
				{
					throw new IllegalArgumentException();
				}
			}
			catch (final IOException e)
			{
				throw new IllegalArgumentException(e);
			}

			// If two paths are provided, then the first path need not reference
			// an existing file. The second path, however, must reference a
			// directory.
			final @Nullable File sourceDirectory =
				paths.length == 2
				? new File(paths[1])
				: null;
			if (sourceDirectory != null && !sourceDirectory.isDirectory())
			{
				throw new IllegalArgumentException();
			}

			addRoot(new ModuleRoot(rootName, repositoryFile, sourceDirectory));
		}
	}

	public void clearRoots ()
	{
		rootMap.clear();
	}

	public void addRoot (final ModuleRoot root)
	{
		rootMap.put(root.name(), root);
	}

	/**
	 * Answer the logical root names in the order that they are specified in
	 * the Avail {@linkplain ModuleDescriptor module} path.
	 *
	 * @return The logical root names.
	 */
	public Set<String> rootNames ()
	{
		return Collections.unmodifiableSet(rootMap.keySet());
	}

	/**
	 * Answer the {@linkplain ModuleRoot module roots} in the order that they
	 * are specified in the Avail {@linkplain ModuleDescriptor module} path.
	 *
	 * @return The module roots.
	 */
	public Set<ModuleRoot> roots ()
	{
		final Set<ModuleRoot> roots = new LinkedHashSet<>();
		for (final Entry<String, ModuleRoot> entry : rootMap.entrySet())
		{
			roots.add(entry.getValue());
		}
		return Collections.unmodifiableSet(roots);
	}

	@Override
	public Iterator<ModuleRoot> iterator ()
	{
		return Collections.unmodifiableSet(roots()).iterator();
	}

	/**
	 * Answer the {@linkplain ModuleRoot module root} bound to the specified
	 * logical root name.
	 *
	 * @param rootName
	 *        A logical root name, typically something owned by a vendor of
	 *        Avail {@linkplain ModuleDescriptor modules}.
	 * @return The module root, or {@code null} if no such binding exists.
	 */
	public @Nullable ModuleRoot moduleRootFor (final String rootName)
	{
		return rootMap.get(rootName);
	}

	/**
	 * Construct a new {@link ModuleRoots} from the specified Avail {@linkplain
	 * ModuleDescriptor module} path.
	 *
	 * @param modulePath
	 *        An Avail {@linkplain ModuleDescriptor module} path.
	 * @throws IllegalArgumentException
	 *         If the Avail {@linkplain ModuleDescriptor module} path is
	 *         malformed.
	 */
	public ModuleRoots (final String modulePath)
	{
		parseAvailModulePath(modulePath);
	}

	/**
	 * Write a JSON encoding of the {@linkplain ModuleRoots module roots} to
	 * the specified {@link JSONWriter}.
	 *
	 * @param writer
	 *        A {@code JSONWriter}.
	 */
	public void writeOn (final JSONWriter writer)
	{
		writer.startArray();
		for (final ModuleRoot root : roots())
		{
			writer.write(root.name());
		}
		writer.endArray();
	}

	/**
	 * Write a JSON object whose fields are the {@linkplain ModuleRoots module
	 * roots} and whose values are {@linkplain
	 * ModuleRoot#writePathsOn(JSONWriter) JSON arrays} containing path
	 * information.
	 *
	 * @param writer
	 *        A {@link JSONWriter}.
	 */
	public void writePathsOn (final JSONWriter writer)
	{
		writer.startObject();
		for (final ModuleRoot root : roots())
		{
			writer.write(root.name());
			root.writePathsOn(writer);
		}
		writer.endObject();
	}
}
