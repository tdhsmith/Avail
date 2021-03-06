/*
 * L1OperandTypeDispatcher.java
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

package com.avail.interpreter.levelOne;

import com.avail.descriptor.FunctionDescriptor;

/**
 * A visitor for {@linkplain L1OperandType}s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface L1OperandTypeDispatcher
{
	/**
	 * The operand is an {@linkplain L1OperandType#IMMEDIATE immediate value},
	 * encoding an integer as itself.
	 */
	void doImmediate ();

	/**
	 * The operand is a {@linkplain L1OperandType#LITERAL literal value},
	 * encoded by a subscript into some list of literals.
	 */
	void doLiteral ();

	/**
	 * The operand is a {@linkplain L1OperandType#LOCAL local}, encoded by a
	 * subscript into the arguments and locals area.
	 */
	void doLocal ();

	/**
	 * The operand is a declaration {@linkplain L1OperandType#OUTER captured}
	 * set an outer scope, encoded as a subscript into some {@linkplain
	 * FunctionDescriptor function}'s list of outer variables.
	 */
	void doOuter ();

	/**
	 * The operand is an extension nybblecode, indicating that the current
	 * {@link L1Operation} has an ordinal equal to the next nybble plus 16.
	 */
	void doExtension ();
}
