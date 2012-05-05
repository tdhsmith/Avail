/**
 * L1InstructionStepper.java
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

package com.avail.interpreter.levelTwo;

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import static com.avail.interpreter.levelTwo.L2Interpreter.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.interpreter.levelOne.*;
import com.avail.interpreter.levelTwo.operation.L2_INTERPRET_UNTIL_INTERRUPT;
import com.avail.interpreter.levelTwo.register.FixedRegister;

/**
 * This class is used to simulate the effect of level one nybblecodes during
 * execution of the {@link L2_INTERPRET_UNTIL_INTERRUPT} instruction, on behalf
 * of a {@link L2Interpreter}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public final class L1InstructionStepper
implements L1OperationDispatcher
{
	/**
	 * The {@link L2Interpreter} on whose behalf to step level one nybblecodes.
	 */
	L2Interpreter interpreter;

	/**
	 * A reusable buffer for holding arguments for method invocations.
	 */
	private final List<AvailObject> argsBuffer;

	/**
	 * Construct a new {@link L1InstructionStepper}.
	 *
	 * @param interpreter
	 *            The {@link L2Interpreter} on whose behalf to step through
	 *            level one nybblecode instructions.
	 */
	public L1InstructionStepper (final @NotNull L2Interpreter interpreter)
	{
		this.interpreter = interpreter;
		argsBuffer = interpreter.argsBuffer;
	}

	/**
	 * Read from the specified integer register.
	 *
	 * @param index Which integer register to read.
	 * @return The value from that register.
	 */
	private int integerAt (final int index)
	{
		return interpreter.integerAt(index);
	}

	/**
	 * Write to the specified integer register.
	 *
	 * @param index Which integer register to write.
	 * @param value The value to write to that register.
	 */
	private void integerAtPut (final int index, final int value)
	{
		interpreter.integerAtPut(index, value);
	}

	/**
	 * Read from the specified object register.
	 *
	 * @param index Which object register to read.
	 * @return The value from that register.
	 */
	private @NotNull AvailObject pointerAt (final int index)
	{
		return interpreter.pointerAt(index);
	}

	/**
	 * Write to the specified object register.
	 *
	 * @param index Which object register to write.
	 * @param value The value to write to that register.
	 */
	private void pointerAtPut (
		final int index,
		final @NotNull AvailObject value)
	{
		interpreter.pointerAtPut(index, value);
	}

	/**
	 * Read from the specified {@link FixedRegister fixed object register}.
	 *
	 * @param fixedRegister Which fixed object register to read.
	 * @return The value from that register.
	 */
	private @NotNull AvailObject pointerAt (
		final FixedRegister fixedRegister)
	{
		return interpreter.pointerAt(fixedRegister);
	}

	/**
	 * Write to the specified {@link FixedRegister fixed object register}.
	 *
	 * @param fixedRegister Which fixed object register to write.
	 * @param value The value to write to that register.
	 */
	private void pointerAtPut (
		final FixedRegister fixedRegister,
		final @NotNull AvailObject value)
	{
		interpreter.pointerAtPut(fixedRegister, value);
	}

	/**
	 * Extract an integer from nybblecode stream.
	 * @return
	 */
	private int getInteger ()
	{
		return interpreter.getInteger();
	}

	/**
	 * Push a value onto the current virtualized continuation's stack (which
	 * is just some consecutively-numbered pointer registers and an integer
	 * register that maintains the position).
	 *
	 * @param value The value to push on the virtualized stack.
	 */
	private final void push (final @NotNull AvailObject value)
	{
		int stackp = integerAt(stackpRegister());
		stackp--;
		assert stackp >= argumentOrLocalRegister(1);
		pointerAtPut(stackp, value);
		integerAtPut(stackpRegister(), stackp);
	}

	/**
	 * Pop a value off the current virtualized continuation's stack (which
	 * is just some consecutively-numbered pointer registers and an integer
	 * register that maintains the position).
	 *
	 * @return The value popped off the virtualized stack.
	 */
	private final @NotNull AvailObject pop ()
	{
		final int stackp = integerAt(stackpRegister());
		assert stackp <= argumentOrLocalRegister(
			pointerAt(FUNCTION).code().numArgsAndLocalsAndStack());
		final AvailObject popped = pointerAt(stackp);
		// Clear the stack slot
		pointerAtPut(stackp, NullDescriptor.nullObject());
		integerAtPut(stackpRegister(), stackp + 1);
		return popped;
	}

	/**
	 * Extract the specified literal from the current function's code.
	 *
	 * @param literalIndex
	 *            The index of the literal to look up in the current
	 *            function's code.
	 * @return
	 *            The literal extracted from the specified literal slot of
	 *            the code.
	 */
	private final @NotNull AvailObject literalAt (final int literalIndex)
	{
		final AvailObject function = pointerAt(FUNCTION);
		final AvailObject code = function.code();
		return code.literalAt(literalIndex);
	}

	/**
	 * Create a continuation from the values of the interpreter's registers.
	 * In particular, the {@link L1InstructionStepper} treats the object
	 * registers immediately following the fixed registers as holding the
	 * exploded content of the current continuation.  The {@link
	 * L2Interpreter#pcRegister()} and {@link L2Interpreter#stackpRegister()}
	 * are also part of the state manipulated by the L1 stepper.
	 *
	 * <p>
	 * Write the resulting continuation into the {@linkplain
	 * FixedRegister#CALLER caller register}.
	 * </p>
	 */
	public void reifyContinuation ()
	{
		final AvailObject function = pointerAt(FUNCTION);
		final AvailObject code = function.code();
		final AvailObject continuation =
			ContinuationDescriptor.mutable().create(
				code.numArgsAndLocalsAndStack());
		continuation.caller(pointerAt(CALLER));
		continuation.function(function);
		continuation.pc(integerAt(pcRegister()));
		continuation.stackp(
			integerAt(stackpRegister()) + 1 - argumentOrLocalRegister(1));
		final AvailObject chunk = interpreter.chunk();
		assert chunk == L2ChunkDescriptor.unoptimizedChunk();
		continuation.levelTwoChunkOffset(
			chunk,
			L2ChunkDescriptor.offsetToContinueUnoptimizedChunk());
		for (int i = code.numArgsAndLocalsAndStack(); i >= 1; i--)
		{
			continuation.argOrLocalOrStackAtPut(
				i,
				pointerAt(argumentOrLocalRegister(i)));
		}
		interpreter.wipeObjectRegisters();
		pointerAtPut(CALLER, continuation);
	}

	@Override
	public void L1_doCall()
	{
		final AvailObject implementations = literalAt(getInteger());
		final AvailObject expectedReturnType = literalAt(getInteger());
		final int numArgs = implementations.numArgs();
		if (debugL1)
		{
			System.out.printf(" (%s)", implementations.name().name());
		}
		argsBuffer.clear();
		for (int i = numArgs; i >= 1; i--)
		{
			argsBuffer.add(0, pop());
		}
		final AvailObject matching =
			implementations.lookupByValuesFromList(argsBuffer);
		if (matching.equalsNull())
		{
			error(
				"Ambiguous or invalid lookup of %s",
				implementations.name().name());
			return;
		}
		if (matching.isForward())
		{
			error(
				"Attempted to execute forward method %s "
				+ "before it was defined.",
				implementations.name().name());
			return;
		}
		if (matching.isAbstract())
		{
			error(
				"Attempted to execute an abstract method %s.",
				implementations.name().name());
			return;
		}
		// Leave the expected return type pushed on the stack.  This will be
		// used when the method returns, and it also helps distinguish label
		// continuations from call continuations.
		push(expectedReturnType);

		// Call the method...
		reifyContinuation();
		interpreter.invokePossiblePrimitiveWithReifiedCaller(
			matching.bodyBlock(),
			pointerAt(CALLER));
	}

	@Override
	public void L1_doPushLiteral ()
	{
		final int literalIndex = getInteger();
		final AvailObject constant = literalAt(literalIndex);
		// We don't need to make constant beImmutable because *code objects*
		// are always immutable.
		push(constant);
	}

	@Override
	public void L1_doPushLastLocal ()
	{
		final int localIndex = argumentOrLocalRegister(getInteger());
		final AvailObject local = pointerAt(localIndex);
		pointerAtPut(localIndex, NullDescriptor.nullObject());
		push(local);
	}

	@Override
	public void L1_doPushLocal ()
	{
		final int localIndex = argumentOrLocalRegister(getInteger());
		final AvailObject local = pointerAt(localIndex);
		local.makeImmutable();
		push(local);
	}

	@Override
	public void L1_doPushLastOuter ()
	{
		final AvailObject function = pointerAt(FUNCTION);
		final int outerIndex = getInteger();
		final AvailObject outer = function.outerVarAt(outerIndex);
		if (outer.equalsNull())
		{
			error("Someone prematurely erased this outer var");
			return;
		}
		if (!function.optionallyNilOuterVar(outerIndex))
		{
			outer.makeImmutable();
		}
		push(outer);
	}

	@Override
	public void L1_doClose ()
	{
		final int numCopiedVars = getInteger();
		final int literalIndexOfCode = getInteger();
		final AvailObject codeToClose = literalAt(literalIndexOfCode);
		final AvailObject newFunction = FunctionDescriptor.mutable().create(
			numCopiedVars);
		newFunction.code(codeToClose);
		for (int i = numCopiedVars; i >= 1; i--)
		{
			final AvailObject value = pop();
			assert !value.equalsNull();
			newFunction.outerVarAtPut(i, value);
		}
		/*
		 * We don't assert assertObjectUnreachableIfMutable: on the popped
		 * outer variables because each outer variable's new reference from
		 * the function balances the lost reference from the wiped stack.
		 * Likewise we don't tell them makeImmutable(). The function itself
		 * should remain mutable at this point, otherwise the outer
		 * variables would have to makeImmutable() to be referenced by an
		 * immutable function.
		 */
		push(newFunction);
	}

	@Override
	public void L1_doSetLocal ()
	{
		final int localIndex = argumentOrLocalRegister(getInteger());
		final AvailObject localVariable = pointerAt(localIndex);
		final AvailObject value = pop();
		// The value's reference from the stack is now from the variable.
		localVariable.setValue(value);
	}

	@Override
	public void L1_doGetLocalClearing ()
	{
		final int localIndex = argumentOrLocalRegister(getInteger());
		final AvailObject localVariable = pointerAt(localIndex);
		final AvailObject value = localVariable.getValue();
		if (localVariable.traversed().descriptor().isMutable())
		{
			localVariable.clearValue();
		}
		else
		{
			value.makeImmutable();
		}
		push(value);
	}

	@Override
	public void L1_doPushOuter ()
	{
		final AvailObject function = pointerAt(FUNCTION);
		final int outerIndex = getInteger();
		final AvailObject outer = function.outerVarAt(outerIndex);
		if (outer.equalsNull())
		{
			error("Someone prematurely erased this outer var");
			return;
		}
		outer.makeImmutable();
		push(outer);
	}

	@Override
	public void L1_doPop ()
	{
		pop();
	}

	@Override
	public void L1_doGetOuterClearing ()
	{
		final AvailObject function = pointerAt(FUNCTION);
		final int outerIndex = getInteger();
		final AvailObject outerVariable = function.outerVarAt(outerIndex);
		final AvailObject value = outerVariable.getValue();
		if (outerVariable.traversed().descriptor().isMutable())
		{
			outerVariable.clearValue();
		}
		else
		{
			value.makeImmutable();
		}
		push(value);
	}

	@Override
	public void L1_doSetOuter ()
	{
		final AvailObject function = pointerAt(FUNCTION);
		final int outerIndex = getInteger();
		final AvailObject outerVariable = function.outerVarAt(outerIndex);
		if (outerVariable.equalsNull())
		{
			error("Someone prematurely erased this outer var");
			return;
		}
		final AvailObject newValue = pop();
		// The value's reference from the stack is now from the variable.
		outerVariable.setValue(newValue);
	}

	@Override
	public void L1_doGetLocal ()
	{
		final int localIndex = argumentOrLocalRegister(getInteger());
		final AvailObject localVariable = pointerAt(localIndex);
		final AvailObject value = localVariable.getValue();
		value.makeImmutable();
		push(value);
	}

	@Override
	public void L1_doMakeTuple ()
	{
		final int count = getInteger();
		final AvailObject tuple = ObjectTupleDescriptor.mutable().create(
			count);
		for (int i = count; i >= 1; i--)
		{
			tuple.tupleAtPut(i, pop());
		}
		tuple.hashOrZero(0);
		push(tuple);
	}

	@Override
	public void L1_doGetOuter ()
	{
		final AvailObject function = pointerAt(FUNCTION);
		final int outerIndex = getInteger();
		final AvailObject outerVariable = function.outerVarAt(outerIndex);
		final AvailObject outer = outerVariable.getValue();
		if (outer.equalsNull())
		{
			error("Someone prematurely erased this outer var");
			return;
		}
		outer.makeImmutable();
		push(outer);
	}

	@Override
	public void L1_doExtension ()
	{
		final AvailObject function = pointerAt(FUNCTION);
		final AvailObject code = function.code();
		final AvailObject nybbles = code.nybbles();
		int pc = integerAt(pcRegister());
		final byte nybble = nybbles.extractNybbleFromTupleAt(pc);
		pc++;
		integerAtPut(pcRegister(), pc);
		L1Operation.values()[nybble + 16].dispatch(this);
	}

	@Override
	public void L1Ext_doPushLabel ()
	{
		final AvailObject function = pointerAt(FUNCTION);
		final AvailObject code = function.code();
		final int numArgs = code.numArgs();
		assert code.primitiveNumber() == 0;
		final List<AvailObject> args = new ArrayList<AvailObject>(numArgs);
		for (int i = 1; i <= numArgs; i++)
		{
			args.add(pointerAt(argumentOrLocalRegister(i)));
		}
		final int numLocals = code.numLocals();
		final List<AvailObject> locals =
			new ArrayList<AvailObject>(numLocals);
		for (int i = 1; i <= numLocals; i++)
		{
			locals.add(pointerAt(argumentOrLocalRegister((numArgs + i))));
		}
		assert interpreter.chunk() == L2ChunkDescriptor.unoptimizedChunk();
		final AvailObject newContinuation = ContinuationDescriptor.create(
			function,
			pointerAt(CALLER),
			L2ChunkDescriptor.unoptimizedChunk(),
			L2ChunkDescriptor.offsetToContinueUnoptimizedChunk(),
			args, locals);
		// Freeze all fields of the new object, including its caller,
		// function, and args.
		newContinuation.makeSubobjectsImmutable();
		// ...always a fresh copy, always mutable (uniquely owned).
		assert newContinuation.caller().equalsNull()
			|| !newContinuation.caller().descriptor().isMutable()
		: "Caller should freeze because two continuations can see it";
		push(newContinuation);
	}

	@Override
	public void L1Ext_doGetLiteral ()
	{
		final int literalIndex = getInteger();
		final AvailObject literalVariable = literalAt(literalIndex);
		// We don't need to make constant beImmutable because *code objects*
		// are always immutable.
		final AvailObject value = literalVariable.getValue();
		value.makeImmutable();
		push(value);
	}

	@Override
	public void L1Ext_doSetLiteral ()
	{
		final int literalIndex = getInteger();
		final AvailObject literalVariable = literalAt(literalIndex);
		final AvailObject value = pop();
		// The value's reference from the stack is now from the variable.
		literalVariable.setValue(value);
	}

	@Override
	public void L1Ext_doSuperCall ()
	{
		final AvailObject implementations = literalAt(getInteger());
		final AvailObject expectedReturnType = literalAt(getInteger());
		final int numArgs = implementations.numArgs();
		// Pop the argument types (the types by which to do a lookup)...
		argsBuffer.clear();
		for (int i = numArgs; i >= 1; i--)
		{
			argsBuffer.add(0, pop());
		}
		final AvailObject matching =
			implementations.lookupByTypesFromList(argsBuffer);
		if (matching.equalsNull())
		{
			error("Ambiguous or invalid lookup");
			return;
		}
		if (matching.isForward())
		{
			error("Attempted to execute forward method " +
				"before it was defined.");
			return;
		}
		if (matching.isAbstract())
		{
			error("Attempted to execute an abstract method.");
			return;
		}
		// Pop the arguments themselves...
		argsBuffer.clear();
		for (int i = numArgs; i >= 1; i--)
		{
			argsBuffer.add(0, pop());
		}
		// Leave the expected return type pushed on the stack.  This will be
		// used when the method returns, and it also helps distinguish label
		// continuations from call continuations.
		push(expectedReturnType);

		// Call the method...
		reifyContinuation();
		interpreter.invokePossiblePrimitiveWithReifiedCaller(
			matching.bodyBlock(),
			pointerAt(CALLER));
	}

	@Override
	public void L1Ext_doGetType ()
	{
		final int depth = getInteger();
		final int deepStackp = integerAt(stackpRegister() + depth);
		final AvailObject value = pointerAt(deepStackp);
		value.makeImmutable();
		push(value.kind());
	}

	@Override
	public void L1Ext_doDuplicate ()
	{
		final int stackp = integerAt(stackpRegister());
		final AvailObject value = pointerAt(stackp);
		value.makeImmutable();
		push(value);
	}

	@Override
	public  void L1Ext_doReserved ()
	{
		error("That nybblecode is not supported");
		return;
	}

	@Override
	public void L1Implied_doReturn ()
	{
		final AvailObject caller = pointerAt(CALLER);
		final AvailObject value = pop();
		interpreter.returnToCaller(caller, value);
	}
}