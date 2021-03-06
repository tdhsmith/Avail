/*
 * ModuleRoot.java
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
import com.avail.persistence.IndexedFileException;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.io.File;

/**
 * A {@code ModuleRoot} represents a vendor of Avail modules and/or the vended
 * modules themselves.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class ModuleRoot
{
	/** The {@linkplain ModuleRoot module root} name. */
	private final String name;

	/**
	 * Answer the {@code ModuleRoot}'s name.
	 *
	 * @return The name.
	 */
	public String name ()
	{
		return name;
	}

	/**
	 * The {@linkplain IndexedRepositoryManager indexed repository} that
	 * contains compiled {@linkplain ModuleDescriptor modules} for this
	 * {@linkplain ModuleRoot root}.
	 */
	private final IndexedRepositoryManager repository;

	/**
	 * Answer the {@linkplain IndexedRepositoryManager indexed repository} that
	 * contains compiled {@linkplain ModuleDescriptor modules} for this module
	 * root.
	 *
	 * @return The requested path.
	 */
	public IndexedRepositoryManager repository ()
	{
		return repository;
	}

	/**
	 * Clear the content of the repository for this root.
	 */
	public void clearRepository ()
	{
		repository.clear();
	}

	/**
	 * If provided, then the {@linkplain File path} to the directory that
	 * contains source {@linkplain ModuleDescriptor modules} for this
	 * {@linkplain ModuleRoot root}.
	 */
	private final @Nullable File sourceDirectory;

	/**
	 * The {@linkplain File path} to the directory that contains source
	 * {@linkplain ModuleDescriptor modules} for this module root.
	 *
	 * @return The requested path, or {@code null} if no source path is
	 *         available.
	 */
	public @Nullable File sourceDirectory ()
	{
		return sourceDirectory;
	}

	/**
	 * Construct a new {@code ModuleRoot}.
	 *
	 * @param name
	 *        The name of the module root.
	 * @param repository
	 *        The {@linkplain File path} to the {@linkplain
	 *        IndexedRepositoryManager indexed repository} that contains
	 *        compiled {@linkplain ModuleDescriptor modules} for this root.
	 * @param sourceDirectory
	 *        The {@linkplain File path} to the directory that contains source
	 *        {@linkplain ModuleDescriptor modules} for this {@linkplain
	 *        ModuleRoot root}, or {@code null} if no source path is available.
	 * @throws IndexedFileException
	 *         If the indexed repository could not be opened.
	 */
	@SuppressWarnings("ThrowsRuntimeException")
	public ModuleRoot (
		final String name,
		final File repository,
		final @Nullable File sourceDirectory)
	throws IndexedFileException
	{
		this.name = name;
		this.repository = new IndexedRepositoryManager(name, repository);
		this.sourceDirectory = sourceDirectory;
	}

	/**
	 * Write the {@linkplain IndexedRepositoryManager#fileName() binary
	 * repository path} and the {@linkplain #sourceDirectory() source module
	 * path} (respectively) into a new JSON array.
	 *
	 * @param writer
	 *        A {@link JSONWriter}.
	 */
	public void writePathsOn (final JSONWriter writer)
	{
		writer.startArray();
		writer.write(repository.fileName().getAbsolutePath());
		final @Nullable File dir = sourceDirectory;
		if (dir == null)
		{
			writer.writeNull();
		}
		else
		{
			writer.write(dir.getAbsolutePath());
		}
		writer.endArray();
	}
}
