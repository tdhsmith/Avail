/*
 * ContinuationDescriptor.java
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

package com.avail.descriptor;

import com.avail.AvailRuntime;
import com.avail.annotations.AvailMethod;
import com.avail.annotations.EnumField;
import com.avail.annotations.EnumField.Converter;
import com.avail.annotations.HideFieldJustForPrinting;
import com.avail.descriptor.CompiledCodeDescriptor.L1InstructionDecoder;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Chunk.Generation;
import com.avail.interpreter.primitive.continuations.P_ContinuationStackData;
import com.avail.interpreter.primitive.controlflow.P_CatchException;
import com.avail.interpreter.primitive.controlflow.P_ExitContinuationWithResult;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments;
import com.avail.io.TextInterface;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationDescriptor.IntegerSlots.*;
import static com.avail.descriptor.ContinuationDescriptor.ObjectSlots.*;
import static com.avail.descriptor.ContinuationTypeDescriptor.continuationTypeForFunctionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.VariableDescriptor.newVariableWithContentType;
import static com.avail.interpreter.levelTwo.L2Chunk.unoptimizedChunk;

/**
 * A {@linkplain ContinuationDescriptor continuation} acts as an immutable
 * execution stack.  A running {@linkplain FiberDescriptor fiber}
 * conceptually operates by repeatedly replacing its continuation with a new one
 * (i.e., one derived from the previous state by nybblecode execution rules),
 * performing necessary side-effects as it does so.
 *
 * <p>
 * A continuation can be {@linkplain
 * P_ExitContinuationWithResult exited}, which causes
 * the current fiber's continuation to be replaced by the specified
 * continuation's caller.  A return value is supplied to this caller.  A
 * continuation can also be {@linkplain
 * P_RestartContinuationWithArguments restarted},
 * either with a specified tuple of arguments or {@linkplain
 * P_RestartContinuation with the original arguments}.
 * </p>
 *
 * @author Mark van Gulik&lt;mark@availlang.org&gt;
 */
public final class ContinuationDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * A composite field containing the {@linkplain #PROGRAM_COUNTER program
		 * counter} and {@linkplain #STACK_POINTER stack pointer}.
		 */
		PROGRAM_COUNTER_AND_STACK_POINTER,

		/**
		 * A composite field containing the {@linkplain #LEVEL_TWO_OFFSET level
		 * two offset}, and perhaps more later.
		 */
		LEVEL_TWO_OFFSET_AND_OTHER;

		/**
		 * The index into the current continuation's {@linkplain
		 * ObjectSlots#FUNCTION function's} compiled code's tuple of nybblecodes
		 * at which execution will next occur.
		 */
		@EnumField(
			describedBy = Converter.class,
			lookupMethodName = "decimal")
		public static final BitField PROGRAM_COUNTER = bitField(
			PROGRAM_COUNTER_AND_STACK_POINTER,
			32,
			32);

		/**
		 * An index into this continuation's {@linkplain ObjectSlots#FRAME_AT_
		 * frame slots}.  It grows from the top + 1 (empty stack), and at its
		 * deepest it just abuts the last local variable.
		 */
		@EnumField(
			describedBy = Converter.class,
			lookupMethodName = "decimal")
		public static final BitField STACK_POINTER = bitField(
			PROGRAM_COUNTER_AND_STACK_POINTER,
			0,
			32);

		/**
		 * The Level Two {@linkplain L2Chunk#instructions instruction} index at
		 * which to resume.
		 */
		@EnumField(
			describedBy = Converter.class,
			lookupMethodName = "decimal")
		public static final BitField LEVEL_TWO_OFFSET = bitField(
			LEVEL_TWO_OFFSET_AND_OTHER,
			32,
			32);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The continuation that invoked this one, or {@linkplain
		 * NilDescriptor#nil nil} for the outermost continuation. When a
		 * continuation is not directly created by a {@linkplain
		 * L1Operation#L1Ext_doPushLabel push-label instruction}, it will have a
		 * type pushed on it. This type is checked against any value that the
		 * callee attempts to return to it. This supports link-time type
		 * strengthening at call sites.
		 */
		@HideFieldJustForPrinting
		CALLER,

		/**
		 * The {@linkplain FunctionDescriptor function} being executed via this
		 * continuation.
		 */
		FUNCTION,

		/**
		 * The {@link L2Chunk} which can be resumed directly by the {@link
		 * Interpreter} to effect continued execution.
		 */
		LEVEL_TWO_CHUNK,

		/**
		 * The slots allocated for locals, arguments, and stack entries.  The
		 * arguments are first, then the locals, and finally the stack entries
		 * (growing downwards from the top).  At its deepest, the stack slots
		 * will abut the last local.
		 */
		FRAME_AT_;
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return e == LEVEL_TWO_OFFSET_AND_OTHER
			|| e == LEVEL_TWO_CHUNK;
	}

	/**
	 * Set both my level one program counter and level one stack pointer.
	 */
	@Override @AvailMethod
	void o_AdjustPcAndStackp (
		final AvailObject object,
		final int pc,
		final int stackp)
	{
		assert isMutable();
		object.setSlot(PROGRAM_COUNTER, pc);
		object.setSlot(STACK_POINTER, stackp);
	}

	@Override @AvailMethod
	AvailObject o_ArgOrLocalOrStackAt (
		final AvailObject object,
		final int subscript)
	{
		return object.slot(FRAME_AT_, subscript);
	}

	@Override @AvailMethod
	void o_ArgOrLocalOrStackAtPut (
		final AvailObject object,
		final int subscript,
		final AvailObject value)
	{
		object.setSlot(FRAME_AT_, subscript, value);
	}

	@Override @AvailMethod
	A_Continuation o_Caller (final AvailObject object)
	{
		return object.slot(CALLER);
	}

	@Override
	int o_CurrentLineNumber (final AvailObject object)
	{
		final A_RawFunction code = object.function().code();
		final A_Tuple encodedDeltas = code.lineNumberEncodedDeltas();
		final L1InstructionDecoder instructionDecoder =
			new L1InstructionDecoder();
		code.setUpInstructionDecoder(instructionDecoder);
		instructionDecoder.pc(1);
		int lineNumber = code.startingLineNumber();
		int instructionCounter = 1;

		while (!instructionDecoder.atEnd())
		{
			final int encodedDelta =
				encodedDeltas.tupleIntAt(instructionCounter++);
			final int decodedDelta = (encodedDelta & 1) == 0
				? encodedDelta >> 1
				: -(encodedDelta >> 1);
			lineNumber += decodedDelta;
			// Now skip one nybblecode instruction.
			final L1Operation op = instructionDecoder.getOperation();
			for (int i = op.operandTypes().length - 1; i >= 0; i--)
			{
				instructionDecoder.getOperand();
			}
		}
		return lineNumber;
	}

	/**
	 * If immutable, copy the object as mutable, otherwise answer the original
	 * mutable continuation.  This is used by the {@linkplain Interpreter
	 * interpreter} to ensure it is always executing a mutable continuation and
	 * is therefore always able to directly modify it.
	 */
	@Override @AvailMethod
	A_Continuation o_EnsureMutable (final AvailObject object)
	{
		if (isMutable())
		{
			return object;
		}
		return AvailObjectRepresentation.newLike(mutable, object, 0, 0);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsContinuation(object);
	}

	@Override @AvailMethod
	boolean o_EqualsContinuation (
		final AvailObject object,
		final A_Continuation aContinuation)
	{
		if (object.sameAddressAs(aContinuation))
		{
			return true;
		}
		if (!object.caller().equals(aContinuation.caller()))
		{
			return false;
		}
		if (!object.function().equals(aContinuation.function()))
		{
			return false;
		}
		if (object.pc() != aContinuation.pc())
		{
			return false;
		}
		if (object.stackp() != aContinuation.stackp())
		{
			return false;
		}
		for (int i = object.numSlots(); i >= 1; i--)
		{
			if (!object.argOrLocalOrStackAt(i).equals(
				aContinuation.argOrLocalOrStackAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	A_Function o_Function (final AvailObject object)
	{
		return object.slot(FUNCTION);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		int h = 0x593599A;
		h ^= object.caller().hash();
		h += object.function().hash() + object.pc() * object.stackp();
		for (int i = object.numSlots(); i >= 1; i--)
		{
			h = h * 23 + 0x221C9 ^ object.argOrLocalOrStackAt(i).hash();
		}
		return h;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return continuationTypeForFunctionType(object.function().kind());
	}

	/**
	 * Set both my chunk index and the offset into it.
	 */
	@Override @AvailMethod
	void o_LevelTwoChunkOffset (
		final AvailObject object,
		final L2Chunk chunk,
		final int offset)
	{
		if (isShared())
		{
			synchronized (object)
			{
				object.setSlot(LEVEL_TWO_CHUNK, chunk.chunkPojo);
				object.setSlot(LEVEL_TWO_OFFSET, offset);
			}
		}
		else
		{
			object.setSlot(LEVEL_TWO_CHUNK, chunk.chunkPojo);
			object.setSlot(LEVEL_TWO_OFFSET, offset);
		}
	}

	@Override @AvailMethod
	L2Chunk o_LevelTwoChunk (final AvailObject object)
	{
		final L2Chunk chunk =
			object.mutableSlot(LEVEL_TWO_CHUNK).javaObjectNotNull();
		if (chunk != unoptimizedChunk && chunk.isValid())
		{
			Generation.usedChunk(chunk);
		}
		return chunk;
	}

	@Override @AvailMethod
	int o_LevelTwoOffset (final AvailObject object)
	{
		return object.mutableSlot(LEVEL_TWO_OFFSET);
	}

	@Override
	String o_NameForDebugger (final AvailObject object)
	{
		final StringBuilder builder = new StringBuilder();
		builder.append(super.o_NameForDebugger(object));
		builder.append(": ");
		final A_RawFunction code = object.function().code();
		builder.append(code.methodName().asNativeString());
		builder.append(":");
		builder.append(object.currentLineNumber());
		final @Nullable Primitive primitive =
			code.primitive();
		if (primitive == P_CatchException.instance)
		{
			builder.append(", CATCH var = ");
			builder.append(object.argOrLocalOrStackAt(4).value().value());
		}
		return builder.toString();
	}

	/**
	 * Answer the number of slots allocated for arguments, locals, and stack
	 * entries.
	 */
	@Override @AvailMethod
	int o_NumSlots (final AvailObject object)
	{
		return object.variableObjectSlotsCount();
	}

	@Override @AvailMethod
	int o_Pc (final AvailObject object)
	{
		return object.slot(PROGRAM_COUNTER);
	}

	@Override
	A_Continuation o_ReplacingCaller (
		final AvailObject object,
		final A_Continuation newCaller)
	{
		final AvailObject mutableVersion = isMutable()
			? object
			: AvailObjectRepresentation.newLike(mutable, object, 0, 0);
		mutableVersion.setSlot(CALLER, newCaller);
		return mutableVersion;
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.CONTINUATION;
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return false;
	}

	/**
	 * Read from the stack at the given subscript, which is one-relative and
	 * based on just the stack area.
	 */
	@Override @AvailMethod
	AvailObject o_StackAt (final AvailObject object, final int subscript)
	{
		return object.slot(FRAME_AT_, subscript);
	}

	@Override @AvailMethod
	int o_Stackp (final AvailObject object)
	{
		return object.slot(STACK_POINTER);
	}

	/**
	 * Create a new continuation with the given data.  The continuation should
	 * represent the state upon entering the new context - i.e., set the pc to
	 * the first instruction, clear the stack, and set up new local variables.
	 *
	 * @param function
	 *        The function being invoked.
	 * @param caller
	 *        The calling continuation.
	 * @param startingChunk
	 *        The level two chunk to invoke.
	 * @param startingOffset
	 *        The offset into the chunk at which to resume.
	 * @param args
	 *        The {@link List} of arguments.
	 * @return The new continuation.
	 */
	public static A_Continuation createLabelContinuation (
		final A_Function function,
		final A_Continuation caller,
		final L2Chunk startingChunk,
		final int startingOffset,
		final List<AvailObject> args)
	{
		final A_RawFunction code = function.code();
		assert code.primitive() == null;
		final int frameSize = code.numSlots();
		final AvailObject cont = mutable.create(frameSize);
		cont.setSlot(CALLER, caller);
		cont.setSlot(FUNCTION, function);
		cont.setSlot(PROGRAM_COUNTER, 0); // Indicates this is a label.
		cont.setSlot(STACK_POINTER, frameSize + 1);
		cont.levelTwoChunkOffset(startingChunk, startingOffset);
		//  Set up arguments...
		final int numArgs = args.size();
		assert numArgs == code.numArgs();

		// Arguments area.  These are used by P_RestartContinuation, but they're
		// replaced before resumption if using
		// P_RestartContinuationWithArguments.
		cont.setSlotsFromList(FRAME_AT_, 1, args, 0, numArgs);

		// All the remaining slots.  DO NOT capture or build locals.
		cont.fillSlots(FRAME_AT_, numArgs + 1, frameSize - numArgs, nil);
		return cont;
	}

	/**
	 * Create a mutable continuation with the specified fields.  Fill the stack
	 * frame slots with {@link NilDescriptor#nil}.
	 *
	 * @param function
	 *            The function being invoked/resumed.
	 * @param caller
	 *            The calling continuation of this continuation.
	 * @param pc
	 *            The level one program counter.
	 * @param stackp
	 *            The level one operand stack depth.
	 * @param levelTwoChunk
	 *            The {@linkplain L2Chunk level two chunk} to execute.
	 * @param levelTwoOffset
	 *            The level two chunk offset at which to resume.
	 * @return A new mutable continuation.
	 */
	@ReferencedInGeneratedCode
	public static A_Continuation createContinuationExceptFrame (
		final A_Function function,
		final A_Continuation caller,
		final int pc,
		final int stackp,
		final L2Chunk levelTwoChunk,
		final int levelTwoOffset)
	{
		final A_RawFunction code = function.code();
		final int frameSize = code.numSlots();
		final AvailObject continuation = mutable.create(frameSize);
		continuation.setSlot(CALLER, caller);
		continuation.setSlot(FUNCTION, function);
		continuation.setSlot(PROGRAM_COUNTER, pc);
		continuation.setSlot(STACK_POINTER, stackp);
		continuation.setSlot(LEVEL_TWO_CHUNK, levelTwoChunk.chunkPojo);
		continuation.setSlot(LEVEL_TWO_OFFSET, levelTwoOffset);
		continuation.fillSlots(FRAME_AT_, 1, frameSize, nil);
		return continuation;
	}

	/**
	 * Create a mutable continuation with the specified fields.  Initialize the
	 * stack slot from the list of fields.
	 *
	 * @param function
	 *            The function being invoked/resumed.
	 * @param caller
	 *            The calling continuation of this continuation.
	 * @param pc
	 *            The level one program counter.
	 * @param stackp
	 *            The level one operand stack depth.
	 * @param levelTwoChunk
	 *            The {@linkplain L2Chunk level two chunk} to execute.
	 * @param levelTwoOffset
	 *            The level two chunk offset at which to resume.
	 * @return A new mutable continuation.
	 */
	public static A_Continuation createContinuationWithFrame (
		final A_Function function,
		final A_Continuation caller,
		final int pc,
		final int stackp,
		final L2Chunk levelTwoChunk,
		final int levelTwoOffset,
		final List <? extends A_BasicObject> frameValues,
		final int zeroBasedStartIndex)
	{
		final AvailObject continuation =
			(AvailObject) createContinuationExceptFrame(
				function, caller, pc, stackp, levelTwoChunk, levelTwoOffset);
		continuation.setSlotsFromList(
			FRAME_AT_,
			1,
			frameValues,
			zeroBasedStartIndex,
			continuation.numSlots());
		return continuation;
	}

	/**
	 * Construct a new {@code ContinuationDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ContinuationDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.CONTINUATION_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link ContinuationDescriptor}. */
	private static final ContinuationDescriptor mutable =
		new ContinuationDescriptor(Mutability.MUTABLE);

	@Override
	ContinuationDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ContinuationDescriptor}. */
	private static final ContinuationDescriptor immutable =
		new ContinuationDescriptor(Mutability.IMMUTABLE);

	@Override
	ContinuationDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link ContinuationDescriptor}. */
	private static final ContinuationDescriptor shared =
		new ContinuationDescriptor(Mutability.SHARED);

	@Override
	ContinuationDescriptor shared ()
	{
		return shared;
	}

	/**
	 * A substitute for {@linkplain AvailObject nil}, for use by
	 * {@link P_ContinuationStackData}.
	 */
	private static final AvailObject nilSubstitute =
		newVariableWithContentType(bottom()).makeShared();

	/**
	 * Answer a substitute for {@linkplain AvailObject nil}. This is
	 * primarily for use by {@link P_ContinuationStackData}.
	 *
	 * @return An immutable bottom-typed variable.
	 */
	public static AvailObject nilSubstitute ()
	{
		return nilSubstitute;
	}

	/**
	 * Create a list of descriptions of the stack frames ({@linkplain
	 * ContinuationDescriptor continuations}) of the specified continuation.
	 * Invoke the specified {@linkplain Continuation1NotNull Java continuation}
	 * with the resultant list. This list begins with the newest frame and ends
	 * with the base frame.
	 *
	 * @param runtime
	 *        The {@linkplain AvailRuntime Avail runtime} to use for
	 *        stringification.
	 * @param textInterface
	 *        The {@linkplain TextInterface text interface} for {@linkplain
	 *        A_Fiber fibers} started due to stringification. This need not be
	 *        the {@linkplain AvailRuntime#textInterface() default text
	 *        interface}.
	 * @param availContinuation
	 *        The Avail continuation to dump.
	 * @param javaContinuation
	 *        What to do with the list of {@linkplain String strings}.
	 */
	public static void dumpStackThen (
		final AvailRuntime runtime,
		final TextInterface textInterface,
		final A_Continuation availContinuation,
		final Continuation1NotNull<List<String>> javaContinuation)
	{
		final List<A_Continuation> frames = new ArrayList<>(20);
		for (
			A_Continuation c = availContinuation;
			!c.equalsNil();
			c = c.caller())
		{
			frames.add(c);
		}
		final int lines = frames.size();
		if (lines == 0)
		{
			javaContinuation.value(Collections.emptyList());
			return;
		}
		final List<A_Type> allTypes = new ArrayList<>();
		for (final A_Continuation frame : frames)
		{
			final A_RawFunction code = frame.function().code();
			final A_Type functionType = code.functionType();
			final A_Type paramsType = functionType.argsTupleType();
			for (int i = 1, limit = code.numArgs(); i <= limit; i++)
			{
				allTypes.add(paramsType.typeAtIndex(i));
			}
		}
		final String[] strings = new String[lines];
		Interpreter.stringifyThen(
			runtime,
			textInterface,
			allTypes,
			allTypeNames ->
			{
				int allTypesIndex = 0;
				for (
					int frameIndex = 0, end = frames.size();
					frameIndex < end;
					frameIndex++)
				{
					final A_Continuation frame = frames.get(frameIndex);
					final A_RawFunction code = frame.function().code();
					final StringBuilder signatureBuilder =
						new StringBuilder(1000);
					for (int i = 1, limit = code.numArgs(); i <= limit; i++)
					{
						if (i != 1)
						{
							signatureBuilder.append(", ");
						}
						signatureBuilder.append(
							allTypeNames.get(allTypesIndex++));
					}
					final A_Module module = code.module();
					strings[frameIndex] = String.format(
						"#%d: %s [%s] (%s:%d)",
						lines - frameIndex,
						code.methodName().asNativeString(),
						signatureBuilder.toString(),
						module.equalsNil()
							? "?"
							: module.moduleName().asNativeString(),
						frame.currentLineNumber());
				}
				assert allTypesIndex == allTypeNames.size();
				javaContinuation.value(Arrays.asList(strings));
			});
	}
}
