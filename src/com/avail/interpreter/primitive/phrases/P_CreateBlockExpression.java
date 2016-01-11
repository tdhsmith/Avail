/**
 * P_CreateBlockExpression.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.phrases;

import static com.avail.descriptor.BlockNodeDescriptor.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.descriptor.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Create a {@linkplain BlockNodeDescriptor
 * block expression} from the specified {@linkplain ParseNodeKind#ARGUMENT_NODE
 * argument declarations}, primitive number, statements, result type, and
 * exception set.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_CreateBlockExpression
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_CreateBlockExpression().init(
			5, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 5;
		final A_Tuple argDecls = args.get(0);
		final A_Number primitive = args.get(1);
		final A_Tuple statements = args.get(2);
		final A_Type resultType = args.get(3);
		final A_Set exceptions = args.get(4);
		// Verify that each element of "statements" is actually a statement,
		// and that the last statement's expression type agrees with
		// "resultType".
		final List<A_Phrase> flat =
			new ArrayList<>(statements.tupleSize() + 3);
		for (final A_Phrase statement : statements)
		{
			statement.flattenStatementsInto(flat);
		}
		if (!ParseNodeTypeDescriptor.containsOnlyStatements(flat, resultType))
		{
			return interpreter.primitiveFailure(
				E_BLOCK_CONTAINS_INVALID_STATEMENTS);
		}
		final AvailObject block = newBlockNode(
			argDecls,
			primitive,
			statements,
			resultType,
			exceptions,
			0);
		return interpreter.primitiveSuccess(block);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.zeroOrMoreOf(
					ARGUMENT_NODE.mostGeneralType()),
				IntegerRangeTypeDescriptor.unsignedShorts(),
				TupleTypeDescriptor.zeroOrMoreOf(
					PARSE_NODE.mostGeneralType()),
				InstanceMetaDescriptor.topMeta(),
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ObjectTypeDescriptor.exceptionType())),
			BLOCK_NODE.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstance(
			E_BLOCK_CONTAINS_INVALID_STATEMENTS.numericCode());
	}
}