/**
 * Message.java
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

package com.avail.server.messages;

import com.avail.server.AvailServer;

/**
 * An {@link AvailServer} sends and receives {@code Message}s. A {@code Message}
 * received by the server represents a command from the client, whereas a {@code
 * Message} sent by the server represents a response to a command.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class Message
{
	/** The {@linkplain String content} of the {@linkplain Message message}. */
	private final String content;

	/**
	 * Construct a new {@link Message}.
	 *
	 * @param content
	 *        The {@linkplain String content}.
	 */
	public Message (final String content)
	{
		this.content = content;
	}

	/**
	 * Answer the {@linkplain String content} of the {@linkplain Message
	 * message}.
	 *
	 * @return The message's content.
	 */
	public String content ()
	{
		return content;
	}
}