/**
 * com.avail.newcompiler/SuperCastNodeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.compiler.node;

import static com.avail.descriptor.AvailObject.*;
import java.util.List;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.Transformer1;

/**
 * My instances represent a weakening of the type of an argument of a {@link
 * SendNodeDescriptor message send}, in order to invoke a more general
 * implementation.  Multiple arguments of the same message send may be weakened
 * in this way.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class SuperCastNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The expression that yields some multi-method argument.
		 */
		EXPRESSION,

		/**
		 * The {@link TypeDescriptor type} to consider the expression to be for
		 * the purpose of multi-method lookup.
		 */
		SUPER_CAST_TYPE

	}


	/**
	 * Setter for field expression.
	 */
	@Override
	public void o_Expression (
		final AvailObject object,
		final AvailObject expression)
	{
		object.objectSlotPut(ObjectSlots.EXPRESSION, expression);
	}

	/**
	 * Getter for field expression.
	 */
	@Override
	public AvailObject o_Expression (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.EXPRESSION);
	}


	/**
	 * Setter for field superCastType.
	 */
	@Override
	public void o_SuperCastType (
		final AvailObject object,
		final AvailObject superCastType)
	{
		object.objectSlotPut(ObjectSlots.SUPER_CAST_TYPE, superCastType);
	}

	/**
	 * Getter for field superCastType.
	 */
	@Override
	public AvailObject o_SuperCastType (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.SUPER_CAST_TYPE);
	}


	@Override
	public AvailObject o_ExpressionType (final AvailObject object)
	{
		return object.superCastType();
	}

	@Override
	public AvailObject o_Type (final AvailObject object)
	{
		return Types.superCastNode.object();
	}

	@Override
	public AvailObject o_ExactType (final AvailObject object)
	{
		return Types.superCastNode.object();
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return
			object.expression().hash() * Multiplier
				+ object.superCastType().hash()
			^ 0xD8CCD162;
	}

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return object.type().equals(another.type())
			&& object.expression().equals(another.expression())
			&& object.superCastType().equals(another.superCastType());
	}

	@Override
	public void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		error("A superCast can only be done to an argument of a call.");
	}

	@Override
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		// My parent (send) node deals with my altered semantics.
		object.expression().emitValueOn(codeGenerator);
	}

	@Override
	public void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		object.expression(aBlock.value(object.expression()));
	}


	@Override
	public void o_ValidateLocally (
		final AvailObject object,
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		if (!parent.type().equals(Types.sendNode.object()))
		{
			error("Only use superCast notation as a message argument");
		}
	}




	/**
	 * Construct a new {@link SuperCastNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public SuperCastNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link SuperCastNodeDescriptor}.
	 */
	private final static SuperCastNodeDescriptor mutable =
		new SuperCastNodeDescriptor(true);

	/**
	 * Answer the mutable {@link SuperCastNodeDescriptor}.
	 *
	 * @return The mutable {@link SuperCastNodeDescriptor}.
	 */
	public static SuperCastNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link SuperCastNodeDescriptor}.
	 */
	private final static SuperCastNodeDescriptor immutable =
		new SuperCastNodeDescriptor(false);

	/**
	 * Answer the immutable {@link SuperCastNodeDescriptor}.
	 *
	 * @return The immutable {@link SuperCastNodeDescriptor}.
	 */
	public static SuperCastNodeDescriptor immutable ()
	{
		return immutable;
	}

}