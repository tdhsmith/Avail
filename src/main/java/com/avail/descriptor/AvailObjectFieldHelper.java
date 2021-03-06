/*
 * AvailObjectFieldHelper.java
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

package com.avail.descriptor;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.StackPrinter.trace;

/**
 * This class assists with the presentation of {@link AvailObject}s in the
 * Eclipse debugger.  Since AvailObjects have a uniform structure consisting of
 * a descriptor, an array of AvailObjects, and an array of {@code int}s, it is
 * essential to the understanding of a hierarchy of Avail objects that they be
 * presented at the right level of abstraction, including the use of symbolic
 * names for conceptual subobjects.
 *
 * <p>
 * Eclipse is still kind of fiddly about these presentations, requiring explicit
 * manipulation through dialogs (well, maybe there's some way to hack around
 * with the Eclipse preference files).  Here are the minimum steps by which to
 * set up symbolic Avail descriptions:
 * <ol>
 * <li>Preferences... &rarr; Java &rarr; Debug &rarr; Logical Structures &rarr;
 * Add:
 *   <ul>
 *   <li>Qualified name: com.avail.descriptor.AvailIntegerValueHelper</li>
 *   <li>Description: Hide integer value field</li>
 *   <li>Structure type: Single value</li>
 *   <li>Code: {@code return new Object[0];}</li>
 *   </ul>
 * </li>
 * <li>Preferences... &rarr; Java &rarr; Debug &rarr; Logical Structures &rarr;
 * Add:
 *   <ul>
 *   <li>Qualified name: com.avail.descriptor.AvailObject</li>
 *   <li>Description: Present Avail objects</li>
 *   <li>Structure type: Single value</li>
 *   <li>Code: {@code return describeForDebugger();}</li>
 *   </ul>
 * </li>
 * <li>Preferences... &rarr; Java &rarr; Debug &rarr; Logical Structures &rarr;
 * Add:
 *   <ul>
 *   <li>Qualified name: com.avail.interpreter.Interpreter</li>
 *   <li>Description: Present Interpreter as stack frames</li>
 *   <li>Structure type: Single value</li>
 *   <li>Code: {@code return describeForDebugger();}</li>
 *   </ul>
 * </li>
 * <li>Preferences... &rarr; Java &rarr; Debug &rarr; Logical Structures &rarr;
 * Add:
 *   <ul>
 *   <li>Qualified name: com.avail.descriptor.AvailObjectFieldHelper</li>
 *   <li>Description: Present helper's value's fields instead of the helper</li>
 *   <li>Structure type: Single value</li>
 *   <li>Code: {@code return value;}</li>
 *   </ul>
 * </li>
 * <li>Preferences... &rarr; Java &rarr; Debug &rarr; Detail Formatters &rarr;
 * Add:
 *   <ul>
 *   <li>Qualified type name: com.avail.descriptor.AvailObjectFieldHelper</li>
 *   <li>Detail formatter code snippet: {@code return name();}</li>
 *   <li>Enable this detail formatter: (checked)</li>
 *   <li>(after OK) Show variable details: As the label for all variables</li>
 *   </ul>
 * </li>
 * <li>In the Debug perspective, go to the Variables view.  Select the tool bar
 * icon whose hover help is Show Logical Structure.</li>
 * </ol>
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailObjectFieldHelper
{
	/**
	 * The object containing this field.
	 */
	private final @Nullable A_BasicObject parentObject;

	/**
	 * The actual value being presented with the given label.
	 */
	public final @Nullable Object value;

	/**
	 * The slot of the parent object in which the value occurs.
	 */
	public final AbstractSlotsEnum slot;

	/**
	 * This value's subscript within a repeated slot, or -1 if not repeated.
	 */
	public final int subscript;

	/**
	 * The name to present for this field.
	 */
	private @Nullable String name;

	/** Construct a new {@code AvailObjectFieldHelper}.
	 *
	 * @param parentObject
	 *            The object containing the value.
	 * @param slot
	 *            The {@linkplain AbstractSlotsEnum slot} in which the value
	 *            occurs.
	 * @param subscript
	 *            The optional subscript for a repeating slot.  Uses -1 to
	 *            indicate this is not a repeating slot.
	 * @param value
	 *            The value found in that slot of the object.
	 */
	public AvailObjectFieldHelper (
		final @Nullable A_BasicObject parentObject,
		final AbstractSlotsEnum slot,
		final int subscript,
		final @Nullable Object value)
	{
		this.parentObject = parentObject;
		this.slot = slot;
		this.subscript = subscript;
		this.value = value;
	}

	/**
	 * Answer the string to display for this field.
	 *
	 * @return A {@link String}.
	 */
	@SuppressWarnings("unused")
	public String nameForDebugger ()
	{
		if (name != null)
		{
			return name;
		}
		final String string = privateComputeNameForDebugger();
		name = string;
		return string;
	}

	private String privateComputeNameForDebugger ()
	{
		final StringBuilder builder = new StringBuilder();
		if (subscript != -1)
		{
			builder.append(slot.name(), 0, slot.name().length() - 1);
			builder.append('[');
			builder.append(subscript);
			builder.append(']');
		}
		else
		{
			builder.append(slot.name());
		}

		if (value == null)
		{
			builder.append(" = Java null");
		}
		else if (value instanceof AvailObject)
		{
			builder.append(' ');
			builder.append(((AvailObject) value).nameForDebugger());
		}
		else if (value instanceof AvailIntegerValueHelper)
		{
			try
			{
				final IntegerSlotsEnum strongSlot =
					(IntegerSlotsEnum) slot;
				final List<BitField> bitFields =
					AbstractDescriptor.bitFieldsFor(strongSlot);
				if (!bitFields.isEmpty())
				{
					// Remove the name.
					builder.delete(0, builder.length());
				}
				AbstractDescriptor.describeIntegerSlot(
					(AvailObject) stripNull(parentObject),
					((AvailIntegerValueHelper) value).longValue,
					strongSlot,
					bitFields,
					builder);
			}
			catch (final RuntimeException e)
			{
				builder.append(
					"PROBLEM DESCRIBING INTEGER FIELD:\n");
				builder.append(trace(e));
			}
		}
		else if (value instanceof String)
		{
			builder.append(" = Java String: ");
			builder.append((String) value);
		}
		else if (value instanceof String[])
		{
			builder.append(" = Multi-line text");
		}
		else
		{
			builder
				.append(" = ")
				.append(value.getClass().getCanonicalName());
		}
		return builder.toString();
	}

	@SuppressWarnings("unused")
	public @Nullable Object describeForDebugger ()
	{
		if (value instanceof AvailObject)
		{
			return ((AvailObject) value).describeForDebugger();
		}
		if (value instanceof AvailIntegerValueHelper)
		{
			return new Object[0];
		}
		return value;
	}
}
