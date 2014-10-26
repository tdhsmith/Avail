/**
 * UninitializedThisVariable.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.interpreter.jvm;

import java.io.DataOutput;
import java.io.IOException;

/**
 * The {@link VerificationTypeInfo
 * <code>UninitializedThis_variable_info</code>} item indicates that
 * the location has the verification type
 * <code>uninitializedThis</code>.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class UninitializedThisVariable extends VerificationTypeInfo
{
	/**
	 * The Offset item indicates the offset, in the code array of the
	 * <code>Code</code> attribute that contains this
	 * {@link StackMapTableAttribute}, of the <code>new</code> instruction
	 * that created the object being stored in the location.
	 */
	private final short offset;

	/**
	 * Construct a new {@link IntegerVariable}.
	 * @param offset
	 *
	 */
	UninitializedThisVariable(final short offset)
	{
		super();
		this.offset = offset;
	}

	@Override
	protected int size ()
	{
		return 3;
	}

	@Override
	byte typeValue ()
	{
		return 8;
	}

	@Override
	void writeTo (final DataOutput out) throws IOException
	{
		out.writeByte(typeValue());
		out.writeByte(offset);
	}
}
