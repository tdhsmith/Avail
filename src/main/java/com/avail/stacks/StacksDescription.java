/*
 * StacksDescription.java
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

import com.avail.utility.json.JSONWriter;

import java.util.List;

/**
 * A collection of {@linkplain AbstractStacksToken tokens} that make up a
 * comment description.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksDescription
{
	/**
	 * The tokens that make up a description in a comment.
	 */
	final List<AbstractStacksToken> descriptionTokens;

	/**
	 * Construct a new {@link StacksDescription}.
	 * @param descriptionTokens
	 * 		The tokens that make up a description in a comment.
	 *
	 */
	public StacksDescription (
		final List<AbstractStacksToken> descriptionTokens)
	{
		this.descriptionTokens = descriptionTokens;
	}

	/**
	 * Create JSON content from the description
	 * @param linkingFileMap
	 * 		A map for all files in Stacks
	 * @param hashID hashID The ID for this implementation
	 * @param errorLog errorLog The {@linkplain StacksErrorLog}
	 * @param jsonWriter The {@linkplain JSONWriter writer} collecting the
	 * 		stacks content.
	 */
	public void toJSON(final LinkingFileMap linkingFileMap, final int hashID,
		final StacksErrorLog errorLog, final JSONWriter jsonWriter)
	{
		final StringBuilder stringBuilder = new StringBuilder();
		final int listSize = descriptionTokens.size();
		if (listSize > 0)
		{
			for (int i = 0; i < listSize - 1; i++)
			{
				stringBuilder.append(
					descriptionTokens.get(i).toJSON(
						linkingFileMap, hashID, errorLog, jsonWriter));

				switch (descriptionTokens.get(i + 1).lexeme()) {
					case ".":
					case ",":
					case ":":
					case "?":
					case ";":
					case "!":
						break;
					default:
						stringBuilder.append(" ");
				}
			}
			stringBuilder.append(
				descriptionTokens.get(listSize - 1).toJSON(
					linkingFileMap, hashID, errorLog, jsonWriter));
		}
		jsonWriter.write(stringBuilder.toString());
	}
}
