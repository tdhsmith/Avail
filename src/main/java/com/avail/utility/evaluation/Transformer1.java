/*
 * Transformer1.java
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

package com.avail.utility.evaluation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.avail.utility.Nulls.stripNull;

/**
 * Implementors of {@code Transformer1} provide a single arbitrary operation
 * that accepts one argument and produces a result.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @param <X> The type of the sole argument to the operation.
 * @param <Y> The type of value produced by the operation.
 */
@FunctionalInterface
public interface Transformer1 <X,Y>
{
	/**
	 * Perform the operation.
	 *
	 * @param arg The argument to the operation.
	 * @return The result of performing the operation.
	 */
	@Nullable Y value (@Nullable X arg);

	/**
	 * Perform the operation, then assert a {@link Nonnull} condition for the
	 * result as a convenience.
	 *
	 * @param arg The argument to transform.
	 * @return The non-null transformed value.
	 */
	default Y valueNotNull (final @Nullable X arg)
	{
		return stripNull(value(arg));
	}
}
