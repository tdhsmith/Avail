/**
 * L2ValueManifest.java
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
package com.avail.optimizer;

import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.optimizer.values.L2SemanticValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.avail.utility.Nulls.stripNull;
import static java.util.Collections.unmodifiableSet;

/**
 * The {@code L1ValueManifest} maintains a bidirectional mapping between visible
 * registers and {@link L2SemanticValue}s.
 *
 * <p>The mapping is maintained bidirectionally (value → register and
 * register → value), so that registers can be efficiently removed.  This
 * happens at control flow merges, where only the intersection of the semantic
 * values available in each predecessor edge is kept, specifically via the
 * introduction of new registers defined by phi instructions.</p>
 *
 * <p>Note that in any manifest, any particular {@link L2SemanticValue} must
 * have <em>at most one</em> register mapped to it.</p>
 */
public final class L2ValueManifest
{
	/**
	 * The current mapping from {@link L2SemanticValue} to {@link
	 * L2ReadPointerOperand}.  A separate manifest is created to represent
	 * different places in the {@link L2ControlFlowGraph}.  This is mirrored in
	 * the {@link #registerToSemanticValues}, although that uses the {@link
	 * L2Register} directly instead of an {@link L2ReadPointerOperand}.
	 */
	private final Map<L2SemanticValue, L2ReadPointerOperand>
		semanticValueToRegister;

	/**
	 * The current mapping from {@link L2Register} to the {@link
	 * L2SemanticValue}s that it holds.  A separate manifest is created to
	 * represent different places in the {@link L2ControlFlowGraph}.  This is
	 * mirrored in the {@link #semanticValueToRegister}, although that uses an
	 * {@link L2ReadPointerOperand} instead of an {@link L2Register} so that
	 * strengthening tests for regions of code can be represented.
	 */
	private final Map<L2Register, Set<L2SemanticValue>>
		registerToSemanticValues;

	/**
	 * Create an empty manifest.
	 */
	public L2ValueManifest ()
	{
		this.semanticValueToRegister = new HashMap<>();
		this.registerToSemanticValues = new HashMap<>();
	}

	/**
	 * Copy an existing manifest.
	 *
	 * @param originalManifest
	 * 	The original manifest.
	 */
	public L2ValueManifest (final L2ValueManifest originalManifest)
	{
		this.semanticValueToRegister = new HashMap<>(
			originalManifest.semanticValueToRegister);
		this.registerToSemanticValues = new HashMap<>();
		originalManifest.registerToSemanticValues.forEach(
			(k, v) -> registerToSemanticValues.put(k, new HashSet<>(v)));
	}

	/**
	 * Look up the given semantic value, answering the register that holds that
	 * value, if any, otherwise {@code null}.
	 *
	 * @param semanticValue The semantic value to look up.
	 * @return The {@link L2ReadPointerOperand} holding that value.
	 */
	public @Nullable L2ReadPointerOperand semanticValueToRegister (
		final L2SemanticValue semanticValue)
	{
		return semanticValueToRegister.get(semanticValue);
	}

	/**
	 * Look up the given register, answering the semantic values that it holds,
	 * if any, otherwise {@code null}.
	 *
	 * @param register
	 *        The {@link L2Register} to look up.
	 * @return The {@link Set} of {@link L2SemanticValue}s that it currently
	 *         holds.
	 */
	public @Nullable Set<L2SemanticValue> registerToSemanticValues (
		final L2Register register)
	{
		final @Nullable Set<L2SemanticValue> set =
			registerToSemanticValues.get(register);
		return set == null ? null : unmodifiableSet(set);
	}

	/**
	 * Record the fact that the given register now holds this given semantic
	 * value, in addition to any it may have already held.  There must not be
	 * a register already associated with this semantic value.
	 *
	 * @param semanticValue
	 *        The {@link L2SemanticValue} to associate with the register.
	 * @param registerRead
	 *        The {@link L2Register} to associate with the semantic value.
	 */
	public void addBinding (
		final L2SemanticValue semanticValue,
		final L2ReadPointerOperand registerRead)
	{
		assert !semanticValueToRegister.containsKey(semanticValue);
		semanticValueToRegister.put(semanticValue, registerRead);
		final Set<L2SemanticValue> semanticValues =
			registerToSemanticValues.computeIfAbsent(
				registerRead.register(), k -> new HashSet<>());
		semanticValues.add(semanticValue);
	}

	/**
	 * Replace all bindings for the sourceRegister with bindings to the same
	 * {@link L2SemanticValue}s for the destinationRegister.
	 *
	 * @param sourceRegister
	 *        The source of the register-register move.
	 * @param destinationRegister
	 *        The destination of the register-register move.
	 */
	public void replaceRegister (
		final L2ReadPointerOperand sourceRegister,
		final L2WritePointerOperand destinationRegister)
	{
		final @Nullable Set<L2SemanticValue> sourceSemanticValues =
			registerToSemanticValues.get(sourceRegister.register());
		final L2ReadPointerOperand destinationRead = destinationRegister.read();
		if (sourceSemanticValues != null)
		{
			for (final L2SemanticValue semanticValue : sourceSemanticValues)
			{
				assert semanticValueToRegister.get(semanticValue).register()
					== sourceRegister.register();
				semanticValueToRegister.put(semanticValue, destinationRead);
			}
			registerToSemanticValues.remove(sourceRegister.register());
			registerToSemanticValues.put(
				destinationRegister.register(),
				new HashSet<>(sourceSemanticValues));
		}
	}

	/**
	 * Remove all information about registers and semantic values from this
	 * manifest.
	 */
	public void clear ()
	{
		registerToSemanticValues.clear();
		semanticValueToRegister.clear();
	}

	/**
	 * Populate the empty receiver with bindings from the incoming manifests.
	 * Only keep the bindings for {@link L2SemanticValue}s that occur in all
	 * incoming manifests.  Generate phi functions as needed on the provided
	 * {@link L2Translator}.  The phi functions' source registers correspond
	 * positionally with the list of manifests.
	 *
	 * @param manifests
	 *        The list of manifests from which to populate the receiver.
	 * @param translator
	 *        The {@link L1Translator} on which to write any necessary phi
	 *        functions.
	 */
	void populateFromIntersection (
		final List<L2ValueManifest> manifests,
		final L1Translator translator)
	{
		assert semanticValueToRegister.isEmpty();
		assert registerToSemanticValues.isEmpty();
		final int manifestsSize = manifests.size();
		if (manifestsSize == 0)
		{
			return;
		}
		if (manifestsSize == 1)
		{
			final L2ValueManifest soleManifest = manifests.get(0);
			semanticValueToRegister.putAll(
				soleManifest.semanticValueToRegister);
			soleManifest.registerToSemanticValues.forEach(
				(k, v) -> registerToSemanticValues.put(k, new HashSet<>(v)));
			return;
		}
		final Iterator<L2ValueManifest> iterator = manifests.iterator();
		final Set<L2SemanticValue> semanticValues = new HashSet<>(
			iterator.next().semanticValueToRegister.keySet());
		while (iterator.hasNext())
		{
			semanticValues.retainAll(
				iterator.next().semanticValueToRegister.keySet());
		}
		for (final L2SemanticValue semanticValue : semanticValues)
		{
			final List<L2ReadPointerOperand> sources =
				new ArrayList<>(manifests.size());
			final Set<L2Register> distinctRegisters = new HashSet<>();
			@Nullable TypeRestriction restriction = null;
			for (final L2ValueManifest manifest : manifests)
			{
				final L2ReadPointerOperand reader =
					stripNull(manifest.semanticValueToRegister(semanticValue));
				final L2Register register = reader.register();
				sources.add(reader);
				distinctRegisters.add(register);
				restriction = restriction == null
					? reader.restriction()
					: restriction.union(reader.restriction());
			}
			assert restriction != null;
			if (distinctRegisters.size() == 1)
			{
				// All of the incoming edges had the same register bound to the
				// semantic value.
				addBinding(
					semanticValue,
					new L2ReadPointerOperand(
						(L2ObjectRegister) distinctRegisters.iterator().next(),
						restriction));
			}
			else
			{
				// Create a phi function.
				final L2WritePointerOperand newWrite =
					translator.newObjectRegisterWriter(
						restriction.type, restriction.constantOrNull);
				translator.addInstruction(
					L2_PHI_PSEUDO_OPERATION.instance,
					new L2ReadVectorOperand(sources),
					newWrite);
				addBinding(semanticValue, newWrite.read());
			}
		}
	}
}