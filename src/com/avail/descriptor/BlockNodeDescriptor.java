/**
 * BlockNodeDescriptor.java
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

package com.avail.descriptor;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.EnumField;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.Strings;
import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.BlockNodeDescriptor.IntegerSlots.PRIMITIVE;
import static com.avail.descriptor.BlockNodeDescriptor.IntegerSlots
	.STARTING_LINE_NUMBER;
import static com.avail.descriptor.BlockNodeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind
	.MODULE_CONSTANT;
import static com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind
	.MODULE_VARIABLE;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tupleFromList;

/**
 * My instances represent occurrences of blocks (functions) encountered in code.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class BlockNodeDescriptor
extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@linkplain Integer int}.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * A slot containing multiple {@link BitField}s.
		 */
		PRIMITIVE_AND_STARTING_LINE_NUMBER;

		/**
		 * The {@linkplain Primitive primitive} number to invoke for this block.
		 * This is not the {@link Enum#ordinal()} of the primitive, but rather
		 * its {@link Primitive#primitiveNumber}.
		 */
		@EnumField(
			describedBy=Primitive.class,
			lookupMethodName="byPrimitiveNumberOrNull")
		static final BitField PRIMITIVE = bitField(
			PRIMITIVE_AND_STARTING_LINE_NUMBER,
			0,
			32);

		/**
		 * The line number on which this block starts.
		 */
		static final BitField STARTING_LINE_NUMBER = bitField(
			PRIMITIVE_AND_STARTING_LINE_NUMBER,
			32,
			32);
	}

	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The block's tuple of argument declarations.
		 */
		ARGUMENTS_TUPLE,

		/**
		 * The tuple of statements contained in this block.
		 */
		STATEMENTS_TUPLE,

		/**
		 * The type this block is expected to return an instance of.
		 */
		RESULT_TYPE,

		/**
		 * A tuple of variables needed by this block.  This is set after the
		 * {@linkplain BlockNodeDescriptor block node} has already been
		 * created.
		 */
		NEEDED_VARIABLES,

		/**
		 * The block's set of exception types that may be raised.  This set
		 * <em>has not yet been normalized</em> (e.g., removing types that are
		 * subtypes of types that are also present in the set).
		 */
		DECLARED_EXCEPTIONS
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		// Optimize for one-liners...
		final A_Tuple argumentsTuple = object.argumentsTuple();
		final int argCount = argumentsTuple.tupleSize();
		final @Nullable Primitive primitive = object.primitive();
		final A_Tuple statementsTuple = object.statementsTuple();
		final int statementsSize = statementsTuple.tupleSize();
		@Nullable A_Type explicitResultType = object.resultType();
		if (statementsSize >= 1
			&& statementsTuple.tupleAt(statementsSize).expressionType()
				.equals(explicitResultType))
		{
			explicitResultType = null;
		}
		@Nullable A_Set declaredExceptions = object.declaredExceptions();
		if (declaredExceptions.setSize() == 0)
		{
			declaredExceptions = null;
		}
		final boolean endsWithStatement = statementsSize < 1
			|| statementsTuple.tupleAt(statementsSize).expressionType().isTop();
		if (argCount == 0
			&& primitive == null
			&& statementsSize == 1
			&& explicitResultType == null
			&& declaredExceptions == null)
		{
			// See if the lone statement fits on a line.
			final StringBuilder tempBuilder = new StringBuilder();
			statementsTuple.tupleAt(1).printOnAvoidingIndent(
				tempBuilder,
				recursionMap,
				indent);
			if (tempBuilder.indexOf("\n") == -1
				&& tempBuilder.length() < 100)
			{
				builder.append('[');
				builder.append(tempBuilder);
				if (endsWithStatement)
				{
					builder.append(';');
				}
				builder.append(']');
				return;
			}
		}

		// Use multiple lines instead...
		builder.append('[');
		boolean wroteAnything = false;
		if (argCount > 0)
		{
			wroteAnything = true;
			for (int argIndex = 1; argIndex <= argCount; argIndex++)
			{
				Strings.newlineTab(builder, indent);
				argumentsTuple.tupleAt(argIndex).printOnAvoidingIndent(
					builder, recursionMap, indent);
				if (argIndex < argCount)
				{
					builder.append(",");
				}
			}
			Strings.newlineTab(builder, indent - 1);
			builder.append("|");
		}
		boolean skipFailureDeclaration = false;
		if (primitive != null
			&& !primitive.hasFlag(Flag.SpecialReturnConstant)
			&& !primitive.hasFlag(Flag.SpecialReturnSoleArgument)
			&& !primitive.hasFlag(Flag.SpecialReturnGlobalValue))
		{
			wroteAnything = true;
			Strings.newlineTab(builder, indent);
			builder.append("Primitive ");
			builder.append(primitive.name());
			if (!primitive.hasFlag(Flag.CannotFail))
			{
				builder.append(" (");
				statementsTuple.tupleAt(1).printOnAvoidingIndent(
					builder, recursionMap, indent);
				builder.append(")");
				skipFailureDeclaration = true;
			}
			builder.append(";");
		}
		for (int index = 1; index <= statementsSize; index++)
		{
			final A_Phrase statement = statementsTuple.tupleAt(index);
			if (skipFailureDeclaration)
			{
				assert statement.isInstanceOf(
					DECLARATION_NODE.mostGeneralType());
				skipFailureDeclaration = false;
			}
			else
			{
				wroteAnything = true;
				Strings.newlineTab(builder, indent);
				statement.printOnAvoidingIndent(
					builder, recursionMap, indent);
				if (index < statementsSize || endsWithStatement)
				{
					builder.append(";");
				}
			}
		}
		if (wroteAnything)
		{
			Strings.newlineTab(builder, indent - 1);
		}
		builder.append(']');
		if (explicitResultType != null)
		{
			builder.append(" : ");
			builder.append(explicitResultType);
		}
		if (declaredExceptions != null)
		{
			builder.append(" ^ ");
			builder.append(declaredExceptions);
		}
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == NEEDED_VARIABLES;
	}

	@Override @AvailMethod
	A_Tuple o_ArgumentsTuple (final AvailObject object)
	{
		return object.slot(ARGUMENTS_TUPLE);
	}

	@Override @AvailMethod
	A_Tuple o_StatementsTuple (final AvailObject object)
	{
		return object.slot(STATEMENTS_TUPLE);
	}

	@Override @AvailMethod
	A_Type o_ResultType (final AvailObject object)
	{
		return object.slot(RESULT_TYPE);
	}

	@Override @AvailMethod
	A_Tuple o_NeededVariables (final AvailObject object)
	{
		return object.mutableSlot(NEEDED_VARIABLES);
	}

	@Override @AvailMethod
	void o_NeededVariables (
		final AvailObject object,
		final A_Tuple neededVariables)
	{
		object.setMutableSlot(NEEDED_VARIABLES, neededVariables);
	}

	@Override @AvailMethod
	A_Set o_DeclaredExceptions (final AvailObject object)
	{
		return object.slot(DECLARED_EXCEPTIONS);
	}

	@Override @AvailMethod
	@Nullable Primitive o_Primitive (final AvailObject object)
	{
		return Primitive.byNumber(object.slot(PRIMITIVE));
	}

	@Override @AvailMethod
	int o_StartingLineNumber (final AvailObject object)
	{
		return object.slot(STARTING_LINE_NUMBER);
	}

	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		final List<A_Type> argumentTypes =
			new ArrayList<>(object.argumentsTuple().tupleSize());
		for (final A_Phrase argDeclaration : object.argumentsTuple())
		{
			argumentTypes.add(argDeclaration.declaredType());
		}
		return
			functionType(tupleFromList(argumentTypes), object.resultType());
	}

	/**
	 * The expression "[expr]" has no effect, only a value.
	 */
	@Override @AvailMethod
	void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		// No effect.
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_RawFunction compiledBlock =
			object.generateInModule(codeGenerator.module());
		if (object.neededVariables().tupleSize() == 0)
		{
			final A_Function function =
				createFunction(compiledBlock, emptyTuple());
			function.makeImmutable();
			codeGenerator.emitPushLiteral(function);
		}
		else
		{
			codeGenerator.emitCloseCode(
				compiledBlock,
				object.neededVariables());
		}
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		final @Nullable Primitive prim = object.primitive();
		return
			(((object.argumentsTuple().hash() * multiplier
				+ object.statementsTuple().hash()) * multiplier
				+ object.resultType().hash()) * multiplier
				+ object.neededVariables().hash()) * multiplier
				+ (prim == null ? 0 : prim.primitiveNumber) * multiplier
			^ 0x05E6A04A;
	}

	@Override @AvailMethod
	boolean o_EqualsParseNode (
		final AvailObject object,
		final A_Phrase aParseNode)
	{
		return !aParseNode.isMacroSubstitutionNode()
			&& object.parseNodeKind().equals(aParseNode.parseNodeKind())
			&& object.argumentsTuple().equals(aParseNode.argumentsTuple())
			&& object.statementsTuple().equals(aParseNode.statementsTuple())
			&& object.resultType().equals(aParseNode.resultType())
			&& object.neededVariables().equals(aParseNode.neededVariables())
			&& object.primitive() == aParseNode.primitive();
	}

	@Override
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		return BLOCK_NODE;
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> aBlock)
	{
		A_Tuple arguments = object.argumentsTuple();
		for (int i = 1; i <= arguments.tupleSize(); i++)
		{
			arguments = arguments.tupleAtPuttingCanDestroy(
				i, aBlock.valueNotNull(arguments.tupleAt(i)), true);
		}
		object.setSlot(ARGUMENTS_TUPLE, arguments);
		A_Tuple statements = object.statementsTuple();
		for (int i = 1; i <= statements.tupleSize(); i++)
		{
			statements = statements.tupleAtPuttingCanDestroy(
				i, aBlock.valueNotNull(statements.tupleAt(i)), true);
		}
		object.setSlot(STATEMENTS_TUPLE, statements);
	}


	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<A_Phrase> aBlock)
	{
		for (final AvailObject argument : object.argumentsTuple())
		{
			aBlock.value(argument);
		}
		for (final AvailObject statement : object.statementsTuple())
		{
			aBlock.value(statement);
		}
	}

	@Override
	void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Make sure our neededVariables list has up-to-date information about
		// the outer variables that are accessed in me, because they have to be
		// captured when a function is made for me.

		collectNeededVariablesOfOuterBlocks(object);
	}


	/**
	 * Answer an Avail compiled block compiled from the given block node, using
	 * the given {@link AvailCodeGenerator}.
	 *
	 * @param object
	 *        The block phrase.
	 * @param module
	 *        The {@linkplain ModuleDescriptor module} which is intended to hold
	 *        the resulting code.
	 * @return An {@link AvailObject} of type {@linkplain FunctionDescriptor
	 *         function}.
	 */
	@Override @AvailMethod
	A_RawFunction o_GenerateInModule (
		final AvailObject object,
		final A_Module module)
	{
		return AvailCodeGenerator.generateFunction(module, object);
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.BLOCK_PHRASE;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("block phrase");
		final int primitive = object.slot(PRIMITIVE);
		writer.write("primitive");
		writer.write(primitive);
		writer.write("starting line");
		writer.write(object.slot(STARTING_LINE_NUMBER));
		writer.write("arguments");
		object.slot(ARGUMENTS_TUPLE).writeTo(writer);
		writer.write("statements");
		object.slot(STATEMENTS_TUPLE).writeTo(writer);
		writer.write("result type");
		object.slot(RESULT_TYPE).writeTo(writer);
		writer.write("needed variables");
		object.slot(NEEDED_VARIABLES).writeTo(writer);
		writer.write("declared exceptions");
		object.slot(DECLARED_EXCEPTIONS).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("block phrase");
		final int primitive = object.slot(PRIMITIVE);
		writer.write("primitive");
		writer.write(primitive);
		writer.write("starting line");
		writer.write(object.slot(STARTING_LINE_NUMBER));
		writer.write("arguments");
		object.slot(ARGUMENTS_TUPLE).writeSummaryTo(writer);
		writer.write("statements");
		object.slot(STATEMENTS_TUPLE).writeSummaryTo(writer);
		writer.write("result type");
		object.slot(RESULT_TYPE).writeSummaryTo(writer);
		writer.write("needed variables");
		object.slot(NEEDED_VARIABLES).writeSummaryTo(writer);
		writer.write("declared exceptions");
		object.slot(DECLARED_EXCEPTIONS).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Return a {@linkplain List list} of all {@linkplain
	 * DeclarationNodeDescriptor declaration nodes} defined by this block.
	 * This includes arguments, locals, and labels.
	 *
	 * @param object The Avail block node to scan.
	 * @return The list of declarations.
	 */
	private static List<A_Phrase> allLocallyDefinedVariables (
		final A_Phrase object)
	{
		final List<A_Phrase> declarations = new ArrayList<>(10);
		for (final A_Phrase argumentDeclaration : object.argumentsTuple())
		{
			declarations.add(argumentDeclaration);
		}
		declarations.addAll(locals(object));
		declarations.addAll(labels(object));
		return declarations;
	}

	/**
	 * Answer the labels present in this block's list of statements. There is
	 * either zero or one label, and it must be the first statement.
	 *
	 * @param object The block node to examine.
	 * @return A list of between zero and one labels.
	 */
	public static List<A_Phrase> labels (final A_Phrase object)
	{
		final List<A_Phrase> labels = new ArrayList<>(1);
		for (final AvailObject maybeLabel : object.statementsTuple())
		{
			if (maybeLabel.isInstanceOfKind(LABEL_NODE.mostGeneralType()))
			{
				labels.add(maybeLabel);
			}
		}
		return labels;
	}

	/**
	 * Answer the declarations of this block's local variables.  Do not include
	 * the label declaration if present, nor argument declarations.
	 *
	 * @param object The block node to examine.
	 * @return This block's local variable declarations.
	 */
	public static List<A_Phrase> locals (final A_Phrase object)
	{
		final List<A_Phrase> locals = new ArrayList<>(5);
		for (final A_Phrase maybeLocal : object.statementsTuple())
		{
			if (maybeLocal.isInstanceOfKind(DECLARATION_NODE.mostGeneralType())
				&& !maybeLocal.isInstanceOfKind(LABEL_NODE.mostGeneralType()))
			{
				locals.add(maybeLocal);
			}
		}
		return locals;
	}

	/**
	 * Construct a block phrase.
	 *
	 * @param argumentsList
	 *            The {@linkplain List list} of {@linkplain
	 *            DeclarationNodeDescriptor argument declarations}.
	 * @param primitive
	 *            The {@linkplain Primitive#primitiveNumber index} of the
	 *            primitive that the resulting block will invoke.
	 * @param statementsList
	 *            The {@linkplain List list} of statement
	 *            {@linkplain ParseNodeDescriptor nodes}.
	 * @param resultType
	 *            The {@linkplain TypeDescriptor type} that will be returned by
	 *            the block.
	 * @param declaredExceptions
	 *            The {@linkplain SetDescriptor set} of exception types that may
	 *            be raised by this block.  <em>This is not yet normalized.</em>
	 * @param lineNumber
	 *            The line number on which the block starts.
	 * @return
	 *            A block node.
	 */
	public static A_Phrase newBlockNode (
		final List<A_Phrase> argumentsList,
		final int primitive,
		final List<A_Phrase> statementsList,
		final A_Type resultType,
		final A_Set declaredExceptions,
		final int lineNumber)
	{
		return newBlockNode(
			tupleFromList(argumentsList),
			primitive,
			tupleFromList(statementsList),
			resultType,
			declaredExceptions,
			lineNumber);
	}

	/**
	 * Construct a block phrase.
	 *
	 * @param arguments
	 *            The {@linkplain TupleDescriptor tuple} of {@linkplain
	 *            DeclarationNodeDescriptor argument declarations}.
	 * @param primitive
	 *            The index of the primitive that the resulting block will
	 *            invoke.
	 * @param statements
	 *            The {@linkplain TupleDescriptor tuple} of statement
	 *            {@linkplain ParseNodeDescriptor nodes}.
	 * @param resultType
	 *            The {@linkplain TypeDescriptor type} that will be returned by
	 *            the block.
	 * @param declaredExceptions
	 *            The {@linkplain SetDescriptor set} of exception types that may
	 *            be raised by this block.  <em>This is not yet normalized.</em>
	 * @param lineNumber
	 *            The line number of the current module at which this block
	 *            begins.
	 * @return
	 *            A block node.
	 */
	public static AvailObject newBlockNode (
		final A_Tuple arguments,
		final int primitive,
		final A_Tuple statements,
		final A_Type resultType,
		final A_Set declaredExceptions,
		final int lineNumber)
	{
		final List<A_Phrase> flattenedStatements =
			new ArrayList<>(statements.tupleSize() + 3);
		for (final A_Phrase statement : statements)
		{
			statement.flattenStatementsInto(flattenedStatements);
		}
		// Remove useless statements that are just top literals, other than the
		// final statement.  Actually remove any bare literals, not just top.
		for (int index = flattenedStatements.size() - 2; index >= 0; index--)
		{
			final A_BasicObject statement = flattenedStatements.get(index);
			if (statement.isInstanceOfKind(LITERAL_NODE.mostGeneralType()))
			{
				flattenedStatements.remove(index);
			}
		}
		final AvailObject block = mutable.create();
		block.setSlot(ARGUMENTS_TUPLE, arguments);
		block.setSlot(PRIMITIVE, primitive);
		block.setSlot(
			STATEMENTS_TUPLE,
			tupleFromList(flattenedStatements));
		block.setSlot(RESULT_TYPE, resultType);
		block.setSlot(NEEDED_VARIABLES, nil);
		block.setSlot(DECLARED_EXCEPTIONS, declaredExceptions);
		block.setSlot(STARTING_LINE_NUMBER, lineNumber);
		block.makeShared();
		return block;
	}

	/**
	 * Ensure that the block phrase is valid.  Throw an appropriate exception if
	 * it is not.
	 *
	 * @param blockNode
	 *        The block phrase to validate.
	 */
	public static void recursivelyValidate (
		final A_Phrase blockNode)
	{
		treeDoWithParent(blockNode, A_Phrase::validateLocally, null);
		assert blockNode.neededVariables().tupleSize() == 0;
	}

	/**
	 * Figure out what outer variables will need to be captured when a function
	 * for me is built.
	 *
	 * @param object The block phrase to analyze.
	 */
	private void collectNeededVariablesOfOuterBlocks (final AvailObject object)
	{
		final Set<A_Phrase> providedByMe = new HashSet<>();
		providedByMe.addAll(allLocallyDefinedVariables(object));
		final Set<A_Phrase> neededDeclarationsSet = new HashSet<>();
		final List<A_Phrase> neededDeclarations = new ArrayList<>();
		object.childrenDo(new Continuation1<A_Phrase>()
		{
			@Override
			public void value (final @Nullable A_Phrase node)
			{
				assert node != null;
				if (node.parseNodeKindIsUnder(BLOCK_NODE))
				{
					for (final A_Phrase declaration : node.neededVariables())
					{
						if (!providedByMe.contains(declaration)
							&& !neededDeclarationsSet.contains(declaration))
						{
							neededDeclarationsSet.add(declaration);
							neededDeclarations.add(declaration);
						}
					}
					return;
				}
				if (node.parseNodeKindIsUnder(VARIABLE_USE_NODE))
				{
					final A_Phrase declaration = node.declaration();
					if (!providedByMe.contains(declaration)
						&& declaration.declarationKind() != MODULE_VARIABLE
						&& declaration.declarationKind() != MODULE_CONSTANT
						&& !neededDeclarationsSet.contains(declaration))
					{
						neededDeclarationsSet.add(declaration);
						neededDeclarations.add(declaration);
					}
					// Avoid visiting the declaration explicitly, otherwise
					// uses of declarations that have initializations will cause
					// variables used in those initializations to accidentally
					// be captured as well.
					return;
				}
				node.childrenDo(this);
			}
		});
		object.neededVariables(tupleFromList(neededDeclarations));
	}

	/**
	 * Construct a new {@code BlockNodeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private BlockNodeDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.BLOCK_PHRASE_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link BlockNodeDescriptor}. */
	private static final BlockNodeDescriptor mutable =
		new BlockNodeDescriptor(Mutability.MUTABLE);

	@Override
	BlockNodeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link BlockNodeDescriptor}. */
	private static final BlockNodeDescriptor shared =
		new BlockNodeDescriptor(Mutability.SHARED);

	@Override
	BlockNodeDescriptor shared ()
	{
		return shared;
	}
}
