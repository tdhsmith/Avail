/**
 * MarkerNodeDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.MarkerNodeDescriptor.ObjectSlots.MARKER_VALUE;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.MARKER_NODE;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;

/**
 * My instances represent a parsing marker that can be pushed onto the parse
 * stack.  It should never occur as part of a composite {@linkplain
 * ParseNodeDescriptor parse node}, and is not capable of emitting code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class MarkerNodeDescriptor
extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain MarkerNodeDescriptor marker} being wrapped in a form
		 * suitable for the parse stack.
		 */
		MARKER_VALUE
	}

	@Override @AvailMethod
	AvailObject o_MarkerValue (final AvailObject object)
	{
		return object.slot(MARKER_VALUE);
	}

	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		// This shouldn't make a difference.
		return TOP.o();
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append("Marker(");
		builder.append(object.markerValue());
		builder.append(")");
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		assert false : "A marker node can not generate code.";
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.markerValue().hash() ^ 0xCBCACACC;
	}

	@Override @AvailMethod
	boolean o_EqualsParseNode (
		final AvailObject object,
		final A_Phrase aParseNode)
	{
		return !aParseNode.isMacroSubstitutionNode()
			&& object.parseNodeKind().equals(aParseNode.parseNodeKind())
			&& object.markerValue().equals(aParseNode.markerValue());
	}

	@Override
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		return MARKER_NODE;
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> aBlock)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		throw unsupportedOperationException();
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		// There's currently no reason to serialize a marker phrase.  This may
		// change at some point.
		throw unsupportedOperationException();
	}

	@Override
	void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		throw unsupportedOperationException();
	}

	/**
	 * Create a {@linkplain MarkerNodeDescriptor marker node} wrapping the given
	 * {@link A_BasicObject}.
	 *
	 * @param markerValue The value to wrap.
	 * @return A new immutable marker node.
	 */
	public static AvailObject newMarkerNode (final A_BasicObject markerValue)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(MARKER_VALUE, markerValue);
		instance.makeShared();
		return instance;
	}

	/**
	 * Construct a new {@link MarkerNodeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private MarkerNodeDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.MARKER_PHRASE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link MarkerNodeDescriptor}. */
	private static final MarkerNodeDescriptor mutable =
		new MarkerNodeDescriptor(Mutability.MUTABLE);

	@Override
	MarkerNodeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link MarkerNodeDescriptor}. */
	private static final MarkerNodeDescriptor shared =
		new MarkerNodeDescriptor(Mutability.SHARED);

	@Override
	MarkerNodeDescriptor shared ()
	{
		return shared;
	}
}
