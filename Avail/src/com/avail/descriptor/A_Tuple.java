/**
 * A_Tuple.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;

/**
 * {@code A_Tuple} is an interface that specifies the tuple-specific operations
 * that an {@link AvailObject} must implement.  It's a sub-interface of {@link
 * A_BasicObject}, the interface that defines the behavior that all AvailObjects
 * are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Tuple
extends A_BasicObject, Iterable<AvailObject>
{
	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain TupleDescriptor tuple}. The size of the
	 * subrange of both objects is determined by the index range supplied for
	 * the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aTuple
	 *        The tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithAnyTupleStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple aTuple,
		int startIndex2);

	/**
	 * @param i
	 * @param tupleSize
	 * @param aByteArrayTuple
	 * @param j
	 * @return
	 */
	boolean compareFromToWithByteArrayTupleStartingAt (
		int i,
		int tupleSize,
		A_Tuple aByteArrayTuple,
		int j);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain ByteStringDescriptor byte string}. The
	 * size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aByteString
	 *        The byte string used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the byte string's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithByteStringStartingAt (
		int startIndex1,
		int endIndex1,
		AvailObject aByteString,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain ByteTupleDescriptor byte tuple}. The
	 * size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aByteTuple
	 *        The byte tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the byte tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithByteTupleStartingAt (
		int startIndex1,
		int endIndex1,
		AvailObject aByteTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain NybbleTupleDescriptor nybble tuple}.
	 * The size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aNybbleTuple
	 *        The nybble tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the nybble tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithNybbleTupleStartingAt (
		int startIndex1,
		int endIndex1,
		AvailObject aNybbleTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain ObjectTupleDescriptor object tuple}.
	 * The size of the subrange of both objects is determined by the index range
	 * supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param anObjectTuple
	 *        The object tuple used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the object tuple's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithObjectTupleStartingAt (
		int startIndex1,
		int endIndex1,
		AvailObject anObjectTuple,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of another object. The size of the subrange of both objects is
	 * determined by the index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param anotherObject
	 *        The other object used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the other object's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithStartingAt (
		int startIndex1,
		int endIndex1,
		A_Tuple anotherObject,
		int startIndex2);

	/**
	 * Compare a subrange of the {@linkplain AvailObject receiver} with a
	 * subrange of the given {@linkplain TwoByteStringDescriptor two-byte
	 * string}. The size of the subrange of both objects is determined by the
	 * index range supplied for the receiver.
	 *
	 * @param startIndex1
	 *        The inclusive lower bound of the receiver's subrange.
	 * @param endIndex1
	 *        The inclusive upper bound of the receiver's subrange.
	 * @param aTwoByteString
	 *        The two-byte string used in the comparison.
	 * @param startIndex2
	 *        The inclusive lower bound of the two-byte string's subrange.
	 * @return {@code true} if the contents of the subranges match exactly,
	 *         {@code false} otherwise.
	 */
	boolean compareFromToWithTwoByteStringStartingAt (
		int startIndex1,
		int endIndex1,
		AvailObject aTwoByteString,
		int startIndex2);

	/**
	 * Dispatch to the descriptor.
	 */
	int computeHashFromTo (int start, int end);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple concatenateTuplesCanDestroy (boolean canDestroy);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple copyAsMutableObjectTuple ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple copyAsMutableSpliceTuple ();

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple copyTupleFromToCanDestroy (
		int start,
		int end,
		boolean canDestroy);

	/**
	 * Given two objects that are known to be equal, is the first one in a
	 * better form (more compact, more efficient, older generation) than the
	 * second one?
	 *
	 * @param anotherObject
	 * @return
	 */
	boolean isBetterRepresentationThan (
		A_BasicObject anotherObject);

	/**
	 * Dispatch to the descriptor.
	 */
	byte extractNybbleFromTupleAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	int hashFromTo (int startIndex, int endIndex);

	/**
	 * Dispatch to the descriptor.
	 */
	AvailObject tupleAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	void tupleAtPut (int index, AvailObject anObject);

	/**
	 * @param index
	 * @param anObject
	 */
	void objectTupleAtPut (int index, A_BasicObject anObject);

	/**
	 * Dispatch to the descriptor.
	 */
	A_Tuple tupleAtPuttingCanDestroy (
		int index,
		A_BasicObject newValueObject,
		boolean canDestroy);

	/**
	 * Answer the specified element of the tuple.  It must be an {@linkplain
	 * IntegerDescriptor integer} in the range [-2^31..2^31), and is returned as
	 * a Java {@code int}.
	 *
	 * @param index Which 1-based index to use to subscript the tuple.
	 * @return The {@code int} form of the specified tuple element.
	 */
	int tupleIntAt (int index);

	/**
	 * Answer the number of elements in this tuple.
	 *
	 * @return The maximum valid 1-based index for this tuple.
	 */
	int tupleSize ();

	/**
	 * Construct a Java {@linkplain Set set} from the receiver, a {@linkplain
	 * TupleDescriptor tuple}.
	 *
	 * @return A set containing each element in the tuple.
	 */
	A_Set asSet ();

	/**
	 * Answer whether this is a {@linkplain SpliceTupleDescriptor splice tuple}.
	 * @return Whether this is a splice tuple.
	 */
	boolean isSplice ();

	/**
	 * Answer an {@linkplain Iterator iterator} suitable for traversing the
	 * elements of the {@linkplain AvailObject receiver} with a Java
	 * <em>foreach</em> construct.
	 *
	 * @return An {@linkplain Iterator iterator}.
	 */
	@Override
	Iterator<AvailObject> iterator ();

	/**
	 * @param newElement
	 * @param canDestroy
	 * @return
	 */
	A_Tuple appendCanDestroy (
		A_BasicObject newElement,
		boolean canDestroy);

	/**
	 * Only valid for {@linkplain SpliceTupleDescriptor splice tuples}.  Extract
	 * the tuple which the zone with the specified index is based on.
	 *
	 * @param zone Which zone of the splice tuple is of interest.
	 * @return The tuple on which that zone is based.
	 */
	A_BasicObject subtupleForZone (int zone);


	/**
	 * @return
	 */
	public ByteBuffer byteBuffer ();
}