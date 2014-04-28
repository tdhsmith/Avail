/**
 * StacksParameterTag.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

/**
 * The contents of an Avail comment "@param" tag
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksParameterTag extends AbstractStacksTag
{
	/**
	 * The name of the parameter variable.
	 */
	final private QuotedStacksToken paramName;

	/**
	 * The type of the parameter
	 */
	final private QuotedStacksToken paramType;

	/**
	 * The description of the parameter.
	 */
	final private StacksDescription paramDescription;

	/**
	 * Construct a new {@link StacksParameterTag}.
	 *
	 * @param paramType
	 * 		The type of the parameter
	 * @param paramDescription
	 * 		The description of the parameter.
	 * @param paramName
	 * 		The name of the parameter variable.
	 */
	public StacksParameterTag (
		final QuotedStacksToken paramName,
		final QuotedStacksToken paramType,
		final StacksDescription paramDescription)
	{
		this.paramType = paramType;
		this.paramDescription = paramDescription;
		this.paramName = paramName;
	}

	/**
	 * @return the returnDescription
	 */
	public StacksDescription paramDescription ()
	{
		return paramDescription;
	}

	/**
	 * @return the returnType
	 */
	public QuotedStacksToken paramType ()
	{
		return paramType;
	}

	/**
	 * @return the paramName
	 */
	public QuotedStacksToken paramName ()
	{
		return paramName;
	}

	@Override
	public String toHTML(final HTMLFileMap htmlFileMap,
		final int hashID, final StacksErrorLog errorLog)
	{
		final StringBuilder paramTypeBuilder = new StringBuilder();
		if (htmlFileMap.internalLinks().containsKey(paramType.lexeme()))
		{
			paramTypeBuilder.append("<a ng-click=\"myParent().changeLinkValue('")
				.append(htmlFileMap.internalLinks().get(paramType.lexeme()))
				.append("')\" href=\"")
				.append(htmlFileMap.internalLinks().get(paramType.lexeme()))
				.append("\">")
				.append(paramType.toHTML(htmlFileMap, hashID, errorLog))
				.append("</a>");
		}
		else
		{
			paramTypeBuilder.append(paramType
				.toHTML(htmlFileMap, hashID, errorLog));
		}

		final StringBuilder stringBuilder = new StringBuilder()
			.append(tabs(4) + "<tr "
				+ HTMLBuilder.tagClass(HTMLClass.classMethodParameters)
				+ ">\n")
			.append(tabs(5) + "<td id=\"")
			.append(paramName.lexeme()).append(hashID).append("\" ")
			.append(HTMLBuilder
					.tagClass(HTMLClass.classStacks, HTMLClass.classICode)
				+ ">")
			.append(paramName.toHTML(htmlFileMap, hashID, errorLog))
			.append("</td>\n")
			.append(tabs(5) + "<td "
				+ HTMLBuilder
					.tagClass(HTMLClass.classStacks, HTMLClass.classICode)
				+ ">")
			.append(paramTypeBuilder)
			.append("</td>\n")
			.append(tabs(5) + "<td "
				+ HTMLBuilder
					.tagClass(HTMLClass.classStacks, HTMLClass.classIDesc)
				+ ">\n")
			.append(tabs(6) + paramDescription.toHTML(htmlFileMap, hashID,
				errorLog))
			.append("\n"+ tabs(5) + "</td>\n")
			.append(tabs(4) + "</tr>\n");
		return stringBuilder.toString();
	}
}
