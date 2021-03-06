/*
 * SerializerInstruction.java
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

package com.avail.serialization;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;

/**
 * A {@code SerializerInstruction} combines an {@link AvailObject} and a
 * {@link SerializerOperation} suitable for serializing it.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
final class SerializerInstruction
{
	/**
	 * An array of subobjects resulting from decomposing the object.  These
	 * correspond to the operation's {@link SerializerOperation#operands()}.
	 */
	private final A_BasicObject[] subobjects;

	/**
	 * The {@link SerializerOperation} that can decompose this object for
	 * serialization.
	 */
	private final SerializerOperation operation;

	/**
	 * The index of this instruction in the list of instructions produced by a
	 * {@link Serializer}.
	 */
	private int index = -1;

	/**
	 * Set this instruction's absolute index in its {@link Serializer}'s
	 * list of instructions.
	 *
	 * @param theIndex The instruction's index.
	 */
	void index (final int theIndex)
	{
		assert index == -1;
		index = theIndex;
	}

	/**
	 * Answer this instruction's absolute index in its {@link Serializer}'s
	 * list of instructions.
	 *
	 * @return The instruction's index.
	 */
	int index ()
	{
		return index;
	}

	/**
	 * Answer whether this instruction has been assigned an instruction index,
	 * which happens when the instruction is written.
	 *
	 * @return Whether this instruction has been written.
	 */
	boolean hasBeenWritten ()
	{
		return index >= 0;
	}

	/**
	 * Answer the {@link SerializerOperation} that will serialize the object.
	 *
	 * @return The {@code SerializerOperation} used to decompose the object.
	 */
	SerializerOperation operation ()
	{
		return operation;
	}

	/**
	 * Answer the number of subobjects that this instruction has.
	 *
	 * @return The number of subobjects.
	 */
	int subobjectsCount ()
	{
		return subobjects.length;
	}

	/**
	 * Answer the subobject at the given zero-based subscript.
	 *
	 * @param subscript The zero-based subobject subscript.
	 * @return The {@link A_BasicObject} at the given subscript.
	 */
	A_BasicObject getSubobject (final int subscript)
	{
		return subobjects[subscript];
	}

	/**
	 * Write this already traced instruction to the {@link Serializer}.
	 *
	 * @param serializer Where to write the instruction.
	 */
	void writeTo (
		final Serializer serializer)
	{
		operation.writeObject(subobjects, serializer);
	}

	/**
	 * Construct a new {@code SerializerInstruction}.
	 *
	 * @param operation
	 *        The {@link SerializerOperation} that will decompose the object for
	 *        serialization.
	 * @param object
	 *        The object to record by this instruction.
	 * @param serializer
	 *        The {@link Serializer} to which this instruction will record.
	 */
	SerializerInstruction (
		final SerializerOperation operation,
		final A_BasicObject object,
		final Serializer serializer)
	{
		this.operation = operation;
		this.subobjects = operation.decompose((AvailObject) object, serializer);
	}
}
