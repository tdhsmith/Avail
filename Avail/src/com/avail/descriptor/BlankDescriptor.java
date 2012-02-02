/**
 * BlankDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.AvailObject.error;
import java.util.List;
import com.avail.annotations.*;

/**
 * My instance is used as a place-holder in {@linkplain MapDescriptor maps} to
 * indicate where a key has been removed, postponing a rehash of the map until
 * a sufficient percentage of entries are blank.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class BlankDescriptor
extends Descriptor
{
	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append("Blank");
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsBlank();
	}

	@Override @AvailMethod
	boolean o_EqualsBlank (
		final @NotNull AvailObject object)
	{
		//  There is only one blank.

		return true;
	}

	@Override @AvailMethod
	boolean o_EqualsNullOrBlank (
		final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		//  Answer the object's hash.  The blank object should hash to zero, because the
		//  only place it can appear in a data structure is as a filler object.  The blank object
		//  may only appear in sets and maps.

		return 0;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		error("The blank object has no type", object);
		return NullDescriptor.nullObject();
	}

	/**
	 * The sole instance of the (immutable) {@code BlankDescriptor blank
	 * descriptor}.
	 */
	private static AvailObject SoleInstance;

	/**
	 * Create the sole instance of the immutable {@code BlankDescriptor}.
	 */
	static void createWellKnownObjects ()
	{
		SoleInstance = immutable().create();
	}

	/**
	 * Clear any static references to publicly accessible objects.
	 */
	static void clearWellKnownObjects ()
	{
		SoleInstance = null;
	}

	/**
	 * Answer the sole instance of the (immutable) {@code BlankDescriptor blank
	 * descriptor}.
	 *
	 * @return The blank object.
	 */
	static AvailObject blank ()
	{
		return SoleInstance;
	}

	/**
	 * Construct a new {@link BlankDescriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected BlankDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link BlankDescriptor}.
	 */
	private static final BlankDescriptor mutable = new BlankDescriptor(true);

	/**
	 * Answer the mutable {@link BlankDescriptor}.
	 *
	 * @return The mutable {@link BlankDescriptor}.
	 */
	public static BlankDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link BlankDescriptor}.
	 */
	private static final BlankDescriptor immutable = new BlankDescriptor(false);

	/**
	 * Answer the immutable {@link BlankDescriptor}.
	 *
	 * @return The immutable {@link BlankDescriptor}.
	 */
	public static BlankDescriptor immutable ()
	{
		return immutable;
	}
}
