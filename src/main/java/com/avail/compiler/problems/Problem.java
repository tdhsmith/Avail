/*
 * Problem.java
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

package com.avail.compiler.problems;

import com.avail.builder.ModuleName;
import com.avail.descriptor.CharacterDescriptor;
import com.avail.utility.evaluation.Continuation1NotNull;

import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.text.MessageFormat;

/**
 * A {@code Problem} is produced when encountering an unexpected or less than
 * ideal situation during compilation.  Within an interactive system, the
 * problem is presumably presented to the user in some manner, whereas in batch
 * usage multiple problems may simply be collected and later presented in
 * aggregate.
 *
 * <p>Subclasses (typically anonymous) may override {@link
 * #continueCompilation()} and {@link #abortCompilation()} to specify how a
 * particular problem site can continue or abort compilation, respectively. It
 * is the responsibility of any client problem handler to <em>decide</em>
 * whether to continue compiling or abort.  By default, {@code
 * continueCompilation()} simply invokes {@code abortCompilation()}.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class Problem
{
	/**
	 * The {@linkplain ModuleName unresolved, canonical name} of the module in
	 * which the problem occurred.
	 */
	public final @Nullable ModuleName moduleName;

	/**
	 * The one-based line number within the file in which the problem occurred.
	 * This may be approximate.
	 *
	 * <p>If the problem involves the entire file (file not found, no read
	 * access, etc.), a line number of 0 can indicate this.</p>
	 */
	public final int lineNumber;

	/**
	 * The approximate location of the problem within the source file as a
	 * zero-based subscript of the full-Unicode {@linkplain CharacterDescriptor
	 * code points} of the file.  {@linkplain Character#isSurrogate(char)
	 * Surrogate pairs} are treated as a single code point.
	 *
	 * <p>It is <em>strongly</em> recommended that Avail source files are
	 * always encoded in the UTF-8 {@linkplain Charset character set}.  The
	 * current compiler as of 2014.01.26 <em>requires</em> source files to be in
	 * UTF-8 encoding.</p>
	 */
	public final long characterInFile;

	/**
	 * The {@link ProblemType type} of problem that was encountered.
	 */
	public final ProblemType type;

	/**
	 * A {@link String} summarizing the issue.  This string will have
	 * {@linkplain MessageFormat} substitution applied to it, using the supplied
	 * {@linkplain #arguments}.
	 */
	private final String messagePattern;

	/**
	 * The parameters to be applied to the {@link #messagePattern}, which is
	 * expected to comply with the grammar of a {@link MessageFormat}.
	 */
	private final Object [] arguments;

	/**
	 * Construct a new {@code Problem}.
	 *
	 * @param moduleName
	 *        The name of the module in which the problem was encountered, or
	 *        {@code null} if no module is in context.
	 * @param lineNumber
	 *        The one-based line number on which the problem occurred, or zero
	 *        if there is no suitable line to blame.
	 * @param characterInFile
	 *        The zero-based code point position in the file.  Surrogate pairs
	 *        are treated as a single code point.
	 * @param type
	 *        The {@link ProblemType} that classifies this problem.
	 * @param messagePattern
	 *        A {@link String} complying with the {@link MessageFormat}
	 *        pattern specification.
	 * @param arguments
	 *        The arguments with which to parameterize the messagePattern.
	 */
	public Problem (
		final @Nullable ModuleName moduleName,
		final int lineNumber,
		final long characterInFile,
		final ProblemType type,
		final String messagePattern,
		final Object... arguments)
	{
		this.moduleName = moduleName;
		this.lineNumber = lineNumber;
		this.characterInFile = characterInFile;
		this.type = type;
		this.messagePattern = messagePattern;
		this.arguments = arguments.clone();
	}

	/**
	 * Report this problem to the {@link ProblemHandler handler}. Answer
	 * whether an attempt should be made to continue parsing past this problem.
	 *
	 * @param handler
	 *        The problem handler.
	 * @param decider
	 *        How to {@linkplain Problem#continueCompilation() continue} or
	 *        {@linkplain Problem#abortCompilation() abort} compilation.
	 *        Accepts a {@linkplain Boolean boolean} that is {@code true} iff
	 *        compilation should continue.
	 */
	final void report (
		final ProblemHandler handler,
		final Continuation1NotNull<Boolean> decider)
	{
		type.report(this, handler, decider);
	}

	/**
	 * Attempt to continue compiling past this problem.  If continuing to
	 * compile is inappropriate or impossible for the receiver, then as a
	 * convenience, this method simply calls {@link #abortCompilation()}.
	 */
	protected void continueCompilation ()
	{
		abortCompilation();
	}

	/**
	 * Give up compilation.  Note that either {@link #continueCompilation()} or
	 * this method must be invoked by code handling {@code Problem}s.
	 */
	protected abstract void abortCompilation ();

	@Override
	public String toString ()
	{
		return MessageFormat.format(messagePattern, arguments);
	}
}
