/**
 * DefinitionDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.DefinitionDescriptor.ObjectSlots.*;
import com.avail.annotations.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Transformer1;

/**
 * {@code DefinitionDescriptor} is an abstraction for things placed into a
 * {@linkplain MethodDescriptor method}.  They can be:
 * <ul>
 * <li>{@linkplain AbstractDefinitionDescriptor abstract declarations},</li>
 * <li>{@linkplain ForwardDefinitionDescriptor forward declarations},</li>
 * <li>{@linkplain MethodDefinitionDescriptor method definitions}, or</li>
 * <li>{@linkplain MacroDefinitionDescriptor macro definitions}.</li>
 * </ul>
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 */
public abstract class DefinitionDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@link MethodDescriptor method} in which this is a definition.
		 */
		DEFINITION_METHOD,

		/**
		 * The {@link ModuleDescriptor module} in which this definition occurs.
		 */
		MODULE
	}

	@Override @AvailMethod
	abstract A_Type o_BodySignature (final AvailObject object);

	@Override @AvailMethod
	public A_Method o_DefinitionMethod (final AvailObject object)
	{
		return object.slot(DEFINITION_METHOD);
	}

	@Override @AvailMethod
	public A_Module o_DefinitionModule (final AvailObject object)
	{
		return object.slot(MODULE);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	abstract int o_Hash (final AvailObject object);

	@Override @AvailMethod
	boolean o_IsAbstractDefinition (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsForwardDefinition (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsMethodDefinition (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsMacroDefinition (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	abstract A_Type o_Kind (final AvailObject object);

	@Override @AvailMethod
	A_Type o_ParsingSignature (final AvailObject object)
	{
		// Non-macro definitions have a signature derived from the
		// bodySignature.
		final Transformer1<A_Type, A_Type> transformer =
			new Transformer1<A_Type, A_Type>()
			{
				@Override
				public A_Type value (@Nullable final A_Type argumentType)
				{
					assert argumentType != null;
					return ParseNodeKind.PARSE_NODE.create(argumentType);
				}
			};
		return TupleTypeDescriptor.mappingElementTypes(
			object.bodySignature().argsTupleType(), transformer);
	}

	@Override @AvailMethod
	abstract SerializerOperation o_SerializerOperation (
		final AvailObject object);

	/**
	 * Construct a new {@link MapBinDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	protected DefinitionDescriptor (
		final Mutability mutability,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, objectSlotsEnumClass, integerSlotsEnumClass);
	}
}
