/*
 * TupleTypeDescriptor.java
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
import com.avail.annotations.ThreadSafe;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.UnaryOperator;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.*;
import static com.avail.descriptor.ObjectTupleDescriptor.*;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleTypeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.TypeDescriptor.Types.CHARACTER;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * A tuple type can be the {@linkplain AvailObject#kind() type} of a {@linkplain
 * TupleDescriptor tuple}, or something more general. It has a canonical form
 * consisting of three pieces of information:
 *
 * <ul>
 * <li>the {@linkplain ObjectSlots#SIZE_RANGE size range}, an {@linkplain
 * IntegerRangeTypeDescriptor integer range type} that a conforming tuple's
 * size must be an instance of,</li>
 * <li>a {@linkplain TupleDescriptor tuple} of {@linkplain TypeDescriptor types}
 * corresponding with the initial elements of the tuple, and</li>
 * <li>a {@linkplain ObjectSlots#DEFAULT_TYPE default type} for all elements
 * beyond the tuple of types to conform to.</li>
 * </ul>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class TupleTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * An {@linkplain IntegerRangeTypeDescriptor integer range type} that
		 * contains all allowed {@linkplain
		 * TupleDescriptor#o_TupleSize(AvailObject) tuple sizes} for instances
		 * of this type.
		 */
		SIZE_RANGE,

		/**
		 * The types of the leading elements of tuples that conform to this
		 * type. This is reduced at construction time to the minimum size of
		 * tuple that covers the same range. For example, if the last element
		 * of this tuple equals the {@link #DEFAULT_TYPE} then this tuple will
		 * be shortened by one.
		 */
		TYPE_TUPLE,

		/**
		 * The type for all subsequent elements of the tuples that conform to
		 * this type.
		 */
		DEFAULT_TYPE
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		if (object.slot(TYPE_TUPLE).tupleSize() == 0)
		{
			if (object.slot(SIZE_RANGE).equals(wholeNumbers()))
			{
				if (object.slot(DEFAULT_TYPE).equals(ANY.o()))
				{
					aStream.append("tuple");
					return;
				}
				if (object.slot(DEFAULT_TYPE).equals(CHARACTER.o()))
				{
					aStream.append("string");
					return;
				}
				//  Okay, it's homogeneous and of arbitrary size...
				aStream.append('<');
				object.defaultType().printOnAvoidingIndent(
					aStream,
					recursionMap,
					(indent + 1));
				aStream.append("…|>");
				return;
			}
		}
		aStream.append('<');
		final int end = object.slot(TYPE_TUPLE).tupleSize();
		for (int i = 1; i <= end; i++)
		{
			object.typeAtIndex(i).printOnAvoidingIndent(
				aStream,
				recursionMap,
				indent + 1);
			aStream.append(", ");
		}
		object.slot(DEFAULT_TYPE).printOnAvoidingIndent(
			aStream,
			recursionMap,
			indent + 1);
		aStream.append("…|");
		final A_Type sizeRange = object.slot(SIZE_RANGE);
		sizeRange.lowerBound().printOnAvoidingIndent(
			aStream,
			recursionMap,
			indent + 1);
		if (!sizeRange.lowerBound().equals(sizeRange.upperBound()))
		{
			aStream.append("..");
			sizeRange.upperBound().printOnAvoidingIndent(
				aStream,
				recursionMap,
				indent + 1);
		}
		aStream.append('>');
	}

	@Override @AvailMethod
	A_Type o_DefaultType (final AvailObject object)
	{
		return object.slot(DEFAULT_TYPE);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsTupleType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Tuple types are equal if and only if their sizeRange, typeTuple, and
	 * defaultType match.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_EqualsTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return object.sameAddressAs(aTupleType)
			|| (object.slot(SIZE_RANGE).equals(aTupleType.sizeRange())
				&& object.slot(DEFAULT_TYPE).equals(aTupleType.defaultType())
				&& object.slot(TYPE_TUPLE).equals(aTupleType.typeTuple()));
	}

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final A_BasicObject anotherObject)
	{
		return object.representationCostOfTupleType()
			< anotherObject.representationCostOfTupleType();
	}

	@Override @AvailMethod
	int o_RepresentationCostOfTupleType (
		final AvailObject object)
	{
		return 1;
	}

	@Override @AvailMethod
	int o_Hash (
		final AvailObject object)
	{
		return hashOfTupleTypeWithSizesHashTypesHashDefaultTypeHash(
			object.slot(SIZE_RANGE).hash(),
			object.slot(TYPE_TUPLE).hash(),
			object.slot(DEFAULT_TYPE).hash());
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Answer the union of the types that object's instances could have in the
	 * given range of indices.  Out-of-range indices are treated as bottom,
	 * which don't affect the union (unless all indices are out of range).
	 * </p>
	 */
	@Override @AvailMethod
	A_Type o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		if (startIndex > endIndex)
		{
			return bottom();
		}
		if (startIndex == endIndex)
		{
			return object.typeAtIndex(startIndex);
		}
		final A_Number upper = object.sizeRange().upperBound();
		if (fromInt(startIndex).greaterThan(upper))
		{
			return bottom();
		}
		final A_Tuple leading = object.typeTuple();
		final int interestingLimit = leading.tupleSize() + 1;
		final int clipStart = max(min(startIndex, interestingLimit), 1);
		final int clipEnd = max(min(endIndex, interestingLimit), 1);
		A_Type typeUnion = object.typeAtIndex(clipStart);
		for (int i = clipStart + 1; i <= clipEnd; i++)
		{
			typeUnion = typeUnion.typeUnion(object.typeAtIndex(i));
		}
		return typeUnion;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final AvailObject object,
		final A_Type aType)
	{
		return aType.isSupertypeOfTupleType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Tuple type A is a supertype of tuple type B iff all the <em>possible
	 * instances</em> of B would also be instances of A.  Types that are
	 * indistinguishable under this condition are considered the same type.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		if (object.equals(aTupleType))
		{
			return true;
		}
		if (!aTupleType.sizeRange().isSubtypeOf(object.slot(SIZE_RANGE)))
		{
			return false;
		}
		final A_Tuple subTuple = aTupleType.typeTuple();
		final A_Tuple superTuple = object.slot(TYPE_TUPLE);
		int end = max(subTuple.tupleSize(), superTuple.tupleSize()) + 1;
		final A_Number smallUpper = aTupleType.sizeRange().upperBound();
		if (smallUpper.isInt())
		{
			end = min(end, smallUpper.extractInt());
		}
		for (int i = 1; i <= end; i++)
		{
			final A_Type subType;
			if (i <= subTuple.tupleSize())
			{
				subType = subTuple.tupleAt(i);
			}
			else
			{
				subType = aTupleType.defaultType();
			}
			final A_Type superType;
			if (i <= superTuple.tupleSize())
			{
				superType = superTuple.tupleAt(i);
			}
			else
			{
				superType = object.slot(DEFAULT_TYPE);
			}
			if (!subType.isSubtypeOf(superType))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsTupleType (final AvailObject object)
	{
		// I am a tupleType, so answer true.
		return true;
	}

	@Override
	boolean o_IsVacuousType (final AvailObject object)
	{
		final A_Number minSizeObject = object.sizeRange().lowerBound();
		if (!minSizeObject.isInt())
		{
			return false;
		}
		// Only check the element types up to the minimum size.  It's ok to stop
		// after the variations (i.e., just after the leading typeTuple).
		final int minSize = min(
			minSizeObject.extractInt(),
			object.slot(TYPE_TUPLE).tupleSize() + 1);
		for (int i = 1; i <= minSize; i++)
		{
			if (object.typeAtIndex(i).isVacuousType())
			{
				return true;
			}
		}
		return false;
	}

	@Override
	@Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		if (object.isSubtypeOf(stringType()))
		{
			return String.class;
		}
		return super.o_MarshalToJava(object, ignoredClassHint);
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.TUPLE_TYPE;
	}

	@Override @AvailMethod
	A_Type o_SizeRange (final AvailObject object)
	{
		return object.slot(SIZE_RANGE);
	}

	@Override @AvailMethod
	A_Tuple o_TupleOfTypesFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		// Answer the tuple of types over the given range of indices.  Any
		// indices out of range for this tuple type will be ⊥.
		assert startIndex >= 1;
		final int size = endIndex - startIndex + 1;
		assert size >= 0;
		return generateObjectTupleFrom(
			size,
			i -> i <= size
				? object.typeAtIndex(i + startIndex - 1).makeImmutable()
				: bottom());
	}

	@Override @AvailMethod
	A_Type o_TypeAtIndex (
		final AvailObject object,
		final int index)
	{
		// Answer what type the given index would have in an object instance of
		// me.  Answer bottom if the index is out of bounds.
		if (index <= 0)
		{
			return bottom();
		}
		final A_Number upper = object.slot(SIZE_RANGE).upperBound();
		if (upper.isInt())
		{
			if (upper.extractInt() < index)
			{
				return bottom();
			}
		}
		else if (upper.lessThan(fromInt(index)))
		{
			return bottom();
		}
		final A_Tuple leading = object.slot(TYPE_TUPLE);
		if (index <= leading.tupleSize())
		{
			return leading.tupleAt(index);
		}
		return object.slot(DEFAULT_TYPE);
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
		return another.typeIntersectionOfTupleType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		A_Type newSizesObject =
			object.slot(SIZE_RANGE).typeIntersection(aTupleType.sizeRange());
		final A_Tuple lead1 = object.slot(TYPE_TUPLE);
		final A_Tuple lead2 = aTupleType.typeTuple();
		A_Tuple newLeading;
		if (lead1.tupleSize() > lead2.tupleSize())
		{
			newLeading = lead1;
		}
		else
		{
			newLeading = lead2;
		}
		newLeading.makeImmutable();
		//  Ensure first write attempt will force copying.
		final int newLeadingSize = newLeading.tupleSize();
		for (int i = 1; i <= newLeadingSize; i++)
		{
			final A_Type intersectionObject =
				object.typeAtIndex(i).typeIntersection(
					aTupleType.typeAtIndex(i));
			if (intersectionObject.isBottom())
			{
				return bottom();
			}
			newLeading = newLeading.tupleAtPuttingCanDestroy(
				i,
				intersectionObject,
				true);
		}
		// Make sure entries in newLeading are immutable, as
		// typeIntersection(...)can answer one of its arguments.
		newLeading.makeSubobjectsImmutable();
		final A_Type newDefault =
			object.typeAtIndex(newLeadingSize + 1).typeIntersection(
				aTupleType.typeAtIndex(newLeadingSize + 1));
		if (newDefault.isBottom())
		{
			final A_Number newLeadingSizeObject = fromInt(newLeadingSize);
			if (newLeadingSizeObject.lessThan(newSizesObject.lowerBound()))
			{
				return bottom();
			}
			if (newLeadingSizeObject.lessThan(newSizesObject.upperBound()))
			{
				newSizesObject = integerRangeType(
					newSizesObject.lowerBound(),
					newSizesObject.lowerInclusive(),
					newLeadingSizeObject,
					true);
			}
		}
		//  safety until all primitives are destructive
		return tupleTypeForSizesTypesDefaultType(
			newSizesObject, newLeading, newDefault.makeImmutable());
	}

	@Override @AvailMethod
	A_Tuple o_TypeTuple (final AvailObject object)
	{
		return object.slot(TYPE_TUPLE);
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
		return another.typeUnionOfTupleType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		final A_Type newSizesObject =
			object.slot(SIZE_RANGE).typeUnion(aTupleType.sizeRange());
		final A_Tuple lead1 = object.slot(TYPE_TUPLE);
		final A_Tuple lead2 = aTupleType.typeTuple();
		A_Tuple newLeading;
		if (lead1.tupleSize() > lead2.tupleSize())
		{
			newLeading = lead1;
		}
		else
		{
			newLeading = lead2;
		}
		newLeading.makeImmutable();
		//  Ensure first write attempt will force copying.
		final int newLeadingSize = newLeading.tupleSize();
		for (int i = 1; i <= newLeadingSize; i++)
		{
			final A_Type unionObject =
				object.typeAtIndex(i).typeUnion(aTupleType.typeAtIndex(i));
			newLeading = newLeading.tupleAtPuttingCanDestroy(
				i, unionObject, true);
		}
		// Make sure entries in newLeading are immutable, as typeUnion(...) can
		// answer one of its arguments.
		newLeading.makeSubobjectsImmutable();
		final A_Type newDefault =
			object.typeAtIndex(newLeadingSize + 1).typeUnion(
				aTupleType.typeAtIndex(newLeadingSize + 1));
		// Safety until all primitives are destructive
		newDefault.makeImmutable();
		return tupleTypeForSizesTypesDefaultType(
			newSizesObject, newLeading, newDefault);
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("tuple type");
		writer.write("leading types");
		object.slot(TYPE_TUPLE).writeTo(writer);
		writer.write("default type");
		object.slot(DEFAULT_TYPE).writeTo(writer);
		writer.write("cardinality");
		object.slot(SIZE_RANGE).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("tuple type");
		writer.write("leading types");
		object.slot(TYPE_TUPLE).writeSummaryTo(writer);
		writer.write("default type");
		object.slot(DEFAULT_TYPE).writeSummaryTo(writer);
		writer.write("cardinality");
		object.slot(SIZE_RANGE).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create the tuple type specified by the arguments. The size range
	 * indicates the allowable tuple sizes for conforming tuples, the type tuple
	 * indicates the types of leading elements (if a conforming tuple is long
	 * enough to include those indices), and the default type is the type of the
	 * remaining elements. Canonize the tuple type to the simplest
	 * representation that includes exactly those tuples that a naive tuple type
	 * constructed from these parameters would include. In particular, if no
	 * tuples are possible then answer bottom, otherwise remove any final
	 * occurrences of the default from the end of the type tuple, trimming it to
	 * no more than the maximum size in the range.
	 *
	 * @param sizeRange The allowed sizes of conforming tuples.
	 * @param typeTuple The types of the initial elements of conforming tuples.
	 * @param defaultType The type of remaining elements of conforming tuples.
	 * @return A canonized tuple type with the specified properties.
	 */
	@SuppressWarnings("TailRecursion")
	public static A_Type tupleTypeForSizesTypesDefaultType (
		final A_Type sizeRange,
		final A_Tuple typeTuple,
		final A_Type defaultType)
	{
		if (sizeRange.isBottom())
		{
			return bottom();
		}
		assert sizeRange.lowerBound().isFinite();
		assert sizeRange.upperBound().isFinite() || !sizeRange.upperInclusive();
		if (sizeRange.upperBound().equalsInt(0)
				&& sizeRange.lowerBound().equalsInt(0))
		{
			return privateTupleTypeForSizesTypesDefaultType(
				sizeRange,
				emptyTuple(),
				bottom());
		}
		final int typeTupleSize = typeTuple.tupleSize();
		if (fromInt(typeTupleSize).greaterOrEqual(sizeRange.upperBound()))
		{
			// The (nonempty) tuple hits the end of the range – disregard the
			// passed defaultType and use the final element of the tuple as the
			// defaultType, while removing it from the tuple.  Recurse for
			// further reductions.
			final int upper = sizeRange.upperBound().extractInt();
			return tupleTypeForSizesTypesDefaultType(
				sizeRange,
				typeTuple.copyTupleFromToCanDestroy(
					1,
					upper - 1,
					false),
				typeTuple.tupleAt(upper).makeImmutable());
		}
		if (typeTupleSize > 0
				&& typeTuple.tupleAt(typeTupleSize).equals(defaultType))
		{
			//  See how many other redundant entries we can drop.
			int index = typeTupleSize - 1;
			while (index > 0 && typeTuple.tupleAt(index).equals(defaultType))
			{
				index--;
			}
			return tupleTypeForSizesTypesDefaultType(
				sizeRange,
				typeTuple.copyTupleFromToCanDestroy(1, index, false),
				defaultType);
		}
		return privateTupleTypeForSizesTypesDefaultType(
			sizeRange, typeTuple, defaultType);
	}

	/**
	 * Answer a tuple type consisting of either zero or one occurrences of the
	 * given element type.
	 *
	 * @param aType A {@linkplain TypeDescriptor type}.
	 * @return A size [0..1] tuple type whose element has the given type.
	 *
	 */
	public static A_Type zeroOrOneOf (final A_Type aType)
	{
		return tupleTypeForSizesTypesDefaultType(
			zeroOrOne(),
			emptyTuple(),
			aType);
	}

	/**
	 * Answer a tuple type consisting of zero or more of the given element type.
	 *
	 * @param aType A {@linkplain TypeDescriptor type}.
	 * @return A size [0..∞) tuple type whose elements have the given type.
	 *
	 */
	public static A_Type zeroOrMoreOf (final A_Type aType)
	{
		return tupleTypeForSizesTypesDefaultType(
			wholeNumbers(),
			emptyTuple(),
			aType);
	}

	/**
	 * Answer a tuple type consisting of one or more of the given element type.
	 *
	 * @param aType A {@linkplain TypeDescriptor type}.
	 * @return A size [1..∞) tuple type whose elements have the given type.
	 *
	 */
	public static A_Type oneOrMoreOf (final A_Type aType)
	{
		return tupleTypeForSizesTypesDefaultType(
			naturalNumbers(),
			emptyTuple(),
			aType);
	}

	/**
	 * Answer a fixed size tuple type consisting of the given element types.
	 *
	 * @param types
	 *        A variable number of types corresponding to the elements of the
	 *        resulting tuple type.
	 * @return A fixed-size tuple type.
	 *
	 */
	@ReferencedInGeneratedCode
	public static A_Type tupleTypeForTypes (final A_Type... types)
	{
		return tupleTypeForSizesTypesDefaultType(
			singleInt(types.length),
			tupleFromArray(types),
			bottom());
	}

	/**
	 * Answer a fixed size tuple type consisting of the given element types.
	 *
	 * @param types
	 *        A {@link List} of {@link A_Type}s corresponding to the elements of
	 *        the resulting fixed-size tuple type.
	 * @return A fixed-size tuple type.
	 *
	 */
	@ReferencedInGeneratedCode
	public static A_Type tupleTypeForTypes (final List<? extends A_Type> types)
	{
		return tupleTypeForSizesTypesDefaultType(
			singleInt(types.size()),
			tupleFromList(types),
			bottom());
	}

	/**
	 * Transform a tuple type into another tuple type by {@linkplain
	 * Transformer1 transforming} each of the element types.  Assume the
	 * transformation is stable.  The resulting tuple type should have the same
	 * size range as the input tuple type, except if normalization produces
	 * {@linkplain BottomTypeDescriptor#bottom() bottom}.
	 *
	 * @param aTupleType
	 *        A tuple type whose element types should be transformed.
	 * @param elementTransformer
	 *        A transformation to perform on each element type, to produce the
	 *        corresponding element types of the resulting tuple type.
	 * @return A tuple type resulting from applying the transformation to each
	 *         element type of the supplied tuple type.
	 */
	public static A_Type tupleTypeFromTupleOfTypes (
		final A_Type aTupleType,
		final UnaryOperator<A_Type> elementTransformer)
	{
		final A_Type sizeRange = aTupleType.sizeRange();
		final A_Tuple typeTuple = aTupleType.typeTuple();
		final A_Type defaultType = aTupleType.defaultType();
		final int limit = typeTuple.tupleSize();
		final A_Tuple transformedTypeTuple = generateObjectTupleFrom(
			limit, index -> elementTransformer.apply(typeTuple.tupleAt(index)));
		final A_Type transformedDefaultType =
			elementTransformer.apply(defaultType);
		return tupleTypeForSizesTypesDefaultType(
			sizeRange, transformedTypeTuple, transformedDefaultType);
	}

	/**
	 * Create a tuple type with the specified parameters.  These must already
	 * have been canonized by the caller.
	 *
	 * @param sizeRange The allowed sizes of conforming tuples.
	 * @param typeTuple The types of the initial elements of conforming tuples.
	 * @param defaultType The types of remaining elements of conforming tuples.
	 * @return A tuple type with the specified properties.
	 */
	private static A_Type privateTupleTypeForSizesTypesDefaultType (
		final A_Type sizeRange,
		final A_Tuple typeTuple,
		final A_Type defaultType)
	{
		assert sizeRange.lowerBound().isFinite();
		assert sizeRange.upperBound().isFinite() || !sizeRange.upperInclusive();
		assert sizeRange.lowerBound().extractInt() >= 0;

		final A_Type sizeRangeKind = sizeRange.isEnumeration()
			? sizeRange.computeSuperkind()
			: sizeRange;
		final int limit = min(
			sizeRangeKind.lowerBound().extractInt(),
			typeTuple.tupleSize());
		for (int i = 1; i <= limit; i++)
		{
			assert typeTuple.tupleAt(i).isType();
		}
		final AvailObject result = mutable.create();
		result.setSlot(SIZE_RANGE, sizeRangeKind);
		result.setSlot(TYPE_TUPLE, typeTuple);
		result.setSlot(DEFAULT_TYPE, defaultType);
		return result;
	}

	/**
	 * Answer the hash of the tuple type whose canonized parameters have the
	 * specified hash values.
	 *
	 * @param sizesHash
	 *            The hash of the {@linkplain IntegerRangeTypeDescriptor integer
	 *            range type} that is the size range for some {@linkplain
	 *            TupleTypeDescriptor tuple type} being hashed.
	 * @param typeTupleHash
	 *            The hash of the tuple of types of the leading arguments of
	 *            tuples that conform to some {@code TupleTypeDescriptor
	 *            tuple type} being hashed.
	 * @param defaultTypeHash
	 *            The hash of the type that remaining elements of conforming
	 *            types must have.
	 * @return
	 *            The hash of the {@code TupleTypeDescriptor tuple type}
	 *            whose component hash values were provided.
	 */
	private static int hashOfTupleTypeWithSizesHashTypesHashDefaultTypeHash (
		final int sizesHash,
		final int typeTupleHash,
		final int defaultTypeHash)
	{
		return sizesHash * 13 + defaultTypeHash * 11 + typeTupleHash * 7;
	}

	/**
	 * Construct a new {@code TupleTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private TupleTypeDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.TUPLE_TYPE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@code TupleTypeDescriptor}. */
	private static final TupleTypeDescriptor mutable =
		new TupleTypeDescriptor(Mutability.MUTABLE);

	@Override
	TupleTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@code TupleTypeDescriptor}. */
	private static final TupleTypeDescriptor immutable =
		new TupleTypeDescriptor(Mutability.IMMUTABLE);

	@Override
	TupleTypeDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@code TupleTypeDescriptor}. */
	private static final TupleTypeDescriptor shared =
		new TupleTypeDescriptor(Mutability.SHARED);

	@Override
	TupleTypeDescriptor shared ()
	{
		return shared;
	}

	/** The most general tuple type. */
	private static final A_Type mostGeneralType =
		zeroOrMoreOf(ANY.o()).makeShared();

	/**
	 * Answer the most general tuple type.  This is the supertype of all other
	 * tuple types.
	 *
	 * @return The most general tuple type.
	 */
	public static A_Type mostGeneralTupleType ()
	{
		return mostGeneralType;
	}

	/** The most general string type (i.e., tuples of characters). */
	private static final A_Type stringTupleType =
		zeroOrMoreOf(CHARACTER.o()).makeShared();

	/**
	 * Answer the most general string type.  This type subsumes strings of any
	 * size.
	 *
	 * @return The string type.
	 */
	public static A_Type stringType ()
	{
		return stringTupleType;
	}

	/** The metatype for all tuple types. */
	private static final A_Type meta =
		instanceMeta(mostGeneralType).makeShared();

	/**
	 * Answer the metatype for all tuple types.
	 *
	 * @return The statically referenced metatype.
	 */
	public static A_Type tupleMeta ()
	{
		return meta;
	}
}
