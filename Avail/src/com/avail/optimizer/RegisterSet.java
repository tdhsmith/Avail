/**
 * RegisterSet.java
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

package com.avail.optimizer;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.register.*;
import com.avail.utility.Transformer2;

/**
 * This class maintains register information during naive translation from level
 * one compiled code (nybblecodes) to level two wordcodes ({@linkplain
 * L2ChunkDescriptor chunks}).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class RegisterSet
{
	/**
	 * The {@link L2Translator} using this RegisterSet to translate level one
	 * code to level two.
	 */
	private final L2Translator translator;


	/**
	 * An {@link AtomicLong} used to quickly generate unique 63-bit non-negative
	 * integers which serve to distinguish registers generated by the receiver.
	 */
	final AtomicLong uniqueCounter = new AtomicLong();

	/**
	 * Answer the next value from the unique counter.  This is only used to
	 * distinguish registers for visual debugging.
	 *
	 * @return A long.
	 */
	private long nextUnique ()
	{
		return uniqueCounter.getAndIncrement();
	}

	/**
	 * Construct a new {@link RegisterSet}.
	 *
	 * @param translator The {@link L2Translator} using these registers.
	 */
	RegisterSet (final L2Translator translator)
	{
		this.translator = translator;
		final int numFixed = L2Translator.firstArgumentRegisterIndex;
		final int numRegisters;
		final AvailObject code = codeOrNull();
		if (code == null)
		{
			numRegisters = numFixed;
		}
		else
		{
			numRegisters = numFixed + code.numArgsAndLocalsAndStack();
		}
		architecturalRegisters = new ArrayList<L2ObjectRegister>(numRegisters);
		for (int i = 0; i < numFixed; i++)
		{
			architecturalRegisters.add(
				L2ObjectRegister.precolored(nextUnique(), i));
		}
		for (int i = numFixed; i < numRegisters; i++)
		{
			architecturalRegisters.add(
				new L2ObjectRegister(nextUnique()));
		}
	}

	/**
	 * The architectural registers, representing the fixed registers followed by
	 * each object slot of the current continuation.  During initial translation
	 * of L1 to L2, these registers are used as though they are purely
	 * architectural (even though they're not precolored).  Subsequent
	 * conversion to static single-assignment form splits non-contiguous uses of
	 * these registers into distinct registers, assisting later optimizations.
	 */
	final List<L2ObjectRegister> architecturalRegisters;

	/**
	 * The mapping from each register to its current type, if known.
	 */
	final Map<L2Register, AvailObject> registerTypes =
		new HashMap<L2Register, AvailObject>(10);

	/**
	 * The mapping from each register to its current value, if known.
	 */
	final Map<L2Register, AvailObject> registerConstants =
		new HashMap<L2Register, AvailObject>(10);

	/**
	 * The mapping from each register to a list of other registers that have the
	 * same value (if any).  These occur in the order in which the registers
	 * acquired the value.
	 *
	 * <p>
	 * The inverse map is kept in {@link #invertedOrigins}, to more efficiently
	 * disconnect this information.
	 * </p>
	 */
	final Map<L2Register, List<L2Register>> registerOrigins =
		new HashMap<L2Register, List<L2Register>>();

	/**
	 * The inverse of {@link #registerOrigins}.  For each key, the value is the
	 * collection of registers that this value has been copied into (and not yet
	 * been overwritten).
	 */
	final Map<L2Register, Set<L2Register>> invertedOrigins =
		new HashMap<L2Register, Set<L2Register>>();

	/**
	 * Answer the base {@linkplain CompiledCodeDescriptor compiled code} for
	 * which this chunk is being constructed.  Answer null when generating the
	 * default chunk.
	 *
	 * @return The root compiled code being translated.
	 */
	public @Nullable AvailObject codeOrNull ()
	{
		return translator.codeOrNull();
	}

	/**
	 * Answer the base {@linkplain CompiledCodeDescriptor compiled code} for
	 * which this chunk is being constructed.  Fail if it's null, which happens
	 * when the default chunk is being generated.
	 *
	 * @return The root compiled code being translated.
	 */
	public AvailObject codeOrFail ()
	{
		return translator.codeOrFail();
	}

	/**
	 * Allocate a fresh {@linkplain L2ObjectRegister object register} that
	 * nobody else has used yet.
	 *
	 * @return The new register.
	 */
	L2ObjectRegister newObject ()
	{
		return new L2ObjectRegister(uniqueCounter.getAndIncrement());
	}

	/**
	 * Answer whether this register contains a constant at the current code
	 * generation point.
	 *
	 * @param register The register.
	 * @return Whether the register has most recently been assigned a constant.
	 */
	public boolean hasConstantAt (
		final L2Register register)
	{
		return registerConstants.containsKey(register);
	}

	/**
	 * Associate this register with a constant at the current code generation
	 * point.
	 *
	 * @param register
	 *            The register.
	 * @param value
	 *            The constant {@link AvailObject value} bound to the register.
	 */
	public void constantAtPut (
		final L2Register register,
		final AvailObject value)
	{
		registerConstants.put(register, value);
		registerTypes.put(
			register,
			AbstractEnumerationTypeDescriptor.withInstance(value));
		propagateWriteTo(register);
	}

	/**
	 * Retrieve the constant currently associated with this register, or null
	 * if the register is not bound to a constant at this point.
	 *
	 * @param register The register.
	 * @return The constant object or null.
	 */
	public AvailObject constantAt (
		final L2Register register)
	{
		return registerConstants.get(register);
	}

	/**
	 * Remove any current constant binding for the specified register.
	 *
	 * @param register The register.
	 */
	public void removeConstantAt (
		final L2Register register)
	{
		registerConstants.remove(register);
	}

	/**
	 * Answer whether this register has a type bound to it at the current code
	 * generation point.
	 *
	 * @param register The register.
	 * @return Whether the register has a known type at this point.
	 */
	public boolean hasTypeAt (
		final L2Register register)
	{
		return registerTypes.containsKey(register);
	}

	/**
	 * Answer the type bound to the register at this point in the code.
	 *
	 * @param register The register.
	 * @return The type bound to the register, or null if not bound.
	 */
	public AvailObject typeAt (
		final L2Register register)
	{
		return registerTypes.get(register);
	}

	/**
	 * Associate this register with a type at the current code generation point.
	 *
	 * @param register
	 *            The register.
	 * @param type
	 *            The type of object that will be in the register at this point.
	 */
	public void typeAtPut (
		final L2Register register,
		final AvailObject type)
	{
		registerTypes.put(register, type);
	}

	/**
	 * Unbind any type information from the register at this point in the code.
	 *
	 * @param register The register from which to clear type information.
	 */
	public void removeTypeAt (
		final L2Register register)
	{
		registerTypes.remove(register);
	}

	/**
	 * Remove all information about any registers having an equivalent value to
	 * the specified register (probably because something was just written to
	 * it).
	 */
	public void clearOrigins ()
	{
		registerOrigins.clear();
		invertedOrigins.clear();
	}


	/**
	 * The sourceRegister's value was just written to the destinationRegister.
	 * Propagate this information into the registerOrigins to allow the earliest
	 * remaining register with the same value to always be used during register
	 * source normalization.  This is essential for eliminating redundant moves.
	 *
	 * <p>
	 * Eventually primitive constructor/deconstructor pairs (e.g., tuple
	 * creation and tuple subscripting) could be combined in a similar way to
	 * perform a simple object escape analysis.  For example, consider this
	 * sequence of level two instructions:
	 * <ul>
	 * <li>r1 := ...</li>
	 * <li>r2 := ...</li>
	 * <li>r3 := makeTuple(r1, r2)</li>
	 * <li>r4 := tupleAt(r3, 1)</li>
	 * </ul>
	 * It can be shown that r4 will always contain the value that was in r1.
	 * In fact, if r3 is no longer needed then the tuple doesn't even have to be
	 * constructed at all.  While this isn't expected to be useful by itself,
	 * inlining is expected to reveal a great deal of such combinations.
	 * </p>
	 *
	 * @param sourceRegister
	 *            The {@link L2Register} which is the source of a move.
	 * @param destinationRegister
	 *            The {@link L2Register} which is the destination of a move.
	 */
	public void propagateMove (
		final L2Register sourceRegister,
		final L2Register destinationRegister)
	{
		if (sourceRegister == destinationRegister)
		{
			return;
		}
		propagateWriteTo(destinationRegister);
		final List<L2Register> sourceOrigins = registerOrigins.get(
			sourceRegister);
		final List<L2Register> destinationOrigins =
			sourceOrigins == null
				? new ArrayList<L2Register>(1)
				: new ArrayList<L2Register>(sourceOrigins);
		destinationOrigins.add(sourceRegister);
		registerOrigins.put(destinationRegister, destinationOrigins);
		for (final L2Register origin : destinationOrigins)
		{
			Set<L2Register> set = invertedOrigins.get(origin);
			if (set == null)
			{
				set = new HashSet<L2Register>();
				invertedOrigins.put(origin, set);
			}
			set.add(destinationRegister);
		}
	}

	/**
	 * Some sort of write to the destinationRegister has taken place.  Moves
	 * are handled differently.
	 *
	 * <p>
	 * Update the {@link #registerOrigins} and {@link #invertedOrigins} maps to
	 * reflect the fact that the destination register is no longer related to
	 * any of its earlier sources.
	 * </p>
	 *
	 * @param destinationRegister The {@link L2Register} being overwritten.
	 */
	public void propagateWriteTo (
		final L2Register destinationRegister)
	{
		// Firstly, the destinationRegister's value is no longer derived
		// from any other register (until and unless the client says which).
		final List<L2Register> origins =
			registerOrigins.get(destinationRegister);
		if (origins != null && !origins.isEmpty())
		{
			for (final L2Register origin : origins)
			{
				invertedOrigins.get(origin).remove(destinationRegister);
			}
			origins.clear();
		}

		// Secondly, any registers that were derived from the old value of
		// the destinationRegister are no longer equivalent to it.
		final Set<L2Register> descendants =
			invertedOrigins.get(destinationRegister);
		if (descendants != null && !descendants.isEmpty())
		{
			for (final L2Register descendant : descendants)
			{
				final List<L2Register> list = registerOrigins.get(descendant);
				assert list.contains(destinationRegister);
				list.remove(destinationRegister);
			}
			descendants.clear();
		}
	}

	/**
	 * Answer a register which contains the same value as the givenRegister.
	 * Use the register which has held this value for the longest time, as
	 * this should eliminate the most redundant moves.
	 *
	 * @param givenRegister
	 *            An L2Register to normalize.
	 * @param givenOperandType
	 *            The type of {@link L2Operand} in which this register occurs.
	 * @return An {@code L2Register} to use instead of the givenRegister.
	 */
	public L2Register normalize (
		final L2Register givenRegister,
		final L2OperandType givenOperandType)
	{
		if (givenOperandType.isSource && !givenOperandType.isDestination)
		{
			final List<L2Register> origins = registerOrigins.get(givenRegister);
			if (origins == null || origins.isEmpty())
			{
				// The origin of the register's value is indeterminate here.
				return givenRegister;
			}
			// Use the register that has been holding this value the longest.
			return origins.get(0);
		}
		return givenRegister;
	}

	/**
	 * A {@linkplain Transformer2 transformer} which converts from a {@linkplain
	 * L2Register register} to another (or the same) register.  At the point
	 * when the transformation happens, a source register is replaced by the
	 * earliest known register to contain the same value, thereby attempting to
	 * eliminate newer registers introduced by moves and decomposable primitive
	 * pairs (e.g., <a,b>[1]).
	 */
	final Transformer2<L2Register, L2OperandType, L2Register> normalizer =
	new Transformer2<L2Register, L2OperandType, L2Register>()
	{
		@Override
		public L2Register value (
			final L2Register register,
			final L2OperandType operandType)
		{
			return normalize(register, operandType);
		}
	};

	/**
	 * Answer an {@link L2ObjectRegister} representing the specified
	 * architectural register.  This is not physical machine level architectural
	 * register, but rather an abstract representation that the {@link
	 * L2Interpreter} uses at execution time.  Not even that, really, since this
	 * is still code generation time.  The register is a placeholder, subject to
	 * register splitting, value propagation and whatever else happens.  Only
	 * level one code actually treats the registers as architectural at runtime.
	 *
	 * @param registerNumber
	 *            Which architectural register to produce.
	 * @return
	 *            An {@link L2ObjectRegister} corresponding to the specified
	 *            architectural register number.
	 */
	private L2ObjectRegister architectural (
		final int registerNumber)
	{
		return architecturalRegisters.get(registerNumber);
	}

	/**
	 * Answer the specified fixed register.
	 *
	 * @param registerEnum The {@link FixedRegister} identifying the register.
	 * @return The {@link L2ObjectRegister} named by the registerEnum.
	 */
	L2ObjectRegister fixed (
		final FixedRegister registerEnum)
	{
		return architecturalRegisters.get(registerEnum.ordinal());
	}

	/**
	 * Answer the register holding the specified continuation slot.  The slots
	 * are the arguments, then the locals, then the stack entries.  The first
	 * argument occurs just after the {@link FixedRegister}s.
	 *
	 * @param slotNumber
	 *            The index into the continuation's slots.
	 * @return
	 *            A register representing that continuation slot.
	 */
	L2ObjectRegister continuationSlot (
		final int slotNumber)
	{
		return architectural(
			L2Translator.firstArgumentRegisterIndex - 1 + slotNumber);
	}

	/**
	 * Answer the register holding the specified argument/local number (the
	 * 1st argument is the 3rd architectural register).
	 *
	 * @param argumentNumber
	 *            The argument number for which the "architectural" register is
	 *            being requested.  If this is greater than the number of
	 *            arguments, then answer the register representing the local
	 *            variable at that position minus the number of registers.
	 * @return A register that represents the specified argument or local.
	 */
	L2ObjectRegister argumentOrLocal (
		final int argumentNumber)
	{
		final AvailObject code = codeOrFail();
		assert argumentNumber <= code.numArgs() + code.numLocals();
		return continuationSlot(argumentNumber);
	}
}
