/*
 * GenericHelpOption.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.tools.options

import java.io.IOException
import java.lang.String.format
import kotlin.system.exitProcess

/**
 * A `GenericHelpOption` provides an application help message that displays a
 * customizable preamble followed by the complete set of [options][Option].
 *
 * @param OptionKeyType
 *   The type of the option.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new [GenericHelpOption].
 *
 * @param optionKey
 *   The option key.
 * @param preamble
 *   The preamble, i.e. any text that should precede an enumeration of the
 *   [options][Option].
 * @param appendable
 *   The [Appendable] into which the help text should be written.
 */
class GenericHelpOption<OptionKeyType : Enum<OptionKeyType>> constructor(
	optionKey: OptionKeyType,
	preamble: String,
	appendable: Appendable)
: GenericOption<OptionKeyType>(
	optionKey,
	listOf("?"),
	"Display help text containing a description of the application and an "
	+ "enumeration of its options.",
	{ _, _ ->
		try
		{
			writeHelpText(this, preamble, appendable)
			exitProcess(0)
		}
		catch (e: IOException)
		{
			throw OptionProcessingException(e)
		}
	})
{
	companion object
	{
		/**
		 * Write the specified preamble followed by the
		 * [description][Option.description] of the [options][Option] defined by
		 * the specified [option processor][OptionProcessor] into the specified
		 * [Appendable].
		 *
		 * @param KeyType
		 *   The type of the option.
		 * @param optionProcessor
		 *   The [option processor][OptionProcessor] whose [options][Option]
		 *   should be described by the new [GenericHelpOption].
		 * @param preamble
		 *   The preamble, i.e. any text that should precede an enumeration of
		 *   the [options][Option].
		 * @param appendable
		 *   The [Appendable] into which the help text should be written.
		 * @throws IOException
		 *   If an [I/O exception][IOException] occurs.
		 */
		@Throws(IOException::class)
		internal fun <KeyType : Enum<KeyType>> writeHelpText(
			optionProcessor: OptionProcessor<KeyType>,
			preamble: String,
			appendable: Appendable)
		{
			appendable.append(format("%s%n%n", preamble))
			optionProcessor.writeOptionDescriptions(appendable)
		}
	}
}
