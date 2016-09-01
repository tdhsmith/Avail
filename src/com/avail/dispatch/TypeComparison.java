/**
 * TypeComparison.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.dispatch;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.BottomTypeDescriptor;
import com.avail.descriptor.DefinitionDescriptor;

import java.util.List;

/**
 * Answer the relationship between two signatures, the argument tuple
 * types of function types representing (1) a criterion to test, and (2)
 * a definition's signature to be classified.
 */
enum TypeComparison
{
	/**
	 * The definition's signature equals the criterion.
	 */
	SAME_TYPE
		{
			@Override
			public <Element extends A_BasicObject> void applyEffect (
				final Element undecidedDefinition,
				final List<? super Element> ifTruePositiveDefinitions,
				final List<? super Element> ifTrueUndecidedDefinitions,
				final List<? super Element> ifFalseUndecidedDefinitions)
			{
				ifTruePositiveDefinitions.add(undecidedDefinition);
			}
		},

	/**
	 * The definition is a proper ancestor of the criterion.
	 */
	PROPER_ANCESTOR_TYPE
		{
			@Override
			public <Element extends A_BasicObject> void applyEffect (
				final Element undecidedDefinition,
				final List<? super Element> ifTruePositiveDefinitions,
				final List<? super Element> ifTrueUndecidedDefinitions,
				final List<? super Element> ifFalseUndecidedDefinitions)
			{
				ifTruePositiveDefinitions.add(undecidedDefinition);
				ifFalseUndecidedDefinitions.add(undecidedDefinition);
			}
		},

	/**
	 * The definition is a proper descendant of the criterion.
	 */
	PROPER_DESCENDANT_TYPE
		{
			@Override
			public <Element extends A_BasicObject> void applyEffect (
				final Element undecidedDefinition,
				final List<? super Element> ifTruePositiveDefinitions,
				final List<? super Element> ifTrueUndecidedDefinitions,
				final List<? super Element> ifFalseUndecidedDefinitions)
			{
				ifTrueUndecidedDefinitions.add(undecidedDefinition);
			}
		},


	/**
	 * The definition's signature and the criterion are not directly
	 * related, but may share subtypes other than {@linkplain
	 * BottomTypeDescriptor bottom} (⊥).
	 */
	UNRELATED_TYPE
		{
			@Override
			public <Element extends A_BasicObject> void applyEffect (
				final Element undecidedDefinition,
				final List<? super Element> ifTruePositiveDefinitions,
				final List<? super Element> ifTrueUndecidedDefinitions,
				final List<? super Element> ifFalseUndecidedDefinitions)
			{
				ifTrueUndecidedDefinitions.add(undecidedDefinition);
				ifFalseUndecidedDefinitions.add(undecidedDefinition);
			}
		},


	/**
	 * The definition's signature and the criterion have ⊥ as their
	 * nearest common descendant.  Thus, there are no tuples of actual
	 * arguments that satisfy both signatures simultaneously.  This is
	 * a useful distinction from {@link #UNRELATED_TYPE}, since a
	 * successful test against the criterion <em>eliminates</em> the
	 * other definition from being considered possible.
	 */
	DISJOINT_TYPE
		{
			@Override
			public <Element extends A_BasicObject> void applyEffect (
				final Element undecidedDefinition,
				final List<? super Element> ifTruePositiveDefinitions,
				final List<? super Element> ifTrueUndecidedDefinitions,
				final List<? super Element> ifFalseUndecidedDefinitions)
			{
				ifFalseUndecidedDefinitions.add(undecidedDefinition);
			}
		};

	/**
	 * Conditionally augment the supplied lists with the provided
	 * undecided {@linkplain DefinitionDescriptor definition}.  The
	 * decision of which lists to augment depends on this instance,
	 * which is the result of a previous {@linkplain #compare(
	 * A_Type, A_Type) comparison} between the two signatures.
	 *
	 * @param undecidedDefinition
	 *            A {@linkplain DefinitionDescriptor definition} whose
	 *            applicability has not yet been decided at the current
	 *            position in the {@link LookupTree}.
	 * @param ifTruePositiveDefinitions
	 *            A list of definitions that will be applicable to some
	 *            arguments if the arguments meet the criterion.
	 * @param ifTrueUndecidedDefinitions
	 *            A list of definitions that will be undecided for some
	 *            arguments if the arguments meet the criterion.
	 * @param ifFalseUndecidedDefinitions
	 *            A list of definitions that will be applicable to some
	 *            arguments if the arguments do not meet the criterion.
	 */
	public abstract <Element extends A_BasicObject> void applyEffect (
		final Element undecidedDefinition,
		final List<? super Element> ifTruePositiveDefinitions,
		final List<? super Element> ifTrueUndecidedDefinitions,
		final List<? super Element> ifFalseUndecidedDefinitions);

	/**
	 * Compare two signatures (tuple types).  The first is the
	 * criterion, which will eventually be tested against arguments.
	 * The second signature is the one being compared by specificity
	 * with the criterion.
	 *
	 * @param criterionTupleType
	 *            The criterion signature to test against.
	 * @param someTupleType
	 *            A signature to test against the criterion signature.
	 * @return A TypeComparison representing the relationship between
	 *         the criterion and the other signature.
	 */
	public static TypeComparison compare (
		final A_Type criterionTupleType,
		final A_Type someTupleType)
	{
		assert criterionTupleType.isTupleType();
		assert someTupleType.isTupleType();
		final A_Type intersection =
			criterionTupleType.typeIntersection(someTupleType);
		if (intersection.isBottom())
		{
			return DISJOINT_TYPE;
		}
		final boolean below =
			someTupleType.isSubtypeOf(criterionTupleType);
		final boolean above =
			criterionTupleType.isSubtypeOf(someTupleType);
		return
			below
				? (above ? SAME_TYPE : PROPER_DESCENDANT_TYPE)
				: (above ? PROPER_ANCESTOR_TYPE : UNRELATED_TYPE);
	}
}
