/*
 * L2SemanticSlot.java
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
package com.avail.optimizer.values;
import com.avail.descriptor.A_Continuation;
import com.avail.utility.evaluation.Transformer1NotNull;

import static com.avail.descriptor.AvailObject.multiplier;

/**
 * A semantic value which represents a slot of some {@link Frame}'s effective
 * {@link A_Continuation}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
final class L2SemanticSlot
extends L2SemanticValue
{
	/** The {@link Frame} for which this is a virtualized slot. */
	public final Frame frame;

	/** The one-based index of the slot in its {@link Frame}. */
	public final int slotIndex;

	/**
	 * The level one {@link A_Continuation#pc()} at the position just after the
	 * nybblecode instruction that produced this value.  This serves to
	 * distinguish semantic slots at the same index but at different times,
	 * allowing a correct SSA graph and the reordering that it supports.
	 */
	public final int pcAfter;

	/**
	 * Create a new {@code L2SemanticSlot} semantic value.
	 *
	 * @param frame
	 *        The frame for which this represents a slot of a virtualized
	 *        continuation.
	 * @param slotIndex
	 *        The one-based index of the slot in that frame.
	 */
	L2SemanticSlot (
		final Frame frame,
		final int slotIndex,
		final int pcAfter)
	{
		this.frame = frame;
		this.slotIndex = slotIndex;
		this.pcAfter = pcAfter;
	}

	@Override
	public boolean equals (final Object obj)
	{
		if (!(obj instanceof L2SemanticSlot))
		{
			return false;
		}
		final L2SemanticSlot slot = (L2SemanticSlot) obj;
		return frame.equals(slot.frame)
			&& slotIndex == slot.slotIndex
			&& pcAfter == slot.pcAfter;
	}

	@Override
	public int hashCode ()
	{
		int h = slotIndex * multiplier ^ pcAfter;
		h = h * multiplier ^ frame.hashCode();
		return h;
	}

	@Override
	public L2SemanticSlot transform (
		final Transformer1NotNull<L2SemanticValue, L2SemanticValue>
			semanticValueTransformer,
		final Transformer1NotNull<Frame, Frame> frameTransformer)
	{
		final Frame newFrame = frameTransformer.value(frame);
		return newFrame.equals(frame)
			? this
			: new L2SemanticSlot(newFrame, slotIndex, pcAfter);
	}

	@Override
	public String toString ()
	{
		return "Slot #" + slotIndex + " of " + frame + " as of pc=" + pcAfter;
	}
}