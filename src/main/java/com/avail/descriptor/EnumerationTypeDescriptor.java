/*
 * EnumerationTypeDescriptor.java
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
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.AtomDescriptor.falseObject;
import static com.avail.descriptor.AtomDescriptor.trueObject;
import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.EnumerationTypeDescriptor.ObjectSlots.CACHED_SUPERKIND;
import static com.avail.descriptor.EnumerationTypeDescriptor.ObjectSlots.INSTANCES;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.SetDescriptor.emptySet;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;

/**
 * My instances are called <em>enumerations</em>. This descriptor family is
 * used for enumerations with two or more instances (i.e., enumerations for
 * which two or more elements survive canonicalization). For the case of one
 * instance, see {@link InstanceTypeDescriptor}, and for the case of zero
 * instances, see {@link BottomTypeDescriptor}.
 *
 * <p>
 * An enumeration is created from a set of objects that are considered instances
 * of the resulting type.  For example, Avail's {@linkplain #booleanType()
 * boolean type} is simply an enumeration whose instances are {@linkplain
 * AtomDescriptor atoms} representing {@linkplain AtomDescriptor#trueObject()
 * true} and {@linkplain AtomDescriptor#falseObject() false}.  This flexibility
 * allows an enumeration mechanism simply not available in other programming
 * languages. In particular, it allows one to define enumerations whose
 * memberships overlap.  The subtype relationship mimics the subset relationship
 * of the enumerations' membership sets.
 * </p>
 *
 * <p>
 * Because of metacovariance and the useful properties it bestows, enumerations
 * that contain a type as a member (i.e., that type is an instance of the union)
 * also automatically include all subtypes as members.  Thus, an enumeration
 * whose instances are {5, "cheese", {@linkplain
 * TupleTypeDescriptor#mostGeneralTupleType() tuple}} also has the type {@linkplain
 * TupleTypeDescriptor#stringType() string} as a member (string being one
 * of the many subtypes of tuple).  This condition ensures that enumerations
 * satisfy metacovariance, which states that types' types vary the same way as
 * the types: <span style="border-width:thin; border-style:solid; white-space:
 * nowrap">&forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y
 * &rarr; T(x)&sube;T(y))</span>.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class EnumerationTypeDescriptor
extends AbstractEnumerationTypeDescriptor
{
	/** The layout of object slots for my instances. */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The set of {@linkplain AvailObject objects} for which I am the
		 * {@linkplain EnumerationTypeDescriptor enumeration}. If any of the
		 * objects are {@linkplain TypeDescriptor types}, then their subtypes
		 * are also automatically members of this enumeration.
		 */
		INSTANCES,

		/**
		 * Either {@linkplain NilDescriptor#nil nil} or
		 * this enumeration's nearest superkind (i.e., the nearest type that
		 * isn't a union}.
		 */
		CACHED_SUPERKIND
	}

	/**
	 * Extract my set of instances. If any object is itself a type then all of
	 * its subtypes are automatically instances, but they're not returned by
	 * this method. Also, any object that's a type and has a supertype in this
	 * set will have been removed during creation of this enumeration.
	 *
	 * @param object
	 *            The enumeration for which to extract the instances.
	 * @return The instances of this enumeration.
	 */
	static A_Set getInstances (final AvailObject object)
	{
		return object.slot(INSTANCES);
	}

	/**
	 * Answer my nearest superkind (the most specific supertype of me that isn't
	 * also an {@linkplain AbstractEnumerationTypeDescriptor enumeration}). Do
	 * not acquire the argument's monitor.
	 *
	 * @param object
	 *        An enumeration.
	 * @return The kind closest to the given enumeration.
	 */
	private A_Type rawGetSuperkind (final AvailObject object)
	{
		A_Type cached = object.slot(CACHED_SUPERKIND);
		if (cached.equalsNil())
		{
			cached = bottom();
			for (final A_BasicObject instance : getInstances(object))
			{
				cached = cached.typeUnion(instance.kind());
			}
			if (isShared())
			{
				cached = cached.traversed().makeShared();
			}
			object.setSlot(CACHED_SUPERKIND, cached);
		}
		return cached;
	}

	/**
	 * Answer my nearest superkind (the most specific supertype of me that isn't
	 * also an {@linkplain AbstractEnumerationTypeDescriptor enumeration}).
	 *
	 * @param object
	 *        An enumeration.
	 * @return The kind closest to the given enumeration.
	 */
	private A_Type getSuperkind (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return rawGetSuperkind(object);
			}
		}
		return rawGetSuperkind(object);
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == CACHED_SUPERKIND;
	}

	@Override @AvailMethod
	A_Type o_ComputeSuperkind (final AvailObject object)
	{
		return getSuperkind(object);
	}

	@Override @AvailMethod
	A_Number o_InstanceCount (final AvailObject object)
	{
		return fromInt(getInstances(object).setSize());
	}

	@Override @AvailMethod
	A_Set o_Instances (final AvailObject object)
	{
		return getInstances(object);
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		// Print boolean specially.
		if (object.equals(booleanType()))
		{
			aStream.append("boolean");
			return;
		}
		// Default printing.
		getInstances(object).printOnAvoidingIndent(
			aStream,
			recursionMap,
			indent + 1);
		aStream.append("ᵀ");
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * An instance type is only equal to another instance type, and only when
	 * they refer to equal instances.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		final boolean equal =
			another.equalsEnumerationWithSet(getInstances(object));
		if (equal)
		{
			if (!isShared())
			{
				another.makeImmutable();
				object.becomeIndirectionTo(another);
			}
			else if (!another.descriptor().isShared())
			{
				object.makeImmutable();
				another.becomeIndirectionTo(object);
			}
		}
		return equal;
	}

	@Override @AvailMethod
	boolean o_EqualsEnumerationWithSet (
		final AvailObject object,
		final A_Set aSet)
	{
		return getInstances(object).equals(aSet);
	}

	/**
	 * The potentialInstance is a {@linkplain ObjectDescriptor user-defined
	 * object}. See if it is an instance of the object. It is an instance
	 * precisely when it is in object's set of {@linkplain ObjectSlots#INSTANCES
	 * instances}, or if it is a subtype of any type that occurs in the set of
	 * instances.
	 */
	@Override @AvailMethod
	boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return getInstances(object).hasElement(potentialInstance);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return (getInstances(object).hash() ^ 0x15b5b059) * multiplier;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOf (final AvailObject object, final A_Type aType)
	{
		if (aType.isInstanceMeta())
		{
			// I'm an enumeration of non-types, and aType is an instance meta
			// (the only sort of metas that exist these days -- 2012.07.17).
			// See if my instances comply with aType's instance (a type).
			final AvailObject aTypeInstance = aType.instance();
			final A_Set instanceSet = getInstances(object);
			assert instanceSet.isSet();
			if (aTypeInstance.isEnumeration())
			{
				// Check the complete membership.
				for (final AvailObject member : instanceSet)
				{
					if (!aTypeInstance.enumerationIncludesInstance(member))
					{
						return false;
					}
				}
				return true;
			}
			return instanceSet.setElementsAreAllInstancesOfKind(aTypeInstance);
		}
		// I'm an enumeration of non-types, so I could only be an instance of a
		// meta (already excluded), or of ANY or TOP.
		return aType.isSupertypeOfPrimitiveTypeEnum(ANY);
	}

	/**
	 * Compute the type intersection of the object, which is an {@linkplain
	 * EnumerationTypeDescriptor enumeration}, and the argument, which may or
	 * may not be an enumeration (but must be a {@linkplain TypeDescriptor
	 * type}).
	 *
	 * @param object
	 *        An enumeration.
	 * @param another
	 *        Another type.
	 * @return The most general type that is a subtype of both {@code object}
	 *         and {@code another}.
	 */
	@Override
	A_Type computeIntersectionWith (
		final AvailObject object,
		final A_Type another)
	{
		assert another.isType();
		A_Set set = emptySet();
		final A_Set elements = getInstances(object);
		if (another.isEnumeration())
		{
			// Create a new enumeration containing all non-type elements that
			// are simultaneously present in object and another, plus the type
			// intersections of all pairs of types in the product of the sets.
			// This should even correctly deal with bottom as an element.
			final A_Set otherElements = another.instances();
			A_Set myTypes = emptySet();
			for (final AvailObject element : elements)
			{
				if (element.isType())
				{
					myTypes = myTypes.setWithElementCanDestroy(element, true);
				}
				else if (otherElements.hasElement(element))
				{
					set = set.setWithElementCanDestroy(element, true);
				}
			}
			// We have the non-types now, so add the pair-wise intersection of
			// the types.
			if (myTypes.setSize() > 0)
			{
				for (final AvailObject anotherElement : otherElements)
				{
					if (anotherElement.isType())
					{
						for (final A_Type myType : myTypes)
						{
							set = set.setWithElementCanDestroy(
								anotherElement.typeIntersection(myType),
								true);
						}
					}
				}
			}
		}
		else
		{
			// Keep the instances that comply with another, which is not a union
			// type.
			for (final AvailObject element : getInstances(object))
			{
				if (element.isInstanceOfKind(another))
				{
					set = set.setWithElementCanDestroy(element, true);
				}
			}
		}
		if (set.setSize() == 0)
		{
			// Decide whether this should be bottom or bottom's type
			// based on whether object and another are both metas.  Note that
			// object is a meta precisely when one of its instances is a type.
			// One more thing:  The special case of another being bottom should
			// not be treated as being a meta for our purposes, even though
			// bottom technically is a meta.
			if (object.isSubtypeOf(topMeta())
				&& another.isSubtypeOf(topMeta())
				&& !another.isBottom())
			{
				return instanceMeta(bottom());
			}
		}
		return enumerationWith(set);
	}

	/**
	 * Compute the type union of the object, which is an {@linkplain
	 * EnumerationTypeDescriptor enumeration}, and the argument, which may or
	 * may not be an enumeration (but must be a {@linkplain TypeDescriptor
	 * type}).
	 *
	 * @param object
	 *            An enumeration.
	 * @param another
	 *            Another type.
	 * @return The most general type that is a subtype of both {@code object}
	 *         and {@code another}.
	 */
	@Override
	A_Type computeUnionWith (
		final AvailObject object,
		final A_Type another)
	{
		if (another.isEnumeration())
		{
			// Create a new enumeration containing all elements from both
			// enumerations.
			return
				enumerationWith(getInstances(object).setUnionCanDestroy(
					another.instances(),
					false));
		}
		// Go up to my nearest kind, then compute the union with the given kind.
		A_Type union = another;
		for (final A_BasicObject instance : getInstances(object))
		{
			union = union.typeUnion(instance.kind());
		}
		return union;
	}

	@Override @AvailMethod
	A_Tuple o_FieldTypeTuple (final AvailObject object)
	{
		return getSuperkind(object).fieldTypeTuple();
	}

	@Override @AvailMethod
	A_Map o_FieldTypeMap (final AvailObject object)
	{
		return getSuperkind(object).fieldTypeMap();
	}

	@Override @AvailMethod
	A_Number o_LowerBound (final AvailObject object)
	{
		return getSuperkind(object).lowerBound();
	}

	@Override @AvailMethod
	boolean o_LowerInclusive (final AvailObject object)
	{
		return getSuperkind(object).lowerInclusive();
	}

	@Override @AvailMethod
	A_Number o_UpperBound (final AvailObject object)
	{
		return getSuperkind(object).upperBound();
	}

	@Override @AvailMethod
	boolean o_UpperInclusive (final AvailObject object)
	{
		return getSuperkind(object).upperInclusive();
	}

	@Override @AvailMethod
	boolean o_EnumerationIncludesInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return getInstances(object).hasElement(potentialInstance);
	}

	@Override @AvailMethod
	A_Type o_TypeAtIndex (
		final AvailObject object,
		final int index)
	{
		// This is only intended for a TupleType stand-in. Answer what type the
		// given index would have in an object instance of me. Answer
		// bottom if the index is out of bounds.
		assert object.isTupleType();
		return getSuperkind(object).typeAtIndex(index);
	}

	@Override @AvailMethod
	A_Type o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		// Answer the union of the types that object's instances could have in
		// the given range of indices. Out-of-range indices are treated as
		// bottom, which don't affect the union (unless all indices are out
		// of range).
		assert object.isTupleType();
		return getSuperkind(object).unionOfTypesAtThrough(startIndex, endIndex);
	}

	@Override @AvailMethod
	A_Type o_DefaultType (final AvailObject object)
	{
		assert object.isTupleType();
		return getSuperkind(object).defaultType();
	}

	@Override @AvailMethod
	A_Type o_SizeRange (final AvailObject object)
	{
		return getSuperkind(object).sizeRange();
	}

	@Override @AvailMethod
	A_Tuple o_TypeTuple (final AvailObject object)
	{
		return getSuperkind(object).typeTuple();
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		// Check if object (an enumeration) is a subtype of aType (should also
		// be a type).  All members of me must also be instances of aType.
		for (final A_BasicObject instance : getInstances(object))
		{
			if (!instance.isInstanceOf(aType))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsIntegerRangeType (final AvailObject object)
	{
		for (final A_BasicObject instance : getInstances(object))
		{
			if (!instance.isExtendedInteger())
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsLiteralTokenType (final AvailObject object)
	{
		for (final AvailObject instance : getInstances(object))
		{
			if (!instance.isLiteralToken())
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsMapType (final AvailObject object)
	{
		for (final A_BasicObject instance : getInstances(object))
		{
			if (!instance.isMap())
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSetType (final AvailObject object)
	{
		for (final A_BasicObject instance : getInstances(object))
		{
			if (!instance.isSet())
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsTupleType (final AvailObject object)
	{
		for (final A_BasicObject instance : getInstances(object))
		{
			if (!instance.isTuple())
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_AcceptsArgTypesFromFunctionType (
		final AvailObject object,
		final A_Type functionType)
	{
		return getSuperkind(object).acceptsArgTypesFromFunctionType(
			functionType);
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		return getSuperkind(object).acceptsListOfArgTypes(argTypes);
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<? extends A_BasicObject> argValues)
	{
		return getSuperkind(object).acceptsListOfArgValues(argValues);
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final A_Tuple argTypes)
	{
		return getSuperkind(object).acceptsTupleOfArgTypes(argTypes);
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final A_Tuple arguments)
	{
		return getSuperkind(object).acceptsTupleOfArguments(arguments);
	}

	@Override @AvailMethod
	A_Type o_ArgsTupleType (final AvailObject object)
	{
		return getSuperkind(object).argsTupleType();
	}

	@Override @AvailMethod
	A_Set o_DeclaredExceptions (final AvailObject object)
	{
		return getSuperkind(object).declaredExceptions();
	}

	@Override @AvailMethod
	A_Type o_FunctionType (final AvailObject object)
	{
		return getSuperkind(object).functionType();
	}

	@Override @AvailMethod
	A_Type o_ContentType (final AvailObject object)
	{
		return getSuperkind(object).contentType();
	}

	@Override @AvailMethod
	boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<? extends TypeRestriction> argRestrictions)
	{
		return getSuperkind(object).couldEverBeInvokedWith(argRestrictions);
	}

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final A_BasicObject anotherObject)
	{
		// An enumeration with a cached superkind is pretty good.
		return !object.mutableSlot(CACHED_SUPERKIND).equalsNil();
	}

	@Override @AvailMethod
	A_Type o_KeyType (final AvailObject object)
	{
		return getSuperkind(object).keyType();
	}

	@Override @AvailMethod
	A_BasicObject o_Parent (final AvailObject object)
	{
		return getSuperkind(object).parent();
	}

	@Override @AvailMethod
	A_Type o_ReturnType (final AvailObject object)
	{
		return getSuperkind(object).returnType();
	}

	@Override @AvailMethod
	A_Type o_ValueType (final AvailObject object)
	{
		return getSuperkind(object).valueType();
	}

	@Override
	@Nullable Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		if (object.isSubtypeOf(booleanType()))
		{
			return Boolean.TYPE;
		}
		return super.o_MarshalToJava(object, ignoredClassHint);
	}

	@Override
	A_Type o_ReadType (final AvailObject object)
	{
		return getSuperkind(object).readType();
	}

	@Override
	A_Type o_WriteType (final AvailObject object)
	{
		return getSuperkind(object).writeType();
	}

	@Override
	A_Type o_ExpressionType (final AvailObject object)
	{
		A_Type unionType = bottom();
		for (final A_Phrase instance : getInstances(object))
		{
			unionType = unionType.typeUnion(instance.expressionType());
		}
		return unionType;
	}

	@Override
	boolean o_RangeIncludesInt (final AvailObject object, final int anInt)
	{
		return getInstances(object).hasElement(fromInt(anInt));
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.ENUMERATION_TYPE;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		getSuperkind(object).writeTo(writer);
		writer.write("instances");
		object.slot(INSTANCES).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		getSuperkind(object).writeSummaryTo(writer);
		writer.write("instances");
		object.slot(INSTANCES).writeSummaryTo(writer);
		writer.endObject();
	}

	@Override
	TypeTag o_ComputeTypeTag (final AvailObject object)
	{
		final Set<TypeTag> tags = EnumSet.noneOf(TypeTag.class);
		for (final AvailObject instance : getInstances(object))
		{
			tags.add(instance.typeTag());
		}
		if (tags.size() == 1)
		{
			return tags.iterator().next();
		}
		final Iterator<TypeTag> iterator = tags.iterator();
		TypeTag ancestor = iterator.next();
		while (iterator.hasNext())
		{
			ancestor = ancestor.commonAncestorWith(iterator.next());
		}
		return ancestor;
	}

	/**
	 * Construct an enumeration type from a {@linkplain SetDescriptor set} with
	 * at least two instances. The set must have already been normalized, such
	 * that at most one of the elements is itself a {@linkplain TypeDescriptor
	 * type}.
	 *
	 * @param normalizedSet The set of instances.
	 * @return The resulting enumeration.
	 */
	static A_Type fromNormalizedSet (final A_Set normalizedSet)
	{
		assert normalizedSet.setSize() > 1;
		final AvailObject result = mutable.create();
		result.setSlot(INSTANCES, normalizedSet.makeImmutable());
		result.setSlot(CACHED_SUPERKIND, nil);
		return result;
	}

	/**
	 * Construct a new {@code EnumerationTypeDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private EnumerationTypeDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.UNKNOWN_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link EnumerationTypeDescriptor}. */
	private static final AbstractEnumerationTypeDescriptor mutable =
		new EnumerationTypeDescriptor(Mutability.MUTABLE);

	@Override
	AbstractEnumerationTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link EnumerationTypeDescriptor}. */
	private static final AbstractEnumerationTypeDescriptor immutable =
		new EnumerationTypeDescriptor(Mutability.IMMUTABLE);

	@Override
	AbstractEnumerationTypeDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link EnumerationTypeDescriptor}. */
	private static final AbstractEnumerationTypeDescriptor shared =
		new EnumerationTypeDescriptor(Mutability.SHARED);

	@Override
	AbstractEnumerationTypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Avail's boolean type, the equivalent of Java's primitive {@code boolean}
	 * pseudo-type, similar to Java's boxed {@link Boolean} class.
	 */
	private static final A_Type booleanObject;

	/**
	 * The type whose only instance is the value {@link
	 * AtomDescriptor#trueObject() true}.
	 */
	private static final A_Type trueType;

	/**
	 * The type whose only instance is the value {@link
	 * AtomDescriptor#falseObject() false}.
	 */
	private static final A_Type falseType;

	static
	{
		final A_Set set = set(trueObject(), falseObject());
		booleanObject = enumerationWith(set).makeShared();
		trueType = instanceTypeOrMetaOn(trueObject()).makeShared();
		falseType = instanceTypeOrMetaOn(falseObject()).makeShared();
	}

	/**
	 * Return Avail's boolean type.
	 *
	 * @return The enumeration {@link A_Type} that acts as Avail's boolean type.
	 */
	public static A_Type booleanType ()
	{
		return booleanObject;
	}

	/**
	 * Return the type for which {@link AtomDescriptor#trueObject() true} is the
	 * only instance.
	 *
	 * @return true's type.
	 */
	public static A_Type trueType ()
	{
		return trueType;
	}

	/**
	 * Return the type for which {@link AtomDescriptor#falseObject() false} is
	 * the only instance.
	 *
	 * @return false's type.
	 */
	public static A_Type falseType ()
	{
		return falseType;
	}
}
