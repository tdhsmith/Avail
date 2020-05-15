/*
 * AtomWithPropertiesDescriptor.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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
package com.avail.descriptor.atoms

import com.avail.annotations.AvailMethod
import com.avail.descriptor.A_Module
import com.avail.descriptor.AvailObject
import com.avail.descriptor.ModuleDescriptor
import com.avail.descriptor.NilDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor
import com.avail.descriptor.representation.*
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.types.TypeTag
import java.util.*

/**
 * An `atom` is an object that has identity by fiat, i.e., it is
 * distinguished from all other objects by the fact of its creation event and
 * the history of what happens to its references.  Not all objects in Avail have
 * that property (hence the acronym Advanced Value And Identity Language),
 * unlike most object-oriented programming languages.
 *
 *
 *
 * At any time an atom can have properties associated with it.  A property is
 * an association between another atom, known as the property key, and the value
 * of that property, any Avail object.  Atoms without properties have a
 * [representation][AtomDescriptor] that does not include a slot for
 * the properties information, but adding a property causes it to transform (via
 * [AvailObject.becomeIndirectionTo] into a `AtomWithPropertiesDescriptor representation` that has a slot which contains
 * a map from property keys to property values.
 *
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see AtomDescriptor
 *
 * @see AtomWithPropertiesSharedDescriptor
 */
open class AtomWithPropertiesDescriptor
	/**
	 * Construct a new `AtomWithPropertiesDescriptor`.
	 *
	 * @param mutability
	 * The [mutability][Mutability] of the new descriptor.
	 * @param typeTag
	 * The [TypeTag] to use in this descriptor.
	 * @param objectSlotsEnumClass
	 * The Java [Class] which is a subclass of [        ] and defines this object's object slots layout, or
	 * null if there are no object slots.
	 * @param integerSlotsEnumClass
	 * The Java [Class] which is a subclass of [        ] and defines this object's integer slots layout,
	 * or null if there are no integer slots.
	 */
	protected constructor(
		mutability: Mutability,
		typeTag: TypeTag,
		objectSlotsEnumClass: Class<out ObjectSlotsEnum>,
		integerSlotsEnumClass: Class<out IntegerSlotsEnum>
	) : AtomDescriptor(
		mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * The low 32 bits are used for the [.HASH_OR_ZERO], but the upper
		 * 32 can be used by other [BitField]s in subclasses.
		 */
		HASH_AND_MORE;

		companion object {
			/**
			 * A slot to hold the hash value, or zero if it has not been computed.
			 * The hash of an atom is a random number, computed once.
			 */
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32)

			init {
				assert(AtomDescriptor.IntegerSlots.HASH_AND_MORE.ordinal
					== HASH_AND_MORE.ordinal)
				assert(AtomDescriptor.IntegerSlots.HASH_OR_ZERO.isSamePlaceAs(
					HASH_OR_ZERO))
			}
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * A string (non-uniquely) roughly identifying this atom.  It need not
		 * be unique among atoms.  Must have the same ordinal as [ ][AtomDescriptor.ObjectSlots.NAME].
		 */
		NAME,

		/**
		 * The [module][ModuleDescriptor] that was active when this
		 * atom was issued.  This information is crucial to [ ].  Must have the same ordinal as
		 * [AtomDescriptor.ObjectSlots.NAME].
		 */
		ISSUING_MODULE,

		/**
		 * A weak map from this atom's property keys (atoms) to property values.
		 */
		PROPERTY_MAP_POJO;

		companion object {
			init {
				assert(AtomDescriptor.ObjectSlots.NAME.ordinal
					== NAME.ordinal)
				assert(AtomDescriptor.ObjectSlots.ISSUING_MODULE.ordinal
					== ISSUING_MODULE.ordinal)
			}
		}
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	): Boolean {
		return (super.allowsImmutableToMutableReferenceInField(e)
			|| e === IntegerSlots.HASH_AND_MORE
			|| e === ObjectSlots.PROPERTY_MAP_POJO)
	}

	override fun o_MakeShared(self: AvailObject): AvailObject {
		assert(!isShared)
		// The layout of the destination descriptor is the same, so nothing
		// special needs to happen, i.e., object doesn't need to become an
		// indirection.
		val propertyMapPojo = self.slot(ObjectSlots.PROPERTY_MAP_POJO)
		propertyMapPojo.makeShared()
		val propertyMap =
			propertyMapPojo.javaObjectNotNull<Map<A_Atom, AvailObject>>()
		for ((key, value) in propertyMap.entries) {
			key.makeShared()
			value.makeShared()
		}
		self.setDescriptor(AtomWithPropertiesSharedDescriptor.shared)
		return self
	}

	/**
	 * {@inheritDoc}
	 *
	 *
	 *
	 * Add or replace a property of this [atom with][AtomDescriptor].
	 *
	 */
	@AvailMethod
	override fun o_SetAtomProperty(
		self: AvailObject,
		key: A_Atom,
		value: A_BasicObject
	) {
		assert(key.isAtom)
		val propertyMapPojo = self.slot(ObjectSlots.PROPERTY_MAP_POJO)
		val map = propertyMapPojo
			.javaObjectNotNull<MutableMap<A_Atom, A_BasicObject>>()
		when {
			value.equalsNil() -> map.remove(key)
			else -> map[key.makeImmutable()] = value.makeImmutable()
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 *
	 *
	 * Extract the property value of this atom at the specified key.  Return
	 * [nil][NilDescriptor.nil] if no such property exists.
	 *
	 */
	@AvailMethod
	override fun o_GetAtomProperty(
		self: AvailObject,
		key: A_Atom): AvailObject {
		assert(key.isAtom)
		val propertyMapPojo: A_BasicObject = self.slot(ObjectSlots.PROPERTY_MAP_POJO)
		val propertyMap = propertyMapPojo.javaObjectNotNull<Map<A_Atom, AvailObject>>()
		val value = propertyMap[key]
		return value ?: NilDescriptor.nil
	}

	override fun mutable(): AtomWithPropertiesDescriptor {
		return mutable
	}

	override fun immutable(): AtomWithPropertiesDescriptor {
		return immutable
	}

	companion object {
		/**
		 * Create a new atom with the given name.  The name is not globally unique,
		 * but serves to help to visually distinguish atoms.  In this class, the
		 * created object already has an empty property map.
		 *
		 * @param name
		 * A string used to help identify the new atom.
		 * @param issuingModule
		 * Which [module][ModuleDescriptor] was active when the
		 * atom was created.
		 * @return
		 * The new atom, not equal to any object in use before this
		 * method was invoked.
		 */
		@JvmStatic
		fun createAtomWithProperties(
			name: A_String?,
			issuingModule: A_Module?): AvailObject {
			val instance = mutable.create()
			instance.setSlot(ObjectSlots.NAME, name!!)
			instance.setSlot(ObjectSlots.ISSUING_MODULE, issuingModule!!)
			instance.setSlot(ObjectSlots.PROPERTY_MAP_POJO,
				RawPojoDescriptor.identityPojo(
					Collections.synchronizedMap(WeakHashMap<A_Atom, A_BasicObject>())))
			instance.setSlot(IntegerSlots.HASH_OR_ZERO, 0)
			return instance.makeShared()
		}

		/**
		 * Create a new atom with the given name, module, and hash value.  The name
		 * is not globally unique, but serves to help to visually distinguish atoms.
		 * The hash value is provided to allow an existing [ ] to be converted to an [ ].  The client can
		 * convert the original simple atom into an [ ] to the new atom with properties.
		 *
		 * @param name
		 * A string used to help identify the new atom.
		 * @param issuingModule
		 * The module that issued this atom.
		 * @param originalHash
		 * The hash value that must be set for this atom, or zero if it
		 * doesn't matter.
		 * @return
		 * The new atom, not equal to any object in use before this
		 * method was invoked.
		 */
		fun createWithNameAndModuleAndHash(
			name: A_String?,
			issuingModule: A_Module?,
			originalHash: Int): AvailObject {
			val instance = mutable.create()
			instance.setSlot(ObjectSlots.NAME, name!!)
			instance.setSlot(ObjectSlots.ISSUING_MODULE, issuingModule!!)
			instance.setSlot(ObjectSlots.PROPERTY_MAP_POJO,
				RawPojoDescriptor.identityPojo(
					Collections.synchronizedMap(WeakHashMap<A_Atom, A_BasicObject>())))
			instance.setSlot(IntegerSlots.HASH_OR_ZERO, originalHash)
			return instance.makeShared()
		}

		/** The mutable [AtomWithPropertiesDescriptor].  */
		private val mutable = AtomWithPropertiesDescriptor(
			Mutability.MUTABLE,
			TypeTag.ATOM_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)

		/** The immutable [AtomWithPropertiesDescriptor].  */
		private val immutable = AtomWithPropertiesDescriptor(
			Mutability.IMMUTABLE,
			TypeTag.ATOM_TAG,
			ObjectSlots::class.java,
			IntegerSlots::class.java)
	}
}