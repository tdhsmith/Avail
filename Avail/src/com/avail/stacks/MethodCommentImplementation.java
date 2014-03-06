/**
 * MethodCommentImplementation.java
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

import java.util.ArrayList;

/**
 * A comment that describes a particular method implementation
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class MethodCommentImplementation extends AbstractCommentImplementation
{
	/**
	 * The overall description of the implementation
	 */
	final ArrayList<AbstractStacksToken> description;

	/**
	 * The list of {@link StacksParameterTag parameters} of the method
	 * implementation.
	 */
	final ArrayList<StacksParameterTag> parameters;

	/**
	 * The {@link StacksReturnTag "@returns"} content
	 */
	final StacksReturnTag returnsContent;

	/**
	 *
	 */
	final ArrayList<StacksRaisesTag> exceptions;

	/**
	 * Construct a new {@link MethodCommentImplementation}.
	 *
	 * @param signature
	 * 		The {@link MethodCommentSignature signature} of the class/method the
	 * 		comment describes.
	 * @param commentStartLine
	 * 		The start line in the module the comment being parsed appears.
	 * @param author
	 * 		The {@link StacksAuthorTag author} of the implementation.
	 * @param sees
	 * 		A {@link ArrayList} of any {@link StacksSeeTag "@sees"} references.
	 * @param description
	 * 		The overall description of the implementation
	 * @param parameters
	 * 		The list of {@link StacksParameterTag parameters} of the method
	 * 		implementation.
	 * @param returnsContent
	 * 		The {@link StacksReturnTag "@returns"} content
	 * @param exceptions
	 * 		A {@link ArrayList} of any {@link StacksRaisesTag exceptions} the method
	 * 		throws.
	 */
	public MethodCommentImplementation (
		final MethodCommentSignature signature,
		final int commentStartLine,
		final ArrayList<StacksAuthorTag> author,
		final ArrayList<StacksSeeTag> sees,
		final ArrayList<AbstractStacksToken> description,
		final ArrayList<StacksParameterTag> parameters,
		final StacksReturnTag returnsContent,
		final ArrayList<StacksRaisesTag> exceptions)
	{
		super(signature, commentStartLine, author, sees);
		this.description = description;
		this.parameters = parameters;
		this.returnsContent = returnsContent;
		this.exceptions = exceptions;
	}

}