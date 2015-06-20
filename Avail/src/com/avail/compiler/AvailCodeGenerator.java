/**
 * AvailCodeGenerator.java
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

package com.avail.compiler;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind.*;
import java.io.ByteArrayOutputStream;
import java.util.*;
import com.avail.annotations.Nullable;
import com.avail.compiler.instruction.*;
import com.avail.descriptor.*;
import com.avail.descriptor.DeclarationNodeDescriptor.DeclarationKind;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.primitive.*;
import com.avail.interpreter.primitive.privatehelpers.P_340_PushConstant;
import com.avail.interpreter.primitive.privatehelpers.P_341_PushArgument;
import com.avail.interpreter.primitive.privatehelpers.P_342_GetGlobalVariableValue;

/**
 * An {@link AvailCodeGenerator} is used to convert a {@linkplain
 * ParseNodeDescriptor parse tree} into the corresponding {@linkplain
 * CompiledCodeDescriptor compiled code}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailCodeGenerator
{
	/**
	 * The {@linkplain List list} of {@linkplain AvailInstruction instructions}
	 * generated so far.
	 */
	private final List<AvailInstruction> instructions =
		new ArrayList<>(10);

	/**
	 * The number of arguments with which the resulting {@linkplain
	 * CompiledCodeDescriptor compiled code} will be invoked.
	 */
	private final int numArgs;

	/**
	 * A mapping from local variable/constant/argument/label declarations to
	 * index.
	 */
	private final Map<A_Phrase, Integer> varMap =
		new HashMap<>();

	/**
	 * A mapping from lexically captured variable/constant/argument/label
	 * declarations to the index within the list of outer variables that must
	 * be provided when creating a function from the compiled code.
	 */
	private final Map<A_Phrase, Integer> outerMap =
		new HashMap<>();

	/**
	 * The list of literal objects that have been encountered so far.
	 */
	private final List<A_BasicObject> literals =
		new ArrayList<>(10);

	/**
	 * The current stack depth, which is the number of objects that have been
	 * pushed but not yet been popped at this point in the list of instructions.
	 */
	private int depth = 0;

	/**
	 * The maximum stack depth that has been encountered so far.
	 */
	private int maxDepth = 0;

	/**
	 * A mapping from {@link DeclarationKind#LABEL label} to {@link AvailLabel},
	 * a pseudo-instruction.
	 */
	private final Map<A_Phrase, AvailLabel> labelInstructions =
		new HashMap<>();

	/**
	 * The type of result that should be generated by running the code.
	 */
	private final A_Type resultType;

	/**
	 * The {@linkplain SetDescriptor set} of {@linkplain ObjectTypeDescriptor
	 * exceptions} that this code may raise.
	 */
	private final A_Set exceptionSet;

	/**
	 * Which {@linkplain Primitive primitive VM operation} should be invoked, or
	 * zero if none.
	 */
	private @Nullable Primitive primitive;

	/**
	 * The module in which this code occurs.
	 */
	private final A_Module module;

	/**
	 * The line number on which this code starts.
	 */
	private final int lineNumber;

	/**
	 * Answer the index of the literal, adding it if not already present.
	 *
	 * @param aLiteral The literal to look up.
	 * @return The index of the literal.
	 */
	public int indexOfLiteral (
		final A_BasicObject aLiteral)
	{

		int index;
		index = literals.indexOf(aLiteral) + 1;
		if (index == 0)
		{
			literals.add(aLiteral);
			index = literals.size();
		}
		return index;
	}

	/**
	 * Answer the number of arguments that the code under construction accepts.
	 *
	 * @return The code's number of arguments.
	 */
	public int numArgs ()
	{
		return numArgs;
	}

	/**
	 * @return The module in which code generation is deemed to take place.
	 */
	public A_Module module ()
	{
		return module;
	}

	/**
	 * Generate a {@linkplain FunctionDescriptor function} with the supplied
	 * properties.
	 *
	 * @param module The module in which the function is defined.
	 * @param argumentsTuple The tuple of argument declarations.
	 * @param primitive The {@link Primitive} or {@code null}.
	 * @param locals The list of local declarations.
	 * @param labels The list of (zero or one) label declarations.
	 * @param outerVariables Any needed outer variable declarations.
	 * @param statementsTuple A tuple of statement phrases.
	 * @param resultType The return type of the function.
	 * @param declaredExceptions The declared exception set of the function.
	 * @param startingLineNumber The line number of the module at which the
	 *                           function is purported to begin.
	 * @return A function.
	 */
	public static A_RawFunction generateFunction (
		final A_Module module,
		final A_Tuple argumentsTuple,
		final @Nullable Primitive primitive,
		final List<? extends A_Phrase> locals,
		final List<? extends A_Phrase> labels,
		final A_Tuple outerVariables,
		final A_Tuple statementsTuple,
		final A_Type resultType,
		final A_Set declaredExceptions,
		final int startingLineNumber)
	{
		final AvailCodeGenerator generator = new AvailCodeGenerator(
			module,
			argumentsTuple,
			primitive,
			locals,
			labels,
			outerVariables,
			statementsTuple,
			resultType,
			declaredExceptions,
			startingLineNumber);
		generator.stackShouldBeEmpty();
		final int statementsCount = statementsTuple.tupleSize();
		if (statementsCount == 0
			&& (primitive == null || primitive.canHaveNybblecodes()))
		{
			generator.emitPushLiteral(NilDescriptor.nil());
		}
		else
		{
			for (int index = 1; index < statementsCount; index++)
			{
				statementsTuple.tupleAt(index).emitEffectOn(generator);
				generator.stackShouldBeEmpty();
			}
			if (statementsCount > 0)
			{
				final A_Phrase lastStatement =
					statementsTuple.tupleAt(statementsCount);
				if (lastStatement.parseNodeKindIsUnder(LABEL_NODE)
					|| (lastStatement.parseNodeKindIsUnder(ASSIGNMENT_NODE)
						&& lastStatement.expressionType().isTop()))
				{
					// Either the block 1) ends with the label declaration or
					// 2) is top-valued and ends with an assignment. Push the
					// nil object as the return value.
					lastStatement.emitEffectOn(generator);
					generator.emitPushLiteral(NilDescriptor.nil());
				}
				else
				{
					lastStatement.emitValueOn(generator);
				}
			}
		}
		return generator.endBlock();
	}

	/**
	 * Set up code generation of a raw function.
	 *
	 * @param module The module in which the function is defined.
	 * @param argumentsTuple The tuple of argument declarations.
	 * @param thePrimitive The {@link Primitive} or {@code null}.
	 * @param locals The list of local declarations.
	 * @param labels The list of (zero or one) label declarations.
	 * @param outerVariables Any needed outer variable declarations.
	 * @param statementsTuple A tuple of statement phrases.
	 * @param resultType The return type of the function.
	 * @param declaredException The declared exception set of the function.
	 * @param startingLineNumber The line number of the module at which the
	 *                           function is purported to begin.
	 */
	private AvailCodeGenerator (
		final A_Module module,
		final A_Tuple argumentsTuple,
		final @Nullable Primitive thePrimitive,
		final List<? extends A_Phrase> locals,
		final List<? extends A_Phrase> labels,
		final A_Tuple outerVariables,
		final A_Tuple statementsTuple,
		final A_Type resultType,
		final A_Set declaredException,
		final int startingLineNumber)
	{
		this.module = module;
		numArgs = argumentsTuple.tupleSize();
		for (final A_Phrase argumentDeclaration : argumentsTuple)
		{
			varMap.put(argumentDeclaration, varMap.size() + 1);
		}
		primitive = thePrimitive;
		for (final A_Phrase local : locals)
		{
			varMap.put(local, varMap.size() + 1);
		}
		for (final AvailObject outerVar : outerVariables)
		{
			outerMap.put(outerVar, outerMap.size() + 1);
		}
		for (final A_Phrase label : labels)
		{
			labelInstructions.put(label, new AvailLabel());
		}
		this.resultType = resultType;
		this.exceptionSet = declaredException;
		this.lineNumber = startingLineNumber;
	}

	/**
	 * Finish compilation of the block, answering the resulting compiledCode
	 * object.
	 *
	 * @return A {@linkplain CompiledCodeDescriptor compiled code} object.
	 */
	private A_RawFunction endBlock ()
	{
		fixFinalUses();
		final ByteArrayOutputStream nybbles = new ByteArrayOutputStream(50);
		// Detect blocks that immediately return a constant and mark them with a
		// special primitive number.
		if (primitive == null && instructions.size() == 1)
		{
			final AvailInstruction onlyInstruction = instructions.get(0);
			if (onlyInstruction instanceof AvailPushLiteral
				&& ((AvailPushLiteral)onlyInstruction).index() == 1)
			{
				primitive(P_340_PushConstant.instance);
			}
			if (numArgs() == 1
				&& onlyInstruction instanceof AvailPushLocalVariable
				&& ((AvailPushLocalVariable)onlyInstruction).index() == 1)
			{
				primitive(P_341_PushArgument.instance);
			}
			// Only target module constants, not module variables. Module
			// variables can be unassigned, and reading an unassigned module
			// variable must fail appropriately.
			if (onlyInstruction instanceof AvailGetLiteralVariable
				&& ((AvailGetLiteralVariable)onlyInstruction).index() == 1
				&& literals.get(0).isInitializedWriteOnceVariable())
			{
				primitive(
					P_342_GetGlobalVariableValue.instance);
			}
		}
		// Make sure we're not closing over variables that don't get used.
		final BitSet unusedOuters = new BitSet(outerMap.size());
		unusedOuters.flip(0, outerMap.size());
		for (final AvailInstruction instruction : instructions)
		{
			if (instruction.isOuterUse())
			{
				final int i = ((AvailInstructionWithIndex)instruction).index();
				unusedOuters.clear(i - 1);
			}
			instruction.writeNybblesOn(nybbles);
		}
		if (!unusedOuters.isEmpty())
		{
			final Set<A_Phrase> unusedOuterDeclarations = new HashSet<>();
			for (final Map.Entry<A_Phrase, Integer> entry : outerMap.entrySet())
			{
				if (unusedOuters.get(entry.getValue() - 1))
				{
					unusedOuterDeclarations.add(entry.getKey());
				}
			}
			assert false
				: "Some outers were unused: " + unusedOuterDeclarations;
		}
		final List<Integer> nybblesArray = new ArrayList<>();
		for (final byte nybble : nybbles.toByteArray())
		{
			nybblesArray.add(Integer.valueOf(nybble));
		}
		final A_Tuple nybbleTuple = TupleDescriptor.fromIntegerList(
			nybblesArray);
		nybbleTuple.makeShared();
		assert resultType.isType();
		final A_Type[] argsArray = new A_Type[numArgs];
		final A_Type[] localsArray =
			new A_Type[varMap.size() - numArgs];
		for (final Map.Entry<A_Phrase, Integer> entry : varMap.entrySet())
		{
			final int i = entry.getValue();
			final A_Type argDeclType = entry.getKey().declaredType();
			if (i <= numArgs)
			{
				assert argDeclType.isType();
				argsArray[i - 1] = argDeclType;
			}
			else
			{
				localsArray[i - numArgs - 1] =
					VariableTypeDescriptor.wrapInnerType(argDeclType);
			}
		}
		final A_Tuple argsTuple = TupleDescriptor.from(argsArray);
		final A_Tuple localsTuple = TupleDescriptor.from(localsArray);
		final A_Type [] outerArray = new A_Type[outerMap.size()];
		for (final Map.Entry<A_Phrase, Integer> entry : outerMap.entrySet())
		{
			final int i = entry.getValue();
			final A_Phrase argDecl = entry.getKey();
			final A_Type argDeclType = argDecl.declaredType();
			final DeclarationKind kind = argDecl.declarationKind();
			if (kind == ARGUMENT || kind == LABEL)
			{
				outerArray[i - 1] = argDeclType;
			}
			else
			{
				outerArray[i - 1] =
					VariableTypeDescriptor.wrapInnerType(argDeclType);
			}
		}
		final A_Tuple outerTuple = TupleDescriptor.from(outerArray);
		final A_Type functionType =
			FunctionTypeDescriptor.create(argsTuple, resultType, exceptionSet);
		final A_RawFunction code = CompiledCodeDescriptor.create(
			nybbleTuple,
			varMap.size() - numArgs,
			maxDepth,
			functionType,
			primitive,
			TupleDescriptor.fromList(literals),
			localsTuple,
			outerTuple,
			module,
			lineNumber);
		code.makeImmutable();
		return code;
	}

	/**
	 * Decrease the tracked stack depth by the given amount.
	 *
	 * @param delta The number of things popped off the stack.
	 */
	public void decreaseDepth (
		final int delta)
	{
		depth -= delta;
		assert depth >= 0
			: "Inconsistency - Generated code would pop too much.";
	}

	/**
	 * Increase the tracked stack depth by the given amount.
	 *
	 * @param delta The number of things pushed onto the stack.
	 */
	public void increaseDepth (
		final int delta)
	{
		depth += delta;
		if (depth > maxDepth)
		{
			maxDepth = depth;
		}
	}

	/**
	 * Verify that the stack is empty at this point.
	 */
	public void stackShouldBeEmpty ()
	{
		assert depth == 0 : "The stack should be empty here";
	}

	/**
	 * Write a regular multimethod call.  I expect my arguments to have been
	 * pushed already.
	 *
	 * @param nArgs The number of arguments that the method accepts.
	 * @param bundle The message bundle for the method in which to look up the
	 *               method definition being invoked.
	 * @param returnType The expected return type of the call.
	 */
	public void emitCall (
		final int nArgs,
		final A_Bundle bundle,
		final A_Type returnType)
	{
		final int messageIndex = indexOfLiteral(bundle);
		final int returnIndex = indexOfLiteral(returnType);
		instructions.add(new AvailCall(messageIndex, returnIndex));
		// Pops off arguments.
		decreaseDepth(nArgs);
		// Pushes expected return type, to be overwritten by return value.
		increaseDepth(1);
	}

	/**
	 * Write a super-call.  I expect my arguments and their types to have been
	 * pushed already (interleaved).
	 *
	 * @param nArgs
	 *        The number of arguments that the method accepts.
	 * @param bundle
	 *        The message bundle for the method in which to look up the method
	 *        definition being invoked.
	 * @param returnType
	 *        The expected return type of the call.
	 * @param superUnionType
	 *        The tuple type used to direct method lookup.
	 */
	public void emitSuperCall (
		final int nArgs,
		final A_Bundle bundle,
		final A_Type returnType,
		final A_Type superUnionType)
	{
		final int messageIndex = indexOfLiteral(bundle);
		final int returnIndex = indexOfLiteral(returnType);
		final int superUnionIndex = indexOfLiteral(superUnionType);
		instructions.add(
			new AvailSuperCall(messageIndex, returnIndex, superUnionIndex));
		// Pops all arguments.
		decreaseDepth(nArgs);
		// Pushes expected return type, to be overwritten by return value.
		increaseDepth(1);
	}

	/**
	 * Create a function from {@code CompiledCodeDescriptor compiled code} and
	 * the pushed outer (lexically bound) variables.
	 *
	 * @param compiledCode
	 *        The code from which to make a function.
	 * @param neededVariables
	 *        A {@linkplain TupleDescriptor tuple} of {@linkplain
	 *        DeclarationNodeDescriptor declarations} of variables that the code
	 *        needs to access.
	 */
	public void emitCloseCode (
		final A_RawFunction compiledCode,
		final A_Tuple neededVariables)
	{
		for (final A_Phrase variableDeclaration : neededVariables)
		{
			emitPushLocalOrOuter(variableDeclaration);
		}
		final int codeIndex = indexOfLiteral(compiledCode);
		instructions.add(new AvailCloseCode(
			neededVariables.tupleSize(),
			codeIndex));
		// Copied variables are popped.
		decreaseDepth(neededVariables.tupleSize());
		// Function is pushed.
		increaseDepth(1);
	}

	/**
	 * Emit code to duplicate the element at the top of the stack.
	 */
	public void emitDuplicate ()
	{
		increaseDepth(1);
		instructions.add(new AvailDuplicate());
	}

	/**
	 * Emit code to get the value of a literal variable.
	 *
	 * @param aLiteral
	 *            The {@linkplain VariableDescriptor variable} that should have
	 *            its value extracted.
	 */
	public void emitGetLiteral (
		final A_BasicObject aLiteral)
	{
		increaseDepth(1);
		final int index = indexOfLiteral(aLiteral);
		instructions.add(new AvailGetLiteralVariable(index));
	}

	/**
	 * Emit code to get the value of a local or outer (captured) variable.
	 *
	 * @param localOrOuter
	 *            The {@linkplain DeclarationNodeDescriptor declaration} of the
	 *            variable that should have its value extracted.
	 */
	public void emitGetLocalOrOuter (
		final A_BasicObject localOrOuter)
	{
		increaseDepth(1);
		if (varMap.containsKey(localOrOuter))
		{
			instructions.add(new AvailGetLocalVariable(
				varMap.get(localOrOuter)));
			return;
		}
		if (outerMap.containsKey(localOrOuter))
		{
			instructions.add(new AvailGetOuterVariable(
				outerMap.get(localOrOuter)));
			return;
		}
		assert !labelInstructions.containsKey(localOrOuter)
			: "This case should have been handled a different way!";
		assert false : "Consistency error - unknown variable.";
	}

	/**
	 * Emit a {@linkplain DeclarationNodeDescriptor declaration} of a {@link
	 * DeclarationKind#LABEL label} for the current block.
	 *
	 * @param labelNode The label declaration.
	 */
	public void emitLabelDeclaration (
		final A_BasicObject labelNode)
	{
		assert instructions.isEmpty()
		: "Label must be first statement in block";
		// stack is unaffected.
		instructions.add(labelInstructions.get(labelNode));
	}

	/**
	 * Emit code to create a {@linkplain TupleDescriptor tuple} from the top N
	 * items on the stack.
	 *
	 * @param count How many pushed items to pop for the new tuple.
	 */
	public void emitMakeTuple (
		final int count)
	{
		instructions.add(new AvailMakeTuple(count));
		decreaseDepth(count);
		increaseDepth(1);
	}

	/**
	 * Emit code to permute the top N items on the stack with the given
	 * N-element permutation.
	 *
	 * @param permutation
	 *        A tuple of one-based integers that forms a permutation.
	 */
	public void emitPermute (final A_Tuple permutation)
	{
		final int index = indexOfLiteral(permutation);
		instructions.add(new AvailPermute(index));
	}

	/**
	 * Emit code to pop the top value from the stack.
	 */
	public void emitPop ()
	{
		instructions.add(new AvailPop());
		decreaseDepth(1);
	}

	/**
	 * Emit code to push a literal object onto the stack.
	 *
	 * @param aLiteral The object to push.
	 */
	public void emitPushLiteral (
		final A_BasicObject aLiteral)
	{
		increaseDepth(1);
		final int index = indexOfLiteral(aLiteral);
		instructions.add(new AvailPushLiteral(index));
	}

	/**
	 * Push a variable.  It can be local to the current block or defined in an
	 * outer scope.
	 *
	 * @param variableDeclaration The variable declaration.
	 */
	public void emitPushLocalOrOuter (
		final A_BasicObject variableDeclaration)
	{
		increaseDepth(1);
		if (varMap.containsKey(variableDeclaration))
		{
			instructions.add(
				new AvailPushLocalVariable(varMap.get(variableDeclaration)));
			return;
		}
		if (outerMap.containsKey(variableDeclaration))
		{
			instructions.add(
				new AvailPushOuterVariable(outerMap.get(variableDeclaration)));
			return;
		}
		assert labelInstructions.containsKey(variableDeclaration)
			: "Consistency error - unknown variable.";
		instructions.add(new AvailPushLabel());
	}

	/**
	 * Emit code to pop the stack and write the popped value into a literal
	 * variable.
	 *
	 * @param aLiteral The variable in which to write.
	 */
	public void emitSetLiteral (
		final A_BasicObject aLiteral)
	{
		final int index = indexOfLiteral(aLiteral);
		instructions.add(new AvailSetLiteralVariable(index));
		//  Value to assign has been popped.
		decreaseDepth(1);
	}

	/**
	 * Emit code to pop the stack and write into a local or outer variable.
	 *
	 * @param localOrOuter
	 *            The {@linkplain DeclarationNodeDescriptor declaration} of the
	 *            {@link DeclarationKind#LOCAL_VARIABLE local} or outer variable
	 *            in which to write.
	 */
	public void emitSetLocalOrOuter (
		final A_BasicObject localOrOuter)
	{
		decreaseDepth(1);
		if (varMap.containsKey(localOrOuter))
		{
			instructions.add(
				new AvailSetLocalVariable(varMap.get(localOrOuter)));
			return;
		}
		if (outerMap.containsKey(localOrOuter))
		{
			instructions.add(
				new AvailSetOuterVariable(outerMap.get(localOrOuter)));
			return;
		}
		assert !labelInstructions.containsKey(localOrOuter)
			: "You can't assign to a label!";
		assert false : "Consistency error - unknown variable.";
	}

	/**
	 * Set the primitive number to write in the generated code.  A failed
	 * attempt at running the primitive will be followed by running the level
	 * one code (nybblecodes) that this class generates.
	 *
	 * @param thePrimitive The {@link Primitive} or {@code null}.
	 */
	public void primitive (
		final Primitive thePrimitive)
	{
		assert primitive == null : "Primitive was already set";
		primitive = thePrimitive;
	}

	/**
	 * Figure out which uses of local and outer variables are final uses.  This
	 * interferes with the concept of labels in the following way.  We now only
	 * allow labels as the first statement in a block, so you can only restart
	 * or exit a continuation (not counting the debugger usage, which shouldn't
	 * affect us).  Restarting requires only the arguments and outer variables
	 * to be preserved, as all local variables are recreated (unassigned) on
	 * restart.  Exiting doesn't require anything of any non-argument locals, so
	 * no problem there.  Note that after the last place in the code where the
	 * continuation is constructed we don't even need any arguments or outer
	 * variables unless the code after this point actually uses the arguments.
	 * We'll be a bit conservative here and simply clean up those arguments and
	 * outer variables which are used after the last continuation construction
	 * point, at their final use points, while always cleaning up final uses of
	 * local non-argument variables.
	 */
	public void fixFinalUses ()
	{
		List<AvailVariableAccessNote> localData;
		List<AvailVariableAccessNote> outerData;
		localData = new ArrayList<AvailVariableAccessNote>(
			Arrays.asList(new AvailVariableAccessNote[varMap.size()]));
		outerData = new ArrayList<AvailVariableAccessNote>(
			Arrays.asList(new AvailVariableAccessNote[outerMap.size()]));
		for (int index = 1, end = instructions.size(); index <= end; index++)
		{
			final AvailInstruction instruction = instructions.get(index - 1);
			instruction.fixFlagsUsingLocalDataOuterDataCodeGenerator(
				localData,
				outerData,
				this);
		}
		final @Nullable Primitive p = primitive;
		if (p != null)
		{
			// If necessary, then prevent clearing of the primitive failure
			// variable after its last usage.
			if (p.hasFlag(Flag.PreserveFailureVariable))
			{
				assert !p.hasFlag(Flag.CannotFail);
				final AvailInstruction fakeFailureVariableUse =
					new AvailGetLocalVariable(numArgs + 1);
				fakeFailureVariableUse
					.fixFlagsUsingLocalDataOuterDataCodeGenerator(
						localData,
						outerData,
						this);
			}
			// If necessary, then prevent clearing of the primitive arguments
			// after their last usage.
			if (p.hasFlag(Flag.PreserveArguments))
			{
				for (int index = 1; index <= numArgs; index++)
				{
					final AvailInstruction fakeArgumentUse =
						new AvailPushLocalVariable(index);
					fakeArgumentUse
						.fixFlagsUsingLocalDataOuterDataCodeGenerator(
							localData,
							outerData,
							this);
				}
			}
		}
	}
}
