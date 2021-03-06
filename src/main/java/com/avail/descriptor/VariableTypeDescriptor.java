/*
 * VariableTypeDescriptor.java
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

import com.avail.annotations.AvailMethod;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.ReadWriteVariableTypeDescriptor.fromReadAndWriteTypes;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.descriptor.VariableTypeDescriptor.ObjectSlots.INNER_TYPE;

/**
 * A {@code VariableTypeDescriptor variable type} is the {@linkplain
 * TypeDescriptor type} of any {@linkplain VariableDescriptor variable} that can
 * only hold objects having the specified {@linkplain ObjectSlots#INNER_TYPE
 * inner type}. The read and write capabilities of the object instances are
 * equivalent, therefore the inner type is invariant.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see ReadWriteVariableTypeDescriptor
 */
public final class VariableTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The type of values that my object instances can contain.
		 */
		INNER_TYPE
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append("↑");
		object.slot(INNER_TYPE).printOnAvoidingIndent(
			aStream,
			recursionMap,
			(indent + 1));
	}

	@Override @AvailMethod
	A_Type o_ReadType (final AvailObject object)
	{
		return object.slot(INNER_TYPE);
	}

	@Override @AvailMethod
	A_Type o_WriteType (final AvailObject object)
	{
		return object.slot(INNER_TYPE);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsVariableType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsVariableType (
		final AvailObject object,
		final A_Type aType)
	{
		if (object.sameAddressAs(aType))
		{
			return true;
		}
		final boolean same =
			aType.readType().equals(object.slot(INNER_TYPE))
			&& aType.writeType().equals(object.slot(INNER_TYPE));
		if (same)
		{
			if (!isShared())
			{
				aType.makeImmutable();
				object.becomeIndirectionTo(aType);
			}
			else if (!aType.descriptor().isShared())
			{
				object.makeImmutable();
				aType.becomeIndirectionTo(object);
			}
		}
		return same;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return (object.slot(INNER_TYPE).hash() ^ 0x7613E420) + 0x024E3167;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfVariableType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		final AvailObject innerType = object.slot(INNER_TYPE);

		// Variable types are covariant by read capability and contravariant by
		// write capability.
		return aVariableType.readType().isSubtypeOf(innerType)
			&& innerType.isSubtypeOf(aVariableType.writeType());
	}

	@Override @AvailMethod
	A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		if (object.isSubtypeOf(another))
		{
			return object;
		}
		if (another.isSubtypeOf(object))
		{
			return another;
		}
		return another.typeIntersectionOfVariableType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		final A_Type innerType = object.slot(INNER_TYPE);
		// The intersection of two variable types is a variable type whose
		// read type is the type intersection of the two incoming read types and
		// whose write type is the type union of the two incoming write types.
		return variableReadWriteType(
			innerType.typeIntersection(aVariableType.readType()),
			innerType.typeUnion(aVariableType.writeType()));
	}

	@Override @AvailMethod
	A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
	{
		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		return another.typeUnionOfVariableType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		final A_Type innerType = object.slot(INNER_TYPE);

		// The union of two variable types is a variable type whose
		// read type is the type union of the two incoming read types and whose
		// write type is the type intersection of the two incoming write types.
		return variableReadWriteType(
			innerType.typeUnion(aVariableType.readType()),
			innerType.typeIntersection(aVariableType.writeType()));
	}

	@Override @AvailMethod
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.SIMPLE_VARIABLE_TYPE;
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Since there isn't an immutable variant, make the object shared.
			return object.makeShared();
		}
		return object;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable type");
		writer.write("write type");
		final AvailObject innerType = object.slot(INNER_TYPE);
		innerType.writeTo(writer);
		writer.write("read type");
		innerType.writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable type");
		writer.write("write type");
		final AvailObject innerType = object.slot(INNER_TYPE);
		innerType.writeSummaryTo(writer);
		writer.write("read type");
		innerType.writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a variable type based on the given content {@linkplain
	 * TypeDescriptor type}.
	 *
	 * @param innerType
	 *        The content type on which to base the variable type.
	 * @return
	 *        The new variable type.
	 */
	public static A_Type variableTypeFor (final A_Type innerType)
	{
		final AvailObject result = mutable.create();
		result.setSlot(INNER_TYPE, innerType.makeImmutable());
		return result;
	}

	/**
	 * Create a variable type based on the given read and write {@linkplain
	 * TypeDescriptor types}.
	 *
	 * @param readType
	 *        The read type.
	 * @param writeType
	 *        The write type.
	 * @return The new variable type.
	 */
	public static A_Type variableReadWriteType (
		final A_Type readType,
		final A_Type writeType)
	{
		if (readType.equals(writeType))
		{
			return variableTypeFor(readType);
		}
		return fromReadAndWriteTypes(readType, writeType);
	}

	/**
	 * Construct a new {@code VariableTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private VariableTypeDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.VARIABLE_TYPE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link VariableTypeDescriptor}. */
	private static final VariableTypeDescriptor mutable =
		new VariableTypeDescriptor(Mutability.MUTABLE);

	@Override
	VariableTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link VariableTypeDescriptor}. */
	private static final VariableTypeDescriptor shared =
		new VariableTypeDescriptor(Mutability.SHARED);

	@Override
	VariableTypeDescriptor immutable ()
	{
		// There is only a shared variant, not an immutable one.
		return shared;
	}

	@Override
	VariableTypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * The most general {@linkplain ReadWriteVariableTypeDescriptor variable
	 * type}.
	 */
	private static final A_Type mostGeneralType =
		variableReadWriteType(TOP.o(), bottom()).makeShared();

	/**
	 * Answer the most general {@linkplain ReadWriteVariableTypeDescriptor
	 * variable type}.
	 *
	 * @return The most general {@linkplain ReadWriteVariableTypeDescriptor
	 *         variable type}.
	 */
	public static A_Type mostGeneralVariableType ()
	{
		return mostGeneralType;
	}

	/**
	 * The (instance) type of the most general {@linkplain
	 * ReadWriteVariableTypeDescriptor variable} metatype.
	 */
	private static final A_Type variableMeta =
		instanceMeta(mostGeneralType).makeShared();

	/**
	 * Answer the (instance) type of the most general {@linkplain
	 * ReadWriteVariableTypeDescriptor variable} metatype.
	 *
	 * @return
	 *         The instance type containing the most general {@linkplain
	 *         ReadWriteVariableTypeDescriptor variable} metatype.
	 */
	public static A_Type variableMeta ()
	{
		return variableMeta;
	}
}
