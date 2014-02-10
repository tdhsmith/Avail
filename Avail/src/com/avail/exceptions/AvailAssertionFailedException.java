/**
 * AvailAssertionFailedException.java
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

package com.avail.exceptions;

import com.avail.descriptor.*;

/**
 * An {@code AvailAssertionFailedException} is thrown when an Avail assertion
 * fails.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class AvailAssertionFailedException
extends Exception
{
	/**
	 * The serial version identifier.
	 */
	private static final long serialVersionUID = -3945878927329358120L;

	/**
	 * The {@linkplain StringDescriptor error message} describing the
	 * assertion.
	 */
	private final A_String assertionString;

	/**
	 * Return the {@linkplain StringDescriptor error message} describing the
	 * assertion.
	 *
	 * @return The interpretation of the assertion.
	 */
	public A_String assertionString ()
	{
		return assertionString;
	}

	/**
	 * Construct a new {@link AvailAssertionFailedException}.
	 *
	 * @param assertionString
	 *        The {@linkplain StringDescriptor error message} describing the
	 *        assertion.
	 */
	public AvailAssertionFailedException (
		final A_String assertionString)
	{
		assert assertionString.isString();
		this.assertionString = assertionString;
	}

	/**
	 * Construct a new {@link AvailAssertionFailedException}.
	 *
	 * @param assertionString
	 *        The {@linkplain StringDescriptor error message} describing the
	 *        assertion.
	 */
	public AvailAssertionFailedException (
		final String assertionString)
	{
		this.assertionString = StringDescriptor.from(assertionString);
	}

	@Override
	public String getMessage ()
	{
		return String.format(
			"An assertion failed: %s%n",
			assertionString.asNativeString());
	}
}