/*
 * NumberedChoice.java
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
package com.avail.compiler.splitter;
import com.avail.compiler.splitter.InstructionGenerator.Label;
import com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.exceptions.SignatureException;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.avail.compiler.ParsingOperation.PUSH_LITERAL;
import static com.avail.compiler.ParsingOperation.TYPE_CHECK_ARGUMENT;
import static com.avail.compiler.splitter.MessageSplitter.throwSignatureException;
import static com.avail.compiler.splitter.WrapState.SHOULD_NOT_HAVE_ARGUMENTS;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.IntegerDescriptor.fromInt;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.ListPhraseTypeDescriptor.emptyListPhraseType;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_NUMBERED_CHOICE;
import static com.avail.utility.Nulls.stripNull;

/**
 * A {@code NumberedChoice} is a special subgroup (i.e., not a root group)
 * indicated by an {@linkplain Metacharacter#EXCLAMATION_MARK exclamation mark}
 * following a {@linkplain Group group}.  It must not contain {@linkplain
 * Argument arguments} or subgroups and it must not contain a {@linkplain
 * Metacharacter#DOUBLE_DAGGER double dagger}.  The group contains an {@link
 * Alternation}, and parsing the group causes exactly one of the alternatives to
 * be parsed.  The 1-based index of the alternative is produced as a literal
 * constant argument.
 *
 * <p>
 * For example, consider parsing a send of the message
 * "my«cheese|bacon|Elvis»!" from the string "my bacon cheese".  The bacon
 * token will be parsed, causing this to be an invocation of the message
 * with the single argument 2 (indicating the second choice).  The cheese
 * token is not considered part of this message send (and will lead to a
 * failed parse if some method like "_cheese" is not present.
 * </p>
 */
final class NumberedChoice
extends Expression
{
	/**
	 * The alternation expression, exactly one alternative of which must be
	 * chosen.
	 */
	private final Alternation alternation;

	/**
	 * Construct a new {@code NumberedChoice}.
	 *
	 * @param alternation
	 *        The enclosed {@link Alternation}.
	 */
	NumberedChoice (final Alternation alternation)
	{
		super(alternation.positionInName);
		this.alternation = alternation;
	}

	@Override
	boolean isArgumentOrGroup ()
	{
		return true;
	}

	@Override
	int underscoreCount ()
	{
		assert alternation.underscoreCount() == 0;
		return 0;
	}

	@Override
	boolean isLowerCase ()
	{
		return alternation.isLowerCase();
	}

	@Override
	void extractSectionCheckpointsInto (
		final List<SectionCheckpoint> sectionCheckpoints)
	{
		alternation.extractSectionCheckpointsInto(sectionCheckpoints);
	}

	@Override
	void checkType (
		final A_Type argumentType,
		final int sectionNumber)
	throws SignatureException
	{
		// The declared type of the subexpression must be a subtype of
		// [1..N] where N is the number of alternatives.
		if (!argumentType.isSubtypeOf(
			inclusive(1, alternation.alternatives().size())))
		{
			throwSignatureException(E_INCORRECT_TYPE_FOR_NUMBERED_CHOICE);
		}
	}

	@Override
	WrapState emitOn (
		final A_Type phraseType,
		final InstructionGenerator generator,
		final WrapState wrapState)
	{
		/* branch to @target1.
		 * ...do first alternative.
		 * push literal 1.
		 * jump to @done.
		 * @target1:
		 *
		 * branch to @target2.
		 * ...do second alternative.
		 * push literal 2.
		 * jump to @done.
		 * @target2:
		 * ...
		 * @targetN-2nd:
		 *
		 * branch to @targetN-1st.
		 * ...do N-1st alternative.
		 * push literal N-1.
		 * jump to @done.
		 * @targetN-1st:
		 *
		 * ;;;no branch
		 * ...do Nth alternative.
		 * push literal N.
		 * ;;;no jump
		 * @done:
		 * ...
		 */
		generator.flushDelayed();
		final int numAlternatives = alternation.alternatives().size() - 1;
		final Label $exit = new Label();
		for (int index = 0; index <= numAlternatives; index++)
		{
			final Label $next = new Label();
			final boolean last = index == numAlternatives;
			if (!last)
			{
				generator.emitBranchForward(this, $next);
			}
			final Expression alternative =
				alternation.alternatives().get(index);
			// If a section checkpoint occurs within a numbered choice, we
			// *do not* pass the choice number as an argument.  Therefore
			// nothing new has been pushed for us to clean up at this point.
			alternative.emitOn(
				emptyListPhraseType(),
				generator,
				SHOULD_NOT_HAVE_ARGUMENTS);
			generator.emit(
				this, PUSH_LITERAL, MessageSplitter.indexForConstant(
					fromInt(index + 1)));
			if (!last)
			{
				generator.emitJumpForward(this, $exit);
				generator.emit($next);
			}
		}
		generator.emit($exit);
		generator.emitDelayed(
			this,
			TYPE_CHECK_ARGUMENT,
			MessageSplitter.indexForConstant(phraseType));
		return wrapState.processAfterPushedArgument(this, generator);
	}

	@Override
	public String toString ()
	{
		return getClass().getSimpleName() + '(' + alternation + ')';
	}

	@Override
	public void printWithArguments (
		final @Nullable Iterator<? extends A_Phrase> argumentProvider,
		final StringBuilder builder,
		final int indent)
	{
		final A_Phrase literal = stripNull(argumentProvider).next();
		assert literal.isInstanceOf(
			PhraseKind.LITERAL_PHRASE.mostGeneralType());
		final int index = literal.token().literal().extractInt();
		builder.append('«');
		final Expression alternative =
			alternation.alternatives().get(index - 1);
		alternative.printWithArguments(
			Collections.emptyIterator(), builder, indent);
		builder.append("»!");
	}

	@Override
	boolean shouldBeSeparatedOnLeft ()
	{
		// Starts with a guillemet, so don't bother with a leading space.
		return false;
	}

	@Override
	boolean shouldBeSeparatedOnRight ()
	{
		// Don't bother with a space after the close guillemet and
		// exclamation mark.
		return false;
	}

	@Override
	boolean mightBeEmpty (
		final A_Type phraseType)
	{
		return alternation.mightBeEmpty(bottom());
	}
}
