/*
 * P_MethodHasDefinitionForArgumentTypes.java
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

package com.avail.interpreter.primitive.methods

import com.avail.descriptor.A_Atom
import com.avail.descriptor.A_BasicObject
import com.avail.descriptor.A_Bundle
import com.avail.descriptor.A_Method
import com.avail.descriptor.A_Tuple
import com.avail.descriptor.A_Type
import com.avail.descriptor.MethodDescriptor
import com.avail.descriptor.TupleDescriptor
import com.avail.descriptor.TypeDescriptor
import com.avail.exceptions.MethodDefinitionException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.AtomDescriptor.falseObject
import com.avail.descriptor.AtomDescriptor.trueObject
import com.avail.descriptor.EnumerationTypeDescriptor.booleanType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.exceptions.AvailErrorCode.E_AMBIGUOUS_METHOD_DEFINITION
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import com.avail.exceptions.AvailErrorCode.E_NO_METHOD
import com.avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Does the [method][MethodDescriptor]
 * have a unique definition for the specified [ tuple][TupleDescriptor] of parameter [types][TypeDescriptor]?
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_BundleHasDefinitionForArgumentTypes : Primitive(2, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(2)
		val methodName = interpreter.argument(0)
		val argTypes = interpreter.argument(1)
		val bundle = methodName.bundleOrNil()
		if (bundle.equalsNil())
		{
			return interpreter.primitiveFailure(E_NO_METHOD)
		}
		val method = bundle.bundleMethod()
		if (argTypes.tupleSize() != method.numArgs())
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		try
		{
			val definition = method.lookupByTypesFromTuple(argTypes)
			assert(!definition.equalsNil())
			return interpreter.primitiveSuccess(trueObject())
		}
		catch (e: MethodDefinitionException)
		{
			return interpreter.primitiveSuccess(falseObject())
		}

	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				ATOM.o(),
				zeroOrMoreOf(anyMeta())),
			booleanType())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(
			set(
				E_AMBIGUOUS_METHOD_DEFINITION,
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_NO_METHOD,
				E_NO_METHOD_DEFINITION))
	}

}