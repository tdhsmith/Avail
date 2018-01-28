/*
 * L2ReadOperand.java
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

package com.avail.interpreter.levelTwo.operand;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.interpreter.levelTwo.register.L2Register;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.instanceTypeOrMetaOn;
import static com.avail.descriptor.SetDescriptor.emptySet;

/**
 * {@code L2ReadOperand} abstracts the capabilities of actual register read
 * operands.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <R>
 *        The subclass of {@link L2Register}.
 * @param <T>
 *        The type for {@link TypeRestriction}s.
 */
public abstract class L2ReadOperand<
	R extends L2Register<T>,
	T extends A_BasicObject>
extends L2Operand
{
	/**
	 * The actual {@link L2Register}.
	 */
	private R register;

	/**
	 * A type restriction, certified by the VM, that this particular read of
	 * this register is guaranteed to satisfy. This supplements the more basic
	 * type restriction already present in the {@link L2IntRegister} itself.
	 */
	private final TypeRestriction<T> restriction;

	/**
	 * Construct a new {@code L2ReadOperand} for the specified {@link
	 * L2Register} and optional restriction.
	 *
	 * @param register
	 *        The register.
	 * @param restriction
	 *        The further {@link TypeRestriction} to apply to this particular
	 *        read.
	 */
	L2ReadOperand (
		final R register,
		final @Nullable TypeRestriction<T> restriction)
	{
		this.register = register;
		this.restriction =
			restriction == null ? register.restriction() : restriction;
		assert this.restriction.type.isSubtypeOf(register.restriction().type)
			: "Read restriction is weaker than register's restriction";
	}

	/**
	 * Answer this read's {@link L2Register}.
	 *
	 * @return The register.
	 */
	public final R register ()
	{
		return register;
	}

	/**
	 * Answer the {@link L2Register}'s {@link L2Register#finalIndex finalIndex}.
	 *
	 * @return The index of the register, computed during register coloring.
	 */
	public final int finalIndex ()
	{
		return register.finalIndex();
	}

	/**
	 * Answer the type restriction for this register read.
	 *
	 * @return A {@link TypeRestriction}.
	 */
	public final TypeRestriction<T> restriction ()
	{
		return restriction;
	}

	/**
	 * Answer this read's type restriction's basic type.
	 *
	 * @return An {@link A_Type}.
	 */
	public final A_Type type ()
	{
		return restriction.type;
	}

	/**
	 * Answer this read's type restriction's constant value (i.e., the exact
	 * value that this read is guaranteed to produce), or {@code null} if such
	 * a constraint is not available.
	 *
	 * @return The exact {@link A_BasicObject} that's known to be in this
	 *         register, or else {@code null}.
	 */
	public final @Nullable T constantOrNull ()
	{
		return restriction.constantOrNull;
	}

	@Override
	public final void instructionWasAdded (final L2Instruction instruction)
	{
		register.addUse(instruction);
	}

	@Override
	public final void instructionWasRemoved (final L2Instruction instruction)
	{
		register.removeUse(instruction);
	}

	@Override
	public final void replaceRegisters (
		final Map<L2Register<?>, L2Register<?>> registerRemap,
		final L2Instruction instruction)
	{
		final @Nullable L2Register<?> replacement = registerRemap.get(register);
		if (replacement == null || replacement == register)
		{
			return;
		}
		register.removeUse(instruction);
		replacement.addUse(instruction);
		//noinspection unchecked
		register = (R) register.getClass().cast(replacement);
	}

	@Override
	public final void addSourceRegistersTo (
		final List<L2Register<?>> sourceRegisters)
	{
		sourceRegisters.add(register);
	}

	@Override
	public final String toString ()
	{
		//noinspection StringConcatenationMissingWhitespace
		return
			"@"
				+ register
				+ register.restriction().suffixString();
	}

	/**
	 * Create a {@code PhiRestriction}, which narrows a register's type
	 * information along a control flow branch. The type and optional constant
	 * value are supplied.
	 *
	 * @param restrictedType
	 *        The type that the register will hold along this branch.
	 * @param restrictedConstantOrNull
	 *        Either {@code null} or the exact value that the register will hold
	 *        along this branch.
	 */
	public final PhiRestriction restrictedTo (
		final A_Type restrictedType,
		final @Nullable A_BasicObject restrictedConstantOrNull)
	{
		return new PhiRestriction(
			register,
			restrictedType.typeIntersection(type()),
			restrictedConstantOrNull);
	}

	/**
	 * Create a {@code PhiRestriction}, which narrows a register's type
	 * information along a control flow branch.  The exact value is supplied.
	 *
	 * @param restrictedConstant
	 *        The exact value that the register will hold along this branch.
	 */
	public final PhiRestriction restrictedToValue (final T restrictedConstant)
	{
		final @Nullable A_Type type = type();
		assert restrictedConstant.isInstanceOf(type)
			: "This register has no possible values.";
		return new PhiRestriction(
			register,
			instanceTypeOrMetaOn(restrictedConstant).typeIntersection(type),
			restrictedConstant);
	}

	/**
	 * Create a {@code PhiRestriction}, which narrows a register's type
	 * information along a control flow branch.  A value to exclude from the
	 * existing type is provided.
	 *
	 * @param excludedConstant
	 *        The value that the register <em>cannot</em> hold along this
	 *        branch.
	 */
	public final PhiRestriction restrictedWithoutValue (
		final T excludedConstant)
	{
		final @Nullable A_Type type = type();
		final @Nullable A_BasicObject constantOrNull = constantOrNull();
		if (constantOrNull != null)
		{
			// It's unclear if this is necessarily a problem, or if it's
			// actually reasonable for code that will soon be marked dead.
			assert !excludedConstant.equals(constantOrNull)
				: "This register has no possible values.";
			// The excluded value is irrelevant.
			return new PhiRestriction(register, type, constantOrNull);
		}
		final A_Type restrictedType;
		if (type.instanceCount().isFinite() && !type.isInstanceMeta())
		{
			restrictedType = enumerationWith(
				type.instances().setWithoutElementCanDestroy(
					excludedConstant, false));
		}
		else
		{
			restrictedType = type;
		}
		return new PhiRestriction(
			register,
			restrictedType,
			restrictedType.instanceCount().equalsInt(1)
				&& !restrictedType.isInstanceMeta()
				? restrictedType.instance()
				: null);
	}

	/**
	 * Create a {@code PhiRestriction}, which narrows a register's type
	 * information along a control flow branch.  A type is provided to exclude,
	 * although we don't yet maintain precise negative type information.
	 *
	 * @param excludedType
	 *        The value that the register <em>cannot</em> hold along this
	 *        branch.
	 */
	public final PhiRestriction restrictedWithoutType (
		final A_Type excludedType)
	{
		final @Nullable A_Type type = type();
		final @Nullable A_BasicObject constantOrNull = constantOrNull();
		if (constantOrNull != null)
		{
			// It's unclear if this is necessarily a problem, or if it's
			// actually reasonable for code that will soon be marked dead.
			assert !constantOrNull.isInstanceOf(excludedType)
				: "This register has no possible values.";
			// The excluded type is irrelevant.
			return new PhiRestriction(register, type, constantOrNull);
		}
		if (type.instanceCount().isFinite() && !type.isInstanceMeta())
		{
			A_Set elements = emptySet();
			for (final A_BasicObject element : type.instances())
			{
				if (!element.isInstanceOf(excludedType))
				{
					elements = elements.setWithElementCanDestroy(element, true);
				}
			}
			elements = elements.makeImmutable();
			return new PhiRestriction(
				register, enumerationWith(elements), null);
		}
		// Be conservative and ignore the type subtraction.  We could eventually
		// record this information.
		return new PhiRestriction(register, type, null);
	}
}