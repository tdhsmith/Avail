/*
 * L2ConstantOperand.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2OperandDispatcher;
import com.avail.interpreter.levelTwo.L2OperandType;

import static com.avail.descriptor.CompiledCodeTypeDescriptor.mostGeneralCompiledCodeType;
import static com.avail.interpreter.levelOne.L1Decompiler.decompile;

/**
 * An {@code L2ConstantOperand} is an operand of type {@link
 * L2OperandType#CONSTANT}.  It also holds the actual {@link AvailObject} that
 * is the constant.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2ConstantOperand
extends L2Operand
{
	/**
	 * The actual constant value.
	 */
	public final AvailObject object;

	/**
	 * Construct a new {@code L2ConstantOperand} with the specified {@link
	 * AvailObject constant}.
	 *
	 * @param object
	 *        The constant value.
	 */
	public L2ConstantOperand (final A_BasicObject object)
	{
		this.object = object.makeShared();
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.CONSTANT;
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("$(");
		if (object.isInstanceOf(mostGeneralCompiledCodeType()))
		{
			builder.append(decompile(object));
		}
		else
		{
			builder.append(object);
		}
		builder.append(")");
		return builder.toString();
	}
}
