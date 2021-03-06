/*
 * SemanticRestrictionCommentImplementation.java
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

import static com.avail.descriptor.StringDescriptor.stringFrom;

/**
 * A comment implementation of grammatical restrictions
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class SemanticRestrictionCommentImplementation extends
	AbstractCommentImplementation
{
	/**
	 *  The list of input types in the semantic restriction.
	 */
	final ArrayList<StacksRestrictsTag> restricts;

	/**
	 * The {@link StacksReturnTag "@returns"} content
	 */
	final ArrayList<StacksReturnTag> returnsContent;

	/**
	 * The hash id for this implementation
	 */
	private final int hashID;

	/**
	 * Construct a new {@link SemanticRestrictionCommentImplementation}.
	 *
	 * @param signature
	 * 		The {@link SemanticRestrictionCommentSignature signature} of the
	 * 		class/method the comment describes.
	 * @param commentStartLine
	 * 		The start line in the module the comment being parsed appears.
	 * @param author
	 * 		The {@link StacksAuthorTag authors} of the implementation.
	 * @param sees
	 * 		A {@link ArrayList} of any {@link StacksSeeTag "@sees"} references.
	 * @param description
	 * 		The overall description of the implementation
	 * @param categories
	 * 		The categories the implementation appears in
	 * @param aliases
	 * 		The aliases the implementation is known by
	 * @param restricts
	 * 		The list of input types in the semantic restriction.
	 * @param returnsContent
	 * 		The {@link StacksReturnTag "@returns"} content
	 */
	public SemanticRestrictionCommentImplementation (
		final SemanticRestrictionCommentSignature signature,
		final int commentStartLine,
		final ArrayList<StacksAuthorTag> author,
		final ArrayList<StacksSeeTag> sees,
		final StacksDescription description,
		final ArrayList<StacksCategoryTag> categories,
		final ArrayList<StacksAliasTag> aliases,
		final ArrayList<StacksRestrictsTag> restricts,
		final ArrayList<StacksReturnTag> returnsContent)
	{
		super(signature, commentStartLine, author, sees, description,
			categories,aliases, false);
		this.restricts = restricts;
		this.returnsContent = returnsContent;

		final StringBuilder concatenatedInputParams = new StringBuilder();

		for (final String param : signature.orderedInputTypes)
		{
			concatenatedInputParams.append(param);
		}

		this.hashID = stringFrom(concatenatedInputParams.toString()).hash();
	}

	@Override
	public void addToImplementationGroup(
		final ImplementationGroup implementationGroup)
	{
		implementationGroup.addSemanticRestriction(this);
	}

	@Override
	public void addImplementationToImportModule (
		final A_String name, final StacksImportModule importModule)
	{
		importModule.addSemanticImplementation(name, this);
	}

	@Override
	public void toJSON (
		final LinkingFileMap linkingFileMap,
		final String nameOfGroup,
		final StacksErrorLog errorLog,
		final JSONWriter jsonWriter)
	{
		jsonWriter.write("type");
		jsonWriter.write("method");
		signature().toJSON(nameOfGroup, isSticky(), jsonWriter);

		jsonWriter.write("sees");
		jsonWriter.startArray();
		for (final StacksSeeTag see : sees)
		{
			jsonWriter.write(see.thingToSee().toJSON(linkingFileMap, hashID,
				errorLog, jsonWriter));
		}
		jsonWriter.endArray();

		jsonWriter.write("description");
		description.toJSON(linkingFileMap, hashID, errorLog, jsonWriter);


		//The ordered position of the parameter in the method signature.
		jsonWriter.write("parameters");
		jsonWriter.startArray();
		int position = 1;
		for (final StacksRestrictsTag restrictTag : restricts)
		{
			restrictTag.toJSON(linkingFileMap, hashID, errorLog, position++,
				jsonWriter);
		}
		jsonWriter.endArray();

		if (!returnsContent.isEmpty())
		{
			returnsContent.get(0)
				.toJSON(linkingFileMap, hashID, errorLog, 1, jsonWriter);
		}
		else
		{
			jsonWriter.write("returns");
			jsonWriter.startArray();
			jsonWriter.endArray();
		}
	}
}
