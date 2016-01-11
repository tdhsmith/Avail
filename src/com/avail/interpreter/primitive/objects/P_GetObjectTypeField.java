/**
 * P_GetObjectTypeField.java
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
package com.avail.interpreter.primitive.objects;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.E_NO_SUCH_FIELD;
import static com.avail.interpreter.Primitive.Fallibility.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Extract the specified {@linkplain
 * AtomDescriptor field}'s type from the {@linkplain ObjectTypeDescriptor
 * object type}.
 */
public final class P_GetObjectTypeField extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_GetObjectTypeField().init(
			2, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Type objectType = args.get(0);
		final A_Atom field = args.get(1);
		final A_Map fieldTypeMap = objectType.fieldTypeMap();
		if (!fieldTypeMap.hasKey(field))
		{
			return interpreter.primitiveFailure(E_NO_SUCH_FIELD);
		}
		return interpreter.primitiveSuccess(fieldTypeMap.mapAt(field));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ObjectTypeDescriptor.meta(),
				ATOM.o()),
			InstanceMetaDescriptor.anyMeta());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type objectMeta = argumentTypes.get(0);
		final A_Type fieldType = argumentTypes.get(1);

		if (objectMeta.isBottom())
		{
			return BottomTypeDescriptor.bottom();
		}
		if (fieldType.isEnumeration())
		{
			final A_Type objectType = objectMeta.instance();
			final A_Map fieldTypeMap = objectType.fieldTypeMap();
			A_Type union = BottomTypeDescriptor.bottom();
			for (final A_Atom possibleField : fieldType.instances())
			{
				if (!fieldTypeMap.hasKey(possibleField))
				{
					// Unknown field, so the field type could be any type.
					return InstanceMetaDescriptor.anyMeta();
				}
				union = union.typeUnion(fieldTypeMap.mapAt(possibleField));
			}
			// Shift it up; a primitive invocation will return the field's type.
			return InstanceMetaDescriptor.on(union);
		}
		return super.returnTypeGuaranteedByVM(argumentTypes);
	}

	@Override
	public Fallibility fallibilityForArgumentTypes (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type objectMeta = argumentTypes.get(0);
		final A_Type fieldType = argumentTypes.get(1);

		if (fieldType.isEnumeration())
		{
			final A_Type objectType = objectMeta.instance();
			final A_Map fieldTypeMap = objectType.fieldTypeMap();
			for (final A_Atom possibleField : fieldType.instances())
			{
				if (!fieldTypeMap.hasKey(possibleField))
				{
					// Unknown field.
					return CallSiteCanFail;
				}
			}
			return CallSiteCannotFail;
		}
		return CallSiteCanFail;
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstance(
			E_NO_SUCH_FIELD.numericCode());
	}
}