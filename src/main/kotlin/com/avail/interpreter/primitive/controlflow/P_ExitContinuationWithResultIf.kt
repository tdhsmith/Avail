/*
 * P_ExitContinuationWithResultIf.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package com.avail.interpreter.primitive.controlflow

import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.ContinuationDescriptor
import com.avail.descriptor.ContinuationTypeDescriptor.mostGeneralContinuationType
import com.avail.descriptor.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.descriptor.atoms.AtomDescriptor.trueObject
import com.avail.exceptions.AvailErrorCode.E_CONTINUATION_EXPECTED_STRONGER_TYPE
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CanSwitchContinuations
import com.avail.interpreter.Primitive.Result.CONTINUATION_CHANGED
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operation.L2_RETURN
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import com.avail.optimizer.L2Generator.edgeTo

/**
 * **Primitive:** Exit the given [continuation][ContinuationDescriptor]
 * (returning result to its caller), but only if the provided boolean is true.
 * Otherwise do nothing.
 */
object P_ExitContinuationWithResultIf : Primitive(
	3,
	CanInline,
	CanSwitchContinuations)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		val (con, result, condition) = interpreter.argsBuffer

		if (!condition.extractBoolean())
		{
			return interpreter.primitiveSuccess(nil)
		}

		// The primitive fails if the value being returned disagrees with the
		// label continuation's function's return type.  Any stronger check, as
		// specified in a semantic restriction, will be tested in the caller.
		if (!result.isInstanceOf(
				con.function().code().functionType().returnType()))
		{
			return interpreter.primitiveFailure(
				E_CONTINUATION_EXPECTED_STRONGER_TYPE)
		}

		interpreter.setReifiedContinuation(con.caller())
		interpreter.function = null
		interpreter.chunk = null
		interpreter.offset = Integer.MAX_VALUE
		interpreter.returnNow = true
		interpreter.setLatestResult(result)
		return CONTINUATION_CHANGED
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralContinuationType(),
				ANY.o(),
				booleanType()),
			TOP.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_CONTINUATION_EXPECTED_STRONGER_TYPE))

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val (continuationReg, valueReg, conditionReg) = arguments

		// Check for the common case that the continuation was created for this
		// very frame.
		val generator = translator.generator
		val manifest = generator.currentManifest()
		val synonym = manifest.semanticValueToSynonym(
			continuationReg.semanticValue())
		val label = generator.topFrame.label()
		if (manifest.hasSemanticValue(label) &&
			manifest.semanticValueToSynonym(label) == synonym)
		{
			// We're conditionally exiting the current frame.
			val exit = generator.createBasicBlock("Exit")
			val dontExit = generator.createBasicBlock("Don't exit")
			translator.generateJumpIfEqualsConstant(
				generator.readBoxed(
					conditionReg.originalBoxedWriteSkippingMoves(true)),
				trueObject(),
				edgeTo(exit),
				edgeTo(dontExit))
			generator.startBlock(exit)
			generator.addInstruction(
				L2_RETURN.instance,
				valueReg)
			generator.startBlock(dontExit)
			callSiteHelper.useAnswer(translator.generator.boxedConstant(nil))
			return true
		}
		return false
	}
}