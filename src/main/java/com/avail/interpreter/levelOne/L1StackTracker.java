/*
 * L1StackTracker.java
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

import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.AvailObject;

import javax.annotation.Nullable;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.Math.max;

/**
 * An {@code L1StackTracker} verifies the integrity of a sequence of {@link
 * L1Operation}s and operands, and calculates how big a stack will be necessary.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
abstract class L1StackTracker implements L1OperationDispatcher
{
	/**
	 * The operands of the {@link L1Operation} being traced.
	 */
	@Nullable int [] currentOperands = null;

	/**
	 * @return The current {@link L1Operation}'s encoded operands.
	 */
	int [] currentOperands ()
	{
		return stripNull(currentOperands);
	}

	/**
	 * The number of items that will have been pushed on the stack at this
	 * point in the code.
	 */
	int currentDepth = 0;

	/**
	 * The maximum stack depth needed so far.
	 */
	int maxDepth = 0;

	/**
	 * Answer the deepest encountered stack depth so far.
	 *
	 * @return The maximum
	 */
	int maxDepth ()
	{
		return maxDepth;
	}

	/**
	 * Record the effect of an L1 instruction.
	 *
	 * @param operation The {@link L1Operation}.
	 * @param operands The operation's {@code int} operands.
	 */
	void track (
		final L1Operation operation,
		final int... operands)
	{
		assert currentDepth >= 0;
		assert maxDepth >= currentDepth;
		currentOperands = operands;
		operation.dispatch(this);
		currentOperands = null;
		assert currentDepth >= 0;
		maxDepth = max(maxDepth, currentDepth);
	}

	/**
	 * Answer the literal at the specified index.
	 *
	 * @param literalIndex The literal's index.
	 * @return The literal {@link AvailObject}.
	 */
	abstract AvailObject literalAt (int literalIndex);

	@Override
	public void L1_doCall ()
	{
		final A_Bundle bundle = literalAt(currentOperands()[0]);
		currentDepth += 1 - bundle.bundleMethod().numArgs();
	}

	@Override
	public void L1_doPushLiteral ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doPushLastLocal ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doPushLocal ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doPushLastOuter ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doClose ()
	{
		currentDepth += 1 - currentOperands()[0];
	}

	@Override
	public void L1_doSetLocal ()
	{
		currentDepth--;
	}

	@Override
	public void L1_doGetLocalClearing ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doPushOuter ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doPop ()
	{
		currentDepth--;
	}

	@Override
	public void L1_doGetOuterClearing ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doSetOuter ()
	{
		currentDepth--;
	}

	@Override
	public void L1_doGetLocal ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doMakeTuple ()
	{
		currentDepth += 1 - currentOperands()[0];
	}

	@Override
	public void L1_doGetOuter ()
	{
		currentDepth++;
	}

	@Override
	public void L1_doExtension ()
	{
		error("The extension nybblecode should not be dispatched.");
	}

	@Override
	public void L1Ext_doPushLabel ()
	{
		currentDepth++;
	}

	@Override
	public void L1Ext_doGetLiteral ()
	{
		currentDepth++;
	}

	@Override
	public void L1Ext_doSetLiteral ()
	{
		currentDepth--;
	}

	@Override
	public void L1Ext_doDuplicate ()
	{
		currentDepth++;
	}

	@Override
	public void L1Ext_doSetSlot ()
	{
		currentDepth--;
	}

	@SuppressWarnings("EmptyMethod")
	@Override
	public void L1Ext_doPermute ()
	{
		// No change.
	}

	@Override
	public void L1Ext_doSuperCall ()
	{
		final A_Bundle bundle = literalAt(currentOperands()[0]);
		currentDepth += 1 - bundle.bundleMethod().numArgs();
	}
}
