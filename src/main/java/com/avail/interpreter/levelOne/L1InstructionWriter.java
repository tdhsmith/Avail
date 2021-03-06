/*
 * L1InstructionWriter.java
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

package com.avail.interpreter.levelOne;

import com.avail.annotations.InnerAccess;
import com.avail.descriptor.*;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.avail.descriptor.CompiledCodeDescriptor.newCompiledCode;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.NybbleTupleDescriptor.generateNybbleTupleFrom;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.TupleDescriptor.tupleFromIntegerList;
import static com.avail.utility.Nulls.stripNull;

/**
 * An instance of this class can be used to construct a {@linkplain
 * CompiledCodeDescriptor compiled code object} without detailed knowledge of
 * the level one nybblecode instruction set.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L1InstructionWriter
{
	/**
	 * The stream of nybbles that have been generated thus far.
	 */
	private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

	/**
	 * The collection of literal objects that have been accumulated thus far.
	 */
	@InnerAccess final List<AvailObject> literals = new ArrayList<>();

	/**
	 * An inverse mapping of the literal objects encountered thus far.  The map
	 * is from each literal object to its 1-based index.
	 */
	private final Map<A_BasicObject, Integer> reverseLiterals = new HashMap<>();

	/**
	 * Locate or record the specified literal object.  Answer its 1-based index.
	 *
	 * @param literal The literal object to look up.
	 * @return The object's 1-based literal index.
	 */
	public int addLiteral (final A_BasicObject literal)
	{
		@Nullable Integer index = reverseLiterals.get(literal);
		if (index == null)
		{
			literals.add((AvailObject) literal);
			index = literals.size();
			reverseLiterals.put(literal, index);
		}
		return index;
	}

	/**
	 * The {@link List} of argument {@linkplain TypeDescriptor types} for this
	 * {@linkplain CompiledCodeDescriptor compiled code}.
	 */
	private List<A_Type> argumentTypes = new ArrayList<>();

	/**
	 * Set the array of types of the arguments.
	 *
	 * @param argTypes The vararg array of argument types.
	 */
	public void argumentTypes (final A_Type... argTypes)
	{
		assert localTypes.size() == 0
			: "Must declare argument types before allocating locals";
		Collections.addAll(argumentTypes, argTypes);
	}

	/**
	 * Specify the types of the arguments that the resulting {@linkplain
	 * CompiledCodeDescriptor compiled code object} will accept.
	 *
	 * @param argTypes
	 *            A {@linkplain TupleDescriptor tuple} of {@linkplain
	 *            TypeDescriptor types} corresponding with the types which the
	 *            {@linkplain FunctionDescriptor} under construction will
	 *            accept.
	 */
	public void argumentTypesTuple (final A_Tuple argTypes)
	{
		assert localTypes.size() == 0
		: "Must declare argument types before allocating locals";
		final List<A_Type> types = new ArrayList<>(
			argTypes.tupleSize());
		for (final AvailObject type : argTypes)
		{
			types.add(type);
		}
		this.argumentTypes = types;
	}

	/**
	 * The return type of the {@linkplain FunctionDescriptor} under
	 * construction.
	 */
	private @Nullable A_Type returnType;

	/**
	 * Set the return type that the {@linkplain FunctionDescriptor} under
	 * construction will produce.
	 *
	 * @param theReturnType The return type of the function.
	 */
	public void returnType (final A_Type theReturnType)
	{
		this.returnType = theReturnType;
	}

	/**
	 * Answer the return type that the {@linkplain CompiledCodeDescriptor code}
	 * under construction will produce.
	 *
	 * @return The return type.
	 */
	private A_Type returnType ()
	{
		return stripNull(returnType);
	}

	/** The types of the local variables. */
	private final List<A_Type> localTypes = new ArrayList<>();

	/** The types of the local constants. */
	private final List<A_Type> constantTypes = new ArrayList<>();

	/**
	 * Declare a local variable with the specified type.  Answer its index.  The
	 * index is relative to the start of the arguments.
	 *
	 * @param localType The {@linkplain TypeDescriptor type} of the local.
	 * @return The index of the local variable.
	 */
	public int createLocal (final A_Type localType)
	{
		assert localType.isInstanceOf(topMeta());
		localTypes.add(localType);
		return localTypes.size() + argumentTypes.size();
	}

	/**
	 * The types of the outer (lexically captured) variables.  Note that
	 * arguments of outer scopes can also be captured, which aren't technically
	 * variables.
	 */
	private final List<A_Type> outerTypes = new ArrayList<>();

	/**
	 * Declare an outer (lexically captured) variable, specifying its type.
	 *
	 * @param outerType
	 *            The {@linkplain TypeDescriptor type} of the outer variable.
	 * @return
	 *            The index of the newly declared outer variable.
	 */
	public int createOuter (final A_Type outerType)
	{
		outerTypes.add(outerType);
		return outerTypes.size();
	}

	/**
	 * The {@linkplain Primitive primitive} {@linkplain
	 * Primitive#primitiveNumber number} of the {@linkplain
	 * CompiledCodeDescriptor compiled code object} being generated.
	 */
	private @Nullable Primitive primitive = null;

	/**
	 * Specify the {@linkplain Primitive primitive} of the {@linkplain
	 * CompiledCodeDescriptor compiled code object} being created.
	 *
	 * @param thePrimitive The primitive.
	 */
	public void primitive (final Primitive thePrimitive)
	{
		assert this.primitive == null : "Don't set the primitive twice";
		this.primitive = thePrimitive;
	}

	/** The module containing this code. */
	final A_Module module;

	/** The line number at which this code starts. */
	final int startingLineNumber;

	/** The line number at which the current nybblecode is associated. */
	public int currentLineNumber;

	/**
	 * For the start of each nybblecode instruction, indicate how much to add to
	 * the code's starting line number to get to the line number in the source
	 * that led to generation of that nybblecode.
	 *
	 * <p>The values are encoded to increase the odds that the tuple can be
	 * represented as nybbles or unsigned bytes.  Expressions' evaluation can
	 * sometimes effectively step backwards syntactically, so negative numbers
	 * need to be encoded as well.  We use the low bit to distinguish forward
	 * deltas (even) from backward deltas (odd), shifting right to get the
	 * magnitude.  This introduces an intentional encoding hole (the value 1
	 * represents negative zero), which we may exploit at a later date.</p>
	 */
	private final List<Integer> lineNumberEncodedDeltas = new ArrayList<>();

	/**
	 * The phrase that should be captured for this raw function.  {@link
	 * NilDescriptor#nil nil} is also valid.
	 */
	final A_Phrase phrase;

	/**
	 * Create a new {@code L1InstructionWriter Level One instruction writer}.
	 *
	 * @param module
	 *        The module containing this code.
	 * @param startingLineNumber
	 *        Where this code starts in the module.
	 * @param phrase
	 *        The phrase that should be captured for this raw function.  {@link
	 *        NilDescriptor#nil nil} is also valid, but less informative.
	 */
	public L1InstructionWriter (
		final A_Module module,
		final int startingLineNumber,
		final A_Phrase phrase)
	{
		this.module = module;
		this.startingLineNumber = startingLineNumber;
		this.currentLineNumber = startingLineNumber;
		this.phrase = phrase;
	}

	/**
	 * The {@linkplain L1StackTracker mechanism} used to ensure the stack is
	 * correctly balanced at the end and does not pop more than has been pushed.
	 * It also records the maximum stack depth for correctly sizing {@linkplain
	 * ContinuationDescriptor continuations} (essentially stack frames) at
	 * runtime.
	 */
	final L1StackTracker stackTracker = new L1StackTracker ()
	{
		@Override AvailObject literalAt (final int literalIndex)
		{
			return literals.get(literalIndex - 1);
		}
	};

	/**
	 * Write a numerically encoded operand.  All operands are encoded the same
	 * way in the level one nybblecode instruction set.  Values 0-9 take a
	 * single nybble, 10-57 take two, 58-313 take three, 314-65535 take five,
	 * and 65536..2^31-1 take nine nybbles.
	 *
	 * @param operand
	 *            An {@code int}-encoded operand of some {@linkplain
	 *            L1Operation operation}.
	 */
	private void writeOperand (final int operand)
	{
		if (operand < 10)
		{
			stream.write(operand);
		}
		else if (operand < 58)
		{
			stream.write(operand + 150 >>> 4);
			stream.write(operand + 150 & 15);
		}
		else if (operand < 314)
		{
			stream.write(13);
			stream.write(operand - 58 >>> 4);
			stream.write(operand - 58 & 15);
		}
		else if (operand < 65536)
		{
			stream.write(14);
			stream.write(operand >>> 12);
			stream.write(operand >>> 8 & 15);
			stream.write(operand >>> 4 & 15);
			stream.write(operand & 15);
		}
		else
		{
			stream.write(15);
			stream.write(operand >>> 28);
			stream.write(operand >>> 24 & 15);
			stream.write(operand >>> 20 & 15);
			stream.write(operand >>> 16 & 15);
			stream.write(operand >>> 12 & 15);
			stream.write(operand >>> 8 & 15);
			stream.write(operand >>> 4 & 15);
			stream.write(operand & 15);
		}
	}

	/**
	 * Write an L1 instruction to the nybblecode stream. Some opcodes
	 * (automatically) write an extension nybble (0xF).  Also write any
	 * {@linkplain L1OperandType operands}.  Validate that the right number of
	 * operands are provided.
	 *
	 * @param lineNumber
	 *        The source code line number responsible for generating this
	 *        instruction.
	 * @param operation
	 *        The {@link L1Operation} to write.
	 * @param operands The {@code int} operands for the operation.
	 */
	public void write (
		final int lineNumber,
		final L1Operation operation,
		final int... operands)
	{
		final int newLineNumber =
			lineNumber == 0 ? currentLineNumber : lineNumber;
		final int delta = newLineNumber - currentLineNumber;
		final int encodedDelta = delta < 0
			? delta << 1
			: -delta << 1 | 1;
		lineNumberEncodedDeltas.add(encodedDelta);
		currentLineNumber = newLineNumber;

		stackTracker.track(operation, operands);
		final byte opcode = (byte) operation.ordinal();
		if (opcode <= 15)
		{
			stream.write(opcode);
		}
		else
		{
			stream.write(L1Operation.L1_doExtension.ordinal());
			stream.write(opcode - 16);
		}
		for (final int operand : operands)
		{
			writeOperand(operand);
		}
	}

	/**
	 * Extract the {@linkplain NybbleTupleDescriptor tuple of nybbles} encoding
	 * the instructions of the {@linkplain CompiledCodeDescriptor compiled code
	 * object} under construction.
	 *
	 * @return A {@linkplain TupleDescriptor} of nybbles ({@linkplain
	 *         IntegerDescriptor integers} in the range 0..15).
	 */
	private AvailObject nybbles ()
	{
		final int size = stream.size();
		final byte [] byteArray = stream.toByteArray();
		final AvailObject nybbles = generateNybbleTupleFrom(
			size, i -> byteArray[i - 1]);
		nybbles.makeImmutable();
		return nybbles;
	}

	/**
	 * Produce the {@linkplain CompiledCodeDescriptor compiled code object}
	 * which we have just incrementally specified.
	 *
	 * @return A compiled code object (which can be lexically "closed" to a
	 *         {@linkplain FunctionDescriptor function} by supplying the outer
	 *         variables to capture).
	 */
	public AvailObject compiledCode ()
	{
		final @Nullable Primitive p = primitive;
		assert p == null || p.hasFlag(Flag.CannotFail) || localTypes.size() > 0
		: "Fallible primitive needs a primitive failure variable";
		return newCompiledCode(
			nybbles(),
			stackTracker.maxDepth(),
			functionType(tupleFromList(argumentTypes), returnType()),
			primitive,
			tupleFromList(literals),
			tupleFromList(localTypes),
			tupleFromList(constantTypes),
			tupleFromList(outerTypes),
			module,
			startingLineNumber,
			tupleFromIntegerList(lineNumberEncodedDeltas),
			phrase);
	}
}
