/*
 * RuntimeBitField.java
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

import com.avail.annotations.EnumField;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

/**
 * A {@code RuntimeBitField} is constructed at class loading time and contains
 * any cached information needed to access a range of up to 32 contiguous bits
 * from an {@linkplain IntegerSlotsEnum integer slot}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@SuppressWarnings("ComparableImplementedButEqualsNotOverridden")
public final class BitField
implements Comparable<BitField>
{
	/**
	 * The {@link IntegerSlotsEnum integer slot} within which this bit field
	 * occurs.
	 */
	final IntegerSlotsEnum integerSlot;

	/**
	 * The zero-based {@link IntegerSlotsEnum integer slot} within which this
	 * bit field occurs.
	 */
	final int integerSlotIndex;

	/**
	 * The lowest bit position that this BitField occupies.  Zero ({@code 0}) is
	 * the rightmost or lowest order bit.
	 */
	private final int shift;

	/**
	 * The number of bits that this BitField occupies within a {@code long}.
	 */
	final int bits;

	/**
	 * A string of 1's of length {@link #bits}, right aligned in the {@code
	 * long}.  Even though bit fields can't be more than 32 long, it's a long
	 * to avoid sign extension problems.
	 */
	private final long lowMask;

	/**
	 * An integer containing 1-bits in exactly the positions reserved for this
	 * bit field.
	 */
	private final long mask;

	/**
	 * An integer containing 0-bits in exactly the positions reserved for this
	 * bit field.
	 */
	private final long invertedMask;

	/**
	 * The name of this {@code BitField}.  This is filled in as needed by the
	 * default {@linkplain AbstractDescriptor#printObjectOnAvoidingIndent(
	 * AvailObject, StringBuilder, IdentityHashMap, int) object printing}
	 * mechanism.
	 */
	@Nullable String name;

	/**
	 * The {@link EnumField} with which this {@code BitField} is annotated, if
	 * any.  This is populated by the default {@linkplain
	 * AbstractDescriptor#printObjectOnAvoidingIndent(AvailObject,
	 * StringBuilder, IdentityHashMap, int) object printing} mechanism.
	 */
	@Nullable EnumField enumField;

	/**
	 * Construct a new {@code BitField}.
	 *
	 * @param integerSlot
	 *        The {@link IntegerSlotsEnum integer slot} in which this bit field
	 *        occurs.
	 * @param shift
	 *        The bit position of the rightmost (lowest order) bit occupied by
	 *        this {@code BitField}.
	 * @param bits
	 *        The number of bits this {@code BitField} occupies.
	 */
	public BitField (
		final IntegerSlotsEnum integerSlot,
		final int shift,
		final int bits)
	{
		assert shift == (shift & 63);
		assert bits > 0;
		assert bits <= 32;  // Must use at most 32 bits of the long.
		assert shift + bits <= 64;

		this.integerSlot = integerSlot;
		this.shift = shift;
		this.bits = bits;

		integerSlotIndex = integerSlot.ordinal();
		lowMask = ((1L << bits) - 1);
		mask = lowMask << shift;
		invertedMask = ~mask;
	}

	@Override
	public int compareTo (final @Nullable BitField bitField)
	{
		assert bitField != null;
		assert integerSlot == bitField.integerSlot
			: "Bit fields of different slots are incomparable";
		// Order by descending shift values.
		return Integer.compare(bitField.shift, shift);
	}

	/**
	 * Answer whether the receiver and the passed {@code BitField} occupy the
	 * same span of bits at the same integer slot number.
	 *
	 * @param bitField The other {@code BitField}.
	 * @return Whether the two BitFields have the same slot number, start bit,
	 *         and end bit.
	 */
	public boolean isSamePlaceAs (final BitField bitField)
	{
		return integerSlotIndex == bitField.integerSlotIndex
			&& shift == bitField.shift
			&& bits == bitField.bits;
	}

	@Override
	public String toString ()
	{
		return String.format(
			"%s(%d:%d)",
			getClass().getSimpleName(),
			shift,
			bits);
	}

	/**
	 * Extract this {@code BitField} from the given {@code long}.
	 * @param longValue A long.
	 * @return An {@code int} extracted from the range of bits this {@code
	 *         BitField} occupies.
	 */
	int extractFromLong (final long longValue)
	{
		return (int) ((longValue >>> shift) & lowMask);
	}

	/**
	 * Given a {@code long} field value, replace the bits occupied by this
	 * {@code BitField} with the supplied {@code int} value, returning the
	 * resulting {@code long}.
	 * @param longValue A {@code long} into which to replace the bit field.
	 * @param bitFieldValue The {@code int} to be written into the bit field.
	 * @return The {@code long} with the bit field updated.
	 */
	long replaceBits (final long longValue, final int bitFieldValue)
	{
		return (longValue & invertedMask)
			| ((((long) bitFieldValue) << shift) & mask);
	}
}
