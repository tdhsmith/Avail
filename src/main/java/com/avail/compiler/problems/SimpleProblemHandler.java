/*
 * SimpleProblemHandler.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.compiler.problems;

import com.avail.utility.evaluation.Continuation1NotNull;


/**
 * A {@code SimpleProblemHandler} is a {@link ProblemHandler} that handles all
 * {@link Problem}s the same way, via its {@link #handleGeneric(Problem,
 * Continuation1NotNull)} method.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@FunctionalInterface
public interface SimpleProblemHandler extends ProblemHandler
{
	/**
	 * One of the {@link ProblemType}-specific handler methods was invoked, but
	 * (1) it was not specifically overridden in the subclass, and (2) this
	 * method was not specifically overridden in the subclass.  Always fail in
	 * this circumstance.
	 *
	 * @param problem
	 *        The problem being handled generically.
	 * @param decider
	 *        How to {@linkplain Problem#continueCompilation() continue} or
	 *        {@linkplain Problem#abortCompilation() abort} compilation.
	 *        Accepts a {@linkplain Boolean boolean} that is {@code true} iff
	 *        compilation should continue.
	 */
	@Override
	void handleGeneric (
		final Problem problem,
		final Continuation1NotNull<Boolean> decider);
}
