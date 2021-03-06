/*
 * GlobalCommentSignature.java
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

import static java.lang.String.format;

/**
 * GLOBAL Comment Signature
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class GlobalCommentSignature extends CommentSignature
{
	/**
	 * The type of the global module variable.
	 */
	final String globalType;
	/**
	 * Construct a new {@link GlobalCommentSignature}.
	 *
	 * @param name
	 * @param module
	 * @param globalType
	 */
	public GlobalCommentSignature (final String name, final String module,
		final String globalType)
	{
		super(name, module);
		this.globalType = globalType;
	}

	@Override
	public String toString ()
	{
		return format(
			"%s -> %s",
			name(),
			globalType);
	}

	@Override
	public void toJSON (final String nameOfGroup, final boolean sticky,
		final JSONWriter jsonWriter)
	{
		jsonWriter.write("sticky");
		jsonWriter.write(sticky);
		jsonWriter.write("source");
		jsonWriter.write(module());
		jsonWriter.write("globalType");
		jsonWriter.write(globalType.replace("<", "&lt;"));
	}
}
