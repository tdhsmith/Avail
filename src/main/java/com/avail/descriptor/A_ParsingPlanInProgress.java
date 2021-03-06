/*
 * A_ParsingPlanInProgress.java
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

package com.avail.descriptor;

/**
 * {@code A_ParsingPlanInProgress} is an interface that specifies the operations
 * that must be implemented by a {@linkplain ParsingPlanInProgressDescriptor
 * parsing-plan-in-progress}.  It's a sub-interface of {@link A_BasicObject},
 * the interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_ParsingPlanInProgress
extends A_BasicObject
{
	/**
	 * Answer the program counter that this plan-in-progress represents.
	 *
	 * @return The index into the plan's parsing instructions.
	 */
	int parsingPc ();

	/**
	 * Answer this {@linkplain ParsingPlanInProgressDescriptor
	 * plan-in-progress's} {@link A_DefinitionParsingPlan}.
	 */
	A_DefinitionParsingPlan parsingPlan ();

	/**
	 * Answer a Java {@link String} representing this message name being parsed
	 * at its position within the plan's parsing instructions.
	 *
	 * @return A string describing the parsing plan with an indicator at the
	 *         specified parsing instruction.
	 */
	String nameHighlightingPc ();

	/**
	 * Answer whether this plan-in-progress is at a backward jump instruction.
	 *
	 * @return Whether it jumps backward from here.
	 */
	boolean isBackwardJump ();
}
