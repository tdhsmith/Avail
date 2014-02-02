/**
 * AvailEmergencyExitException.java
 * Copyright © 1993-2014, Mark van Gulik and Todd L Smith.
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

import com.avail.descriptor.A_String;
import com.avail.descriptor.StringDescriptor;

/**
 * An {@code AvailEmergencyExitException} is thrown when a primitive fails
 * during system bootstrapping.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class AvailEmergencyExitException
extends Exception
{
	/** The serial version identifier. */
	private static final long serialVersionUID = 3368815860637333527L;

	/**
	 * The {@linkplain StringDescriptor error message} describing the
	 * emergency exit situation.
	 */
	private final A_String failureString;

	/**
	 * Return the {@linkplain StringDescriptor error message} describing the
	 * emergency exit situation.
	 *
	 * @return The interpretation of the exception.
	 */
	public A_String failureString ()
	{
		return failureString;
	}

	/**
	 * Construct a new {@link AvailEmergencyExitException}.
	 *
	 * @param failureString
	 *        The {@linkplain StringDescriptor error message} describing the
	 *        emergency exit situation.
	 */
	public AvailEmergencyExitException (final A_String failureString)
	{
		assert failureString.isString();
		this.failureString = failureString;
	}

	/**
	 * Construct a new {@link AvailEmergencyExitException}.
	 *
	 * @param failureString
	 *        The {@linkplain StringDescriptor error message} describing the
	 *        emergency exit situation.
	 */
	public AvailEmergencyExitException (final String failureString)
	{
		this.failureString = StringDescriptor.from(failureString);
	}

	@Override
	public String getMessage ()
	{
		return String.format(
			"A bootstrap operation failed: %s%n",
			failureString.asNativeString());
	}
}
