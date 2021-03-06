/*
 * AvailInstruction.java
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

package com.avail.compiler.instruction;

import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Tuple;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * {@code AvailInstruction} implements an abstract instruction set that doesn't
 * have to agree precisely with the actual implemented Level One nybblecode
 * instruction set.  The mapping is approximately one-to-one, however, other
 * than providing the ability to defer certain analyses, such as last-use of
 * variables, until after selection of AvailInstructions.  This allows the
 * analysis to simply mark the already abstractly-emitted instructions with
 * information that affects the precise nybblecodes that will ultimately be
 * emitted.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class AvailInstruction
{
	/** The tuple of tokens that contributed to producing this instruction. */
	private A_Tuple relevantTokens;

	/**
	 * Construct an instruction.  Capture the tokens that contributed to it.
	 *
	 * @param relevantTokens
	 *        The {@link A_Tuple} of {@link A_Token}s that are associated with
	 *        this instruction.
	 */
	public AvailInstruction (final A_Tuple relevantTokens)
	{
		this.relevantTokens = relevantTokens;
	}

	/**
	 * Write a nybble-coded int in a variable-sized format to the {@linkplain
	 * ByteArrayOutputStream stream}.  Small values take only one nybble,
	 * and we can represent any int up to {@link Integer#MAX_VALUE}.
	 *
	 * @param anInteger The integer to write.
	 * @param aStream The stream on which to write the integer.
	 */
	public static void writeIntegerOn (
		final int anInteger,
		final ByteArrayOutputStream aStream)
	{
		assert anInteger >= 0 : "Only positive integers, please";
		if (anInteger < 10)
		{
			aStream.write(anInteger);
		}
		else if (anInteger < 0x3A)
		{
			aStream.write((anInteger - 10 + 0xA0) >>> 4);
			aStream.write((anInteger - 10 + 0xA0) & 15);
		}
		else if (anInteger < 0x13A)
		{
			aStream.write(13);
			aStream.write((anInteger - 0x3A) >>> 4);
			aStream.write((anInteger - 0x3A) & 15);
		}
		else if (anInteger < 0x10000)
		{
			aStream.write(14);
			aStream.write(anInteger >>> 12);
			aStream.write((anInteger >>> 8) & 15);
			aStream.write((anInteger >>> 4) & 15);
			aStream.write(anInteger & 15);
		}
		else
		{
			// Treat it as an unsigned int.  i<0 case was already handled.
			aStream.write(15);
			aStream.write(anInteger >>> 28);
			aStream.write((anInteger >>> 24) & 15);
			aStream.write((anInteger >>> 20) & 15);
			aStream.write((anInteger >>> 16) & 15);
			aStream.write((anInteger >>> 12) & 15);
			aStream.write((anInteger >>> 8) & 15);
			aStream.write((anInteger >>> 4) & 15);
			aStream.write(anInteger & 15);
		}
	}

	/**
	 * Write nybbles representing this instruction to the {@linkplain
	 * ByteArrayOutputStream stream}.
	 *
	 * @param aStream Where to write the nybbles.
	 */
	public abstract void writeNybblesOn (final ByteArrayOutputStream aStream);

	/**
	 * The instructions of a block are being iterated over.  Coordinate
	 * optimizations between instructions using localData and outerData, two
	 * {@linkplain List lists} manipulated by overrides of this method.  Treat
	 * each instruction as though it is the last one in the block, and save
	 * enough information in the lists to be able to undo consequences of this
	 * assumption when a later instruction shows it to be unwarranted.
	 *
	 * <p>
	 * The data lists are keyed by local or outer index.  Each entry is an
	 * {@link AvailVariableAccessNote}, which keeps track of the immediately
	 * previous use of the variable.
	 * </p>
	 *
	 * @param localData
	 *        A list of {@linkplain AvailVariableAccessNote}s, one for each
	 *        local variable.
	 * @param outerData
	 *        A list of {@linkplain AvailVariableAccessNote}s, one for each
	 *        outer variable.
	 * @param codeGenerator
	 *        The code generator.
	 */
	public void fixUsageFlags (
		final List<AvailVariableAccessNote> localData,
		final List<AvailVariableAccessNote> outerData,
		final AvailCodeGenerator codeGenerator)
	{
		// Do nothing here in the general case.
	}

	/**
	 * Answer whether this instruction is a use of an outer variable.
	 *
	 * @return False for this class, possibly true in a subclass.
	 */
	public boolean isOuterUse ()
	{
		return false;
	}

	/**
	 * Answer which line number to say that this instruction occurs on.  Use
	 * the {@link #relevantTokens} as an approximation, but subclasses might be
	 * able to be more precise.  Answer -1 if this instruction doesn't seem to
	 * have a location in the source associated with it.
	 *
	 * @return The line number for this instruction, or {@code -1}.
	 */
	public int lineNumber ()
	{
		if (relevantTokens.tupleSize() == 0)
		{
			return -1;
		}
		final A_Token firstToken = relevantTokens.tupleAt(1);
		return firstToken.lineNumber();
	}

	/**
	 * Get the tuple of tokens that contributed to producing this instruction.
	 *
	 * @return The {@link A_Tuple} of relevant {@link A_Token}s.
	 */
	public A_Tuple relevantTokens ()
	{
		return relevantTokens;
	}

	/**
	 * Replace the tuple of tokens related to this instruction.
	 *
	 * @param relevantTokens
	 *        The replacement {@link A_Tuple} of {@link A_Token}s.
	 */
	public void setRelevantTokens (final A_Tuple relevantTokens)
	{
		this.relevantTokens = relevantTokens;
	}
}
