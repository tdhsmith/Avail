/**
 * ParseNodeTypeDescriptor.java
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

package com.avail.descriptor;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.ParseNodeTypeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.IdentityHashMap;
import java.util.List;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

/**
 * Define the structure and behavior of parse node types.  The parse node types
 * are all parameterized by expression type, but they also have a relationship
 * to each other based on a fiat hierarchy.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ParseNodeTypeDescriptor
extends TypeDescriptor
{
	/**
	 * My slots of type {@linkplain Integer int}.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for caching the hash, and the upper 32 are
		 * for the parse node kind.
		 */
		@HideFieldInDebugger
		HASH_AND_KIND;

		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		static final BitField HASH_OR_ZERO = bitField(HASH_AND_KIND, 0, 32);

		/**
		 * The {@linkplain ParseNodeKind kind} of parse node, encoded as an
		 * {@code int}.
		 */
		@EnumField(describedBy=ParseNodeKind.class)
		static final BitField KIND = bitField(HASH_AND_KIND, 32, 32);
	}

	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The type of value that this expression would produce.
		 */
		EXPRESSION_TYPE,

		/**
		 * The tuple of covariant phrase parameterizations.  See each individual
		 * {@link ParseNodeKind} for a description of how each slot of the tuple
		 * is to be interpreted.
		 */
		COVARIANT_PHRASE_PARAMETERIZATIONS
	}

	public final static class CovariantPhraseParameterization
	{
		/**
		 * The name to print when describing this parameterization of some
		 * phrase type.
		 */
		final String name;

		/**
		 * The most general type this parameterization can have.
		 *
		 * <p>If a phrase type has this type for this parameterization, the
		 * parameterization won't be shown at all in the print representation
		 * of the type.
		 */
		final A_Type mostGeneralType;

		/**
		 * The one-based index of this parameterization within
		 */
		@InnerAccess
		int index = -1;

		CovariantPhraseParameterization (
			final String name,
			final A_Type mostGeneralType)
		{
			this.name = name;
			this.mostGeneralType = mostGeneralType;
		}
	}

	final static CovariantPhraseParameterization co(
		final String name,
		final A_Type mostGeneralType)
	{
		return new CovariantPhraseParameterization(name, mostGeneralType);
	}

	final static CovariantPhraseParameterization[] parameterizations(
		final CovariantPhraseParameterization... parameterizations)
	{
		for (int i = 0; i < parameterizations.length; i++)
		{
			assert parameterizations[i].index == -1;
			parameterizations[i].index = i + 1;
		}
		return parameterizations;
	}

	/**
	 * My hierarchy of kinds of parse nodes.
	 */
	public enum ParseNodeKind
	implements IntegerEnumSlotDescriptionEnum
	{
		/** The root parse node kind. */
		PARSE_NODE("phrase type", null)
		{
			@Override
			CovariantPhraseParameterization[] privateParameterizations ()
			{
				return parameterizations();
			}
		},

		/** The kind of a parse marker. */
		MARKER_NODE("marker phrase type", PARSE_NODE)
		{
			@Override
			CovariantPhraseParameterization[] privateParameterizations ()
			{
				return parameterizations(
					co("value", InstanceMetaDescriptor.anyMeta()));
			}
		},

		/** The abstract parent kind of all expression nodes. */
		EXPRESSION_NODE("expression phrase type", PARSE_NODE)
		{
			@Override
			CovariantPhraseParameterization[] privateParameterizations ()
			{
				return parameterizations();
			}
		},

		/**
		 * The kind of an {@linkplain AssignmentNodeDescriptor assignment node}.
		 */
		ASSIGNMENT_NODE("assignment phrase type", EXPRESSION_NODE)
		{
			@Override
			CovariantPhraseParameterization[] privateParameterizations ()
			{
				return parameterizations(
					co("expression", EXPRESSION_NODE.mostGeneralType()),
					co("declaration", DECLARATION_NODE.mostGeneralType()));
			}
		},

		/** The kind of a {@linkplain BlockNodeDescriptor block node}. */
		BLOCK_NODE("block phrase type", EXPRESSION_NODE)
		{
			@Override
			CovariantPhraseParameterization[] privateParameterizations ()
			{
				return parameterizations(
					co("arguments", TupleTypeDescriptor.oneOrMoreOf(
						ARGUMENT_NODE.mostGeneralType())),
					co("primitive", IntegerRangeTypeDescriptor.inclusive(
						IntegerDescriptor.zero(),
						IntegerDescriptor.fromInt(65535))),
					co("failure",
						PRIMITIVE_FAILURE_REASON_NODE.mostGeneralType()),
					co("label", LABEL_NODE.mostGeneralType()),
					co("statements", SEQUENCE_NODE.mostGeneralType()),
					co("exceptions",
						SetTypeDescriptor.setTypeForSizesContentType(
							IntegerRangeTypeDescriptor.wholeNumbers(),
							ObjectTypeDescriptor.exceptionType())));
			}

			@Override
			A_Type mostGeneralInnerType ()
			{
				return FunctionTypeDescriptor.mostGeneralType();
			}
		},

		/** The kind of a {@linkplain LiteralNodeDescriptor literal node}. */
		LITERAL_NODE("literal node type", EXPRESSION_NODE)
		{
			@Override
			CovariantPhraseParameterization[] privateParameterizations ()
			{
				return parameterizations(
					co("token", TOKEN.o()));
			}

			@Override
			A_Type mostGeneralInnerType ()
			{
				return Types.ANY.o();
			}
		},

		/**
		 * The kind of a {@linkplain ReferenceNodeDescriptor reference node}.
		 */
		REFERENCE_NODE("variable reference phrase type", EXPRESSION_NODE)
		{
			@Override
			CovariantPhraseParameterization[] privateParameterizations ()
			{
				return parameterizations(
					co("declaration", DECLARATION_NODE.mostGeneralType()));
			}

			@Override
			A_Type mostGeneralInnerType ()
			{
				return VariableTypeDescriptor.mostGeneralType();
			}
		},

		/**
		 * The kind of a {@linkplain SuperCastNodeDescriptor super cast node}.
		 */
		SUPER_CAST_NODE("super cast phrase", EXPRESSION_NODE)
		{
			@Override
			CovariantPhraseParameterization[] privateParameterizations ()
			{
				return parameterizations(
					co("expression", EXPRESSION_NODE.create(ANY.o())),
					co("lookup type", InstanceMetaDescriptor.anyMeta()));
			}

			@Override
			A_Type mostGeneralInnerType ()
			{
				return Types.ANY.o();
			}
		},

		/** The kind of a {@linkplain SendNodeDescriptor send node}. */
		SEND_NODE("send phrase type", EXPRESSION_NODE),

		/** The kind of a {@linkplain ListNodeDescriptor list node}. */
		LIST_NODE("list phrase type", EXPRESSION_NODE)
		{
			@Override
			A_Type mostGeneralInnerType ()
			{
				return TupleTypeDescriptor.mostGeneralType();
			}
		},

		/**
		 * The kind of a {@linkplain PermutedListNodeDescriptor permuted list
		 * node}. */
		PERMUTED_LIST_NODE("permuted list phrase type", LIST_NODE)
		{
			@Override
			A_Type mostGeneralInnerType ()
			{
				return TupleTypeDescriptor.mostGeneralType();
			}
		},

		/**
		 * The kind of a {@linkplain VariableUseNodeDescriptor variable use
		 * node}.
		 */
		VARIABLE_USE_NODE("variable use phrase type", EXPRESSION_NODE)
		{
			@Override
			A_Type mostGeneralInnerType ()
			{
				return Types.ANY.o();
			}
		},

		/** A phrase that does not produce a result. */
		STATEMENT_NODE("statement phrase type", PARSE_NODE),

		/** The kind of a {@linkplain SequenceNodeDescriptor sequence node}. */
		SEQUENCE_NODE("sequence phrase type", STATEMENT_NODE),

		/**
		 * The kind of a {@linkplain FirstOfSequenceNodeDescriptor
		 * first-of-sequence node}.
		 */
		FIRST_OF_SEQUENCE_NODE("first-of-sequence phrase type", STATEMENT_NODE),

		/**
		 * The kind of a {@linkplain DeclarationNodeDescriptor declaration
		 * node}.
		 */
		DECLARATION_NODE("declaration phrase type", STATEMENT_NODE),

		/** The kind of an argument declaration node. */
		ARGUMENT_NODE("argument phrase type", DECLARATION_NODE),

		/** The kind of a label declaration node. */
		LABEL_NODE("label phrase type", DECLARATION_NODE),

		/** The kind of a local variable declaration node. */
		LOCAL_VARIABLE_NODE("local variable phrase type", DECLARATION_NODE),

		/** The kind of a local constant declaration node. */
		LOCAL_CONSTANT_NODE("local constant phrase type", DECLARATION_NODE),

		/** The kind of a module variable declaration node. */
		MODULE_VARIABLE_NODE("module variable phrase type", DECLARATION_NODE),

		/** The kind of a module constant declaration node. */
		MODULE_CONSTANT_NODE("module constant phrase type", DECLARATION_NODE),

		/** The kind of a primitive failure reason variable declaration. */
		PRIMITIVE_FAILURE_REASON_NODE(
			"primitive failure reason phrase type", DECLARATION_NODE),

		/**
		 * A statement phrase built from an expression.  At the moment, only
		 * assignments and sends can be expression-as-statement phrases.
		 */
		EXPRESSION_AS_STATEMENT_NODE(
			"expression as statement phrase type", STATEMENT_NODE),

		/** The result of a macro substitution. */
		MACRO_SUBSTITUTION("macro substitution phrase type", PARSE_NODE);

		/**
		 * The kind of parse node that this kind is a child of.
		 */
		final @Nullable ParseNodeKind parentKind;

		/**
		 * Answer the kind of parse node of which this object is the type.
		 *
		 * @return My parent parse node kind.
		 */
		public final @Nullable ParseNodeKind parentKind ()
		{
			return parentKind;
		}

		/**
		 * The enumeration values are responsible for providing an array of
		 * {@link CovariantPhraseParameterization}s that indicate how this kind
		 * of type is to be parameterized.
		 *
		 * @return An array of {@link CovariantPhraseParameterization}s.
		 */
//		abstract CovariantPhraseParameterization[] privateParameterizations ();
		//TODO Remove debug
		CovariantPhraseParameterization[] privateParameterizations () {return null;}

		/**
		 * The most general inner type for this kind of parse node.
		 *
		 * @return The most general inner type for this kind of parse node.
		 */
		A_Type mostGeneralInnerType ()
		{
			return Types.TOP.o();
		}

		/**
		 * The depth of this object in the ParseNodeKind hierarchy.
		 */
		final int depth;

		/**
		 * The most general type for this kind of parse node.
		 */
		private final A_Type mostGeneralType =
			create(mostGeneralInnerType()).makeShared();

		/** The JSON name of this type. */
		final String jsonName;

		/**
		 * The covariant parameterizations of this phrase type.
		 */
		@Nullable CovariantPhraseParameterization[] parameterizations;

		/**
		 * Construct a new {@link ParseNodeKind}.
		 *
		 * @param jsonName
		 *        The JSON name of this type.
		 * @param parentKind
		 *        The kind of parse node of which this is the type.
		 */
		ParseNodeKind (
			final String jsonName,
			final @Nullable ParseNodeKind parentKind)
		{
			this.jsonName = jsonName;
			this.parentKind = parentKind;
			if (parentKind == null)
			{
				depth = 0;
			}
			else
			{
				depth = parentKind.depth + 1;
			}
		}

		/**
		 * Create a {@linkplain ParseNodeTypeDescriptor parse node type} given
		 * the expression type (the type of object produced by the expression).
		 *
		 * @param expressionType
		 *        The type of object that will be produced by an expression
		 *        which is of the type being constructed.
		 * @return The new parse node type, whose kind is the receiver.
		 */
		public final A_Type create (final A_Type expressionType)
		{
			A_Type boundedExpressionType = expressionType;
			final AvailObject type = mutable.create();
			boundedExpressionType = expressionType.typeIntersection(
				mostGeneralInnerType());
			boundedExpressionType.makeImmutable();
			type.setSlot(EXPRESSION_TYPE, boundedExpressionType);
			type.setSlot(KIND, ordinal());
			type.setSlot(
				COVARIANT_PHRASE_PARAMETERIZATIONS, NilDescriptor.nil());
			return type;
		}

		/**
		 * Answer a {@linkplain ParseNodeTypeDescriptor parse node type} whose
		 * kind is the receiver and whose expression type is {@linkplain
		 * TypeDescriptor.Types#TOP top}. This is the most general parse node
		 * type of that kind.
		 *
		 * @return The new parse node type, whose kind is the receiver and whose
		 *         expression type is {@linkplain TypeDescriptor.Types#TOP top}.
		 */
		public final A_Type mostGeneralType ()
		{
			return mostGeneralType;
		}

		/**
		 * Answer the {@link ParseNodeKind} that is the nearest common ancestor
		 * to both the receiver and the argument.
		 *
		 * @param another The other {@link ParseNodeKind}.
		 * @return The nearest common ancestor (a {@link ParseNodeKind}).
		 */
		public final ParseNodeKind commonAncestorWith (
			final ParseNodeKind another)
		{
			ParseNodeKind a = this;
			ParseNodeKind b = another;
			while (a != b)
			{
				final int diff = b.depth - a.depth;
				if (diff <= 0)
				{
					a = a.parentKind();
					assert a != null;
				}
				if (diff >= 0)
				{
					b = b.parentKind();
					assert b != null;
				}
			}
			return a;
		}

		/** An array of all {@link ParseNodeKind} enumeration values. */
		private static final ParseNodeKind[] all = values();

		/**
		 * Answer an array of all {@link ParseNodeKind} enumeration values.
		 *
		 * @return An array of all {@link ParseNodeKind} enum values.  Do not
		 *         modify the array.
		 */
		public static ParseNodeKind[] all ()
		{
			return all;
		}

		/**
		 * An array where index (t1 * #values) + t2 indicates whether t1 is a
		 * subkind of t2.
		 */
		private static final boolean[] compatibility =
			new boolean [all.length * all.length];

		static
		{
			// Populate the entire compatibility matrix.
			for (final ParseNodeKind kind1 : all)
			{
				for (final ParseNodeKind kind2 : all)
				{
					final int index = kind1.ordinal() * all.length
						+ kind2.ordinal();
					final boolean compatible =
						kind1.commonAncestorWith(kind2) == kind2;
					compatibility[index] = compatible;
				}
			}
		}

		/**
		 * Answer whether this is a subkind of (or equal to) the specified
		 * {@link ParseNodeKind}.
		 *
		 * @param purportedParent The kind that may be the ancestor.
		 * @return Whether the receiver descends from the argument.
		 */
		public final boolean isSubkindOf (final ParseNodeKind purportedParent)
		{
			final int index =
				ordinal() * all.length + purportedParent.ordinal();
			return compatibility[index];
		}
	}

	/**
	 * Return the type of object that would be produced by a parse node of this
	 * type.
	 *
	 * @return The {@linkplain TypeDescriptor type} of the {@link AvailObject}
	 *         that will be produced by a parse node of this type.
	 */
	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		return object.slot(EXPRESSION_TYPE);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@linkplain ParseNodeTypeDescriptor parse node types} are equal when they
	 * are of the same kind and have the same expression type.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsParseNodeType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@linkplain ParseNodeTypeDescriptor parse node types} are equal when they
	 * are of the same kind and have the same expression type.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_EqualsParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		return object.parseNodeKind() == aParseNodeType.parseNodeKind()
			&& object.slot(EXPRESSION_TYPE).equals(
				aParseNodeType.expressionType());
 	}

	/**
	 * {@linkplain ParseNodeTypeDescriptor parse nodes} must implement {@link
	* AbstractDescriptor#o_Hash(AvailObject) hash}.
	 */
	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(EXPRESSION_TYPE).hash()
			^ (object.slot(KIND) * multiplier);
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfParseNodeType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		final ParseNodeKind myKind = object.parseNodeKind();
		final ParseNodeKind otherKind = aParseNodeType.parseNodeKind();
		if (otherKind.isSubkindOf(myKind))
		{
			return aParseNodeType.expressionType().isSubtypeOf(
				object.slot(EXPRESSION_TYPE));
		}
		return false;
	}

	/**
	 * Return the {@linkplain ParseNodeKind parse node kind} that this parse
	 * node type implements.
	 *
	 * @return The {@linkplain ParseNodeKind kind} of parse node that the object
	 *         is.
	 */
	@Override @AvailMethod
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		final int ordinal = object.slot(KIND);
		return ParseNodeKind.all()[ordinal];
	}

	@Override @AvailMethod
	boolean o_ParseNodeKindIsUnder (
		final AvailObject object,
		final ParseNodeKind expectedParseNodeKind)
	{
		return object.parseNodeKindIsUnder(expectedParseNodeKind);
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.PARSE_NODE_TYPE;
	}

	@Override @AvailMethod
	A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		return another.typeIntersectionOfParseNodeType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		final ParseNodeKind myKind = object.parseNodeKind();
		final ParseNodeKind otherKind = aParseNodeType.parseNodeKind();
		final A_Type myExpressionType = object.slot(EXPRESSION_TYPE);
		final A_Type otherExpressionType = aParseNodeType.expressionType();
		if (myKind.isSubkindOf(otherKind)
			&& myExpressionType.isSubtypeOf(otherExpressionType))
		{
			return object;
		}
		if (otherKind.isSubkindOf(myKind)
			&& otherExpressionType.isSubtypeOf(myExpressionType))
		{
			return aParseNodeType;
		}
		final ParseNodeKind ancestorKind = myKind.commonAncestorWith(otherKind);
		if (ancestorKind == myKind || ancestorKind == otherKind)
		{
			// One kind is the ancestor of the other.  We can work with that.
			final A_Type innerIntersection =
				myExpressionType.typeIntersection(otherExpressionType);
			return (ancestorKind == myKind ? otherKind : myKind).create(
				innerIntersection);
		}
		// There may be a common ancestor, but it isn't one of the supplied
		// kinds.  Since the kinds form a tree, the intersection is impossible.
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
	{
		return another.typeUnionOfParseNodeType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		final ParseNodeKind myKind = object.parseNodeKind();
		final ParseNodeKind otherKind = aParseNodeType.parseNodeKind();
		final A_Type myExpressionType = object.slot(EXPRESSION_TYPE);
		final A_Type otherExpressionType = aParseNodeType.expressionType();
		if (myKind.isSubkindOf(otherKind)
			&& myExpressionType.isSubtypeOf(otherExpressionType))
		{
			return aParseNodeType;
		}
		if (otherKind.isSubkindOf(myKind)
			&& otherExpressionType.isSubtypeOf(myExpressionType))
		{
			return object;
		}
		final ParseNodeKind ancestorKind = myKind.commonAncestorWith(otherKind);
		return ancestorKind.create(
			myExpressionType.typeUnion(otherExpressionType));
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write(object.parseNodeKind().jsonName);
		writer.write("expression type");
		object.slot(EXPRESSION_TYPE).writeTo(writer);
		writer.endObject();
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		final ParseNodeKind kind = object.parseNodeKind();
		if (kind == PARSE_NODE)
		{
			builder.append("phrase");
		}
		else
		{
			final String name = kind.name().toLowerCase()
				.replace("node", "phrase")
				.replace('_', ' ');
			builder.append(name);
		}
		builder.append("⇒");
		object.expressionType().printOnAvoidingIndent(
			builder,
			recursionMap,
			indent + 1);
	}

	/**
	 * Answer a list node type for the given sequence of types.  That's
	 * the type of a list node which when evaluated will produce values
	 * of those corresponding types.
	 *
	 * @param types
	 *        The types of values produced by such a list node's
	 *        corresponding expressions.
	 * @return A list node type.
	 */
	public static A_Type list (final A_Type... types)
	{
		return LIST_NODE.create(TupleTypeDescriptor.forTypes(types));
	}

	/**
	 * Does the specified {@linkplain AvailObject#flattenStatementsInto(List)
	 * flat} {@linkplain List list} of {@linkplain ParseNodeDescriptor parse
	 * nodes} contain only statements?
	 *
	 * TODO MvG - REVISIT to make this work sensibly.  Probably only allow
	 *      statements in a sequence/first-of-sequence, and have blocks hold an
	 *      optional final <em>expression</em>.
	 *
	 * @param flat
	 *        A flattened list of statements.
	 * @param resultType
	 *        The result type of the sequence. Use {@linkplain Types#TOP top}
	 *        if unconcerned about result type.
	 * @return {@code true} if the list contains only statements, {@code false}
	 *         otherwise.
	 */
	public static boolean containsOnlyStatements (
		final List<A_Phrase> flat,
		final A_Type resultType)
	{
		final int statementCount = flat.size();
		for (int i = 0; i < statementCount; i++)
		{
			final A_Phrase statement = flat.get(i);
			assert !statement.parseNodeKindIsUnder(SEQUENCE_NODE);
			final boolean valid;
			if (i + 1 < statementCount)
			{
				valid =
					(statement.parseNodeKindIsUnder(STATEMENT_NODE)
						|| statement.parseNodeKindIsUnder(ASSIGNMENT_NODE)
						|| statement.parseNodeKindIsUnder(SEND_NODE))
					&& statement.expressionType().isTop();
			}
			else
			{
				valid = statement.expressionType().isSubtypeOf(resultType);
			}
			if (!valid)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Construct a new {@link ParseNodeTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public ParseNodeTypeDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link ParseNodeTypeDescriptor}. */
	static final ParseNodeTypeDescriptor mutable =
		new ParseNodeTypeDescriptor(Mutability.MUTABLE);

	@Override
	ParseNodeTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link ParseNodeTypeDescriptor}. */
	private static final ParseNodeTypeDescriptor shared =
		new ParseNodeTypeDescriptor(Mutability.SHARED);

	@Override
	ParseNodeTypeDescriptor immutable ()
	{
		// There is no immutable descriptor.
		return shared;
	}

	@Override
	ParseNodeTypeDescriptor shared ()
	{
		return shared;
	}
}