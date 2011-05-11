/**
 * descriptor/InfinityDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.annotations.*;

/**
 * I represent the {@link ExtendedNumberDescriptor extended integers} positive
 * infinity and negative infinity.  By supporting these as first-class values in
 * Avail we eliminate arbitrary limits, awkward duplication of effort,
 * and a host of other dangling singularities.  For example, it makes sense to
 * talk about iterating from 1 to infinity.  Infinities also play a key role in
 * {@linkplain IntegerRangeTypeDescriptor integer range types}, specifically by
 * their appearance as inclusive or exclusive bounds.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class InfinityDescriptor
extends ExtendedNumberDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		/**
		 * A slot to indicate the sign of the infinity.
		 */
		@EnumField(describedBy=Sign.class)
		SIGN
	}

	/**
	 * An enumeration used to distinguish the two signed infinities.
	 */
	public enum Sign
	{
		/**
		 * The value used to indicate the infinity is positive.
		 */
		POSITIVE,

		/**
		 * The value used to indicate the infinity is negative.
		 */
		NEGATIVE
	}

	@Override
	public int o_InfinitySign (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.SIGN);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		if (!object.isPositive())
		{
			aStream.append('-');
		}
		aStream.append('\u221E');
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsInfinity(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compare infinities by their {@link IntegerSlots#SIGN} fields.
	 * </p>
	 */
	@Override
	public boolean o_EqualsInfinity (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInfinity)
	{
		return object.infinitySign() == anInfinity.infinitySign();
	}

	@Override
	public boolean o_GreaterThanInteger (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return object.isPositive();
	}

	@Override
	public boolean o_GreaterThanSignedInfinity (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return object.isPositive() && !another.isPositive();
	}

	@Override
	public boolean o_IsInstanceOfSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		if (aType.equals(VOID_TYPE.o()))
		{
			return true;
		}
		if (aType.equals(ALL.o()))
		{
			return true;
		}
		if (!aType.isIntegerRangeType())
		{
			return false;
		}
		if (object.isPositive())
		{
			return aType.upperBound().equals(object) && aType.upperInclusive();
		}
		return aType.lowerBound().equals(object) && aType.lowerInclusive();
	}

	@Override
	public boolean o_LessThan (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.greaterThanSignedInfinity(object);
	}

	@Override
	public boolean o_TypeEquals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		// Answer whether object's type is equal to aType (known to be a type).
		// Since my implementation of o_CanComputeHashOfType: answers
		// true, I'm not allowed to allocate objects to figure this out.
		if (!aType.isIntegerRangeType())
		{
			return false;
		}
		if (!aType.lowerBound().equals(object))
		{
			return false;
		}
		if (!aType.lowerInclusive())
		{
			return false;
		}
		if (!aType.upperBound().equals(object))
		{
			return false;
		}
		if (!aType.upperInclusive())
		{
			return false;
		}
		//  ...(inclusive).
		return true;
	}

	@Override
	public boolean o_CanComputeHashOfType (
		final @NotNull AvailObject object)
	{
		// Answer whether object supports the #hashOfType protocol.
		return true;
	}

	@Override
	public AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		// Answer the object's type.
		return IntegerRangeTypeDescriptor.singleInteger(object);
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		// Answer the object's hash value.
		return object.isPositive() ? 0x14B326DA : 0xBF9302D;
	}

	@Override
	public int o_HashOfType (
		final @NotNull AvailObject object)
	{
		// Answer my type's hash value (without creating any objects).
		final int objectHash = object.hash();
		return IntegerRangeTypeDescriptor.computeHash(
			objectHash,
			objectHash,
			true,
			true);
	}

	@Override
	public boolean o_IsFinite (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override
	public AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		return ApproximateTypeDescriptor.withInstance(object.makeImmutable());
	}

	@Override
	public AvailObject o_DivideCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return aNumber.divideIntoInfinityCanDestroy(object, canDestroy);
	}

	@Override
	public AvailObject o_MinusCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return aNumber.subtractFromInfinityCanDestroy(object, canDestroy);
	}

	@Override
	public AvailObject o_PlusCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return aNumber.addToInfinityCanDestroy(object, canDestroy);
	}

	@Override
	public AvailObject o_TimesCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aNumber,
		final boolean canDestroy)
	{
		return aNumber.multiplyByInfinityCanDestroy(object, canDestroy);
	}

	@Override
	public boolean o_IsPositive (
		final @NotNull AvailObject object)
	{
		return object.infinitySign() == Sign.POSITIVE.ordinal();
	}

	@Override
	public AvailObject o_AddToInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInfinity,
		final boolean canDestroy)
	{
		if (anInfinity.isPositive() == object.isPositive())
		{
			return object;
		}
		error("Can't add negative and positive infinities", object);
		return VoidDescriptor.voidObject();
	}

	@Override
	public AvailObject o_AddToIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return object;
	}

	@Override
	public AvailObject o_DivideIntoInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInfinity,
		final boolean canDestroy)
	{
		error("Can't divide infinities", object);
		return VoidDescriptor.voidObject();
	}

	@Override
	public AvailObject o_DivideIntoIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return IntegerDescriptor.zero();
	}

	@Override
	public AvailObject o_MultiplyByInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInfinity,
		final boolean canDestroy)
	{
		return anInfinity.isPositive() == object.isPositive()
			? InfinityDescriptor.positiveInfinity()
			: InfinityDescriptor.negativeInfinity();
	}

	@Override
	public AvailObject o_MultiplyByIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		if (anInteger.equals(IntegerDescriptor.zero()))
		{
			error("Can't multiply infinity by zero", object);
			return VoidDescriptor.voidObject();
		}
		return anInteger.greaterThan(IntegerDescriptor.zero())
				^ object.isPositive()
			? InfinityDescriptor.negativeInfinity()
			: InfinityDescriptor.positiveInfinity();
	}

	@Override
	public AvailObject o_SubtractFromInfinityCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInfinity,
		final boolean canDestroy)
	{
		if (anInfinity.isPositive() ^ object.isPositive())
		{
			return anInfinity;
		}
		error("Can't subtract infinity from same signed infinity", object);
		return VoidDescriptor.voidObject();
	}

	@Override
	public AvailObject o_SubtractFromIntegerCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anInteger,
		final boolean canDestroy)
	{
		return object.isPositive()
			? InfinityDescriptor.negativeInfinity()
			: InfinityDescriptor.positiveInfinity();
	}

	/**
	 * The {@link EnumMap} from {@link Sign} to {@linkplain
	 * InfinityDescriptor actual Avail object} representing that infinity.
	 */
	static final EnumMap<Sign, AvailObject> infinities =
		new EnumMap<Sign, AvailObject>(Sign.class);

	/**
	 * Create the positive and negative infinities.
	 */
	static void createWellKnownObjects ()
	{
		final AvailObject positive = mutable().create();
		positive.integerSlotPut(
			IntegerSlots.SIGN,
			Sign.POSITIVE.ordinal());
		infinities.put(Sign.POSITIVE, positive);

		final AvailObject negative = mutable().create();
		negative.integerSlotPut(
			IntegerSlots.SIGN,
			Sign.NEGATIVE.ordinal());
		infinities.put(Sign.NEGATIVE, negative);
	}

	/**
	 * Release the positive and negative infinities.
	 */
	static void clearWellKnownObjects ()
	{
		infinities.put(Sign.POSITIVE, null);
		infinities.put(Sign.NEGATIVE, null);
	}

	/**
	 * Answer the positive infinity object.
	 *
	 * @return Positive infinity.
	 */
	public static AvailObject positiveInfinity ()
	{
		return infinities.get(Sign.POSITIVE);
	}

	/**
	 * Answer the negative infinity object.
	 *
	 * @return Negative infinity.
	 */
	public static AvailObject negativeInfinity ()
	{
		return infinities.get(Sign.NEGATIVE);
	}

	/**
	 * Construct a new {@link InfinityDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected InfinityDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link InfinityDescriptor}.
	 */
	private final static InfinityDescriptor mutable =
		new InfinityDescriptor(true);

	/**
	 * Answer the mutable {@link InfinityDescriptor}.
	 *
	 * @return The mutable {@link InfinityDescriptor}.
	 */
	public static InfinityDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link InfinityDescriptor}.
	 */
	private final static InfinityDescriptor immutable =
		new InfinityDescriptor(false);

	/**
	 * Answer the immutable {@link InfinityDescriptor}.
	 *
	 * @return The immutable {@link InfinityDescriptor}.
	 */
	public static InfinityDescriptor immutable ()
	{
		return immutable;
	}
}
