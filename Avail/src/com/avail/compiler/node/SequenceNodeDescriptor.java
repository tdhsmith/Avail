/**
 * com.avail.compiler/SequenceNodeDescriptor.java
 * Copyright (c) 2011, Mark van Gulik.
 * All rights reserved.
 *
 * modification, are permitted provided that the following conditions are met:
 * Redistribution and use in source and binary forms, with or without
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

package com.avail.compiler.node;

import java.util.List;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * My instances represent a sequence of {@linkplain ParseNodeDescriptor parse
 * nodes} to be treated as statements, except possibly the last one.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class SequenceNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@link ParseNodeDescriptor statements} that should be considered
		 * to execute sequentially, discarding each result except possibly for
		 * that of the last statement.
		 */
		STATEMENTS,
	}


	/**
	 * Setter for field statements.
	 */
	@Override
	public void o_Statements (
		final AvailObject object,
		final AvailObject statementsTuple)
	{
		assert statementsTuple.tupleSize() > 0;
		object.objectSlotPut(ObjectSlots.STATEMENTS, statementsTuple);
	}

	/**
	 * Getter for field statements.
	 */
	@Override
	public AvailObject o_Statements (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.STATEMENTS);
	}

	@Override
	public AvailObject o_ExpressionType (final AvailObject object)
	{
		AvailObject statements = object.statements();
		assert statements.tupleSize() > 0;
		return statements.tupleAt(statements.tupleSize()).expressionType();
	}

	@Override
	public AvailObject o_Type (final AvailObject object)
	{
		return Types.SEQUENCE_NODE.o();
	}

	@Override
	public AvailObject o_ExactType (final AvailObject object)
	{
		return Types.SEQUENCE_NODE.o();
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return object.statements().hash() + 0xE38140CA;
	}

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return object.type().equals(another.type())
			&& object.statements().equals(another.statements());
	}

	@Override
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final AvailObject statements = object.statements();
		final int statementsCount = statements.tupleSize();
		assert statements.tupleSize() > 0;
		for (int i = 1; i < statementsCount; i++)
		{
			statements.tupleAt(i).emitEffectOn(codeGenerator);
		}
		statements.tupleAt(statementsCount).emitValueOn(codeGenerator);
	}

	@Override
	public void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		for (AvailObject statement : object.statements())
		{
			statement.emitEffectOn(codeGenerator);
		}
	}

	@Override
	public void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		AvailObject statements = object.statements();
		for (int i = 1; i <= statements.tupleSize(); i++)
		{
			statements = statements.tupleAtPuttingCanDestroy(
				i,
				aBlock.value(statements.tupleAt(i)),
				true);
		}
		object.statements(statements);
	}


	@Override
	public void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		for (AvailObject statement : object.statements())
		{
			aBlock.value(statement);
		}
	}


	@Override
	public void o_ValidateLocally (
		final AvailObject object,
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		// Do nothing.
	}


	@Override
	public void o_FlattenStatementsInto (
		final AvailObject object,
		final List<AvailObject> accumulatedStatements)
	{
		for (AvailObject statement : object.statements())
		{
			statement.flattenStatementsInto(accumulatedStatements);
		}
	}


	/**
	 * Create a new {@link SequenceNodeDescriptor sequence node} from the given
	 * {@link TupleDescriptor tuple} of {@link ParseNodeDescriptor statements}.
	 *
	 * @param statements
	 *        The expressions to assemble into a {@link SequenceNodeDescriptor
	 *        sequence node}.
	 * @return The resulting sequence node.
	 */
	public static AvailObject newStatements (final AvailObject statements)
	{
		final AvailObject instance = mutable().create();
		instance.statements(statements);
		return instance;
	}

	/**
	 * Construct a new {@link SequenceNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public SequenceNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link SequenceNodeDescriptor}.
	 */
	private final static SequenceNodeDescriptor mutable =
		new SequenceNodeDescriptor(true);

	/**
	 * Answer the mutable {@link SequenceNodeDescriptor}.
	 *
	 * @return The mutable {@link SequenceNodeDescriptor}.
	 */
	public static SequenceNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link SequenceNodeDescriptor}.
	 */
	private final static SequenceNodeDescriptor immutable =
		new SequenceNodeDescriptor(false);

	/**
	 * Answer the immutable {@link SequenceNodeDescriptor}.
	 *
	 * @return The immutable {@link SequenceNodeDescriptor}.
	 */
	public static SequenceNodeDescriptor immutable ()
	{
		return immutable;
	}
}