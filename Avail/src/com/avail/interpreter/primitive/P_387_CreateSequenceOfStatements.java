/**
 * P_387_CreateSequenceOfStatements.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter.primitive;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 387</strong>: Create a {@linkplain SequenceNodeDescriptor
 * sequence} from the specified {@linkplain TupleDescriptor tuple} of
 * statements.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class P_387_CreateSequenceOfStatements
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final @NotNull static Primitive instance =
		new P_387_CreateSequenceOfStatements().init(1, CanFold);

	@Override
	public @NotNull Result attempt (
		final @NotNull List<AvailObject> args,
		final @NotNull Interpreter interpreter)
	{
		assert args.size() == 1;
		final AvailObject statements = args.get(0);
		final List<AvailObject> flat =
			new ArrayList<AvailObject>(statements.tupleSize() + 3);
		for (final AvailObject statement : statements)
		{
			statement.flattenStatementsInto(flat);
		}
		if (!ParseNodeTypeDescriptor.containsOnlyStatements(flat, TOP.o()))
		{
			return interpreter.primitiveFailure(
				E_SEQUENCE_CONTAINS_INVALID_STATEMENTS);
		}
		return interpreter.primitiveSuccess(statements);
	}

	@Override
	protected @NotNull AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					TupleDescriptor.empty(),
					PARSE_NODE.mostGeneralType())),
			SEQUENCE_NODE.mostGeneralType());
	}
}