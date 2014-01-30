/**
 * SubrangeReverseTupleDescriptor.java
 * Copyright © 1993-2014, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.SubrangeReverseTupleDescriptor.IntegerSlots.*;
import static com.avail.descriptor.SubrangeReverseTupleDescriptor.ObjectSlots.*;
import com.avail.annotations.*;

/**
 * TODO: Document SubrangeReverseTupleDescriptor!
 *
 * @author Richard A Arriaga &lt;rich@availlang.org&gt;
 */
public class SubrangeReverseTupleDescriptor extends TupleDescriptor
{

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The hash value of this subrange tuple, or zero.  If the hash value
		 * happens to equal zero it will have to be recomputed each time it is
		 * requested.  Note that the hash function for tuples was chosen in such
		 * a way that the hash value of the concatenation of subtuples is easily
		 * computable from the hashes of the subtuples and their lengths.
		 */
		@HideFieldInDebugger
		HASH_OR_ZERO,

		/**
		 * The first index of the basis tuple that is within this subrange.
		 */
		END_INDEX,

		/**
		 * The number of elements in this subrange tuple, starting at the
		 * {@link #END_INDEX}.  Must not be zero, and should probably be at
		 * least some reasonable size to avoid time and space overhead.
		 */
		SIZE;

		static
		{
			assert TupleDescriptor.IntegerSlots.HASH_OR_ZERO.ordinal()
				== HASH_OR_ZERO.ordinal();
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The basis tuple of which this is a subrange.  The basis tuple must be
		 * flat -- it may not be another subrange tuple, nor may it be a tree
		 * tuple.
		 */
		BASIS_TUPLE;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compare a subrange of this subrange tuple with part of the given tuple.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		if (object.sameAddressAs(anotherObject) && startIndex1 == startIndex2)
		{
			return true;
		}
		if  (!o_CompareFromToWithAnyTupleStartingAt(
			object, startIndex1, endIndex1, anotherObject, startIndex2))
		{
			return false;
		}
		if (startIndex1 == 1
			&& startIndex2 == 1
			&& endIndex1 == object.tupleSize()
			&& endIndex1 == anotherObject.tupleSize())
		{
			if (!isShared())
			{
				anotherObject.makeImmutable();
				object.becomeIndirectionTo(anotherObject);
			}
			else if (!anotherObject.descriptor().isShared())
			{
				object.makeImmutable();
				anotherObject.becomeIndirectionTo(object);
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_String aByteString,
		final int startIndex2)
	{
		return o_CompareFromToWithStartingAt(
			object, startIndex1, endIndex1, aByteString, startIndex2);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithByteTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteTuple,
		final int startIndex2)
	{
		return o_CompareFromToWithStartingAt(
			object, startIndex1, endIndex1, aByteTuple, startIndex2);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithNybbleTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aNybbleTuple,
		final int startIndex2)
	{
		return o_CompareFromToWithStartingAt(
			object, startIndex1, endIndex1, aNybbleTuple, startIndex2);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithObjectTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anObjectTuple,
		final int startIndex2)
	{
		return o_CompareFromToWithStartingAt(
			object, startIndex1, endIndex1, anObjectTuple, startIndex2);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsAnyTuple(object);
	}

	@Override @AvailMethod
	boolean o_EqualsAnyTuple (
		final AvailObject object,
		final A_Tuple anotherTuple)
	{
		if (object.sameAddressAs(anotherTuple))
		{
			return true;
		}
		if (object.tupleSize() != anotherTuple.tupleSize())
		{
			return false;
		}
		if (object.hash() != anotherTuple.hash())
		{
			return false;
		}
		final int startIndex = object.slot(END_INDEX);
		final int size = object.slot(SIZE);
		return object.slot(BASIS_TUPLE).compareFromToWithStartingAt(
			startIndex,
			startIndex + size - 1,
			anotherTuple,
			1);
	}

	/**
	 * Make a tuple that only contains the given range of elements of the given
	 * tuple.  While it would be easy to always produce another subrange tuple,
	 * this isn't a good idea.  Let the specific kind of flat tuple that is our
	 * basis decide what the cutoff size is.
	 */
	@Override @AvailMethod
	A_Tuple o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		assert 1 <= start && start <= end + 1;
		assert 0 <= end && end <= object.slot(SIZE);
		final int newSize = end - start + 1;
		if (newSize == 0)
		{
			return TupleDescriptor.empty();
		}
		final int oldStartIndex = object.slot(END_INDEX);
		if (canDestroy && isMutable() && newSize >= minSize)
		{
			// Modify the bounds in place.
			object.setSlot(END_INDEX, oldStartIndex + start - 1);
			object.setSlot(SIZE, newSize);
			return object;
		}
		final AvailObject basis = object.slot(BASIS_TUPLE);
		if (!canDestroy)
		{
			basis.makeImmutable();
		}
		// Let the basis decide if a subrange or copying is most appropriate.
		return basis.copyTupleFromToCanDestroy(
			start + oldStartIndex - 1,
			end + oldStartIndex - 1,
			canDestroy);
	}

	@Override @AvailMethod
	A_Tuple o_ConcatenateWith (
		final AvailObject object,
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		assert object.tupleSize() > 0;
		if (otherTuple.tupleSize() == 0)
		{
			object.makeImmutable();
			return object;
		}
		if (!canDestroy)
		{
			object.makeImmutable();
			otherTuple.makeImmutable();
		}
		if (otherTuple.treeTupleLevel() == 0)
		{
			// No tree tuples are involved yet.  Create a bottom-level tree
			// tuple on these two level zero tuples (the tuples may be flat or
			// subranges).
			return TreeTupleDescriptor.createPair(
				object,
				otherTuple,
				1,
				0);
		}
		return TreeTupleDescriptor.concatenateAtLeastOneTree(
			object,
			otherTuple,
			true);
	}

	/**
	 * Answer the element at the given index in the tuple object.
	 */
	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		assert 1 <= index && index <= object.slot(SIZE);
		final int adjustedIndex = object.slot(END_INDEX) - index - 1;
		return object.slot(BASIS_TUPLE).tupleAt(adjustedIndex);
	}

	/**
	 * Answer a tuple with all the elements of object except at the given index
	 * we should have newValueObject.  This may destroy the original tuple if
	 * canDestroy is true.
	 *
	 * We want to balance having to clone the entire basis tuple with having to
	 * build a lot of infrastructure to deal with the alteration.  We keep it
	 * fairly simple by creating two subranges spanning the left half and
	 * right half of this subrange.  We then recurse to update the tuple in the
	 * half containing the index.  Eventually the pieces will be small enough
	 * that a subrange won't be produced, and another implementation will be
	 * invoked instead to deal with a small flat tuple.
	 */
	@Override @AvailMethod
	A_Tuple o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		// Ideas:
		// * Sometimes split the range into more manageable-sized pieces, group
		//   them into a tree tuple, then recurse on the interesting part.
		// * Sometimes copy out the interesting flat range and clobber the slot,
		//   answering the new flat tuple.
		// If the range was small enough for the latter it would have already
		// made the small copy during the initial subrange extraction.  Still,
		// if it's mutable it might be worthwhile.  However, this could still be
		// expensive if tupleAtPuttingCanDestroy has to transform the
		// representation into something broader (e.g., nybbles -> objects).
		final int tupleSize = object.slot(SIZE);
		assert index >= 1 && index <= tupleSize;
		final int adjustment = object.slot(END_INDEX) - 1;
		final int adjustedIndex = index + adjustment;
		final AvailObject basis = object.slot(BASIS_TUPLE).traversed();
		if (!canDestroy)
		{
			basis.makeImmutable();
		}
		assert tupleSize >= 3
		: "subrange is too small; recursion won't bottom out correctly";
		// First, split into two parts, approximately evenly.  Use the
		// coordinate system of the basis tuple.
		final int start = 1 + adjustment;
		final int end = tupleSize + adjustment;
		final int splitPoint = start + (tupleSize >>> 1);
		// Freeze the basis, since there may be two references to it below.
		basis.makeImmutable();
		A_Tuple leftPart = basis.copyTupleFromToCanDestroy(
			start, splitPoint - 1, false);
		assert 1 <= leftPart.tupleSize();
		assert leftPart.tupleSize() < tupleSize;
		A_Tuple rightPart = basis.copyTupleFromToCanDestroy(
			splitPoint, end, false);
		assert 1 <= rightPart.tupleSize();
		assert rightPart.tupleSize() < tupleSize;
		assert leftPart.tupleSize() + rightPart.tupleSize() == tupleSize;
		if (adjustedIndex < splitPoint)
		{
			leftPart = leftPart.tupleAtPuttingCanDestroy(
				index, newValueObject, true);
		}
		else
		{
			rightPart = rightPart.tupleAtPuttingCanDestroy(
				index + start - splitPoint, newValueObject, canDestroy);
		}
		return leftPart.concatenateWith(rightPart, true);
	}

	/**
	 * Answer the integer element at the given index in the tuple object.
	 */
	@Override @AvailMethod
	int o_TupleIntAt (final AvailObject object, final int index)
	{
		assert 1 <= index && index <= object.slot(SIZE);
		final int adjustedIndex = index + object.slot(END_INDEX) - 1;
		return object.slot(BASIS_TUPLE).tupleIntAt(adjustedIndex);
	}

	/**
	 * Answer the number of elements in the tuple as an int.
	 */
	@Override @AvailMethod
	int o_TupleSize (final AvailObject object)
	{
		return object.slot(SIZE);
	}

	/**
	 * Answer approximately how many bits per entry are taken up by this object.
	 *
	 * <p>Make this always seem a little better than the worst flat
	 * representation.</p>
	 */
	@Override @AvailMethod
	int o_BitsPerEntry (final AvailObject object)
	{
		return 31;
	}

	/**
	 * Hash part of the tuple object.
	 */
	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		final A_Tuple basis = object.slot(BASIS_TUPLE);
		final int size = object.slot(SIZE);
		assert 1 <= startIndex && startIndex <= size;
		assert startIndex - 1 <= endIndex && endIndex <= size;
		final int adjustment = object.slot(END_INDEX) - 1;
		return basis.computeHashFromTo(
			startIndex + adjustment,
			endIndex + adjustment);
	}

	/**
	 * Answer the minimum number of elements a subrange tuple may have.  Below
	 * this threshold the subrange representation is expected to be
	 * unnecessarily verbose and slow.
	 */
	public static final int minSize = 10;

	/**
	 * Create a {@linkplain SubrangeTupleDescriptor subrange tuple} with the
	 * given basis tuple, start index, and size.  Make the basis tuple immutable
	 * for safety.
	 *
	 * @param basisTuple The basis tuple of this subrange tuple.
	 * @param startIndex The starting index within the basis tuple
	 * @param size The size of this subrange tuple.
	 * @return A fresh subrange tuple.
	 */
	public static AvailObject createSubrange (
		final A_Tuple basisTuple,
		final int startIndex,
		final int size)
	{
		assert size >= minSize;
		assert size < basisTuple.tupleSize();
		basisTuple.makeImmutable();
		final AvailObject instance = mutable.create(size);
		instance.setSlot(BASIS_TUPLE, basisTuple);
		instance.setSlot(END_INDEX, startIndex);
		instance.setSlot(SIZE, size);
		return instance;
	}

	/**
	 * Construct a new {@link SubrangeTupleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private SubrangeReverseTupleDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link SubrangeTupleDescriptor}. */
	public static final SubrangeReverseTupleDescriptor mutable =
		new SubrangeReverseTupleDescriptor(Mutability.MUTABLE);

	@Override
	SubrangeReverseTupleDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link SubrangeTupleDescriptor}. */
	private static final SubrangeReverseTupleDescriptor immutable =
		new SubrangeReverseTupleDescriptor(Mutability.IMMUTABLE);

	@Override
	SubrangeReverseTupleDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link SubrangeTupleDescriptor}. */
	private static final SubrangeReverseTupleDescriptor shared =
		new SubrangeReverseTupleDescriptor(Mutability.SHARED);

	@Override
	SubrangeReverseTupleDescriptor shared ()
	{
		return shared;
	}
}
