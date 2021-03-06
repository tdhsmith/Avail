/*
 * L2SelectorOperand.java
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.MessageBundleDescriptor;
import com.avail.descriptor.MethodDefinitionDescriptor;
import com.avail.descriptor.MethodDescriptor;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2OperandType;

/**
 * An {@code L2SelectorOperand} is an operand of type {@link
 * L2OperandType#SELECTOR}.  It holds the {@linkplain MessageBundleDescriptor
 * message bundle} that knows the {@linkplain MethodDescriptor method} to
 * invoke.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2SelectorOperand
extends L2Operand
{
	/**
	 * The actual {@linkplain MethodDescriptor method}.
	 */
	public final A_Bundle bundle;

	/**
	 * Construct a new {@code L2SelectorOperand} with the specified {@linkplain
	 * MessageBundleDescriptor message bundle}.
	 *
	 * @param bundle
	 *        The message bundle that holds the {@linkplain MethodDescriptor
	 *        method} in which to look up the {@linkplain
	 *        MethodDefinitionDescriptor method definition} to ultimately
	 *        invoke.
	 */
	public L2SelectorOperand (final A_Bundle bundle)
	{
		this.bundle = bundle;
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.SELECTOR;
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	@Override
	public String toString ()
	{
		return "$" + bundle.message().atomName();
	}
}
