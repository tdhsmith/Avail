/**
 * MessageSplitter.java
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

package com.avail.compiler;

import static com.avail.compiler.ParsingOperation.*;
import static com.avail.compiler.ParsingConversionRule.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.StringDescriptor.*;
import static com.avail.exceptions.AvailErrorCode.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.avail.annotations.*;
import com.avail.compiler.AvailCompiler.ParserState;
import com.avail.compiler.InstructionGenerator.Label;
import com.avail.compiler.scanning.AvailScanner;
import com.avail.descriptor.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.exceptions.*;
import com.avail.utility.Generator;
import com.avail.utility.Pair;
import com.avail.utility.evaluation.Transformer1;

/**
 * {@code MessageSplitter} is used to split Avail message names into a sequence
 * of {@linkplain ParsingOperation instructions} that can be used directly for
 * parsing.
 *
 * <p>Message splitting occurs in two phases.  In the first phase, the
 message is tokenized and parsed into an abstract {@link Expression}
 * tree.  In the second phase, a {@linkplain TupleTypeDescriptor tuple type} of
 * {@link com.avail.descriptor.ParseNodeTypeDescriptor phrase types} is
 * supplied, and produces a tuple of integer-encoded {@link ParsingOperation}s.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class MessageSplitter
{
	/**
	 * The {@linkplain A_Set set} of all {@linkplain AvailErrorCode errors} that
	 * can happen during {@linkplain MessageSplitter message splitting}.
	 */
	public static final A_Set possibleErrors = SetDescriptor.from(
		E_INCORRECT_ARGUMENT_TYPE,
		E_INCORRECT_TYPE_FOR_GROUP,
		E_INCORRECT_TYPE_FOR_COMPLEX_GROUP,
		E_INCORRECT_TYPE_FOR_COUNTING_GROUP,
		E_INCORRECT_TYPE_FOR_BOOLEAN_GROUP,
		E_INCORRECT_TYPE_FOR_NUMBERED_CHOICE,
		E_INCORRECT_USE_OF_DOUBLE_DAGGER,
		E_UNBALANCED_GUILLEMETS,
		E_METHOD_NAME_IS_NOT_CANONICAL,
		E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS,
		E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS,
		E_DOLLAR_SIGN_MUST_FOLLOW_AN_ELLIPSIS,
		E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP,
		E_TILDE_MUST_NOT_FOLLOW_ARGUMENT,
		E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
		E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP,
		E_DOUBLE_QUESTION_MARK_MUST_FOLLOW_A_TOKEN_OR_SIMPLE_GROUP,
		E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
		E_EXPECTED_OPERATOR_AFTER_BACKQUOTE,
		E_UP_ARROW_MUST_FOLLOW_ARGUMENT,
		E_INCONSISTENT_ARGUMENT_REORDERING).makeShared();

	/** A String containing all 51 circled numbers. */
	static final String circledNumbersString =
		"⓪①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯" +
		"⑰⑱⑲⑳㉑㉒㉓㉔㉕㉖㉗㉘㉙㉚㉛㉜㉝" +
		"㉞㉟㊱㊲㊳㊴㊵㊶㊷㊸㊹㊺㊻㊼㊽㊾㊿";

	/** How many circled numbers are in Unicode. */
	static final int circledNumbersCount =
		circledNumbersString.codePointCount(0, circledNumbersString.length());

	/** An array of the circled number code points. */
	static final int [] circledNumberCodePoints = new int[circledNumbersCount];

	/**
	 * A map from the Unicode code points for the circled number characters
	 * found in various regions of the Unicode code space.  See {@link
	 * #circledNumbersString}.
	 */
	static final Map<Integer, Integer> circledNumbersMap =
		new HashMap<>(circledNumbersCount);

	/* Initialize circledNumbersMap and circledNumberCodePoints */
	static
	{
		assert circledNumbersCount == 51;
		int pointer = 0;
		int arrayPointer = 0;
		while (pointer < circledNumbersString.length())
		{
			final int codePoint = circledNumbersString.codePointAt(pointer);
			circledNumbersMap.put(codePoint, pointer);
			circledNumberCodePoints[arrayPointer++] = codePoint;
			pointer += Character.charCount(codePoint);
		}
	}

	/**
	 * The Avail string to be parsed.
	 */
	@InnerAccess
	final A_String messageName;

	/**
	 * The individual tokens ({@linkplain StringDescriptor strings})
	 * constituting the message.
	 *
	 * <p><ul>
	 * <li>Alphanumerics are in runs, separated from other
	 * alphanumerics by a single space.</li>
	 * <li>Operator characters are never beside spaces, and are always parsed as
	 * individual tokens.</li>
	 * <li>{@linkplain StringDescriptor#openGuillemet() Open guillemet} («),
	 * {@linkplain StringDescriptor#doubleDagger() double dagger} (‡), and
	 * {@linkplain StringDescriptor#closeGuillemet() close guillemet} (») are
	 * used to indicate repeated or optional substructures.</li>
	 * <li>The characters {@linkplain StringDescriptor#octothorp() octothorp}
	 * (#) and {@linkplain StringDescriptor#questionMark() question mark} (?)
	 * modify the output of repeated substructures to produce either a count
	 * of the repetitions or a boolean indicating whether an optional
	 * subexpression (expecting no arguments) was present.</li>
	 * <li>Placing a {@linkplain StringDescriptor#questionMark() question mark}
	 * (?) after a group containing arguments but no {@linkplain
	 * StringDescriptor#doubleDagger() double-dagger} (‡) will limit the
	 * repetitions of the group to at most one.  Although limiting the method
	 * definitions to only accept 0..1 occurrences would accomplish the same
	 * grammatical narrowing, the parser might still attempt to parse more than
	 * one occurrence, leading to unnecessarily confusing diagnostics.</li>
	 * <li>An {@linkplain StringDescriptor#exclamationMark() exclamation mark}
	 * (!) can follow a group of alternations to produce the 1-based index of
	 * the alternative that actually occurred.</li>
	 * <li>An {@linkplain StringDescriptor#underscore() underscore} (_)
	 * indicates where an argument occurs.</li>
	 * <li>A {@linkplain StringDescriptor#singleDagger() single dagger} (†) may
	 * occur immediately after an underscore to cause the argument expression to
	 * be evaluated in the static scope during compilation.  This is applicable
	 * to both methods and macros.  The expression must yield a type.</li>
	 * <li>An {@linkplain StringDescriptor#upArrow() up-arrow} (↑) after an
	 * underscore indicates an in-scope variable name is to be parsed.  The
	 * subexpression causes the variable itself to be provided, rather than its
	 * value.</li>
	 * <li>An {@linkplain StringDescriptor#exclamationMark() exclamation mark}
	 * (!) may occur after the underscore instead, to indicate the argument
	 * expression may be ⊤-valued or ⊥-valued.  Since a function (and therefore
	 * a method definition) cannot accept a ⊤-valued argument, this mechanism
	 * only makes sense for macros, since macros bodies are passed phrases,
	 * which may be typed as <em>yielding</em> a top-valued result.</li>
	 * <li>An {@linkplain StringDescriptor#ellipsis() ellipsis} (…) matches a
	 * single {@linkplain TokenDescriptor keyword token}.</li>
	 * <li>An {@linkplain StringDescriptor#exclamationMark() exclamation mark}
	 * (!) after an ellipsis indicates <em>any</em> token will be accepted at
	 * that position.</li>
	 * <li>An {@linkplain StringDescriptor#octothorp() octothorp} (#) after an
	 * ellipsis indicates only a <em>literal</em> token will be accepted.</li>
	 * <li>The N<sup>th</sup> {@linkplain StringDescriptor#sectionSign() section
	 * sign} (§) in a message name indicates where a macro's N<sup>th</sup>
	 * {@linkplain A_Definition#prefixFunctions() prefix function} should be
	 * invoked with the current parse stack up to that point.</li>
	 * <li>A {@linkplain StringDescriptor#backQuote() backquote} (`) can
	 * precede any operator character, such as guillemets or double dagger, to
	 * ensure it is not used in a special way. A backquote may also operate on
	 * another backquote (to indicate that an actual backquote token will appear
	 * in a call).</li>
	 * </ul></p>
	 * @see #messagePartsTuple
	 */
	final List<A_String> messagePartsList = new ArrayList<>(10);

	/**
	 * The sequence of strings constituting discrete tokens of the message name.
	 * @see #messagePartsList
	 */
	final A_Tuple messagePartsTuple;

	/**
	 * A collection of one-based positions in the original string, corresponding
	 * to the {@link #messagePartsList} that have been extracted.
	 */
	final List<Integer> messagePartPositions = new ArrayList<>(10);

	/** The current one-based parsing position in the list of tokens. */
	private int messagePartPosition;

	/**
	 * A record of where each "underscore" occurred in the list of {@link
	 * #messagePartsList}.
	 */
	@InnerAccess List<Integer> underscorePartNumbers = new ArrayList<>();

	/**
	 * The number of {@link SectionCheckpoint}s encountered so far.
	 */
	@InnerAccess int numberOfSectionCheckpoints;

	/** The top-most {@linkplain Sequence sequence}. */
	final Sequence rootSequence;

	/**
	 * The tuple of all encountered permutations (tuples of integers) found in
	 * all message names.  Keeping a statically accessible tuple shared between
	 * message names allows {@link A_BundleTree bundle trees} to easily get to
	 * the permutations they need without having a separate per-tree structure.
	 *
	 * <p>The field is an {@link AtomicReference}, and is accessed in a
	 * wait-free way with compare-and-set and retry.  The tuple itself should
	 * always be marked as shared.  This mechanism is thread-safe.</p>
	 */
	public static AtomicReference<A_Tuple> permutations =
		new AtomicReference<A_Tuple>(TupleDescriptor.empty());

	/**
	 * A statically-scoped index of types for which {@link
	 * ParsingOperation#TYPE_CHECK_ARGUMENT} instructions have been emitted.
	 *
	 * <p>It's implemented as an {@link AtomicReference} containing both the map
	 * from type to index, and the tuple from index to type.  That allows the
	 * structure to be augmented in a wait-free way.</p>
	 */
	public static AtomicReference<Pair<A_Map, A_Tuple>> typesToCheck =
		new AtomicReference<Pair<A_Map,A_Tuple>>(
			new Pair<A_Map, A_Tuple>(
				MapDescriptor.empty(),
				TupleDescriptor.empty()));

	/**
	 * An {@code Expression} represents a structural view of part of the
	 * message name.
	 */
	abstract static class Expression
	{
		/**
		 * The one-based explicit numbering for this argument.  To specify this
		 * in a message name, a circled number (0-50) immediately follows the
		 * underscore or ellipsis (or the close guillemet for permuting {@link
		 * Group}s).
		 */
		private int explicitOrdinal = -1;

		/**
		 * Answer whether or not this an {@linkplain Argument argument} or
		 * {@linkplain Group group}.
		 *
		 * @return {@code true} if and only if this is an argument or group,
		 *         {@code false} otherwise.
		 */
		boolean isArgumentOrGroup ()
		{
			return false;
		}

		/**
		 * Answer whether or not this a {@linkplain Group group}.
		 *
		 * @return {@code true} if and only if this is an argument or group,
		 *         {@code false} otherwise.
		 */
		boolean isGroup ()
		{
			return false;
		}

		/**
		 * If this isn't even a {@link Group} then it doesn't need
		 * double-wrapping.  Override in Group.
		 *
		 * @return {@code true} if this is a group which will generate a tuple
		 *         of fixed-length tuples, {@code false} if this group will
		 *         generate a tuple of individual arguments or subgroups (or if
		 *         this isn't a group).
		 */
		boolean needsDoubleWrapping ()
		{
			return false;
		}

		/**
		 * Answer the number of non-backquoted underscores/ellipses that occur
		 * in this section of the method name.
		 *
		 * @return The number of non-backquoted underscores/ellipses in the
		 *         receiver.
		 */
		int underscoreCount ()
		{
			return 0;
		}

		/**
		 * Return whether the {@link SectionCheckpoint} with the given index is
		 * within this expression.
		 *
		 * @param sectionCheckpointNumber Which section checkpoint to look for.
		 * @return Whether this expression recursively contains the given
		 *         section checkpoint.
		 */
		final boolean containsSectionCheckpoint (
			final int sectionCheckpointNumber)
		{
			final List<SectionCheckpoint> sectionCheckpoints =
				new ArrayList<>();
			extractSectionCheckpointsInto(sectionCheckpoints);
			for (final SectionCheckpoint checkpoint : sectionCheckpoints)
			{
				if (checkpoint.subscript == sectionCheckpointNumber)
				{
					return true;
				}
			}
			return false;
		}

		/**
		 * Return whether any {@link SectionCheckpoint} occurs within this
		 * expression.
		 *
		 * @return Whether this expression recursively contains any section
		 *         checkpoints.
		 */
		final boolean containsAnySectionCheckpoint ()
		{
			final List<SectionCheckpoint> sectionCheckpoints =
				new ArrayList<>();
			extractSectionCheckpointsInto(sectionCheckpoints);
			return !sectionCheckpoints.isEmpty();
		}

		/**
		 * Extract all {@link SectionCheckpoint}s into the specified list.
		 *
		 * @param sectionCheckpoints
		 *        Where to add section checkpoints found within this expression.
		 */
		void extractSectionCheckpointsInto (
			final List<SectionCheckpoint> sectionCheckpoints)
		{
			// Do nothing by default.
		}

		/**
		 * Are all keywords of the expression comprised exclusively of lower
		 * case characters?
		 *
		 * @return {@code true} if all keywords of the expression are comprised
		 *         exclusively of lower case characters, {@code false}
		 *         otherwise.
		 */
		boolean isLowerCase ()
		{
			return true;
		}

		/**
		 * Check that the given type signature is appropriate for this message
		 * expression. If not, throw a {@link SignatureException}.
		 *
		 * <p>This is also called recursively on subcomponents, and it checks
		 * that {@linkplain Argument group arguments} have the correct structure
		 * for what will be parsed. The method may reject parses based on the
		 * number of repetitions of a {@linkplain Group group} at a call site,
		 * but not the number of arguments actually delivered by each
		 * repetition. For example, the message "«_:_‡,»" can limit the number
		 * of _:_ pairs to at most 5 by declaring the tuple type's size to be
		 * [5..5]. However, the message "«_:_‡[_]»" will always produce a tuple
		 * of 3-tuples followed by a 2-tuple (if any elements at all occur).
		 * Attempting to add a method implementation for this message that only
		 * accepted a tuple of 7-tuples would be inappropriate (and
		 * ineffective). Instead, it should be required to accept a tuple whose
		 * size is in the range [2..3].</p>
		 *
		 * <p>Note that the outermost (pseudo)group represents the entire
		 * message, so the caller should synthesize a fixed-length {@linkplain
		 * TupleTypeDescriptor tuple type} for the outermost check.</p>
		 *
		 * @param argumentType
		 *        A {@linkplain TupleTypeDescriptor tuple type} describing the
		 *        types of arguments that a method being added will accept.
		 * @param sectionNumber
		 *        Which {@linkplain SectionCheckpoint} section marker this list
		 *        of argument types are being validated against.  To validate
		 *        the final method or macro body rather than a prefix function,
		 *        use any value greater than the {@linkplain
		 *        #numberOfSectionCheckpoints}.
		 * @throws SignatureException
		 *        If the argument type is inappropriate.
		 */
		abstract public void checkType (
			final A_Type argumentType,
			final int sectionNumber)
		throws SignatureException;

		/**
		 * Write instructions for parsing me to the given list.
		 *
		 * @param generator
		 *        The {@link InstructionGenerator} that accumulates the parsing
		 *        instructions.
		 * @param phraseType
		 *        The type of the phrase being parsed at and inside this parse
		 *        point.  Note that when this is for a list phrase type, it's
		 *        used for unrolling leading iterations of loops up to the end
		 *        of the variation (typically just past the list phrase's tuple
		 *        type's {@link A_Type#typeTuple()}).
		 */
		abstract void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType);

		@Override
		public String toString ()
		{
			return getClass().getSimpleName();
		}

		/**
		 * Pretty-print this part of the message, using the provided argument
		 * {@linkplain ParseNodeDescriptor nodes}.
		 *
		 * @param arguments
		 *        An {@link Iterator} that provides parse nodes to fill in for
		 *        arguments and subgroups.
		 * @param builder
		 *        The {@link StringBuilder} on which to print.
		 * @param indent
		 *        The indentation level.
		 */
		abstract public void printWithArguments (
			@Nullable Iterator<AvailObject> arguments,
			StringBuilder builder,
			int indent);

		/**
		 * Answer whether the pretty-printed representation of this {@link
		 * Expression} should be separated from its left sibling with a space.
		 *
		 * @return Whether this expression should be preceded by a space if it
		 *         has a left sibling.
		 */
		abstract boolean shouldBeSeparatedOnLeft ();

		/**
		 * Answer whether the pretty-printed representation of this {@link
		 * Expression} should be separated from its right sibling with a space.
		 *
		 * @return Whether this expression should be followed by a space if it
		 *         has a right sibling.
		 */
		abstract boolean shouldBeSeparatedOnRight ();

		/**
		 * Answer whether reordering with respect to siblings is applicable to
		 * this kind of expression.
		 *
		 * @return Whether the expression can in theory be reordered.
		 */
		public final boolean canBeReordered ()
		{
			return isArgumentOrGroup();
		}

		/**
		 * Answer my explicitOrdinal, which indicates how to reorder me with my
		 * siblings.  This may only be requested for types of {@link Expression}
		 * that {@link #canBeReordered()}.
		 *
		 * @return My explicitOrdinal or -1.
		 */
		public final int explicitOrdinal ()
		{
			assert canBeReordered();
			return explicitOrdinal;
		}

		/**
		 * Set my explicitOrdinal, which indicates how to reorder me with my
		 * siblings.  This may only be set for types of {@link Expression}
		 * that {@link #canBeReordered()}.
		 *
		 * @param ordinal My explicitOrdinal or -1.
		 */
		public final void explicitOrdinal (final int ordinal)
		{
			assert canBeReordered();
			explicitOrdinal = ordinal;
		}
	}

	/**
	 * A {@linkplain Simple} is an {@linkplain Expression expression} that
	 * represents a single token, except for the double-dagger character.
	 */
	final class Simple
	extends Expression
	{
		/**
		 * The one-based index of this token within the {@link
		 * MessageSplitter#messagePartsList message parts}.
		 */
		final int tokenIndex;

		/**
		 * Construct a new {@linkplain Simple simple expression} representing a
		 * specific token expected in the input.
		 *
		 * @param tokenIndex
		 *        The one-based index of the token within the {@link
		 *        MessageSplitter#messagePartsList message parts}.
		 */
		Simple (final int tokenIndex)
		{
			this.tokenIndex = tokenIndex;
		}

		@Override
		final boolean isLowerCase ()
		{
			final String token =
				messagePartsList.get(tokenIndex - 1).asNativeString();
			return token.toLowerCase().equals(token);
		}

		@Override
		public void checkType (
			final A_Type argumentType,
			final int sectionNumber)
		{
			assert false : "checkType() should not be called for Simple" +
					" expressions";
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			// Parse the specific keyword.
			final ParsingOperation op = generator.caseInsensitive
				? PARSE_PART_CASE_INSENSITIVELY
				: PARSE_PART;
			generator.emit(this, op, tokenIndex);
		}

		@Override
		public String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			builder.append(messagePartsList.get(tokenIndex - 1));
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> arguments,
			final StringBuilder builder,
			final int indent)
		{
			final A_String token = messagePartsList.get(tokenIndex - 1);
			builder.append(token.asNativeString());
		}

		/**
		 * Characters which, if they start a token, should vote for having a
		 * space before the token.  If the predecessor agrees, there will be a
		 * space.
		 */
		final static String charactersThatLikeSpacesBefore = "(=+-×÷*/∧∨:?";

		/**
		 * Characters which, if they end a token, should vote for having a
		 * space after the token.  If the successor agrees, there will be a
		 * space.
		 */
		final static String charactersThatLikeSpacesAfter = ")]=+-×÷*/∧∨→";

		@Override
		boolean shouldBeSeparatedOnLeft ()
		{
			final String token =
				messagePartsList.get(tokenIndex - 1).asNativeString();
			assert token.length() > 0;
			final int firstCharacter = token.codePointAt(0);
			if (Character.isUnicodeIdentifierPart(firstCharacter))
			{
				return true;
			}
			return charactersThatLikeSpacesBefore.indexOf(firstCharacter) >= 0;
		}

		@Override
		boolean shouldBeSeparatedOnRight ()
		{
			final String token =
				messagePartsList.get(tokenIndex - 1).asNativeString();
			assert token.length() > 0;
			final int lastCharacter = token.codePointBefore(token.length());
			if (Character.isUnicodeIdentifierPart(lastCharacter))
			{
				return true;
			}
			return charactersThatLikeSpacesAfter.indexOf(lastCharacter) >= 0;
		}
	}

	/**
	 * An {@linkplain Argument} is an occurrence of {@linkplain
	 * StringDescriptor#underscore() underscore} (_) in a message name. It
	 * indicates where an argument is expected.
	 */
	class Argument
	extends Expression
	{
		/**
		 * The one-based index for this argument.  In particular, it's one plus
		 * the number of non-backquoted underscores/ellipses that occur anywhere
		 * to the left of this one in the message name.
		 */
		final int absoluteUnderscoreIndex;

		/**
		 * Construct an argument.
		 *
		 * @param startTokenIndex The one-based index of the underscore token.
		 */
		Argument (final int startTokenIndex)
		{
			underscorePartNumbers.add(startTokenIndex);
			absoluteUnderscoreIndex = numberOfUnderscores();
		}

		@Override
		boolean isArgumentOrGroup ()
		{
			return true;
		}

		@Override
		int underscoreCount ()
		{
			return 1;
		}

		/**
		 * A simple underscore/ellipsis can be arbitrarily restricted, other
		 * than when it is restricted to the uninstantiable type {@linkplain
		 * BottomTypeDescriptor#bottom() bottom}.
		 */
		@Override
		public void checkType (
			final A_Type argumentType,
			final int sectionNumber)
		throws SignatureException
		{
			if (argumentType.isBottom())
			{
				// Method argument type should not be bottom.
				throwSignatureException(E_INCORRECT_ARGUMENT_TYPE);
			}
		}

		/**
		 * Parse an argument subexpression, then check that it has an acceptable
		 * form (i.e., does not violate a grammatical restriction for that
		 * argument position).
		 */
		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			generator.emit(this, PARSE_ARGUMENT);
			generator.emit(this, CHECK_ARGUMENT, absoluteUnderscoreIndex);
			generator.emit(this, TYPE_CHECK_ARGUMENT, indexForType(phraseType));
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> arguments,
			final StringBuilder builder,
			final int indent)
		{
			assert arguments != null;
			arguments.next().printOnAvoidingIndent(
				builder,
				new IdentityHashMap<A_BasicObject, Void>(),
				indent + 1);
		}

		@Override
		boolean shouldBeSeparatedOnLeft ()
		{
			return true;
		}

		@Override
		boolean shouldBeSeparatedOnRight ()
		{
			return true;
		}
	}

	/**
	 * A {@linkplain ArgumentInModuleScope} is an occurrence of an {@linkplain
	 * StringDescriptor#underscore() underscore} (_) in a message name, followed
	 * immediately by a {@linkplain StringDescriptor#singleDagger() single
	 * dagger} (†). It indicates where an argument is expected, but the argument
	 * must not make use of any local declarations. The argument expression will
	 * be evaluated at compile time and replaced by a {@linkplain
	 * LiteralNodeDescriptor literal} based on the produced value.
	 */
	final class ArgumentInModuleScope
	extends Argument
	{
		/**
		 * Construct a new {@link MessageSplitter.ArgumentInModuleScope}.
		 *
		 * @param startTokenIndex The one-based token index of this argument.
		 */
		public ArgumentInModuleScope (final int startTokenIndex)
		{
			super(startTokenIndex);
		}

		/**
		 * First parse an argument subexpression, then check that it has an
		 * acceptable form (i.e., does not violate a grammatical restriction for
		 * that argument position).  Also ensure that no local declarations that
		 * were in scope before parsing the argument are used by the argument.
		 * Then evaluate the argument expression (at compile time) and replace
		 * it with a {@link LiteralNodeDescriptor literal phrase} wrapping the
		 * produced value.
		 */
		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			generator.emit(this, PARSE_ARGUMENT_IN_MODULE_SCOPE);
			generator.emit(this, CHECK_ARGUMENT, absoluteUnderscoreIndex);
			generator.emit(this, TYPE_CHECK_ARGUMENT, indexForType(phraseType));
			generator.emit(this, CONVERT, EVALUATE_EXPRESSION.number());
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> arguments,
			final StringBuilder builder,
			final int indent)
		{
			assert arguments != null;
			// Describe the token that was parsed as this raw token argument.
			arguments.next().printOnAvoidingIndent(
				builder,
				new IdentityHashMap<A_BasicObject, Void>(),
				indent + 1);
			builder.append("†");
		}
	}

	/**
	 * A {@linkplain VariableQuote} is an occurrence of {@linkplain
	 * StringDescriptor#upArrow() up arrow} (↑) after an underscore in a
	 * message name. It indicates that the expression must be the name of a
	 * {@linkplain VariableDescriptor variable} that is currently in-scope. It
	 * produces a {@linkplain ReferenceNodeDescriptor reference} to the
	 * variable, rather than extracting its value.
	 */
	final class VariableQuote
	extends Argument
	{
		/**
		 * Construct a new {@link MessageSplitter.VariableQuote}.
		 *
		 * @param startTokenIndex The one-based token index of this argument.
		 */
		public VariableQuote (final int startTokenIndex)
		{
			super(startTokenIndex);
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			generator.emit(this, PARSE_VARIABLE_REFERENCE);
			generator.emit(this, CHECK_ARGUMENT, absoluteUnderscoreIndex);
			generator.emit(this, TYPE_CHECK_ARGUMENT, indexForType(phraseType));
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> arguments,
			final StringBuilder builder,
			final int indent)
		{
			assert arguments != null;
			// Describe the variable reference that was parsed as this argument.
			arguments.next().printOnAvoidingIndent(
				builder,
				new IdentityHashMap<A_BasicObject, Void>(),
				indent + 1);
			builder.append("↑");
		}
	}

	/**
	 * An {@linkplain ArgumentForMacroOnly} is the translation of an {@linkplain
	 * StringDescriptor#underscore() underscore} (_) in a message name, followed
	 * immediately by an {@linkplain StringDescriptor#exclamationMark()
	 * exclamation mark} (!).  It indicates where an argument is expected – but
	 * the argument is allowed to be ⊤-valued or ⊥-valued.  Functions (and
	 * therefore method definitions) may not take arguments of type ⊤ or ⊥, so
	 * this mechanism is restricted to use by macros, where the phrases
	 * themselves (including phrases yielding ⊤ or ⊥) are what get passed to
	 * the macro body.
	 *
	 * <p>Because {@link ListNodeDescriptor list phrases} have an {@linkplain
	 * A_Phrase#expressionType()} that depends on the types of the expressinType
	 * of each subexpression, and because ⊥ as an element in a tuple type makes
	 * the entire resulting tuple type also be ⊥, we can't just directly accept
	 * an expression that produces ⊤ or ⊥ (e.g., the resulting list's apparent
	 * cardinality would be lost, as ⊥ is a subtype of every tuple type.</p>
	 */
	final class ArgumentForMacroOnly
	extends Argument
	{
		/**
		 * Construct a new {@link MessageSplitter.ArgumentForMacroOnly}.
		 *
		 * @param startTokenIndex The one-based token index of this argument.
		 */
		public ArgumentForMacroOnly (final int startTokenIndex)
		{
			super(startTokenIndex);
		}

		/**
		 * Parse an argument expression which might be top-valued.
		 */
		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			generator.emit(this, PARSE_TOP_VALUED_ARGUMENT);
			generator.emit(this, CHECK_ARGUMENT, absoluteUnderscoreIndex);
			generator.emit(this, TYPE_CHECK_ARGUMENT, indexForType(phraseType));
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> arguments,
			final StringBuilder builder,
			final int indent)
		{
			assert arguments != null;
			// Produce an ordinary description of the argument, even though it
			// might have an expression type of top.
			arguments.next().printOnAvoidingIndent(
				builder,
				new IdentityHashMap<A_BasicObject, Void>(),
				indent + 1);
		}
	}

	/**
	 * A {@linkplain RawTokenArgument} is an occurrence of {@linkplain
	 * StringDescriptor#ellipsis() ellipsis} (…) in a message name, followed by
	 * an {@linkplain StringDescriptor#exclamationMark() exclamation mark} (!).
	 * It indicates where <em>any</em> raw token is expected, which gets
	 * captured as an argument, wrapped in a literal phrase.
	 */
	class RawTokenArgument
	extends Argument
	{
		/**
		 * Construct a new {@link MessageSplitter.RawTokenArgument}.
		 *
		 * @param startTokenIndex The one-based token index of this argument.
		 */
		public RawTokenArgument (final int startTokenIndex)
		{
			super(startTokenIndex);
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			generator.emit(this, PARSE_ANY_RAW_TOKEN);
			generator.emit(this, TYPE_CHECK_ARGUMENT, indexForType(phraseType));
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> arguments,
			final StringBuilder builder,
			final int indent)
		{
			assert arguments != null;
			// Describe the token that was parsed as this raw token argument.
			arguments.next().printOnAvoidingIndent(
				builder,
				new IdentityHashMap<A_BasicObject, Void>(),
				indent + 1);
		}
	}

	/**
	 * A {@linkplain RawKeywordTokenArgument} is an occurrence of {@linkplain
	 * StringDescriptor#ellipsis() ellipsis} (…) in a message name. It indicates
	 * where a raw keyword token argument is expected. Like its superclass, the
	 * {@link RawTokenArgument}, the token is captured after being placed in a
	 * literal phrase, but in this case the token is restricted to be a {@link
	 * TokenType#KEYWORD keyword} (i.e., alphanumeric).
	 */
	final class RawKeywordTokenArgument
	extends RawTokenArgument
	{
		/**
		 * Construct a new {@link MessageSplitter.RawKeywordTokenArgument}.
		 *
		 * @param startTokenIndex The one-based token index of this argument.
		 */
		public RawKeywordTokenArgument (final int startTokenIndex)
		{
			super(startTokenIndex);
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			generator.emit(this, PARSE_RAW_KEYWORD_TOKEN);
			generator.emit(this, TYPE_CHECK_ARGUMENT, indexForType(phraseType));
		}
	}

	/**
	 * A {@linkplain RawStringLiteralTokenArgument} is an occurrence of
	 * {@linkplain StringDescriptor#ellipsis() ellipsis} (…) in a message name,
	 * followed by a {@linkplain StringDescriptor#dollarSign() dollarSign} ($).
	 * It indicates where a raw string literal token argument is expected. Like
	 * its superclass, the {@link RawTokenArgument}, the token is captured after
	 * being placed in a literal phrase, but in this case the token is
	 * restricted to be a {@link TokenType#LITERAL} (currently positive
	 * integers, doubles, and strings).
	 */
	final class RawStringLiteralTokenArgument
	extends RawTokenArgument
	{
		/**
		 * Construct a new {@link MessageSplitter.RawStringLiteralTokenArgument}.
		 *
		 * @param startTokenIndex The one-based token index of this argument.
		 */
		public RawStringLiteralTokenArgument (final int startTokenIndex)
		{
			super(startTokenIndex);
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			generator.emit(this, PARSE_RAW_STRING_LITERAL_TOKEN);
			generator.emit(this, TYPE_CHECK_ARGUMENT, indexForType(phraseType));
		}
	}

	/**
	 * A {@linkplain RawWholeNumberLiteralTokenArgument} is an occurrence of
	 * {@linkplain StringDescriptor#ellipsis() ellipsis} (…) in a message name,
	 * followed by an {@linkplain StringDescriptor#octothorp() octothorp} (#).
	 * It indicates where a raw whole number literal token argument is expected.
	 * Like its superclass, the {@link RawTokenArgument}, the token is captured
	 * after being placed in a literal phrase, but in this case the token is
	 * restricted to be a {@link TokenType#LITERAL} (currently positive
	 * integers, doubles, and strings).
	 */
	final class RawWholeNumberLiteralTokenArgument
	extends RawTokenArgument
	{
		/**
		 * Construct a new {@link
		 * MessageSplitter.RawStringLiteralTokenArgument}.
		 *
		 * @param startTokenIndex The one-based token index of this argument.
		 */
		public RawWholeNumberLiteralTokenArgument (final int startTokenIndex)
		{
			super(startTokenIndex);
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			generator.emit(this, PARSE_RAW_WHOLE_NUMBER_LITERAL_TOKEN);
			generator.emit(this, TYPE_CHECK_ARGUMENT, indexForType(phraseType));
		}
	}

	/**
	 * A {@link Sequence} is the juxtaposition of any number of other {@link
	 * Expression}s.  It is not itself a repetition, but it can be the left or
	 * right half of a {@link Group} (bounded by the double-dagger (‡)).
	 */
	final class Sequence
	extends Expression
	{
		/**
		 * The sequence of expressions that I comprise.
		 */
		final List<Expression> expressions = new ArrayList<>();

		/**
		 * Which of my {@link #expressions} is an argument, ellipsis, or group?
		 * These are in the order they occur in the {@code expressions} list.
		 */
		final List<Expression> arguments = new ArrayList<>();

		/**
		 * My one-based permutation that takes argument expressions from the
		 * order in which they occur to the order in which they are bound to
		 * arguments at a call site.
		 */
		final List<Integer> permutedArguments = new ArrayList<>();

		/**
		 * A three-state indicator of whether my argument components should be
		 * reordered.  If null, a decision has not yet been made, either during
		 * parsing (because an argument/group has not yet been encountered), or
		 * because this {@code Sequence} has no arguments or subgroups that act
		 * as arguments.  If {@link Boolean#TRUE}, then all argument positions
		 * so far have specified reordering (by using circled numbers), and if
		 * {@link Boolean#FALSE}, then no arguments so far have specified
		 * reordering.
		 */
		@Nullable Boolean argumentsAreReordered = null;

		/**
		 * Add an {@linkplain Expression expression} to the {@link Sequence}.
		 *
		 * @param e
		 *        The expression to add.
		 * @throws MalformedMessageException
		 *         If the absence or presence of argument numbering would be
		 *         inconsistent within this {@link Sequence}.
		 */
		void addExpression (final Expression e)
			throws MalformedMessageException
		{
			expressions.add(e);
			if (e.isArgumentOrGroup())
			{
				arguments.add(e);
			}
			if (e.canBeReordered())
			{
				if (argumentsAreReordered != null
					&& argumentsAreReordered != (e.explicitOrdinal() != -1))
				{
					throwMalformedMessageException(
						E_INCONSISTENT_ARGUMENT_REORDERING,
						"The sequence of subexpressions before or after a "
						+ "double-dagger (‡) in a group must have either all "
						+ "or none of its arguments/subgroups numbered for "
						+ "reordering");
				}
				argumentsAreReordered = e.explicitOrdinal() != -1;
			}
		}

		@Override
		boolean isArgumentOrGroup ()
		{
			return false;
		}

		@Override
		boolean isGroup ()
		{
			return false;
		}

		@Override
		int underscoreCount ()
		{
			int count = 0;
			for (final Expression expr : expressions)
			{
				count += expr.underscoreCount();
			}
			return count;
		}

		@Override
		boolean isLowerCase ()
		{
			for (final Expression expression : expressions)
			{
				if (!expression.isLowerCase())
				{
					return false;
				}
			}
			return true;
		}

		@Override
		void extractSectionCheckpointsInto (
			final List<SectionCheckpoint> sectionCheckpoints)
		{
			for (final Expression expression : expressions)
			{
				expression.extractSectionCheckpointsInto(sectionCheckpoints);
			}
		}

		/**
		 * Check if the given type is suitable for holding values generated by
		 * this sequence.
		 */
		@Override
		public void checkType (
			final A_Type argumentType,
			final int sectionNumber)
		throws SignatureException
		{
			// Always expect a tuple of solutions here.
			if (argumentType.isBottom())
			{
				// Method argument type should not be bottom.
				throwSignatureException(E_INCORRECT_ARGUMENT_TYPE);
			}

			if (!argumentType.isTupleType())
			{
				// The sequence produces a tuple.
				throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP);
			}

			// Make sure the tuple of argument types are suitable for the
			// argument positions that I comprise.  Take the argument reordering
			// permutation into account if present.
			final A_Number expected =
				IntegerDescriptor.fromInt(arguments.size());
			final A_Type sizes = argumentType.sizeRange();
			if (!sizes.lowerBound().equals(expected)
				|| !sizes.upperBound().equals(expected))
			{
				throwSignatureException(
					this == rootSequence
						? E_INCORRECT_NUMBER_OF_ARGUMENTS
						: E_INCORRECT_TYPE_FOR_GROUP);
			}
			for (int i = 1; i <= arguments.size(); i++)
			{
				final Expression argumentOrGroup =
					argumentsAreReordered == Boolean.TRUE
						? arguments.get(permutedArguments.get(i - 1) - 1)
						: arguments.get(i - 1);
				final A_Type providedType = argumentType.typeAtIndex(i);
				assert !providedType.isBottom();
				argumentOrGroup.checkType(providedType, sectionNumber);
			}
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			/*
			 * Generate code to parse the sequence.  After parsing, the stack
			 * contains a new list of parsed expressions for all arguments,
			 * ellipses, and subgroups that were encountered.
			 */
			generator.emit(this, NEW_LIST);
			emitWithoutInitialNewListPushOn (generator, phraseType);
		}

		/**
		 * Emit parsing instructions that assume that there has already been an
		 * empty list pushed, onto which to accumulate arguments.
		 *
		 * @param generator
		 *        The instruction generator with which to emit.
		 * @param phraseType
		 *        The {@link A_Type phrase type} for a definition's signature.
		 */
		@InnerAccess void emitWithoutInitialNewListPushOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			/* After parsing, the list that's already on the stack will contain
			 * all arguments, ellipses, and subgroups that were encountered.
			 */
			assert phraseType.isSubtypeOf(PARSE_NODE.mostGeneralType());
			final A_Type tupleType;
			if (phraseType.isSubtypeOf(LIST_NODE.mostGeneralType()))
			{
				tupleType = phraseType.subexpressionsTupleType();
			}
			else
			{
				tupleType = TupleTypeDescriptor.mappingElementTypes(
					phraseType.expressionType(),
					new Transformer1<A_Type, A_Type>()
					{
						@Override
						public A_Type value (@Nullable final A_Type arg)
						{
							assert arg != null;
							return PARSE_NODE.create(arg);
						}
					});
			}
			int index = 0;
			for (final Expression expression : expressions)
			{
				if (expression.isArgumentOrGroup())
				{
					final A_Type entryType = tupleType.typeAtIndex(++index);
					expression.emitOn(generator, entryType);
					generator.emit(this, APPEND_ARGUMENT);
				}
				else
				{
					expression.emitOn(
						generator, ListNodeTypeDescriptor.empty());
				}
			}
			assert tupleType.sizeRange().lowerBound().equalsInt(index);
			assert tupleType.sizeRange().upperBound().equalsInt(index);
			if (argumentsAreReordered == Boolean.TRUE)
			{
				final A_Tuple permutationTuple =
					TupleDescriptor.fromIntegerList(permutedArguments);
				final int permutationIndex =
					indexForPermutation(permutationTuple);
				// This sequence was already collected into a list node as the
				// arguments/groups were parsed.  Permute the list.
				generator.emit(this, PERMUTE_LIST, permutationIndex);
			}
		}

		@Override
		public String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append("Sequence(");
			boolean first = true;
			for (final Expression e : expressions)
			{
				if (!first)
				{
					builder.append(", ");
				}
				builder.append(e.toString());
				if (e.canBeReordered() && e.explicitOrdinal() != -1)
				{
					builder.appendCodePoint(
						circledNumberCodePoints[e.explicitOrdinal()]);
				}
				first = false;
			}
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> argumentProvider,
			final StringBuilder builder,
			final int indent)
		{
			assert argumentProvider != null;
			boolean needsSpace = false;
			for (final Expression expression : expressions)
			{
				if (needsSpace && expression.shouldBeSeparatedOnLeft())
				{
					builder.append(" ");
				}
				final int oldLength = builder.length();
				expression.printWithArguments(
					argumentProvider,
					builder,
					indent);
				needsSpace = expression.shouldBeSeparatedOnRight()
					&& builder.length() != oldLength;
			}
			assert !argumentProvider.hasNext();
		}

		@Override
		boolean shouldBeSeparatedOnLeft ()
		{
			return !expressions.isEmpty()
				&& expressions.get(0).shouldBeSeparatedOnLeft();
		}

		@Override
		boolean shouldBeSeparatedOnRight ()
		{
			return !expressions.isEmpty()
				&& expressions.get(expressions.size() - 1)
					.shouldBeSeparatedOnRight();
		}

		/**
		 * Check that if ordinals were specified for my N argument positions,
		 * that they are all present and constitute a permutation of [1..N].
		 * If not, throw a {@link MalformedMessageException}.
		 *
		 * @throws MalformedMessageException
		 *         If the arguments have reordering numerals (circled numbers),
		 *         but they don't form a non-trivial permutation of [1..N].
		 */
		public void checkForConsistentOrdinals ()
			throws MalformedMessageException
		{
			if (argumentsAreReordered == Boolean.TRUE)
			{
				checkForConsistentOrdinals(expressions);
			}
		}

		/**
		 * Check whether the provided list of expressions has specified a
		 * reordering that is a non-trivial permutation of [1..N].
		 *
		 * @param subexpressions
		 *        The expressions, N of which are argument subexpressions, which
		 *        we check are distinct values in [1..N] and not all ascending.
		 * @throws MalformedMessageException
		 *         If the reordering numbers of the argument subexpressions are
		 *         not a non-trivial permutation of [1..N].
		 */
		private void checkForConsistentOrdinals (
				final List<? extends Expression> subexpressions)
			throws MalformedMessageException
		{
			final List<Integer> usedOrdinalsList = new ArrayList<>();
			for (final Expression e : subexpressions)
			{
				if (e.canBeReordered())
				{
					usedOrdinalsList.add(e.explicitOrdinal());
				}
			}
			final int size = usedOrdinalsList.size();
			final Set<Integer> usedOrdinalsSet =
				new HashSet<Integer>(usedOrdinalsList);
			final List<Integer> sortedOrdinalsList =
				new ArrayList<>(usedOrdinalsList);
			Collections.sort(sortedOrdinalsList);
			if (usedOrdinalsSet.size() < usedOrdinalsList.size()
				|| sortedOrdinalsList.get(0) != 1
				|| sortedOrdinalsList.get(size - 1) != size
				|| usedOrdinalsList.equals(sortedOrdinalsList))
			{
				// There may have been a duplicate, a lowest value other
				// than 1, a highest value other than the number of values,
				// or the permutation might be the identity permutation (not
				// allowed).  Note that if one of the arguments somehow
				// still had an ordinal of -1 then it will trigger (at
				// least) the lowest value condition.
				throwMalformedMessageException(
					E_INCONSISTENT_ARGUMENT_REORDERING,
					"The circled numbers for this clause must range from 1 "
					+ "to the number of arguments/groups, but must not be "
					+ "in ascending order (got " + usedOrdinalsList + ")");
			}
			assert permutedArguments.isEmpty();
			permutedArguments.addAll(usedOrdinalsList);
		}
	}

	/**
	 * A {@linkplain Group} is delimited by the {@linkplain
	 * StringDescriptor#openGuillemet() open guillemet} («) and {@linkplain
	 * StringDescriptor#closeGuillemet() close guillemet} (») characters, and
	 * may contain subgroups and an occurrence of a {@linkplain
	 * StringDescriptor#doubleDagger() double dagger} (‡). If no double dagger
	 * or subgroup is present, the sequence of message parts between the
	 * guillemets are allowed to occur zero or more times at a call site
	 * (i.e., a send of this message). When the number of {@linkplain
	 * StringDescriptor#underscore() underscores} (_) and {@linkplain
	 * StringDescriptor#ellipsis() ellipses} (…) plus the number of subgroups is
	 * exactly one, the argument (or subgroup) values are assembled into a
	 * {@linkplain TupleDescriptor tuple}. Otherwise the leaf arguments and/or
	 * subgroups are assembled into a tuple of fixed-sized tuples, each
	 * containing one entry for each argument or subgroup.
	 *
	 * <p>When a double dagger occurs in a group, the parts to the left of the
	 * double dagger can occur zero or more times, but separated by the parts to
	 * the right. For example, "«_‡,»" is how to specify a comma-separated tuple
	 * of arguments. This pattern contains a single underscore and no subgroups,
	 * so parsing "1,2,3" would simply produce the tuple <1,2,3>. The pattern
	 * "«_=_;»" will parse "1=2;3=4;5=6;" into <<1,2>,<3,4>,<5,6>> because it
	 * has two underscores.</p>
	 *
	 * <p>The message "«A_‡x_»" parses zero or more occurrences in the text of
	 * the keyword "A" followed by an argument, separated by the keyword "x" and
	 * an argument.  "A 1 x 2 A 3 x 4 A 5" is such an expression (and "A 1 x 2"
	 * is not). In this case, the arguments will be grouped in such a way that
	 * the final element of the tuple, if any, is missing the post-double dagger
	 * elements: <<1,2>,<3,4>,<5>>.</p>
	 */
	final class Group
	extends Expression
	{
		/**
		 * Whether a {@linkplain StringDescriptor#doubleDagger() double dagger}
		 * (‡) has been encountered in the tokens for this group.
		 */
		final boolean hasDagger;

		/**
		 * If a {@linkplain StringDescriptor#doubleDagger() double dagger} (‡)
		 * has been encountered, this holds the one-based index of the {@link
		 * #messagePartsList message part} that was the double dagger.
		 */
		final int daggerPosition;

		/**
		 * The {@link Sequence} of {@link Expression}s that appeared before the
		 * {@linkplain StringDescriptor#doubleDagger() double dagger}, or in the
		 * entire subexpression if no double dagger is present.
		 */
		final Sequence beforeDagger;

		/**
		 * The {@link Sequence} of {@link Expression}s that appear after the
		 * {@linkplain StringDescriptor#doubleDagger() double dagger}, or an
		 * empty sequence if no double dagger is present.
		 */
		final Sequence afterDagger;

		/**
		 * The maximum number of occurrences accepted for this group.
		 */
		int maximumCardinality = Integer.MAX_VALUE;

		/**
		 * Construct a new {@link Group} having a double-dagger (‡).
		 *
		 * @param beforeDagger
		 *        The {@link Sequence} before the double-dagger.
		 * @param daggerPosition
		 *        The 1-based position of the double-dagger.
		 * @param afterDagger
		 *        The {@link Sequence} after the double-dagger.
		 */
		public Group (
			final Sequence beforeDagger,
			final int daggerPosition,
			final Sequence afterDagger)
		{
			this.beforeDagger = beforeDagger;
			this.hasDagger = true;
			this.daggerPosition = daggerPosition;
			this.afterDagger = afterDagger;
		}

		/**
		 * Construct a new {@link Group} that does not contain a double-dagger
		 * (‡).
		 *
		 * @param beforeDagger
		 *        The {@link Sequence} of {@link Expression}s in the group.
		 */
		public Group (final Sequence beforeDagger)
		{
			this.beforeDagger = beforeDagger;
			this.hasDagger = false;
			this.daggerPosition = -1;
			this.afterDagger = new Sequence();
		}

		/**
		 * Add an {@linkplain Expression expression} to the {@link Group},
		 * either before or after the {@linkplain
		 * StringDescriptor#doubleDagger() double dagger}, depending on whether
		 * {@link #hasDagger} has been set.
		 *
		 * @param e
		 *        The expression to add.
		 * @throws MalformedMessageException
		 *         If the absence or presence of argument numbering would be
		 *         inconsistent within this {@link Group}.
		 */
		void addExpression (final Expression e)
			throws MalformedMessageException
		{
			(hasDagger ? afterDagger : beforeDagger).addExpression(e);
		}

		@Override
		boolean isArgumentOrGroup ()
		{
			return true;
		}

		@Override
		boolean isGroup ()
		{
			return true;
		}

		@Override
		int underscoreCount ()
		{
			return beforeDagger.underscoreCount()
				+ afterDagger.underscoreCount();
		}

		@Override
		boolean isLowerCase ()
		{
			return beforeDagger.isLowerCase() && afterDagger.isLowerCase();
		}

		/**
		 * Set the maximum number of times this group may occur.
		 *
		 * @param max
		 *        My new maximum cardinality, or {@link Integer#MAX_VALUE} to
		 *        stand for {@link InfinityDescriptor#positiveInfinity()}.
		 */
		void maximumCardinality (final int max)
		{
			maximumCardinality = max;
		}

		/**
		 * Determine if this group should generate a {@linkplain TupleDescriptor
		 * tuple} of plain arguments or a tuple of fixed-length tuples of plain
		 * arguments.
		 *
		 * @return {@code true} if this group will generate a tuple of
		 *         fixed-length tuples, {@code false} if this group will
		 *         generate a tuple of individual arguments or subgroups.
		 */
		@Override
		boolean needsDoubleWrapping ()
		{
			return beforeDagger.arguments.size() != 1
				|| afterDagger.arguments.size() != 0;
		}

		@Override
		void extractSectionCheckpointsInto (
			final List<SectionCheckpoint> sectionCheckpoints)
		{
			beforeDagger.extractSectionCheckpointsInto(sectionCheckpoints);
			afterDagger.extractSectionCheckpointsInto(sectionCheckpoints);
		}

		/**
		 * Check if the given type is suitable for holding values generated by
		 * this group.
		 */
		@Override
		public void checkType (
			final A_Type argumentType,
			final int sectionNumber)
		throws SignatureException
		{
			// Always expect a tuple of solutions here.
			if (argumentType.isBottom())
			{
				// Method argument type should not be bottom.
				throwSignatureException(E_INCORRECT_ARGUMENT_TYPE);
			}

			if (!argumentType.isTupleType())
			{
				// The group produces a tuple.
				throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP);
			}

			final A_Type requiredRange = IntegerRangeTypeDescriptor.create(
				IntegerDescriptor.zero(),
				true,
				maximumCardinality == Integer.MAX_VALUE
					? InfinityDescriptor.positiveInfinity()
					: IntegerDescriptor.fromInt(maximumCardinality + 1),
				false);

			if (!argumentType.sizeRange().isSubtypeOf(requiredRange))
			{
				// The method's parameter should have a cardinality that's a
				// subtype of what the message name requires.
				throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP);
			}

			if (!needsDoubleWrapping())
			{
				// Expect a tuple of individual values.  No further checks are
				// needed.
			}
			else
			{
				// Expect a tuple of tuples of values, where the inner tuple
				// size ranges from the number of arguments left of the dagger
				// up to that plus the number of arguments right of the dagger.
				assert argumentType.isTupleType();
				final int argsBeforeDagger = beforeDagger.arguments.size();
				final int argsAfterDagger = afterDagger.arguments.size();
				final A_Number expectedLower = IntegerDescriptor.fromInt(
					argsBeforeDagger);
				final A_Number expectedUpper = IntegerDescriptor.fromInt(
					argsBeforeDagger + argsAfterDagger);
				final A_Tuple typeTuple = argumentType.typeTuple();
				final int limit = typeTuple.tupleSize() + 1;
				for (int i = 1; i <= limit; i++)
				{
					final A_Type solutionType = argumentType.typeAtIndex(i);
					if (solutionType.isBottom())
					{
						// It was the empty tuple type.
						break;
					}
					if (!solutionType.isTupleType())
					{
						// The argument should be a tuple of tuples.
						throwSignatureException(E_INCORRECT_TYPE_FOR_GROUP);
					}
					// Check that the solution that will reside at the current
					// index accepts either a full group or a group up to the
					// dagger.
					final A_Type solutionTypeSizes = solutionType.sizeRange();
					final A_Number lower = solutionTypeSizes.lowerBound();
					final A_Number upper = solutionTypeSizes.upperBound();
					if (!lower.equals(expectedLower)
						|| !upper.equals(expectedUpper))
					{
						// This complex group should have elements whose types
						// are tuples restricted to have sizes ranging from the
						// number of argument subexpressions before the double
						// dagger up to the total number of argument
						// subexpressions in this group.
						throwSignatureException(
							E_INCORRECT_TYPE_FOR_COMPLEX_GROUP);
					}
					int j = 1;
					for (final Expression e : beforeDagger.arguments)
					{
						e.checkType(
							solutionType.typeAtIndex(j),
							sectionNumber);
						j++;
					}
					for (final Expression e : afterDagger.arguments)
					{
						e.checkType(
							solutionType.typeAtIndex(j),
							sectionNumber);
						j++;
					}
				}
			}
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			final A_Type subexpressionsTupleType;
			if (phraseType.isSubtypeOf(LIST_NODE.mostGeneralType()))
			{
				subexpressionsTupleType = phraseType.subexpressionsTupleType();
			}
			else
			{
				subexpressionsTupleType =
					TupleTypeDescriptor.mappingElementTypes(
						phraseType.expressionType(),
						new Transformer1<A_Type, A_Type>()
						{
							@Override
							public A_Type value (
								@Nullable final A_Type yieldType)
							{
								return PARSE_NODE.create(yieldType);
							}
						});
			}
			final A_Type sizeRange = subexpressionsTupleType.sizeRange();
			final A_Number minInteger = sizeRange.lowerBound();
			final int minSize = minInteger.isInt()
				? minInteger.extractInt() : Integer.MAX_VALUE;
			final A_Number maxInteger = sizeRange.upperBound();
			final int maxSize = maxInteger.isInt()
				? maxInteger.extractInt() : Integer.MAX_VALUE;
			final int endOfVariation =
				maxSize == 0
					? 0
					: subexpressionsTupleType.typeTuple().tupleSize() + 1;
			generator.emit(this, NEW_LIST);
			if (maxSize == 0)
			{
				// The signature requires an empty list, so that's what we get
				// (emitted above).
			}
			else if (!needsDoubleWrapping())
			{
				/* Special case -- one argument case produces a list of
				 * expressions rather than a list of fixed-length lists of
				 * expressions.  The case of maxSize = 0 was already handled.
				 * The generated instructions should look like:
				 *
				 * push empty list of solutions (emitted above)
				 * branch to $skip (if minSize = 0)
				 * push current parse position on the mark stack
				 * A repetition for each N=1..endOfVariation-1:
				 *     ...Stuff before dagger, appending sole argument.
				 *     branch to $exit (if N ≥ minSize)
				 *     ...Stuff after dagger, nothing if dagger is omitted.
				 *     ...Must not contain an argument or subgroup.
				 *     check progress and update saved position, or abort.
				 * And a final loop:
				 *     $loopStart:
				 *     ...Stuff before dagger, appending sole argument.
				 *     if (endOfVariation < maxSize) then:
				 *         EITHER branch to $exit (if endOfVariation ≥ minSize)
				 *         OR to $exitCheckMin (if endOfVariation < minSize)
				 *         check that the size is still < maxSize.
				 *         ...Stuff after dagger, nothing if dagger is omitted.
				 *         ...Must not contain an argument or subgroup.
				 *         check progress and update saved position, or abort.
				 *         jump to $loopStart.
				 *         if (endOfVariation < minSize) then:
				 *             $exitCheckMin:
				 *             check at least minSize.
				 * $exit:
				 * check progress and update saved position, or abort.
				 * discard the saved position from the mark stack.
				 * $skip:
				 */
				generator.partialListsCount++;
				final Label $skip = new Label();
				final Label $exit = new Label();
				final Label $exitCheckMin = new Label();
				final Label $loopStart = new Label();
				assert beforeDagger.arguments.size() == 1;
				assert afterDagger.arguments.size() == 0;
				if (minSize == 0)
				{
					// If size zero is valid, go to the special $skip label that
					// skips the progress check.  Simply fall through to it if
					// the maxSize is zero (handled above).
					assert maxSize > 0;
					generator.emit(this, BRANCH, $skip);
				}
				generator.emit(this, SAVE_PARSE_POSITION);
				for (int index = 1; index < endOfVariation; index++)
				{
					final A_Type innerPhraseType =
						subexpressionsTupleType.typeAtIndex(index);
					final A_Type singularListType =
						ListNodeTypeDescriptor.createListNodeType(
							LIST_NODE,
							TupleTypeDescriptor.forTypes(
								innerPhraseType.expressionType()),
							TupleTypeDescriptor.forTypes(innerPhraseType));
					beforeDagger.emitWithoutInitialNewListPushOn(
						generator, singularListType);
					if (index >= minSize)
					{
						generator.emit(this, BRANCH, $exit);
					}
					afterDagger.emitWithoutInitialNewListPushOn(
						generator, ListNodeTypeDescriptor.empty());
					generator.emit(this, ENSURE_PARSE_PROGRESS);
				}
				// The homogenous part of the tuple, one or more iterations.
				generator.emit($loopStart);
				final A_Type innerPhraseType =
					subexpressionsTupleType.defaultType();
				final A_Type singularListType =
					ListNodeTypeDescriptor.createListNodeType(
						LIST_NODE,
						TupleTypeDescriptor.forTypes(
							innerPhraseType.expressionType()),
						TupleTypeDescriptor.forTypes(innerPhraseType));
				beforeDagger.emitWithoutInitialNewListPushOn(
					generator, singularListType);
				if (endOfVariation < maxSize)
				{
					generator.emit(
						this,
						BRANCH,
						endOfVariation >= minSize ? $exit : $exitCheckMin);
					if (maxInteger.isFinite())
					{
						generator.emit(this, CHECK_AT_MOST, maxSize - 1);
					}
					afterDagger.emitWithoutInitialNewListPushOn(
						generator,
						ListNodeTypeDescriptor.empty());
					generator.emit(this, ENSURE_PARSE_PROGRESS);
					generator.emit(this, JUMP, $loopStart);
					if (endOfVariation < minSize)
					{
						generator.emit($exitCheckMin);
						generator.emit(this, CHECK_AT_LEAST, minSize);
					}
				}
				generator.emit($exit);
				generator.emit(this, ENSURE_PARSE_PROGRESS);
				generator.emit(this, DISCARD_SAVED_PARSE_POSITION);
				generator.emit($skip);
				generator.partialListsCount--;
			}
			else
			{
				/* General case -- the individual arguments need to be wrapped
				 * with "append" as for the special case above, but the start
				 * of each loop has to push an empty tuple, the dagger has to
				 * branch to a special $exit that closes the last (partial)
				 * group, and the backward jump should be preceded by an append
				 * to capture a solution.  Note that the cae of maxSize = 0 was
				 * already handled.  Here's the code:
				 *
				 * push empty list (the list of solutions, emitted above)
				 * branch to $skip (if minSize = 0)
				 * push current parse position on the mark stack
				 * A repetition for each N=1..endOfVariation-1:
				 *     push empty list (a compound solution)
				 *     ...Stuff before dagger, where arguments and subgroups are
				 *     ...followed by "append" instructions.
				 *     permute left-half arguments tuple if needed
				 *     branch to $exit (if N ≥ minSize)
				 *     ...Stuff after dagger, nothing if dagger is omitted.
				 *     ...Must follow each argument or subgroup with "append"
				 *     ...instruction.
				 *     permute *only* right half of solution tuple if needed
				 *     append  (add complete solution)
				 *     check progress and update saved position, or abort.
				 * And a final loop:
				 *     $loopStart:
				 *     push empty list (a compound solution)
				 *     ...Stuff before dagger, where arguments and subgroups are
				 *     ...followed by "append" instructions.
				 *     permute left-half arguments tuple if needed
				 *     if (endOfVariation < maxSize) then:
				 *         EITHER branch to $exit (if endOfVariation ≥ minSize)
				 *         OR to $exitCheckMin (if endOfVariation < minSize)
				 *         check that the size is still < maxSize.
				 *         ...Stuff after dagger, nothing if dagger is omitted.
				 *         ...Must follow each arg or subgroup with "append"
				 *         ...instruction.
				 *         permute *only* right half of solution tuple if needed
				 *         append  (add complete solution)
				 *         check progress and update saved position, or abort.
				 *         jump to $loopStart.
				 *         if (endOfVariation < minSize) then:
				 *             $exitCheckMin:
				 *             append.
				 *             check at least minSize.
				 *             jump $mergedExit.
				 * $exit:
				 * append  (add partial solution up to dagger)
				 * $mergedExit:
				 * check progress and update saved position, or abort.
				 * discard the saved position from mark stack.
				 * $skip:
				 */
				final Label $skip = new Label();
				final Label $exit = new Label();
				final Label $exitCheckMin = new Label();
				final Label $mergedExit = new Label();
				final Label $loopStart = new Label();
				if (minSize == 0)
				{
					// If size zero is valid, go to the special $skip label that
					// skips the progress check.  Simply fall through to it if
					// the maxSize is zero (handled above).
					assert maxSize > 0;
					generator.emit(this, BRANCH, $skip);
				}
				generator.emit(this, SAVE_PARSE_POSITION);
				for (int index = 1; index < endOfVariation; index++)
				{
					generator.emit(this, NEW_LIST);
					final A_Type sublistPhraseType =
						subexpressionsTupleType.typeAtIndex(index);
					emitDoubleWrappedBeforeDaggerOn(
						generator, sublistPhraseType);
					if (index >= minSize)
					{
						generator.emit(this, BRANCH, $exit);
					}
					emitDoubleWrappedAfterDaggerOn(
						generator, sublistPhraseType);
					generator.emit(this, APPEND_ARGUMENT);
					generator.emit(this, ENSURE_PARSE_PROGRESS);
				}
				// The homogenous part of the tuple, one or more iterations.
				generator.emit($loopStart);
				generator.emit(this, NEW_LIST);
				final A_Type sublistPhraseType =
					subexpressionsTupleType.typeAtIndex(endOfVariation);
				emitDoubleWrappedBeforeDaggerOn(
					generator, sublistPhraseType);
				if (endOfVariation < maxSize)
				{
					generator.emit(
						this,
						BRANCH,
						endOfVariation >= minSize ? $exit : $exitCheckMin);
					if (maxInteger.isFinite())
					{
						generator.emit(this, CHECK_AT_MOST, maxSize - 1);
					}
					emitDoubleWrappedAfterDaggerOn(
						generator, sublistPhraseType);
					generator.emit(this, APPEND_ARGUMENT);
					generator.emit(this, ENSURE_PARSE_PROGRESS);
					generator.emit(this, JUMP, $loopStart);
					if (endOfVariation < minSize)
					{
						generator.emit($exitCheckMin);
						generator.emit(this, APPEND_ARGUMENT);
						generator.emit(this, CHECK_AT_LEAST, minSize);
						generator.emit(this, JUMP, $mergedExit);
					}
				}
				generator.emit($exit);
				generator.emit(this, APPEND_ARGUMENT);
				generator.emit($mergedExit);
				generator.emit(this, ENSURE_PARSE_PROGRESS);
				generator.emit(this, DISCARD_SAVED_PARSE_POSITION);
				generator.emit($skip);
			}
		}

		/**
		 * Emit instructions to parse one occurrence of the portion of this
		 * group before the double-dagger.  Append each argument or subgroup.
		 * Permute this left-half list as needed.
		 *
		 * @param generator
		 *        Where to generate parsing instructions.
		 * @param phraseType
		 *        The phrase type of the particular repetition of this group
		 *        whose before-dagger sequence is to be parsed.
		 */
		private void emitDoubleWrappedBeforeDaggerOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			final A_Type subexpressionsTupleType;
			if (phraseType.isSubtypeOf(LIST_NODE.mostGeneralType()))
			{
				subexpressionsTupleType = phraseType.subexpressionsTupleType();
			}
			else
			{
				subexpressionsTupleType =
					TupleTypeDescriptor.mappingElementTypes(
						phraseType.expressionType(),
						new Transformer1<A_Type, A_Type>()
						{
							@Override
							public A_Type value (
								@Nullable final A_Type yieldType)
							{
								return PARSE_NODE.create(yieldType);
							}
						});
			}
			int expressionCounter = 0;
			generator.partialListsCount += 2;
			for (final Expression expression : beforeDagger.expressions)
			{
				final boolean isArgOrGroup = expression.isArgumentOrGroup();
				expression.emitOn(
					generator,
					isArgOrGroup
						? subexpressionsTupleType.typeAtIndex(
							++expressionCounter)
						: ListNodeTypeDescriptor.empty());
				if (isArgOrGroup)
				{
					// Append to the current solution list.
					generator.emit(this, APPEND_ARGUMENT);
				}
			}
			generator.partialListsCount -= 2;
			if (beforeDagger.argumentsAreReordered == Boolean.TRUE)
			{
				// Permute the list on top of stack.
				final A_Tuple permutationTuple =
					TupleDescriptor.fromIntegerList(
						beforeDagger.permutedArguments);
				final int permutationIndex =
					indexForPermutation(permutationTuple);
				generator.emit(this, PERMUTE_LIST, permutationIndex);
			}
			assert expressionCounter == beforeDagger.arguments.size();
		}

		/**
		 * Emit instructions to parse one occurrence of the portion of this
		 * group after the double-dagger.  Append each argument or subgroup.
		 * Permute just the right half of this list as needed.
		 *
		 * @param generator
		 *        Where to generate parsing instructions.
		 * @param phraseType
		 *        The phrase type of the particular repetition of this group
		 *        whose after-dagger sequence is to be parsed.
		 */
		private void emitDoubleWrappedAfterDaggerOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			final A_Type subexpressionsTupleType;
			if (phraseType.isSubtypeOf(LIST_NODE.mostGeneralType()))
			{
				subexpressionsTupleType = phraseType.subexpressionsTupleType();
			}
			else
			{
				subexpressionsTupleType =
					TupleTypeDescriptor.mappingElementTypes(
						phraseType.expressionType(),
						new Transformer1<A_Type, A_Type>()
						{
							@Override
							public A_Type value (
								@Nullable final A_Type yieldType)
							{
								return PARSE_NODE.create(yieldType);
							}
						});
			}
			int expressionCounter = beforeDagger.arguments.size();
			generator.partialListsCount += 2;
			for (final Expression expression : afterDagger.expressions)
			{
				final boolean isArgOrGroup = expression.isArgumentOrGroup();
				expression.emitOn(
					generator,
					isArgOrGroup
						? subexpressionsTupleType.typeAtIndex(
							++expressionCounter)
						: ListNodeTypeDescriptor.empty());
				if (isArgOrGroup)
				{
					// Append to the current solution list.
					generator.emit(this, APPEND_ARGUMENT);
				}
			}
			generator.partialListsCount -= 2;
			if (afterDagger.argumentsAreReordered == Boolean.TRUE)
			{
				// Permute just the right portion of the list on top of
				// stack.  The left portion was already adjusted in case it
				// was the last iteration and didn't have a right side.
				final int leftArgCount = beforeDagger.arguments.size();
				final int rightArgCount = afterDagger.arguments.size();
				final int adjustedPermutationSize =
					leftArgCount + rightArgCount;
				final ArrayList<Integer> adjustedPermutationList =
					new ArrayList<>(adjustedPermutationSize);
				for (int i = 1; i <= leftArgCount; i++)
				{
					// The left portion is the identity permutation, since
					// the actual left permutation was already applied.
					adjustedPermutationList.add(i);
				}
				for (int i = 0; i < rightArgCount; i++)
				{
					// Adjust the right permutation indices by the size of
					// the left part.
					adjustedPermutationList.add(
						afterDagger.arguments.get(i).explicitOrdinal()
							+ leftArgCount);
				}
				final A_Tuple permutationTuple =
					TupleDescriptor.fromIntegerList(
						adjustedPermutationList);
				final int permutationIndex =
					indexForPermutation(permutationTuple);
				generator.emit(this, PERMUTE_LIST, permutationIndex);
			}
			// Ensure the tuple type was consumed up to its upperBound.
			assert subexpressionsTupleType.sizeRange().upperBound().equalsInt(
				expressionCounter);
		}

		@Override
		public String toString ()
		{
			final List<String> strings = new ArrayList<>();
			for (final Expression e : beforeDagger.expressions)
			{
				final StringBuilder builder = new StringBuilder();
				builder.append(e);
				if (e.canBeReordered() && e.explicitOrdinal() != -1)
				{
					builder.appendCodePoint(
						circledNumberCodePoints[e.explicitOrdinal()]);
				}
				strings.add(builder.toString());
			}
			if (hasDagger)
			{
				strings.add("‡");
				for (final Expression e : afterDagger.expressions)
				{
					strings.add(e.toString());
				}
			}

			final StringBuilder builder = new StringBuilder();
			builder.append("Group(");
			boolean first = true;
			for (final String s : strings)
			{
				if (!first)
				{
					builder.append(", ");
				}
				builder.append(s);
				first = false;
			}
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> argumentProvider,
			final StringBuilder builder,
			final int indent)
		{
			assert argumentProvider != null;
			final boolean needsDouble = needsDoubleWrapping();
			final A_Phrase groupArguments = argumentProvider.next();
			final Iterator<AvailObject> occurrenceProvider =
				groupArguments.expressionsTuple().iterator();
			while (occurrenceProvider.hasNext())
			{
				final AvailObject occurrence = occurrenceProvider.next();
				final Iterator<AvailObject> innerIterator;
				if (needsDouble)
				{
					// The occurrence is itself a list node containing the
					// parse nodes to fill in to this group's arguments and
					// subgroups.
					assert occurrence.isInstanceOfKind(
						LIST_NODE.mostGeneralType());
					innerIterator = occurrence.expressionsTuple().iterator();
				}
				else
				{
					// The argumentObject is a listNode of parse nodes.
					// Each parse node is for the single argument or subgroup
					// which is left of the double-dagger (and there are no
					// arguments or subgroups to the right).
					assert occurrence.isInstanceOfKind(
						EXPRESSION_NODE.mostGeneralType());
					final List<AvailObject> argumentNodes =
						Collections.singletonList(occurrence);
					innerIterator = argumentNodes.iterator();
				}
				printGroupOccurrence(
					innerIterator,
					builder,
					indent,
					occurrenceProvider.hasNext());
				assert !innerIterator.hasNext();
			}
		}

		/**
		 * Pretty-print this part of the message, using the provided iterator
		 * to supply arguments.  This prints a single occurrence of a repeated
		 * group.  The completeGroup flag indicates if the double-dagger and
		 * subsequent subexpressions should also be printed.
		 *
		 * @param argumentProvider
		 *        An iterator to provide parse nodes for this group occurrence's
		 *        arguments and subgroups.
		 * @param builder
		 *        The {@link StringBuilder} on which to print.
		 * @param indent
		 *        The indentation level.
		 * @param completeGroup
		 *        Whether to produce a complete group or just up to the
		 *        double-dagger. The last repetition of a subgroup uses false
		 *        for this flag.
		 */
		public void printGroupOccurrence (
			final Iterator<AvailObject> argumentProvider,
			final StringBuilder builder,
			final int indent,
			final boolean completeGroup)
		{
			builder.append("«");
			final List<Expression> expressionsToVisit;
			if (completeGroup && !afterDagger.expressions.isEmpty())
			{
				expressionsToVisit = new ArrayList<Expression>(
					beforeDagger.expressions.size()
					+ 1
					+ afterDagger.expressions.size());
				expressionsToVisit.addAll(beforeDagger.expressions);
				expressionsToVisit.add(null);  // Represents the dagger
				expressionsToVisit.addAll(afterDagger.expressions);
			}
			else
			{
				expressionsToVisit = beforeDagger.expressions;
			}
			boolean needsSpace = false;
			for (final Expression expr : expressionsToVisit)
			{
				if (expr == null)
				{
					// Place-holder for the double-dagger.
					builder.append("‡");
					needsSpace = false;
				}
				else
				{
					if (needsSpace && expr.shouldBeSeparatedOnLeft())
					{
						builder.append(" ");
					}
					final int oldLength = builder.length();
					expr.printWithArguments(
						argumentProvider,
						builder,
						indent);
					needsSpace = expr.shouldBeSeparatedOnRight()
						&& builder.length() != oldLength;
				}
			}
			assert !argumentProvider.hasNext();
			builder.append("»");
		}

		@Override
		boolean shouldBeSeparatedOnLeft ()
		{
			return false;
		}

		@Override
		boolean shouldBeSeparatedOnRight ()
		{
			return false;
		}
	}

	/**
	 * A {@code Counter} is a special subgroup (i.e., not a root group)
	 * indicated by an {@linkplain StringDescriptor#octothorp() octothorp}
	 * following a {@linkplain Group group}. It may not contain {@linkplain
	 * Argument arguments} or subgroups, though it may contain a {@linkplain
	 * StringDescriptor#doubleDagger() double dagger}.
	 *
	 * <p>When a double dagger appears in a counter, the counter produces a
	 * {@linkplain IntegerRangeTypeDescriptor#wholeNumbers() whole number} that
	 * indicates the number of occurrences of the subexpression to the left of
	 * the double dagger. The message "«very‡,»#good" accepts a single
	 * argument: the count of occurrences of "very".</p>
	 *
	 * <p>When no double dagger appears in a counter, then the counter produces
	 * a whole number that indicates the number of occurrences of the entire
	 * group. The message "«very»#good" accepts a single argument: the count of
	 * occurrences of "very".</p>
	 */
	final static class Counter
	extends Expression
	{
		/** The {@linkplain Group group} whose occurrences should be counted. */
		final Group group;

		/**
		 * Construct a new {@link Counter}.
		 *
		 * @param group
		 *        The {@linkplain Group group} whose occurrences should be
		 *        counted.
		 */
		Counter (final Group group)
		{
			this.group = group;
			explicitOrdinal(group.explicitOrdinal());
			group.explicitOrdinal(-1);
		}

		@Override
		boolean isArgumentOrGroup ()
		{
			return true;
		}

		@Override
		int underscoreCount ()
		{
			assert group.underscoreCount() == 0;
			return 0;
		}

		@Override
		boolean isLowerCase ()
		{
			return group.isLowerCase();
		}

		@Override
		void extractSectionCheckpointsInto (
			final List<SectionCheckpoint> sectionCheckpoints)
		{
			group.extractSectionCheckpointsInto(sectionCheckpoints);
		}

		@Override
		public void checkType (
			final A_Type argumentType,
			final int sectionNumber)
		throws SignatureException
		{
			if (!argumentType.isSubtypeOf(
				IntegerRangeTypeDescriptor.wholeNumbers()))
			{
				// The declared type for the subexpression must be a subtype of
				// whole number.
				throwSignatureException(E_INCORRECT_TYPE_FOR_COUNTING_GROUP);
			}
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			/* push current parse position
			 * push empty list
			 * branch to @loopSkip
			 * @loopStart:
			 * push empty list (represents group presence)
			 * ...Stuff before dagger.
			 * append (add solution)
			 * branch to @loopExit (even if no dagger)
			 * ...Stuff after dagger, nothing if dagger is omitted.  Must
			 * ...follow argument or subgroup with "append" instruction.
			 * check progress and update saved position, or abort.
			 * jump to @loopStart
			 * @loopExit:
			 * check progress and update saved position, or abort.
			 * @loopSkip:
			 * under-pop parse position (remove 2nd from top of stack)
			 */
			final Label $loopStart = new Label();
			final Label $loopExit = new Label();
			final Label $loopSkip = new Label();
			generator.emit(this, SAVE_PARSE_POSITION);
			generator.emit(this, NEW_LIST);
			generator.emit(this, BRANCH, $loopSkip);
			generator.emit($loopStart);
			generator.emit(this, NEW_LIST);
			// Note that even though the Counter cannot contain anything that
			// would push data, the Counter region must not contain a section
			// checkpoint.  There's no point, since the iteration would not be
			// passed, in case it's confusing (number completed versus number
			// started).
			final int oldPartialListsCount = generator.partialListsCount;
			for (final Expression expression : group.beforeDagger.expressions)
			{
				assert !expression.isArgumentOrGroup();
				generator.partialListsCount = Integer.MIN_VALUE;
				expression.emitOn(
					generator,
					null); //TODO MvG - FIGURE OUT the types.
			}
			generator.emit(this, APPEND_ARGUMENT);
			generator.emit(this, BRANCH, $loopExit);
			for (final Expression expression : group.afterDagger.expressions)
			{
				assert !expression.isArgumentOrGroup();
				expression.emitOn(
					generator,
					null); //TODO MvG - FIGURE OUT the types.
			}
			generator.partialListsCount = oldPartialListsCount;
			generator.emit(this, ENSURE_PARSE_PROGRESS);
			generator.emit(this, JUMP, $loopStart);
			generator.emit($loopExit);
			generator.emit(this, ENSURE_PARSE_PROGRESS);
			generator.emit($loopSkip);
			generator.emit(this, DISCARD_SAVED_PARSE_POSITION);
			generator.emit(this, CONVERT, LIST_TO_SIZE.number());
		}

		@Override
		public String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			builder.append(group);
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> argumentProvider,
			final StringBuilder builder,
			final int indent)
		{
			assert argumentProvider != null;
			final A_Phrase countLiteral = argumentProvider.next();
			assert countLiteral.isInstanceOf(
				ParseNodeKind.LITERAL_NODE.mostGeneralType());
			final int count = countLiteral.token().literal().extractInt();
			for (int i = 1; i <= count; i++)
			{
				if (i > 1)
				{
					builder.append(" ");
				}
				group.printGroupOccurrence(
					Collections.<AvailObject>emptyIterator(),
					builder,
					indent,
					isArgumentOrGroup());
			}
			builder.append("#");
		}

		@Override
		boolean shouldBeSeparatedOnLeft ()
		{
			// This Counter node should be separated on the left if the
			// contained group should be.
			return group.shouldBeSeparatedOnLeft();
		}

		@Override
		boolean shouldBeSeparatedOnRight ()
		{
			// This Counter node should be separated on the right to emphasize
			// the trailing "#".
			return true;
		}
	}

	/**
	 * An {@code Optional} is a {@link Sequence} wrapped in guillemets («»), and
	 * followed by a question mark (?).  It may not contain {@link Argument}s or
	 * subgroups, and since it is not a group it may not contain a {@linkplain
	 * StringDescriptor#doubleDagger() double dagger} (‡).
	 *
	 * <p>At a call site, an optional produces a {@linkplain
	 * EnumerationTypeDescriptor#booleanObject() boolean} that indicates whether
	 * there was an occurrence of the group.  For example, the message
	 * "«very»?good" accepts a single argument: a boolean that is {@linkplain
	 * AtomDescriptor#trueObject() true} if the token "very" occurred and
	 * {@linkplain AtomDescriptor#falseObject() false} if it did not.</p>
	 */
	final static class Optional
	extends Expression
	{
		/** The optional {@link Sequence}. */
		final Sequence sequence;

		/**
		 * Construct a new {@link Optional}.
		 *
		 * @param sequence
		 *        The governed {@linkplain Sequence sequence}.
		 */
		Optional (final Sequence sequence)
		{
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
		public void checkType (
			final A_Type argumentType,
			final int sectionNumber)
		throws SignatureException
		{
			if (!argumentType.isSubtypeOf(
				EnumerationTypeDescriptor.booleanObject()))
			{
				// The declared type of the subexpression must be a subtype of
				// boolean.
				throwSignatureException(E_INCORRECT_TYPE_FOR_BOOLEAN_GROUP);
			}
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			/* branch to @absent
			 * push the current parse position on the mark stack
			 * ...Stuff before dagger (i.e., all expressions).
			 * check progress and update saved position or abort.
			 * discard the saved parse position from the mark stack.
			 * push literal true
			 * jump to @groupSkip
			 * @absent:
			 * push literal false
			 * @groupSkip:
			 */
			final Label $absent = new Label();
			final Label $after = new Label();
			generator.emit(this, BRANCH, $absent);
			generator.emit(this, SAVE_PARSE_POSITION);
			assert sequence.argumentsAreReordered != Boolean.TRUE;
			for (final Expression expression : sequence.expressions)
			{
				expression.emitOn(
					generator,
					null); //TODO MvG - FIGURE OUT the types.
			}
			generator.emit(this, ENSURE_PARSE_PROGRESS);
			generator.emit(this, DISCARD_SAVED_PARSE_POSITION);
			generator.emit(this, PUSH_TRUE);
			generator.emit(this, JUMP, $after);
			generator.emit($absent);
			generator.emit(this, PUSH_FALSE);
			generator.emit($after);
		}

		@Override
		public String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			builder.append(sequence);
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> argumentProvider,
			final StringBuilder builder,
			final int indent)
		{
			assert argumentProvider != null;
			final A_Phrase literal = argumentProvider.next();
			assert literal.isInstanceOf(
				ParseNodeKind.LITERAL_NODE.mostGeneralType());
			final boolean flag = literal.token().literal().extractBoolean();
			if (flag)
			{
				builder.append("«");
				sequence.printWithArguments(
					Collections.<AvailObject>emptyIterator(),
					builder,
					indent);
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
	}

	/**
	 * A {@code CompletelyOptional} is a special {@linkplain Expression
	 * expression} indicated by a {@linkplain
	 * StringDescriptor#doubleQuestionMark() double question mark} following a
	 * {@linkplain Simple simple} or {@linkplain Group simple group}. It may not
	 * contain {@linkplain Argument arguments} or non-simple subgroups and it
	 * may not contain a {@linkplain StringDescriptor#doubleDagger() double
	 * dagger}. The expression may appear zero or one times.
	 *
	 * <p>A completely optional does not produce any information. No facility is
	 * provided to determine whether there was an occurrence of the expression.
	 * The message "very??good" accepts no arguments, but may be parsed as
	 * either "very good" or "good".</p>
	 */
	final class CompletelyOptional
	extends Expression
	{
		/** The governed {@linkplain Expression expression}. */
		final Expression expression;

		/**
		 * Construct a new {@link Counter}.
		 *
		 * @param expression
		 *        The governed {@linkplain Expression expression}.
		 * @throws MalformedMessageException
		 *         If the inner expression has an {@link #explicitOrdinal()}.
		 */
		CompletelyOptional (final Expression expression)
			throws MalformedMessageException
		{
			this.expression = expression;
			if (expression.canBeReordered()
				&& expression.explicitOrdinal() != -1)
			{
				throwMalformedMessageException(
					E_INCONSISTENT_ARGUMENT_REORDERING,
					"Completely optional phrase should not have a circled "
					+ "number to indicate reordering");
			}
		}

		@Override
		int underscoreCount ()
		{
			assert expression.underscoreCount() == 0;
			return 0;
		}

		@Override
		boolean isLowerCase ()
		{
			return expression.isLowerCase();
		}

		@Override
		void extractSectionCheckpointsInto (
			final List<SectionCheckpoint> sectionCheckpoints)
		{
			expression.extractSectionCheckpointsInto(sectionCheckpoints);
		}

		@Override
		public void checkType (
			final A_Type argumentType,
			final int sectionNumber)
		throws SignatureException
		{
			assert false :
				"checkType() should not be called for CompletelyOptional" +
				" expressions";
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			/* branch to @expressionSkip.
			 * push current parse position on the mark stack.
			 * ...Simple or stuff before dagger (i.e., all expressions).
			 * check progress and update saved position, or abort.
			 * discard mark position
			 * @expressionSkip:
			 */
			final Label $expressionSkip = new Label();
			generator.emit(this, BRANCH, $expressionSkip);
			generator.emit(this, SAVE_PARSE_POSITION);
			final List<Expression> expressions;
			if (expression instanceof Simple)
			{
				expressions = Collections.singletonList(expression);
			}
			else
			{
				assert expression instanceof Group;
				final Group group = (Group) expression;
				assert group.afterDagger.expressions.isEmpty();
				assert group.underscoreCount() == 0;
				expressions = group.beforeDagger.expressions;
			}
			for (final Expression subexpression : expressions)
			{
				assert !subexpression.isArgumentOrGroup();
				// The partialListsCount stays the same, in case there's a
				// section checkpoint marker within this completely optional
				// region.  That's a reasonable way to indicate that a prefix
				// function should only run when the optional section actually
				// occurs.  Since no completely optional section can produce a
				// value (argument, counter, etc), there's no problem.
				subexpression.emitOn(
					generator,
					null); //TODO MvG - FIGURE OUT types
			}
			generator.emit(this, ENSURE_PARSE_PROGRESS);
			generator.emit(this, DISCARD_SAVED_PARSE_POSITION);
			generator.emit($expressionSkip);
		}

		@Override
		public String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			builder.append(expression);
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> argumentProvider,
			final StringBuilder builder,
			final int indent)
		{
			// Don't consume any real arguments.  In case the expression is
			// itself a group, synthesize a dummy argument for it, containing
			// just an entry for that group.  The entry should itself contain a
			// single empty list of arguments for an occurrence.  That is, there
			// is one argument-position worth of arguments in the iterator, and
			// it holds one occurrence of the group (to make it print once), and
			// since it's really a CompletelyOptional group, the occurrence has
			// no values within it.
			final A_Phrase emptyListNode = ListNodeDescriptor.empty();
			final A_Phrase oneEmptyListNode = ListNodeDescriptor.newExpressions(
				TupleDescriptor.from(emptyListNode));
			expression.printWithArguments(
				TupleDescriptor.from(oneEmptyListNode).iterator(),
				builder,
				indent);
			builder.append("⁇");
		}

		@Override
		boolean shouldBeSeparatedOnLeft ()
		{
			return expression.isGroup() || expression.shouldBeSeparatedOnLeft();
		}

		@Override
		boolean shouldBeSeparatedOnRight ()
		{
			// Emphasize the double question mark that will always be printed
			// by ensuring a space follows it.
			return true;
		}
	}

	/**
	 * {@code CaseInsensitive} is a special decorator {@linkplain Expression
	 * expression} that causes the decorated expression's keywords to generate
	 * {@linkplain ParsingOperation parse instructions} that cause case
	 * insensitive parsing. It is indicated by a trailing {@linkplain
	 * StringDescriptor#tilde() tilde} ("~").
	 */
	final static class CaseInsensitive
	extends Expression
	{
		/**
		 * The {@linkplain Expression expression} whose keywords should be
		 * matched case-insensitively.
		 */
		final Expression expression;

		/**
		 * Construct a new {@link CaseInsensitive}.
		 *
		 * @param expression
		 *        The {@linkplain Expression expression} whose keywords should
		 *        be matched case-insensitively.
		 */
		CaseInsensitive (final Expression expression)
		{
			this.expression = expression;
			if (expression.canBeReordered())
			{
				explicitOrdinal(expression.explicitOrdinal());
				expression.explicitOrdinal(-1);
			}
		}

		@Override
		boolean isArgumentOrGroup ()
		{
			return expression.isArgumentOrGroup();
		}

		@Override
		boolean isGroup ()
		{
			return expression.isGroup();
		}

		@Override
		int underscoreCount ()
		{
			return expression.underscoreCount();
		}

		@Override
		boolean isLowerCase ()
		{
			assert expression.isLowerCase();
			return true;
		}

		@Override
		void extractSectionCheckpointsInto (
			final List<SectionCheckpoint> sectionCheckpoints)
		{
			expression.extractSectionCheckpointsInto(sectionCheckpoints);
		}

		@Override
		public void checkType (
			final A_Type argumentType,
			final int sectionNumber)
		throws SignatureException
		{
			expression.checkType(argumentType, sectionNumber);
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			final boolean oldInsensitive = generator.caseInsensitive;
			generator.caseInsensitive = true;
			expression.emitOn(generator, phraseType);
			generator.caseInsensitive = oldInsensitive;
		}

		@Override
		public String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			builder.append(expression);
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> argumentProvider,
			final StringBuilder builder,
			final int indent)
		{
			expression.printWithArguments(
				argumentProvider,
				builder,
				indent);
			builder.append("~");
		}

		@Override
		boolean shouldBeSeparatedOnLeft ()
		{
			return expression.shouldBeSeparatedOnLeft();
		}

		@Override
		boolean shouldBeSeparatedOnRight ()
		{
			// Since we show the tilde (~) after the subexpression, and since
			// case insensitivity is most likely to apply to textual tokens, we
			// visually emphasize the tilde by ensuring a space follows it.
			return true;
		}
	}

	/**
	 * An {@code Alternation} is a special {@linkplain Expression expression}
	 * indicated by interleaved {@linkplain StringDescriptor#verticalBar()
	 * vertical bars} between {@linkplain Simple simples} and {@linkplain
	 * Group simple groups}. It may not contain {@linkplain Argument arguments}.
	 *
	 * <p>An alternation specifies several alternative parses but does not
	 * produce any information. No facility is provided to determine which
	 * alternative occurred during a parse. The message "a|an_" may be parsed as
	 * either "a_" or "an_".</p>
	 *
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	final static class Alternation
	extends Expression
	{
		/** The alternative {@linkplain Expression expressions}. */
		private final List<Expression> alternatives;

		/**
		 * Answer my {@link List} of {@linkplain #alternatives}.
		 *
		 * @return My alternative {@linkplain Expression expressions}.
		 */
		List<Expression> alternatives ()
		{
			return alternatives;
		}

		/**
		 * Construct a new {@link Alternation}.
		 *
		 * @param alternatives
		 *        The alternative {@linkplain Expression expressions}.
		 */
		Alternation (final List<Expression> alternatives)
		{
			this.alternatives = alternatives;
		}

		@Override
		int underscoreCount ()
		{
			return 0;
		}

		@Override
		boolean isLowerCase ()
		{
			for (final Expression expression : alternatives)
			{
				if (!expression.isLowerCase())
				{
					return false;
				}
			}
			return true;
		}

		@Override
		void extractSectionCheckpointsInto (
			final List<SectionCheckpoint> sectionCheckpoints)
		{
			for (final Expression alternative : alternatives)
			{
				alternative.extractSectionCheckpointsInto(sectionCheckpoints);
			}
		}

		@Override
		public void checkType (
			final A_Type argumentType,
			final int sectionNumber)
		throws SignatureException
		{
			assert false :
				"checkType() should not be called for Alternation expressions";
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			/* push current parse position on the mark stack
			 * branch to @branches[0]
			 * ...First alternative.
			 * jump to @branches[N-1] (the last branch label)
			 * @branches[0]:
			 * ...Repeat for each alternative, omitting the branch and jump for
			 * ...the last alternative.
			 * @branches[N-1]:
			 * check progress and update saved position, or abort.
			 * under-pop parse position (remove 2nd from top of stack)
			 */
			final Label $after = new Label();
			generator.emit(this, SAVE_PARSE_POSITION);
			for (int i = 0; i < alternatives.size(); i++)
			{
				// Generate a branch to the next alternative unless this is the
				// last alternative.
				final Label $nextAlternative = new Label();
				if (i < alternatives.size() - 1)
				{
					generator.emit(this, BRANCH, $nextAlternative);
				}
				// The partialListsCount stays the same, in case there's a
				// section checkpoint marker in one of the alternatives.  That's
				// a reasonable way to indicate that a prefix function should
				// only run when that alternative occurs.  Since no alternative
				// can produce a value (argument, counter, etc), there's no
				// problem.
				alternatives.get(i).emitOn(
					generator,
					null); //TODO MvG - FIGURE OUT types
				// Generate a jump to the last label unless this is the last
				// alternative.
				if (i < alternatives.size() - 1)
				{
					generator.emit(this, JUMP, $after);
				}
				generator.emit($nextAlternative);
			}
			generator.emit($after);
			generator.emit(this, ENSURE_PARSE_PROGRESS);
			generator.emit(this, DISCARD_SAVED_PARSE_POSITION);
		}

		@Override
		public String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			boolean first = true;
			for (final Expression expression : alternatives)
			{
				if (!first)
				{
					builder.append(',');
				}
				builder.append(expression);
				first = false;
			}
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> argumentProvider,
			final StringBuilder builder,
			final int indent)
		{
			boolean isFirst = true;
			for (final Expression alternative : alternatives)
			{
				if (!isFirst)
				{
					builder.append("|");
				}
				alternative.printWithArguments(
					null,
					builder,
					indent);
				isFirst = false;
			}
		}

		@Override
		boolean shouldBeSeparatedOnLeft ()
		{
			return alternatives.get(0).shouldBeSeparatedOnLeft();
		}

		@Override
		boolean shouldBeSeparatedOnRight ()
		{
			final Expression last = alternatives.get(alternatives.size() - 1);
			return last.shouldBeSeparatedOnRight();
		}
	}

	/**
	 * A {@code NumberedChoice} is a special subgroup (i.e., not a root group)
	 * indicated by an {@linkplain StringDescriptor#exclamationMark()
	 * exclamation mark} following a {@linkplain Group group}.  It may not
	 * contain {@linkplain Argument arguments} or subgroups and it may not
	 * contain a {@linkplain StringDescriptor#doubleDagger() double dagger}.
	 * The group contains an {@link Alternation}, and parsing the group causes
	 * exactly one of the alternatives to be parsed.  The 1-based index of the
	 * alternative is produced as a literal constant argument.
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
	final static class NumberedChoice
	extends Expression
	{
		/**
		 * The alternation expression, exactly one alternative of which must be
		 * chosen.
		 */
		final Alternation alternation;

		/**
		 * Construct a new {@link NumberedChoice}.
		 *
		 * @param alternation The enclosed {@link Alternation}.
		 */
		public NumberedChoice (final Alternation alternation)
		{
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
		public void checkType (
			final A_Type argumentType,
			final int sectionNumber)
		throws SignatureException
		{
			if (!argumentType.isSubtypeOf(
				IntegerRangeTypeDescriptor.inclusive(
					IntegerDescriptor.one(),
					IntegerDescriptor.fromInt(
						alternation.alternatives().size()))))
			{
				// The declared type of the subexpression must be a subtype of
				// [1..N] where N is the number of alternatives.
				throwSignatureException(E_INCORRECT_TYPE_FOR_NUMBERED_CHOICE);
			}
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
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
			final int numAlternatives = alternation.alternatives().size() - 1;
			final Label $exit = new Label();
			for (int index = 0; index <= numAlternatives; index++)
			{
				final Label $next = new Label();
				final boolean last = index == numAlternatives;
				if (!last)
				{
					generator.emit(this, BRANCH, $next);
				}
				final Expression alternative =
					alternation.alternatives().get(index);
				// If a section checkpoint occurs within a numbered choice, we
				// *do not* pass the choice number as an argument.  Therefore
				// nothing new has been pushed for us to clean up at this point.
				alternative.emitOn(
					generator,
					null); //TODO MvG - FIGURE OUT the types.
				generator.emit(this, PUSH_INTEGER_LITERAL, index + 1);
				if (!last)
				{
					generator.emit(this, JUMP, $exit);
					generator.emit($next);
				}
			}
			generator.emit($exit);
		}

		@Override
		public String toString ()
		{
			final StringBuilder builder = new StringBuilder();
			builder.append(getClass().getSimpleName());
			builder.append("(");
			builder.append(alternation);
			builder.append(")");
			return builder.toString();
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> argumentProvider,
			final StringBuilder builder,
			final int indent)
		{
			assert argumentProvider != null;
			final A_Phrase literal = argumentProvider.next();
			assert literal.isInstanceOf(
				ParseNodeKind.LITERAL_NODE.mostGeneralType());
			final int index = literal.token().literal().extractInt();
			builder.append('«');
			final Expression alternative =
				alternation.alternatives().get(index - 1);
			alternative.printWithArguments(
				Collections.<AvailObject>emptyIterator(),
				builder,
				indent);
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
	}

	/**
	 * An {@linkplain SectionCheckpoint} expression is an occurrence of the
	 * {@linkplain StringDescriptor#sectionSign() section sign} (§) in a message
	 * name.  It indicates a position at which to save the argument expressions
	 * for the message <em>up to this point</em>.  This value is captured in the
	 * {@link ParserState} for subsequent use by primitive macros that need to
	 * know an outer message send's initial argument expressions while parsing
	 * a subsequent argument expression of the same message.
	 *
	 * <p>In particular, the block definition macro has to capture its
	 * (optional) argument declarations before parsing the (optional) label,
	 * declaration, since the latter has to be created with a suitable
	 * continuation type that includes the argument types.</p>
	 */
	final class SectionCheckpoint
	extends Expression
	{
		/**
		 * The occurrence number of this SectionCheckpoint.  The section
		 * checkpoints are one-based and are numbered consecutively in the order
		 * in which they occur in the whole method name.
		 */
		final int subscript;

		/**
		 * Construct a SectionCheckpoint.
		 */
		SectionCheckpoint ()
		{
			this.subscript = ++numberOfSectionCheckpoints;
		}

		@Override
		void extractSectionCheckpointsInto (
			final List<SectionCheckpoint> sectionCheckpoints)
		{
			sectionCheckpoints.add(this);
		}

		@Override
		public void checkType (
			final A_Type argumentType,
			final int sectionNumber)
		throws SignatureException
		{
			assert false : "checkType() should not be called for " +
				"SectionCheckpoint expressions";
		}

		@Override
		void emitOn (
			final InstructionGenerator generator,
			final A_Type phraseType)
		{
			// Tidy up any partially-constructed groups and invoke the
			// appropriate prefix function.  Note that the partialListsCount is
			// constrained to always be at least one here.
			generator.emit(
				this,
				PREPARE_TO_RUN_PREFIX_FUNCTION,
				generator.partialListsCount);
			generator.emit(this, RUN_PREFIX_FUNCTION, subscript);
		}

		@Override
		public void printWithArguments (
			final @Nullable Iterator<AvailObject> arguments,
			final StringBuilder builder,
			final int indent)
		{
			builder.append("§");
		}

		@Override
		boolean shouldBeSeparatedOnLeft ()
		{
			// The section symbol should always stand out.
			return true;
		}

		@Override
		boolean shouldBeSeparatedOnRight ()
		{
			// The section symbol should always stand out.
			return true;
		}
	}

	/**
	 * Answer the index of the given permutation (tuple of integers), adding it
	 * to the global {@link #permutations} tuple if necessary.
	 *
	 * @param permutation
	 *        The permutation whose globally unique one-based index should be
	 *        determined.
	 * @return The permutation's one-based index.
	 */
	@InnerAccess static int indexForPermutation (final A_Tuple permutation)
	{
		int checkedLimit = 0;
		while (true)
		{
			final A_Tuple before = permutations.get();
			final int newLimit = before.tupleSize();
			for (int i = checkedLimit + 1; i <= newLimit; i++)
			{
				if (before.tupleAt(i).equals(permutation))
				{
					// Already exists.
					return i;
				}
			}
			final A_Tuple after =
				before.appendCanDestroy(permutation, false).makeShared();
			if (permutations.compareAndSet(before, after))
			{
				// Added it successfully.
				return after.tupleSize();
			}
			checkedLimit = newLimit;
		}
	}

	/**
	 * Answer the permutation having the given one-based index.  We need a read
	 * barrier here, but no lock, since the tuple of tuples is only appended to,
	 * ensuring all extant indices will always be valid.
	 *
	 * @param index The index of the permutation to retrieve.
	 * @return The permutation (a {@linkplain A_Tuple tuple} of Avail integers).
	 */
	public static A_Tuple permutationAtIndex (final int index)
	{
		return permutations.get().tupleAt(index);
	}

	/**
	 * Answer the index of the given type, adding it to the global {@link
	 * #typesToCheck} index if necessary.
	 *
	 * @param type
	 *        The type to add to the global index.
	 * @return The one-based index of the type, which can be retrieved later via
	 *         {@link #typeToCheck(int)}.
	 */
	@InnerAccess static int indexForType (final A_Type type)
	{
		while (true)
		{
			final Pair<A_Map, A_Tuple> oldPair = typesToCheck.get();
			A_Map map = oldPair.first();
			if (map.hasKey(type))
			{
				return map.mapAt(type).extractInt();
			}
			final int newIndex = map.mapSize() + 1;
			final A_Type sharedType = type.makeShared();
			map = map.mapAtPuttingCanDestroy(
				sharedType, IntegerDescriptor.fromInt(newIndex), false);
			map = map.makeShared();
			A_Tuple tuple = oldPair.second();
			tuple = tuple.appendCanDestroy(sharedType, false).makeShared();
			assert tuple.tupleSize() == newIndex;
			assert map.mapSize() == newIndex;
			final Pair<A_Map, A_Tuple> newPair = new Pair<>(map, tuple);
			if (typesToCheck.compareAndSet(oldPair, newPair))
			{
				return newIndex;
			}
		}
	}

	/**
	 * Answer the type having the given one-based index in the static {@link
	 * #typesToCheck} tuple.  We need a read barrier here, but no lock, since
	 * the tuple of types is only appended to, ensuring all extant indices will
	 * always be valid.  The corresponding map from types to index is not used
	 * during parsing, only during emission of {@link ParsingOperation}s.
	 *
	 * @param index The index of the type to retrieve.
	 * @return The {@link A_Type} at the given index.
	 */
	public static A_Type typeToCheck (final int index)
	{
		return typesToCheck.get().second().tupleAt(index);
	}

	/**
	 * Construct a new {@link MessageSplitter}, parsing the provided message
	 * into token strings and generating {@linkplain ParsingOperation parsing
	 * instructions} for parsing occurrences of this message.
	 *
	 * @param messageName
	 *        An Avail {@linkplain StringDescriptor string} specifying the
	 *        keywords and arguments of some message being defined.
	 * @throws MalformedMessageException
	 *         If the message name is malformed.
	 */
	public MessageSplitter (final A_String messageName)
		throws MalformedMessageException
	{
		this.messageName = messageName;
		messageName.makeImmutable();
		splitMessage();
		messagePartPosition = 1;
		rootSequence = parseSequence();
		if (!atEnd())
		{
			final A_String part = currentMessagePart();
			String encountered;
			if (part.equals(closeGuillemet()))
			{
				encountered =
					"close guillemet (») with no corresponding open guillemet";
			}
			else if (part.equals(doubleDagger()))
			{
				encountered = "double-dagger (‡) outside of a group";
			}
			else
			{
				encountered = "unexpected token " + part.toString();
			}
			throwMalformedMessageException(
				E_UNBALANCED_GUILLEMETS,
				"Encountered " + encountered);
		}
		messagePartsTuple = TupleDescriptor.fromList(messagePartsList).makeShared();
	}

	/**
	* Dump debugging information about this {@linkplain MessageSplitter} to
	* the specified {@linkplain StringBuilder builder}.
	*
	* @param builder
	*        The accumulator.
	*/
	public void dumpForDebug (final StringBuilder builder)
	{
		builder.append(messageName.asNativeString());
		builder.append("\n------\n");
		for (final A_String part : messagePartsList)
		{
			builder.append("\t");
			builder.append(part.asNativeString());
			builder.append("\n");
		}
	}

	/**
	 * Answer the {@link A_String} that is being decomposed as a message name.
	 *
	 * @return The name of the message being split.
	 */
	public A_String messageName ()
	{
		return messageName;
	}

	/**
	 * Answer a {@linkplain TupleDescriptor tuple} of Avail {@linkplain
	 * StringDescriptor strings} comprising this message.
	 *
	 * @return A tuple of strings.
	 */
	public A_Tuple messageParts ()
	{
		return messagePartsTuple;
	}

	/**
	 * Answer the list of one-based positions in the original string
	 * corresponding to the {@link #messagePartsList} that have been extracted.
	 *
	 * @return A {@link List} of {@link Integer}s.
	 */
	public final List<Integer> messagePartPositions ()
	{
		return messagePartPositions;
	}

	/**
	 * Answer a record of where each "underscore" occurred in the list of {@link
	 * #messagePartsList}.
	 *
	 * @return A {@link List} of one-based {@link Integer}s.
	 */
	public List<Integer> underscorePartNumbers ()
	{
		return underscorePartNumbers;
	}

	/**
	 * Answer whether parsing has reached the end of the message parts.
	 *
	 * @return True if the current position has consumed the last message part.
	 */
	public boolean atEnd ()
	{
		return messagePartPosition > messagePartsList.size();
	}

	/**
	 * Answer the current message part, or {@code null} if we are {@link
	 * #atEnd()}.  Do not consume the message part.
	 *
	 * @return The current message part or null.
	 */
	private @Nullable A_String currentMessagePartOrNull ()
	{
		return atEnd() ? null : currentMessagePart();
	}

	/**
	 * Answer the current message part.  We must not be {@link #atEnd()}.  Do
	 * not consume the message part.
	 *
	 * @return The current message part.
	 */
	private A_String currentMessagePart ()
	{
		assert !atEnd();
		return messagePartsList.get(messagePartPosition - 1);
	}

	/**
	 * Pretty-print a send of this message with given argument nodes.
	 *
	 * @param sendNode
	 *        The {@linkplain SendNodeDescriptor send node} that is being
	 *        printed.
	 * @param builder
	 *        A {@link StringBuilder} on which to pretty-print the send of my
	 *        message with the given arguments.
	 * @param indent
	 *        The current indentation level.
	 */
	public void printSendNodeOnIndent (
		final A_Phrase sendNode,
		final StringBuilder builder,
		final int indent)
	{
		builder.append("«");
		rootSequence.printWithArguments(
			sendNode.argumentsListNode().expressionsTuple().iterator(),
			builder,
			indent);
		builder.append("»");
	}

	/**
	 * Answer a {@linkplain TupleDescriptor tuple} of Avail {@linkplain
	 * IntegerDescriptor integers} describing how to parse this message.
	 * See {@link MessageSplitter} and {@link ParsingOperation} for an
	 * understanding of the parse instructions.
	 *
	 * @param phraseType
	 *        The phrase type (yielding a tuple type) for this signature.
	 * @return The tuple of integers encoding parse instructions for this
	 *         message and argument types.
	 */
	public A_Tuple instructionsTupleFor (final A_Type phraseType)
	{
		final InstructionGenerator generator = new InstructionGenerator();
		rootSequence.emitWithoutInitialNewListPushOn(generator, phraseType);
		return generator.instructionsTuple();
	}

	/**
	 * Answer a {@link List} of {@link Expression} objects that correlates with
	 * the {@linkplain #instructionsTupleFor(A_Type) parsing instructions}
	 * generated for the give message name and provided signature tuple type.
	 * Note that the list is 0-based and the tuple is 1-based.
	 *
	 * @param tupleType The tuple of phrase types for this signature.
	 * @return A list that indicates the origin Expression of each {@link
	 *         ParsingOperation}.
	 */
	public List<Expression> originExpressionsFor (final A_Type tupleType)
	{
		final InstructionGenerator generator = new InstructionGenerator();
		rootSequence.emitOn(generator, tupleType);
		return generator.expressionList();
	}

	/**
	 * Decompose the message name into its constituent token strings. These
	 * can be subsequently parsed to generate the actual parse instructions.
	 * Do not do any semantic analysis here, not even backquote processing –
	 * that would lead to confusion over whether an operator was supposed to be
	 * treated as a special token like open-guillemet («) rather than like a
	 * backquote-escaped open-guillemet token.
	 *
	 * @throws MalformedMessageException If the signature is invalid.
	 */
	private void splitMessage () throws MalformedMessageException
	{
		if (messageName.tupleSize() == 0)
		{
			return;
		}
		int position = 1;
		while (position <= messageName.tupleSize())
		{
			final char ch = (char) messageName.tupleAt(position).codePoint();
			if (ch == ' ')
			{
				if (messagePartsList.size() == 0
					|| isCharacterAnUnderscoreOrSpaceOrOperator(
						(char) messageName.tupleAt(position - 1).codePoint()))
				{
					// Problem is before the space.  Stuff the rest of the input
					// in as a final token to make diagnostics look right.
					messagePartsList.add(
						(A_String)messageName.copyTupleFromToCanDestroy(
							position, messageName.tupleSize(), false));
					messagePartPositions.add(position);
					messagePartPosition = messagePartsList.size() - 1;
					throwMalformedMessageException(
						E_METHOD_NAME_IS_NOT_CANONICAL,
						"Expected alphanumeric character before space");
				}
				//  Skip the space.
				position++;
				if (position > messageName.tupleSize()
						|| isCharacterAnUnderscoreOrSpaceOrOperator(
							(char) messageName.tupleAt(position).codePoint()))
				{
					if ((char) messageName.tupleAt(position).codePoint() == '`'
						&& position != messageName.tupleSize()
						&& (char) messageName.tupleAt(position + 1).codePoint()
							== '_')
					{
						// This is legal; we want to be able to parse
						// expressions like "a _b".
					}
					else
					{
						// Problem is after the space.
						messagePartsList.add(
							(A_String)messageName.copyTupleFromToCanDestroy(
								position, messageName.tupleSize(), false));
						messagePartPositions.add(position);
						messagePartPosition = messagePartsList.size();
						throwMalformedMessageException(
							E_METHOD_NAME_IS_NOT_CANONICAL,
							"Expected alphanumeric character after space");
					}
				}
			}
			else if (ch == '`')
			{
				// Despite what the method comment says, backquote needs to be
				// processed specially when followed by an underscore so that
				// identifiers containing (escaped) underscores can be treated
				// as a single token. Otherwise, they are unparseable.
				if (position == messageName.tupleSize()
					|| messageName.tupleAt(position + 1).codePoint() != '_')
				{
					// We didn't find an underscore, so we need to deal with the
					// backquote in the usual way.
					messagePartsList.add(
						(A_String)(messageName.copyTupleFromToCanDestroy(
							position,
							position,
							false)));
					messagePartPositions.add(position);
					position++;
				}
				else
				{
					boolean sawRegular = false;
					final int start = position;
					while (position <= messageName.tupleSize())
					{
						if (!isCharacterAnUnderscoreOrSpaceOrOperator(
							(char) messageName.tupleAt(position).codePoint()))
						{
							sawRegular = true;
							position++;
						}
						else if (
							messageName.tupleAt(position).codePoint() == '`'
							&& position + 1 <= messageName.tupleSize()
							&& messageName.tupleAt(position + 1).codePoint()
								== '_')
						{
							position += 2;
						}
						else
						{
							break;
						}
					}
					if (sawRegular)
					{
						// If we ever saw something other than `_ in the
						// sequence, then produce a single token that includes
						// the underscores (but not the backquotes).
						final StringBuilder builder = new StringBuilder();
						for (int i = start, limit = position - 1;
							i <= limit;
							i++)
						{
							final int cp = messageName.tupleAt(i).codePoint();
							if (cp != '`')
							{
								builder.appendCodePoint(cp);
							}
						}
						messagePartsList.add(
							StringDescriptor.from(builder.toString()));
						messagePartPositions.add(position);
					}
					else
					{
						// If we never saw a regular character, then produce a
						// token for each character.
						for (int i = start, limit = position - 1;
							i <= limit;
							i++)
						{
							messagePartsList.add((A_String)
								(messageName.copyTupleFromToCanDestroy(
									i,
									i,
									false)));
							messagePartPositions.add(i);
						}
					}
				}
			}
			else if (isCharacterAnUnderscoreOrSpaceOrOperator(ch))
			{
				messagePartsList.add(
					(A_String)(messageName.copyTupleFromToCanDestroy(
						position,
						position,
						false)));
				messagePartPositions.add(position);
				position++;
			}
			else
			{
				messagePartPositions.add(position);
				boolean sawIdentifierUnderscore = false;
				final int start = position;
				while (position <= messageName.tupleSize())
				{
					if (!isCharacterAnUnderscoreOrSpaceOrOperator(
						(char) messageName.tupleAt(position).codePoint()))
					{
						position++;
					}
					else if (messageName.tupleAt(position).codePoint() == '`'
						&& position + 1 <= messageName.tupleSize()
						&& messageName.tupleAt(position + 1).codePoint() == '_')
					{
						sawIdentifierUnderscore = true;
						position += 2;
					}
					else
					{
						break;
					}
				}
				if (sawIdentifierUnderscore)
				{
					final StringBuilder builder = new StringBuilder();
					for (int i = start, limit = position - 1; i <= limit; i++)
					{
						final int cp = messageName.tupleAt(i).codePoint();
						if (cp != '`')
						{
							builder.appendCodePoint(cp);
						}
					}
					messagePartsList.add(StringDescriptor.from(builder.toString()));
				}
				else
				{
					messagePartsList.add(
						(A_String)messageName.copyTupleFromToCanDestroy(
							start,
							position - 1,
							false));
				}
			}
		}
	}

	/**
	 * Create a {@linkplain Group group} from the series of tokens describing
	 * it. This is also used to construct the outermost sequence of {@linkplain
	 * Expression expressions}.  Expect the {@linkplain #messagePartPosition} to
	 * point (via a one-based offset) to the first token of the sequence, or
	 * just past the end if the sequence is empty. Leave the {@code
	 * messagePartPosition} pointing just past the last token of the group.
	 *
	 * <p>Stop parsing the sequence when we reach the end of the tokens, a close
	 * guillemet (»), or a double-dagger (‡).</p>
	 *
	 * @return A {@link Sequence} expression parsed from the {@link
	 *         #messagePartsList}.
	 * @throws MalformedMessageException If the method name is malformed.
	 */
	Sequence parseSequence ()
		throws MalformedMessageException
	{
		List<Expression> alternatives = new ArrayList<Expression>();
		boolean justParsedVerticalBar = false;
		final Sequence sequence = new Sequence();
		while (true)
		{
			assert !justParsedVerticalBar || !alternatives.isEmpty();
			A_String token = atEnd() ? null : currentMessagePart();
			if (token == null)
			{
				if (justParsedVerticalBar)
				{
					throwMalformedMessageException(
						E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
						"Expecting another token or simple group after the "
							+ "vertical bar (|)");
				}
				sequence.checkForConsistentOrdinals();
				return sequence;
			}
			if (token.equals(closeGuillemet()) || token.equals(doubleDagger()))
			{
				if (justParsedVerticalBar)
				{
					final String problem = token.equals(closeGuillemet())
						? "close guillemet (»)"
						: "double-dagger (‡)";
					throwMalformedMessageException(
						E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
						"Expecting another token or simple group after the "
						+ "vertical bar (|), not "
						+ problem);
				}
				sequence.checkForConsistentOrdinals();
				return sequence;
			}
			if (token.equals(underscore()))
			{
				// Capture the one-based index.
				final int argStart = messagePartPosition;
				if (alternatives.size() > 0)
				{
					// Alternations may not contain arguments.
					throwMalformedMessageException(
						E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS,
						"Alternations must not contain arguments");
				}
				messagePartPosition++;
				Expression argument = null;
				@Nullable A_String nextToken = currentMessagePartOrNull();
				int ordinal = -1;
				if (nextToken != null)
				{
					// Just ate the underscore, so immediately after is where
					// we expect an optional circled number to indicate argument
					// reordering.
					final int codePoint = nextToken.tupleAt(1).codePoint();
					if (circledNumbersMap.containsKey(codePoint))
					{
						// In theory we could allow messages to go past ㊿ by
						// allowing a sequence of circled single digits (⓪-⑨)
						// that doesn't start with ⓪.  DEFINITELY not worth the
						// bother for now (2014.12.24).
						ordinal = circledNumbersMap.get(codePoint);
						messagePartPosition++;
						nextToken = currentMessagePartOrNull();
					}
				}
				if (nextToken != null)
				{
					if (nextToken.equals(singleDagger()))
					{
						messagePartPosition++;
						argument = new ArgumentInModuleScope(argStart);
					}
					else if (nextToken.equals(upArrow()))
					{
						messagePartPosition++;
						argument = new VariableQuote(argStart);
					}
					else if (nextToken.equals(exclamationMark()))
					{
						messagePartPosition++;
						argument = new ArgumentForMacroOnly(argStart);
					}
				}
				// If the argument wasn't set already (because it wasn't
				// followed by a modifier), then set it here.
				if (argument == null)
				{
					argument = new Argument(argStart);
				}
				argument.explicitOrdinal(ordinal);
				sequence.addExpression(argument);
			}
			else if (token.equals(ellipsis()))
			{
				final int ellipsisStart = messagePartPosition;
				if (alternatives.size() > 0)
				{
					// Alternations may not contain arguments.
					throwMalformedMessageException(
						E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS,
						"Alternations must not contain arguments");
				}
				messagePartPosition++;
				@Nullable
				final A_String nextToken = currentMessagePartOrNull();
				if (nextToken != null && nextToken.equals(exclamationMark()))
				{
					sequence.addExpression(new RawTokenArgument(ellipsisStart));
					messagePartPosition++;
				}
				else if (nextToken != null && nextToken.equals(octothorp()))
				{
					sequence.addExpression(
						new RawWholeNumberLiteralTokenArgument(ellipsisStart));
					messagePartPosition++;
				}
				else if (nextToken != null && nextToken.equals(dollarSign()))
				{
					sequence.addExpression(
						new RawStringLiteralTokenArgument(ellipsisStart));
					messagePartPosition++;
				}
				else
				{
					sequence.addExpression(
						new RawKeywordTokenArgument(ellipsisStart));
				}
			}
			else if (token.equals(octothorp()))
			{
				throwMalformedMessageException(
					E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS,
					"An octothorp (#) may only follow a simple group («») "
					+ "or an ellipsis (…)");
			}
			else if (token.equals(dollarSign()))
			{
				throwMalformedMessageException(
					E_DOLLAR_SIGN_MUST_FOLLOW_AN_ELLIPSIS,
					"A dollar sign ($) may only follow an ellipsis(…)");
			}
			else if (token.equals(questionMark()))
			{
				throwMalformedMessageException(
					E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP,
					"A question mark (?) may only follow a simple group "
					+ "(optional) or a group with no double-dagger (‡)");
			}
			else if (token.equals(tilde()))
			{
				throwMalformedMessageException(
					E_TILDE_MUST_NOT_FOLLOW_ARGUMENT,
					"A tilde (~) must not follow an argument");
			}
			else if (token.equals(verticalBar()))
			{
				throwMalformedMessageException(
					E_VERTICAL_BAR_MUST_SEPARATE_TOKENS_OR_SIMPLE_GROUPS,
					"A vertical bar (|) may only separate tokens or simple "
					+ "groups");
			}
			else if (token.equals(exclamationMark()))
			{
				throwMalformedMessageException(
					E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP,
					"An exclamation mark (!) may only follow an alternation "
					+ "group (or follow an underscore for macros)");
			}
			else if (token.equals(upArrow()))
			{
				throwMalformedMessageException(
					E_UP_ARROW_MUST_FOLLOW_ARGUMENT,
					"An up-arrow (↑) may only follow an argument");
			}
			else if (circledNumbersMap.containsKey(
				token.tupleAt(1).codePoint()))
			{
				throwMalformedMessageException(
					E_INCONSISTENT_ARGUMENT_REORDERING,
					"Unquoted circled numbers (⓪-㊿) may only follow an "
					+ "argument, an ellipsis, or an argument group");
			}
			else if (token.equals(openGuillemet()))
			{
				// Eat the open guillemet, parse a subgroup, eat the (mandatory)
				// close guillemet, and add the group.
				messagePartPosition++;
				final Group subgroup = parseGroup();
				justParsedVerticalBar = false;
				if (!atEnd())
				{
					token = currentMessagePart();
				}
				// Otherwise token stays an open guillemet, hence not a close...
				if (!token.equals(closeGuillemet()))
				{
					// Expected matching close guillemet.
					throwMalformedMessageException(
						E_UNBALANCED_GUILLEMETS,
						"Expected close guillemet (») to end group");
				}
				messagePartPosition++;
				// Just ate the close guillemet, so immediately after is where
				// we expect an optional circled number to indicate argument
				// reordering.
				if (!atEnd())
				{
					token = currentMessagePart();
					final int codePoint = token.tupleAt(1).codePoint();
					if (circledNumbersMap.containsKey(codePoint))
					{
						// In theory we could allow messages to go past ㊿ by
						// allowing a sequence of circled single digits (⓪-⑨)
						// that doesn't start with ⓪.  DEFINITELY not worth the
						// bother for now (2014.12.24).
						subgroup.explicitOrdinal(
							circledNumbersMap.get(codePoint));
						messagePartPosition++;
					}
				}
				// Try to parse a counter, optional, and/or case-insensitive.
				Expression subexpression = subgroup;
				if (!atEnd())
				{
					token = currentMessagePart();
					if (token.equals(octothorp()))
					{
						if (subgroup.underscoreCount() > 0)
						{
							// Counting group may not contain arguments.
							throwMalformedMessageException(
								E_OCTOTHORP_MUST_FOLLOW_A_SIMPLE_GROUP_OR_ELLIPSIS,
								"An octothorp (#) may only follow a simple "
								+ "group or an ellipsis (…)");
						}
						subexpression = new Counter(subgroup);
						messagePartPosition++;
					}
					else if (token.equals(questionMark()))
					{
						if (subgroup.hasDagger)
						{
							// A question mark after a group with underscores
							// means zero or one occurrence, so a double-dagger
							// would be pointless.
							throwMalformedMessageException(
								E_QUESTION_MARK_MUST_FOLLOW_A_SIMPLE_GROUP,
								"A question mark (?) may only follow a simple "
								+ "group (optional) or a group with arguments "
								+ "(0 or 1 occurrences), but not one with a "
								+ "double-dagger (‡), since that implies "
								+ "multiple occurrences to be separated");
						}
						if (subgroup.underscoreCount() > 0)
						{
							subgroup.maximumCardinality(1);
							subexpression = subgroup;
						}
						else
						{
							subexpression = new Optional(subgroup.beforeDagger);
						}
						messagePartPosition++;
					}
					else if (token.equals(doubleQuestionMark()))
					{
						if (subgroup.underscoreCount() > 0
							|| subgroup.hasDagger)
						{
							// Completely optional group may not contain
							// arguments or double daggers.
							throwMalformedMessageException(
								E_DOUBLE_QUESTION_MARK_MUST_FOLLOW_A_TOKEN_OR_SIMPLE_GROUP,
								"A double question mark (⁇) may only follow "
								+ "a token or simple group, not one with a "
								+ "double-dagger (‡) or arguments");
						}
						subexpression = new CompletelyOptional(subgroup);
						messagePartPosition++;
					}
					else if (token.equals(exclamationMark()))
					{
						if (subgroup.underscoreCount() > 0
							|| subgroup.hasDagger
							|| (subgroup.beforeDagger.expressions.size() != 1)
							|| !(subgroup.beforeDagger.expressions.get(0)
								instanceof Alternation))
						{
							// Numbered choice group may not contain
							// underscores.  The group must also consist of an
							// alternation.
							throwMalformedMessageException(
								E_EXCLAMATION_MARK_MUST_FOLLOW_AN_ALTERNATION_GROUP,
								"An exclamation mark (!) may only follow an "
								+ "alternation group or (for macros) an "
								+ "underscore");
						}
						final Expression alternation =
							subgroup.beforeDagger.expressions.get(0);
						subexpression =
							new NumberedChoice((Alternation)alternation);
						messagePartPosition++;
					}
				}
				if (!atEnd())
				{
					token = currentMessagePart();
					// Try to parse a case-insensitive modifier.
					if (token.equals(tilde()))
					{
						if (!subexpression.isLowerCase())
						{
							throwMalformedMessageException(
								E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
								"Tilde (~) may only occur after a lowercase "
								+ "token or a group of lowercase tokens");
						}
						subexpression = new CaseInsensitive(subexpression);
						messagePartPosition++;
					}
				}
				// Parse a vertical bar. If no vertical bar occurs, then either
				// complete an alternation already in progress (including this
				// most recent expression) or add the subexpression directly to
				// the group.
				if (atEnd() || !currentMessagePart().equals(verticalBar()))
				{
					if (alternatives.size() > 0)
					{
						alternatives.add(subexpression);
						subexpression = new Alternation(alternatives);
						alternatives = new ArrayList<Expression>();
					}
					sequence.addExpression(subexpression);
					justParsedVerticalBar = false;
				}
				else
				{
					if (subexpression.underscoreCount() > 0)
					{
						// Alternations may not contain arguments.
						throwMalformedMessageException(
							E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS,
							"Alternatives must not contain arguments");
					}
					alternatives.add(subexpression);
					messagePartPosition++;
					justParsedVerticalBar = true;
				}
			}
			else if (token.equals(sectionSign()))
			{
				if (alternatives.size() > 0)
				{
					throwMalformedMessageException(
						E_ALTERNATIVE_MUST_NOT_CONTAIN_ARGUMENTS,
						"Alternative must not contain a section "
						+ "checkpoint (§)");
				}
				assert !justParsedVerticalBar;
				sequence.addExpression(new SectionCheckpoint());
				messagePartPosition++;
			}
			else
			{
				// Parse a backquote.
				if (token.equals(backQuote()))
				{
					// Eat the backquote.
					justParsedVerticalBar = false;
					messagePartPosition++;
					if (atEnd())
					{
						// Expected operator character after backquote, not end.
						throwMalformedMessageException(
							E_EXPECTED_OPERATOR_AFTER_BACKQUOTE,
							"Backquote (`) must be followed by an operator "
							+ "character");
					}
					token = currentMessagePart();
					if (token.tupleSize() != 1
						|| !isCharacterAnUnderscoreOrSpaceOrOperator(
							(char)token.tupleAt(1).codePoint()))
					{
						// Expected operator character after backquote.
						throwMalformedMessageException(
							E_EXPECTED_OPERATOR_AFTER_BACKQUOTE,
							"Backquote (`) must be followed by an operator "
							+ "character");
					}
				}
				// Parse a regular keyword or operator.
				justParsedVerticalBar = false;
				Expression subexpression = new Simple(messagePartPosition);
				messagePartPosition++;
				// Parse a completely optional.
				if (!atEnd())
				{
					token = currentMessagePart();
					if (token.equals(doubleQuestionMark()))
					{
						subexpression = new CompletelyOptional(subexpression);
						messagePartPosition++;
					}
				}
				// Parse a case insensitive.
				if (!atEnd())
				{
					token = currentMessagePart();
					if (token.equals(tilde()))
					{
						if (!subexpression.isLowerCase())
						{
							throwMalformedMessageException(
								E_CASE_INSENSITIVE_EXPRESSION_CANONIZATION,
								"Tilde (~) may only occur after a lowercase "
								+ "token or a group of lowercase tokens");
						}
						subexpression = new CaseInsensitive(subexpression);
						messagePartPosition++;
					}
				}
				// Parse a vertical bar. If no vertical bar occurs, then either
				// complete an alternation already in progress (including this
				// most recent expression) or add the subexpression directly to
				// the group.
				if (atEnd() || !currentMessagePart().equals(verticalBar()))
				{
					if (alternatives.size() > 0)
					{
						alternatives.add(subexpression);
						subexpression = new Alternation(alternatives);
						alternatives = new ArrayList<Expression>();
					}
					sequence.addExpression(subexpression);
					justParsedVerticalBar = false;
				}
				else
				{
					alternatives.add(subexpression);
					messagePartPosition++;
					justParsedVerticalBar = true;
				}
			}
		}
	}

	/**
	 * Create a {@linkplain Group group} from the series of tokens describing
	 * it. This is also used to construct the outermost sequence of {@linkplain
	 * Expression expressions}, with the restriction that an occurrence of a
	 * {@linkplain StringDescriptor#doubleDagger() double dagger} in the
	 * outermost pseudo-group is an error. Expect the {@linkplain
	 * #messagePartPosition} to point (via a one-based offset) to the first
	 * token of the group, or just past the end if the group is empty. Leave the
	 * {@code messagePartPosition} pointing just past the last token of the
	 * group.
	 *
	 * <p>The caller is responsible for identifying and skipping an open
	 * guillemet prior to this group, and for consuming the close guillemet
	 * after parsing the group. The outermost caller is also responsible for
	 * ensuring the entire input was exactly consumed.</p>
	 *
	 * @return A {@link Group} expression parsed from the {@link #messagePartsList}.
	 * @throws MalformedMessageException If the method name is malformed.
	 */
	Group parseGroup ()
		throws MalformedMessageException
	{
		final Sequence beforeDagger = parseSequence();
		if (!atEnd() && currentMessagePart().equals(doubleDagger()))
		{
			final int daggerPosition = messagePartPosition;
			messagePartPosition++;
			final Sequence afterDagger = parseSequence();
			if (!atEnd() && currentMessagePart().equals(doubleDagger()))
			{
				// Two daggers were encountered in a group.
				throwMalformedMessageException(
					E_INCORRECT_USE_OF_DOUBLE_DAGGER,
					"A group must have at most one double-dagger (‡)");
			}
			return new Group(beforeDagger, daggerPosition, afterDagger);
		}
		return new Group(beforeDagger);
	}

	/**
	 * Return the number of arguments a {@linkplain
	 * MethodDefinitionDescriptor method} implementing this name would
	 * accept.  Note that this is not necessarily the number of underscores and
	 * ellipses, as a guillemet group may contain zero or more
	 * underscores/ellipses (and other guillemet groups) but count as one
	 * top-level argument.
	 *
	 * @return The number of arguments this message takes.
	 */
	public int numberOfArguments ()
	{
		return rootSequence.arguments.size();
	}

	/**
	 * Return the number of underscores/ellipses present in the method name.
	 * This is not the same as the number of arguments that a method
	 * implementing this name would accept, as a top-level guillemet group with
	 * N recursively embedded underscores/ellipses is counted as N, not one.
	 *
	 * <p>
	 * This count of underscores/ellipses is essential for expressing negative
	 * precedence rules in the presence of repeated arguments.  Also note that
	 * backquoted underscores are not counted, since they don't represent a
	 * position at which a subexpression must occur.  Similarly, backquoted
	 * ellipses are not a place where an arbitrary input token can go.
	 * </p>
	 *
	 * @return The number of non-backquoted underscores/ellipses within this
	 *         method name.
	 */
	public int numberOfUnderscores ()
	{
		return underscorePartNumbers.size();
	}

	/**
	 * Answer the number of section checkpoints (§) present in the method name.
	 *
	 * @return The number of section checkpoints.
	 */
	public int numberOfSectionCheckpoints ()
	{
		return numberOfSectionCheckpoints;
	}
	/**
	 * Check that an {@linkplain DefinitionDescriptor implementation} with
	 * the given {@linkplain FunctionTypeDescriptor signature} is appropriate
	 * for a message like this.
	 *
	 * @param functionType
	 *            A function type.
	 * @param sectionNumber
	 *            The {@link SectionCheckpoint}'s subscript if this is a check
	 *            of a {@linkplain MacroDefinitionDescriptor macro}'s,
	 *            {@linkplain A_Definition#prefixFunctions() prefix function},
	 *            otherwise any value past the total {@link
	 *            #numberOfSectionCheckpoints} for a method or macro body.
	 * @throws SignatureException
	 *            If the function type is inappropriate for the method name.
	 */
	public void checkImplementationSignature (
		final A_Type functionType,
		final int sectionNumber)
	throws SignatureException
	{
		final A_Type argsTupleType = functionType.argsTupleType();
		final A_Type sizes = argsTupleType.sizeRange();
		final A_Number lowerBound = sizes.lowerBound();
		final A_Number upperBound = sizes.upperBound();
		if (!lowerBound.equals(upperBound) || !lowerBound.isInt())
		{
			// Method definitions (and other definitions) should take a
			// definite number of arguments.
			throwSignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		final int lowerBoundInt = lowerBound.extractInt();
		if (lowerBoundInt != numberOfArguments())
		{
			throwSignatureException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		rootSequence.checkType(
			functionType.argsTupleType(),
			sectionNumber);
	}

	/**
	 * Check that an {@linkplain DefinitionDescriptor implementation} with
	 * the given {@linkplain FunctionTypeDescriptor signature} is appropriate
	 * for a message like this.
	 *
	 * @param functionType
	 *            A function type.
	 * @throws SignatureException
	 *            If the function type is inappropriate for the method name.
	 */
	public void checkImplementationSignature (
		final A_Type functionType)
	throws SignatureException
	{
		checkImplementationSignature(functionType, Integer.MAX_VALUE);
	}

	/**
	 * Does the message contain any groups?
	 *
	 * @return {@code true} if the message contains any groups, {@code false}
	 *         otherwise.
	 */
	public boolean containsGroups ()
	{
		for (final Expression expression : rootSequence.expressions)
		{
			if (expression.isGroup())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Answer a String consisting of the name of the message with a visual
	 * indication inserted at the keyword or argument position related to the
	 * given program counter.
	 *
	 * @param pc
	 *        The 1-based instruction index into my {@link
	 *        #instructionsTupleFor(A_Type) instructions}.
	 * @return The annotated, quoted method name.
	 */
	public String nameHighlightingPc (final A_Type signatureType, final int pc)
	{
		if (pc == 0)
		{
			return "(any method invocation)";
		}
		final List<Expression> expressions =
			originExpressionsFor(signatureType);
		// Adjust for tuple vs. list.
		final Expression expression = expressions.get(pc - 1);
		int tokenIndex = -1;
		if (expression instanceof Argument)
		{
			final int absoluteArgumentIndex =
				((Argument) expression).absoluteUnderscoreIndex;
			tokenIndex = underscorePartNumbers().get(absoluteArgumentIndex - 1);
		}
		else if (expression instanceof Simple)
		{
			tokenIndex = ((Simple) expression).tokenIndex;
		}
		String javaString = messageName.asNativeString();
		if (tokenIndex != -1)
		{
			// Add the annotation indicator.
			final int characterPosition =
				messagePartPositions.get(tokenIndex - 1);
			javaString = javaString.substring(0, characterPosition - 1)
					+ AvailCompiler.errorIndicatorSymbol
					+ javaString.substring(characterPosition - 1);
		}
		final A_String availString = StringDescriptor.from(javaString);
		// Finally, quote it.
		return availString.toString();
	}

	/**
	 * Throw a {@link SignatureException} with the given error code.
	 *
	 * @param errorCode The {@link AvailErrorCode} that indicates the problem.
	 * @throws SignatureException Always, with the given error code.
	 */
	static void throwSignatureException (
			final AvailErrorCode errorCode)
		throws SignatureException
	{
		throw new SignatureException(errorCode);
	}

	/**
	 * Throw a {@link MalformedMessageException} with the given error code.
	 *
	 * @param errorCode
	 *        The {@link AvailErrorCode} that indicates the problem.
	 * @param errorMessage
	 *        A description of the problem.
	 * @throws MalformedMessageException
	 *         Always, with the given error code and diagnostic message.
	 */
	void throwMalformedMessageException (
			final AvailErrorCode errorCode,
			final String errorMessage)
		throws MalformedMessageException
	{
		throw new MalformedMessageException(
			errorCode,
			new Generator<String>()
			{
				@Override
				public String value ()
				{
					final StringBuilder builder = new StringBuilder();
					builder.append(errorMessage);
					final String errorIndicator =
						AvailCompiler.errorIndicatorSymbol;
					builder.append(". See arrow (");
					builder.append(errorIndicator);
					builder.append(") in: \"");
					final int characterIndex =
						messagePartPosition > 0
							? messagePartPosition <= messagePartPositions.size()
								? messagePartPositions.get(
									messagePartPosition - 1)
								: messageName.tupleSize() + 1
							: 0;
					final A_String before =
						(A_String)messageName.copyTupleFromToCanDestroy(
							1, characterIndex - 1, false);
					final A_String after =
						(A_String)messageName.copyTupleFromToCanDestroy(
							characterIndex, messageName.tupleSize(), false);
					builder.append(before.asNativeString());
					builder.append(errorIndicator);
					builder.append(after.asNativeString());
					builder.append("\"");
					return builder.toString();
				}
			});
	}

	/**
	 * Answer whether the specified character is an operator character, space,
	 * underscore, or ellipsis.
	 *
	 * @param aCharacter A Java {@code char}.
	 * @return {@code true} if the specified character is an operator character,
	 *          space, underscore, or ellipsis; or {@code false} otherwise.
	 */
	private static boolean isCharacterAnUnderscoreOrSpaceOrOperator (
		final char aCharacter)
	{
		return aCharacter == '_'
			|| aCharacter == '…'
			|| aCharacter == ' '
			|| aCharacter == '/'
			|| aCharacter == '$'
			|| AvailScanner.isOperatorCharacter(aCharacter);
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		dumpForDebug(builder);
		return builder.toString();
	}
}
