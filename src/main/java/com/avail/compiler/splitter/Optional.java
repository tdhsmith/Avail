/*
 * Optional.java
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
import com.avail.compiler.ParsingOperation;
import com.avail.compiler.splitter.InstructionGenerator.Label;
import com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.EnumerationTypeDescriptor;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.exceptions.SignatureException;
import com.avail.utility.evaluation.Continuation0;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.avail.compiler.ParsingOperation.*;
import static com.avail.compiler.splitter.MessageSplitter.throwSignatureException;
import static com.avail.compiler.splitter.WrapState.SHOULD_NOT_HAVE_ARGUMENTS;
import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.ListPhraseTypeDescriptor.emptyListPhraseType;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_TYPE_FOR_BOOLEAN_GROUP;
import static com.avail.utility.Nulls.stripNull;

/**
 * An {@code Optional} is a {@link Sequence} wrapped in guillemets («»), and
 * followed by a question mark (?).  It may not contain {@link Argument}s or
 * subgroups, and since it is not a group it may not contain a {@linkplain
 * Metacharacter#DOUBLE_DAGGER double dagger} (‡).
 *
 * <p>At a call site, an optional produces a {@linkplain
 * EnumerationTypeDescriptor#booleanType() boolean} that indicates whether
 * there was an occurrence of the group.  For example, the message
 * "«very»?good" accepts a single argument: a boolean that is {@linkplain
 * AtomDescriptor#trueObject() true} if the token "very" occurred and
 * {@linkplain AtomDescriptor#falseObject() false} if it did not.</p>
 */
final class Optional
extends Expression
{
	/** The optional {@link Sequence}. */
	private final Sequence sequence;

	/**
	 * Construct a new {@code Optional}.
	 *
	 * @param positionInName
	 *        The position of the group or token in the message name.
	 * @param sequence
	 *        The governed {@linkplain Sequence sequence}.
	 */
	Optional (final int positionInName, final Sequence sequence)
	{
		super(positionInName);
		this.sequence = sequence;
		if (sequence.canBeReordered())
		{
			explicitOrdinal(sequence.explicitOrdinal());
			sequence.explicitOrdinal(-1);
		}
	}

	@Override
	boolean isArgumentOrGroup ()
	{
		return true;
	}

	@Override
	int underscoreCount ()
	{
		assert sequence.underscoreCount() == 0;
		return 0;
	}

	@Override
	boolean isLowerCase ()
	{
		return sequence.isLowerCase();
	}

	@Override
	void extractSectionCheckpointsInto (
		final List<SectionCheckpoint> sectionCheckpoints)
	{
		sequence.extractSectionCheckpointsInto(sectionCheckpoints);
	}

	@Override
	void checkType (
		final A_Type argumentType,
		final int sectionNumber)
	throws SignatureException
	{
		// The declared type of the subexpression must be a subtype of boolean.
		if (!argumentType.isSubtypeOf(booleanType()))
		{
			throwSignatureException(E_INCORRECT_TYPE_FOR_BOOLEAN_GROUP);
		}
	}

	@Override
	WrapState emitOn (
		final A_Type phraseType,
		final InstructionGenerator generator,
		final WrapState wrapState)
	{
		/* branch to @absent
		 * push the current parse position on the mark stack
		 * ...the sequence's expressions...
		 * check progress and update saved position or abort.
		 * discard the saved parse position from the mark stack.
		 * push literal true
		 * jump to @groupSkip
		 * @absent:
		 * push literal false
		 * @groupSkip:
		 */
		generator.flushDelayed();
		final boolean needsProgressCheck =
			sequence.mightBeEmpty(emptyListPhraseType());
		final Label $absent = new Label();
		generator.emitBranchForward(this, $absent);
		generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION);
		assert sequence.argumentsAreReordered != Boolean.TRUE;
		sequence.emitOn(
			emptyListPhraseType(), generator, SHOULD_NOT_HAVE_ARGUMENTS);
		generator.flushDelayed();
		generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS);
		generator.emitIf(
			needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION);
		generator.emit(this, PUSH_LITERAL, MessageSplitter.indexForTrue());
		final Label $after = new Label();
		generator.emitJumpForward(this, $after);
		generator.emit($absent);
		generator.emit(this, PUSH_LITERAL, MessageSplitter.indexForFalse());
		generator.emit($after);
		return wrapState.processAfterPushedArgument(this, generator);
	}

	/**
	 * On the given {@link InstructionGenerator}, output
	 * {@link ParsingOperation}s to handle this {@code Optional}'s present
	 * and absent cases, invoking the {@link Continuation0} within each.
	 *
	 * @param generator
	 *        Where to emit parsing instructions.
	 * @param continuation
	 *        A {@link Continuation0} that generates code both in the case that
	 *        the optional tokens are present and the case that they are absent.
	 */
	void emitInRunThen (
		final InstructionGenerator generator,
		final Continuation0 continuation)
	{
		// emit branch $absent.
		//    emit the inner sequence, which cannot push arguments.
		//    run the continuation.
		//    emit push true.
		//    emit jump $merge.
		// emit $absent.
		//    run the continuation.
		//    emit push false.
		// emit $merge.
		// (the stack now has values pushed by the continuation, followed by the
		//  new boolean, which will need to be permuted into its correct place)
		assert !hasSectionCheckpoints();
		final boolean needsProgressCheck =
			sequence.mightBeEmpty(emptyListPhraseType());
		generator.flushDelayed();
		final Label $absent = new Label();
		generator.emitBranchForward(this, $absent);
		generator.emitIf(needsProgressCheck, this, SAVE_PARSE_POSITION);
		assert sequence.argumentsAreReordered != Boolean.TRUE;
		sequence.emitOn(
			emptyListPhraseType(), generator, SHOULD_NOT_HAVE_ARGUMENTS);
		generator.flushDelayed();
		generator.emitIf(needsProgressCheck, this, ENSURE_PARSE_PROGRESS);
		generator.emitIf(
			needsProgressCheck, this, DISCARD_SAVED_PARSE_POSITION);
		continuation.value();
		generator.flushDelayed();
		generator.emit(this, PUSH_LITERAL, MessageSplitter.indexForTrue());
		final Label $merge = new Label();
		generator.emitJumpForward(this, $merge);
		generator.emit($absent);
		continuation.value();
		generator.flushDelayed();
		generator.emit(this, PUSH_LITERAL, MessageSplitter.indexForFalse());
		generator.emit($merge);
	}

	@Override
	public String toString ()
	{
		return getClass().getSimpleName() + '(' + sequence + ')';
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
		final boolean flag = literal.token().literal().extractBoolean();
		if (flag)
		{
			builder.append('«');
			sequence.printWithArguments(
				Collections.emptyIterator(), builder, indent);
			builder.append("»?");
		}
	}

	@Override
	boolean shouldBeSeparatedOnLeft ()
	{
		// For now.  Eventually we could find out whether there were even
		// any tokens printed by passing an argument iterator.
		return true;
	}

	@Override
	boolean shouldBeSeparatedOnRight ()
	{
		// For now.  Eventually we could find out whether there were even
		// any tokens printed by passing an argument iterator.
		return true;
	}

	@Override
	boolean mightBeEmpty (final A_Type phraseType)
	{
		// Optional things can be absent.
		return true;
	}
}
