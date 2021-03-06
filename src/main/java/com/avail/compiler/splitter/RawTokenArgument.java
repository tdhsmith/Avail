/*
 * RawTokenArgument.java
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
package com.avail.compiler.splitter;
import com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import com.avail.descriptor.A_Type;

import static com.avail.compiler.ParsingOperation.PARSE_ANY_RAW_TOKEN;
import static com.avail.compiler.ParsingOperation.TYPE_CHECK_ARGUMENT;

/**
 * A {@code RawTokenArgument} is an occurrence of {@linkplain
 * Metacharacter#ELLIPSIS ellipsis} (…) in a message name, followed by
 * an {@linkplain Metacharacter#EXCLAMATION_MARK exclamation mark} (!).
 * It indicates where <em>any</em> raw token is expected, which gets
 * captured as an argument, wrapped in a literal phrase.
 */
class RawTokenArgument
extends Argument
{
	/**
	 * Construct a {@code RawTokenArgument}, given the one-based position of the
	 * token in the name, and the absolute index of this argument in the entire
	 * message name.
	 *
	 * @param positionInName
	 *        The one-based position of the start of the token in the message
	 *        name.
	 * @param absoluteUnderscoreIndex
	 *        The one-based index of this argument within the entire message
	 *        name's list of arguments.
	 */
	RawTokenArgument (
		final int positionInName,
		final int absoluteUnderscoreIndex)
	{
		super(positionInName, absoluteUnderscoreIndex);
	}

	@Override
	WrapState emitOn (
		final A_Type phraseType,
		final InstructionGenerator generator,
		final WrapState wrapState)
	{
		generator.flushDelayed();
		generator.emit(this, PARSE_ANY_RAW_TOKEN);
		generator.emitDelayed(
			this,
			TYPE_CHECK_ARGUMENT,
			MessageSplitter.indexForConstant(phraseType));
		return wrapState;
	}
}
