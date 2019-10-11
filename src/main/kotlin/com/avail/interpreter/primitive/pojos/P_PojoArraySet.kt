/*
 * P_PojoArraySet.java
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
package com.avail.interpreter.primitive.pojos

import com.avail.descriptor.*
import com.avail.exceptions.MarshalingException
import com.avail.interpreter.AvailLoader
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import java.lang.reflect.Array

import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.PojoTypeDescriptor.mostGeneralPojoArrayType
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TypeDescriptor.Types.ANY
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE
import com.avail.exceptions.AvailErrorCode.E_JAVA_MARSHALING_FAILED
import com.avail.exceptions.AvailErrorCode.E_SUBSCRIPT_OUT_OF_BOUNDS
import com.avail.interpreter.Primitive.Flag.CanInline

/**
 * **Primitive:** Overwrite the [ element][AvailObject] that resides at the given [ subscript][IntegerDescriptor] of the specified [pojo array][PojoTypeDescriptor].
 */
object P_PojoArraySet : Primitive(3, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(3)
		val pojo = interpreter.argument(0)
		val subscript = interpreter.argument(1)
		val value = interpreter.argument(2)

		val loader = interpreter.availLoaderOrNull()
		loader?.statementCanBeSummarized(false)

		val rawPojo = pojo.rawPojo()
		val array = rawPojo.javaObjectNotNull<Any>()
		val index = subscript.extractInt()
		if (index > Array.getLength(array))
		{
			return interpreter.primitiveFailure(E_SUBSCRIPT_OUT_OF_BOUNDS)
		}
		val contentType = pojo.kind().contentType()
		if (!value.isInstanceOf(contentType))
		{
			return interpreter.primitiveFailure(
				E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE)
		}
		try
		{
			val marshaledType = contentType.marshalToJava(null) as Class<*>?
			Array.set(array, index - 1, value.marshalToJava(marshaledType))
		}
		catch (e: MarshalingException)
		{
			return interpreter.primitiveFailure(e)
		}

		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(tuple(mostGeneralPojoArrayType(),
		                          naturalNumbers(), ANY.o()), TOP.o())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(set(
			E_SUBSCRIPT_OUT_OF_BOUNDS,
			E_CANNOT_STORE_INCORRECTLY_TYPED_VALUE,
			E_JAVA_MARSHALING_FAILED))
	}

}