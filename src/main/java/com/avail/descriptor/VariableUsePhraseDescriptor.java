/*
 * VariableUseNodeDescriptor.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * modification, are permitted provided that the following conditions are met:
 * Redistribution and use in source and binary forms, with or without
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
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.DECLARATION_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.VARIABLE_USE_PHRASE;
import static com.avail.descriptor.TypeDescriptor.Types.TOKEN;
import static com.avail.descriptor.VariableUsePhraseDescriptor.IntegerSlots.FLAGS;
import static com.avail.descriptor.VariableUsePhraseDescriptor.IntegerSlots.LAST_USE;
import static com.avail.descriptor.VariableUsePhraseDescriptor.ObjectSlots.DECLARATION;
import static com.avail.descriptor.VariableUsePhraseDescriptor.ObjectSlots.USE_TOKEN;

/**
 * My instances represent the use of some {@linkplain DeclarationPhraseDescriptor
 * declared entity}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class VariableUsePhraseDescriptor
extends PhraseDescriptor
{
	/**
	 * My slots of type {@linkplain Integer int}.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * A flag indicating (with 0/1) whether this is the last use of the
		 * mentioned entity.
		 */
		FLAGS;

		/**
		 * Whether this is the last use of the mentioned entity.
		 */
		static final BitField LAST_USE = bitField(
			FLAGS, 0, 1);
	}

	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain TokenDescriptor token} that is a mention of the
		 * entity in question.
		 */
		USE_TOKEN,

		/**
		 * The {@linkplain DeclarationPhraseDescriptor declaration} of the entity
		 * that is being mentioned.
		 */
		DECLARATION

		}
	@Override
	boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return e == FLAGS;
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		action.value(object.slot(DECLARATION));
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		object.setSlot(
			DECLARATION, transformer.valueNotNull(object.slot(DECLARATION)));
	}

	@Override @AvailMethod
	A_Phrase o_Declaration (final AvailObject object)
	{
		return object.slot(DECLARATION);
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Phrase declaration = object.slot(DECLARATION);
		declaration.declarationKind().emitVariableValueForOn(
			object.tokens(), declaration, codeGenerator);
	}

	@Override @AvailMethod
	boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return !aPhrase.isMacroSubstitutionNode()
			&& object.phraseKind().equals(aPhrase.phraseKind())
			&& object.slot(USE_TOKEN).equals(aPhrase.token())
			&& object.slot(DECLARATION).equals(aPhrase.declaration())
			&& object.isLastUse() == aPhrase.isLastUse();
	}

	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		return object.slot(DECLARATION).declaredType();
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			(object.slot(USE_TOKEN).hash()) * multiplier
				+ object.slot(DECLARATION).hash()
				^ 0x62CE7BA2;
	}

	@Override @AvailMethod
	void o_IsLastUse (
		final AvailObject object,
		final boolean isLastUse)
	{
		if (isShared())
		{
			synchronized (object)
			{
				object.setSlot(LAST_USE, isLastUse ? 1 : 0);
			}
		}
		else
		{
			object.setSlot(LAST_USE, isLastUse ? 1 : 0);
		}
	}

	@Override @AvailMethod
	boolean o_IsLastUse (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return object.slot(LAST_USE) != 0;
			}
		}
		return object.slot(LAST_USE) != 0;
	}

	@Override
	PhraseKind o_PhraseKind (final AvailObject object)
	{
		return VARIABLE_USE_PHRASE;
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.VARIABLE_USE_PHRASE;
	}

	@Override
	void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Token o_Token (final AvailObject object)
	{
		return object.slot(USE_TOKEN);
	}

	@Override
	A_Tuple o_Tokens (final AvailObject object)
	{
		return tuple(object.slot(USE_TOKEN));
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable use phrase");
		writer.write("token");
		object.slot(USE_TOKEN).writeTo(writer);
		writer.write("declaration");
		object.slot(DECLARATION).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("variable use phrase");
		writer.write("token");
		object.slot(USE_TOKEN).writeSummaryTo(writer);
		writer.write("declaration");
		object.slot(DECLARATION).writeSummaryTo(writer);
		writer.endObject();
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		final A_Token useToken = object.slot(USE_TOKEN);
		builder.append(useToken.string().asNativeString());
	}

	/**
	 * Construct a new variable use phrase.
	 *
	 * @param theToken The token which is the use of the variable in the source.
	 * @param declaration The declaration which is being used.
	 * @return A new variable use phrase.
	 */
	public static AvailObject newUse (
		final A_Token theToken,
		final A_Phrase declaration)
	{
		assert theToken.isInstanceOfKind(TOKEN.o());
		assert declaration.isInstanceOfKind(DECLARATION_PHRASE.mostGeneralType());
		final AvailObject newUse = mutable.create();
		newUse.setSlot(USE_TOKEN, theToken);
		newUse.setSlot(DECLARATION, declaration);
		newUse.setSlot(FLAGS, 0);
		newUse.makeShared();
		return newUse;
	}

	/**
	 * Construct a new {@code VariableUsePhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private VariableUsePhraseDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.VARIABLE_USE_PHRASE_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link VariableUsePhraseDescriptor}. */
	private static final VariableUsePhraseDescriptor mutable =
		new VariableUsePhraseDescriptor(Mutability.MUTABLE);

	@Override
	VariableUsePhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link VariableUsePhraseDescriptor}. */
	private static final VariableUsePhraseDescriptor shared =
		new VariableUsePhraseDescriptor(Mutability.IMMUTABLE);

	@Override
	VariableUsePhraseDescriptor shared ()
	{
		return shared;
	}
}
