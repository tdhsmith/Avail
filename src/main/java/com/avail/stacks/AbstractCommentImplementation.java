/*
 * AbstractCommentImplementation.java
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

package com.avail.stacks;

import com.avail.descriptor.A_String;
import com.avail.utility.json.JSONWriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * An Avail comment implementation
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public abstract class AbstractCommentImplementation
{
	/**
	 * The {@link CommentSignature signature} of the class/method the comment
	 * describes.
	 */
	private final CommentSignature signature;

	/**
	 * @return the signature
	 */
	public CommentSignature signature ()
	{
		return signature;
	}

	/**
	 * Indicates whether or not a method/class is to be documented regardless
	 * of visibility
	 */
	private final boolean sticky;

	/**
	 * @return whether or not the method is {@link #sticky}
	 */
	public boolean isSticky()
	{
		return sticky;
	}

	/**
	 * The start line in the module the comment being parsed appears.
	 */
	final int commentStartLine;

	/**
	 * The author of the implementation.
	 */
	final ArrayList<StacksAuthorTag> authors;

	/**
	 * Any "@sees" references.
	 */
	final ArrayList<StacksSeeTag> sees;

	/**
	 * The overall description of the implementation
	 */
	final StacksDescription description;

	/**
	 * The list of categories the implementation applies to.
	 */
	final ArrayList<StacksCategoryTag> categories;

	/**
	 * The aliases the implementation is known by
	 */
	final ArrayList<StacksAliasTag> aliases;

	/**
	 * Create a string that is unique to this {@linkplain
	 * AbstractCommentImplementation}
	 * @return an identifying string
	 */
	public String identityCheck()
	{
		return (signature.module() + signature.name() + commentStartLine);
	}

	/**
	 *
	 * Construct a new {@link AbstractCommentImplementation}.
	 *
	 * @param signature
	 * 		The {@link CommentSignature signature} of the class/method the
	 * 		comment describes.
	 * @param commentStartLine
	 * 		The start line in the module the comment being parsed appears.
	 * @param authors
	 * 		The {@link StacksAuthorTag author} of the implementation.
	 * @param sees
	 * 		A {@link List} of any {@link StacksSeeTag "@sees"} references.
	 * @param description
	 * 		The overall description of the implementation
	 * @param categories
	 * 		The categories the implementation appears in
	 * @param aliases
	 * 		The aliases the implementation is known by
	 * @param sticky
	 * 		Indicates whether or not the comment is sticky
	 */
	AbstractCommentImplementation(
		final CommentSignature signature,
		final int commentStartLine,
		final ArrayList<StacksAuthorTag> authors,
		final ArrayList<StacksSeeTag> sees,
		final StacksDescription description,
		final ArrayList<StacksCategoryTag> categories,
		final ArrayList<StacksAliasTag> aliases, final boolean sticky)
	{
		this.signature = signature;
		this.commentStartLine = commentStartLine;
		this.authors = authors;
		this.sees = sees;
		this.description = description;
		this.categories = categories;
		this.aliases = aliases;
		this.sticky = sticky;
	}

	/**
	 * Add the implementation to the provided group.
	 * @param implementationGroup
	 */
	public abstract void addToImplementationGroup(
		ImplementationGroup implementationGroup);

	/**
	 * Add the implementation to the provided {@linkplain
	 * StacksExtendsModule}.
	 * @param name
	 * 		Name of the implementation to add to the module.
	 * @param importModule
	 * 		The module to add the implementation to
	 */
	public abstract void addImplementationToImportModule(
		A_String name, StacksImportModule importModule);

	/**
	 * Create JSON content from implementation
	 * @param linkingFileMap
	 * 		The map of file linkage
	 * @param nameOfGroup
	 * 		The name of the implementation as it is to be displayed
	 * @param errorLog The {@linkplain StacksErrorLog}
	 * @param jsonWriter The {@linkplain JSONWriter writer} collecting the
	 * 		stacks content.
	 */
	public abstract void toJSON(final LinkingFileMap linkingFileMap,
		final String nameOfGroup, final StacksErrorLog errorLog,
		JSONWriter jsonWriter);

	/**
	 * @return A set of category String names for this implementation.
	 */
	public HashSet<String> getCategorySet()
	{
		final HashSet<String> categorySet = new HashSet<>();
		for (final StacksCategoryTag aTag : categories)
		{
			categorySet.addAll(aTag.getCategorySet());
		}
		return categorySet;
	}
}
