/**
 * L2OperandDescriber.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.interpreter.levelTwo;

import com.avail.descriptor.*;
import com.avail.interpreter.Primitive;

/**
 * An {@code L2OperandDescriber} uses the {@link L2OperandTypeDispatcher}
 * mechanism to describe one of the operands of a level two instruction.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
class L2OperandDescriber implements L2OperandTypeDispatcher
{
	/**
	 * The numeric operand being described.
	 */
	private int _operand;

	/**
	 * The {@linkplain L2ChunkDescriptor level two chunk} containing the operation
	 * and the operand to be described.
	 */
	private AvailObject _chunk;

	/**
	 * The {@link StringBuilder} on which to write an operand description.
	 */
	private StringBuilder _description;


	/**
	 * Output a description of the given operand to the stream, given its
	 * numeric encoding, its {@linkplain L2OperandType operand type}, and the current
	 * {@linkplain L2ChunkDescriptor chunk}.
	 *
	 * @param operandType
	 *            The {@link L2OperandType} used to interpret the operand.
	 * @param operand
	 *            The numeric operand itself, an {@code int}.
	 * @param chunk
	 *            The current {@linkplain L2ChunkDescriptor level two chunk} within
	 *            which the description is to occur.
	 * @param stream
	 *            The {@link StringBuilder} that will have a suitable operand
	 *            description appended.
	 */
	public void describeInOperandChunkOn (
			final L2OperandType operandType,
			final int operand,
			final AvailObject chunk,
			final StringBuilder stream)
	{
		_operand = operand;
		_chunk = chunk;
		_description = stream;
		operandType.dispatch(this);
	}


	@Override
	public void doConstant()
	{
		_description.append("Const(");
		_description.append(_chunk.literalAt(_operand).toString());
		_description.append(")");
	}
	@Override
	public void doImmediate()
	{
		_description.append("Immediate(");
		_description.append(_operand);
		_description.append(")");
	}
	@Override
	public void doPC()
	{
		_description.append("PC(");
		_description.append(_operand);
		_description.append(")");
	}
	@Override
	public void doPrimitive()
	{
		_description.append("Prim(");
		_description.append(Primitive.byPrimitiveNumber((short)_operand).name());
		_description.append(")");
	}
	@Override
	public void doSelector()
	{
		_description.append("Message(");
		AvailObject impSet = _chunk.literalAt(_operand);
		_description.append(impSet.name().name().asNativeString());
		_description.append(")");
	}
	@Override
	public void doReadPointer()
	{
		_description.append("Obj(");
		_description.append(_operand);
		_description.append(")[read]");
	}
	@Override
	public void doWritePointer()
	{
		_description.append("Obj(");
		_description.append(_operand);
		_description.append(")[write]");
	}
	@Override
	public void doReadWritePointer()
	{
		_description.append("Obj(");
		_description.append(_operand);
		_description.append(")[read/write]");
	}
	@Override
	public void doReadInt()
	{
		_description.append("Int(");
		_description.append(_operand);
		_description.append(")[read]");
	}
	@Override
	public void doWriteInt()
	{
		_description.append("Int(");
		_description.append(_operand);
		_description.append(")[write]");
	}
	@Override
	public void doReadWriteInt()
	{
		_description.append("Int(");
		_description.append(_operand);
		_description.append(")[read/write]");
	}
	@Override
	public void doReadVector()
	{
		_description.append("Vector#");
		_description.append(_operand);
		_description.append("=(");
		AvailObject vector = _chunk.vectors().tupleAt(_operand);
		for (int i = 1; i <= vector.tupleSize(); i++)
		{
			if (i > 1)
			{
				_description.append(",");
			}
			_description.append(vector.tupleAt(i).extractInt());
		}
		_description.append(")[read]");
	}
	@Override
	public void doWriteVector()
	{
		_description.append("Vector#");
		_description.append(_operand);
		_description.append("=(");
		AvailObject vector = _chunk.vectors().tupleAt(_operand);
		for (int i = 1; i <= vector.tupleSize(); i++)
		{
			if (i > 1)
			{
				_description.append(",");
			}
			_description.append(vector.tupleAt(i).extractInt());
		}
		_description.append(")[write]");
	}
	@Override
	public void doReadWriteVector()
	{
		_description.append("Vector#");
		_description.append(_operand);
		_description.append("=(");
		AvailObject vector = _chunk.vectors().tupleAt(_operand);
		for (int i = 1; i <= vector.tupleSize(); i++)
		{
			if (i > 1)
			{
				_description.append(",");
			}
			_description.append(vector.tupleAt(_operand).extractInt());
		}
		_description.append(")[read/write]");
	}
}
