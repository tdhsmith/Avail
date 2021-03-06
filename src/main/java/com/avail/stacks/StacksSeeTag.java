/*
 * StacksSeeTag.java
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

/**
 * The "@see" Avail comment tag.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksSeeTag extends AbstractStacksTag
{
	/**
	 * The thing that should be 'seen'
	 */
	private final RegionStacksToken thingToSee;

	/**
	 * Construct a new {@code StacksSeeTag}.
	 *
	 * @param thingToSee
	 * 		The thing that should be 'seen'
	 */
	public StacksSeeTag (
		final RegionStacksToken thingToSee)
	{
		this.thingToSee = thingToSee;
	}

	/**
	 * @return the thingToSee
	 */
	public RegionStacksToken thingToSee ()
	{
		return thingToSee;
	}

	@Override
	public void toJSON (
		final LinkingFileMap linkingFileMap,
		final int hashID,
		final StacksErrorLog errorLog,
		final int position,
		final JSONWriter jsonWriter)
	{
		thingToSee.toJSON(linkingFileMap, hashID, errorLog, jsonWriter);
	}

	/**
	 * @param linkingFileMap
	 * @param hashID
	 * @param errorLog
	 * @param jsonWriter
	 * @return
	 */
	public String toJSON (
		final LinkingFileMap linkingFileMap,
		final int hashID,
		final StacksErrorLog errorLog,
		final JSONWriter jsonWriter)
	{

		return "<a class="
			+ '"'
			+ "stacks i-code"
			+ '"'
			+ " href="
			+ '"'
			+ thingToSee
			.toJSON(linkingFileMap, hashID, errorLog, jsonWriter)
			+ '"'
			+ ">"
			+ thingToSee.lexeme
			+ "</a>";
	}
}
