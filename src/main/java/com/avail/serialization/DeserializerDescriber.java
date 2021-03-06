/*
 * DeserializerDescriber.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.serialization;

import com.avail.AvailRuntime;
import com.avail.descriptor.AvailObject;

import java.io.InputStream;

/**
 * A {@link DeserializerDescriber} takes a stream of bytes and outputs a
 * description of what would be reconstructed by a {@link Deserializer}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class DeserializerDescriber extends AbstractDeserializer
{
	/** The {@link StringBuilder} on which the description is being written. */
	private final StringBuilder builder = new StringBuilder(1000);

	/**
	 * Decode all of the deserialization steps, and return the resulting {@link
	 * String}.
	 *
	 * @return The descriptive {@link String}.
	 * @throws MalformedSerialStreamException
	 *         If the stream is malformed.
	 */
	public String describe ()
		throws MalformedSerialStreamException
	{
		try
		{
			int objectNumber = 0;
			while (input.available() > 0)
			{
				append(Integer.toString(objectNumber++));
				append(": ");
				SerializerOperation.byOrdinal(readByte()).describe(this);
				append("\n");
			}
		}
		catch (final Exception e)
		{
			throw new MalformedSerialStreamException(e);
		}
		return builder.toString();
	}

	/**
	 * Construct a new {@code DeserializerDescriber}.
	 *
	 * @param input
	 *        An {@link InputStream} from which to reconstruct objects.
	 * @param runtime
	 *        The {@link AvailRuntime} from which to locate well-known objects
	 *        during deserialization.
	 */
	public DeserializerDescriber (
		final InputStream input,
		final AvailRuntime runtime)
	{
		super(input, runtime);
	}

	@Override
	AvailObject objectFromIndex (final int index)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	void recordProducedObject (final AvailObject object)
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Append the given string to my description.
	 *
	 * @param string The {@link String} to append.
	 */
	void append (final String string)
	{
		builder.append(string);
	}
}
