/*
 * ArgumentInModuleScope.java
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
import com.avail.compiler.splitter.MessageSplitter.Metacharacter;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.LiteralPhraseDescriptor;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Iterator;

import static com.avail.compiler.ParsingConversionRule.EVALUATE_EXPRESSION;
import static com.avail.compiler.ParsingOperation.*;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.EXPRESSION_PHRASE;
import static com.avail.utility.Nulls.stripNull;

/**
 * A {@code ArgumentInModuleScope} is an occurrence of an {@linkplain
 * Metacharacter#UNDERSCORE underscore} (_) in a message name, followed
 * immediately by a {@linkplain Metacharacter#SINGLE_DAGGER single dagger} (†).
 * It indicates where an argument is expected, but the argument must not make
 * use of any local declarations. The argument expression will be evaluated at
 * compile time and replaced by a {@linkplain LiteralPhraseDescriptor literal}
 * based on the produced value.
 */
final class ArgumentInModuleScope
extends Argument
{
	/**
	 * Construct an {@code ArgumentInModuleScope}, given the one-based position
	 * of the token in the name, and the absolute index of this argument in the
	 * entire message name.
	 *
	 * @param positionInName
	 *        The one-based position of the start of the token in the message
	 *        name.
	 * @param absoluteUnderscoreIndex
	 *        The one-based index of this argument within the entire message
	 *        name's list of arguments.
	 */
	ArgumentInModuleScope (
		final int positionInName,
		final int absoluteUnderscoreIndex)
	{
		super(positionInName, absoluteUnderscoreIndex);
	}

	/**
	 * First parse an argument subexpression, then check that it has an
	 * acceptable form (i.e., does not violate a grammatical restriction for
	 * that argument position).  Also ensure that no local declarations that
	 * were in scope before parsing the argument are used by the argument.
	 * Then evaluate the argument expression (at compile time) and replace
	 * it with a {@link LiteralPhraseDescriptor literal phrase} wrapping the
	 * produced value.
	 */
	@Override
	WrapState emitOn (
		final A_Type phraseType,
		final InstructionGenerator generator,
		final WrapState wrapState)
	{
		generator.flushDelayed();
		generator.emit(this, PARSE_ARGUMENT_IN_MODULE_SCOPE);
		// Check that the expression is syntactically allowed.
		generator.emitDelayed(this, CHECK_ARGUMENT, absoluteUnderscoreIndex);
		// Check that it's any kind of expression with the right yield type,
		// since it's going to be evaluated and wrapped in a literal phrase.
		final A_Type expressionType = EXPRESSION_PHRASE.create(
			phraseType.expressionType());
		generator.emitDelayed(
			this,
			TYPE_CHECK_ARGUMENT,
			MessageSplitter.indexForConstant(expressionType));
		generator.emitDelayed(this, CONVERT, EVALUATE_EXPRESSION.number());
		return wrapState;
	}

	@Override
	public void printWithArguments (
		final @Nullable Iterator<? extends A_Phrase> arguments,
		final StringBuilder builder,
		final int indent)
	{
		// Describe the token that was parsed as this raw token argument.
		stripNull(arguments).next().printOnAvoidingIndent(
			builder,
			new IdentityHashMap<>(),
			indent + 1);
		builder.append('†');
	}
}
