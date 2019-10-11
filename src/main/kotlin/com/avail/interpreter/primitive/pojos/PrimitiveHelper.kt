/*
 * PrimitiveHelper.java
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

import com.avail.AvailRuntime.HookType
import com.avail.descriptor.A_Function
import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_String
import com.avail.descriptor.A_Tuple
import com.avail.descriptor.A_Type
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.MarshalingException
import com.avail.interpreter.Primitive
import com.avail.interpreter.levelOne.L1InstructionWriter
import com.avail.utility.MutableOrNull
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.HashSet

import com.avail.AvailRuntime.HookType.RAISE_JAVA_EXCEPTION_IN_AVAIL
import com.avail.descriptor.BottomTypeDescriptor.bottom
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom.APPLY
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.PojoTypeDescriptor.marshalDefiningType
import com.avail.descriptor.PojoTypeDescriptor.pojoTypeForClass
import com.avail.descriptor.VariableTypeDescriptor.variableTypeFor
import com.avail.exceptions.AvailErrorCode.E_JAVA_FIELD_NOT_AVAILABLE
import com.avail.exceptions.AvailErrorCode.E_JAVA_FIELD_REFERENCE_IS_AMBIGUOUS
import com.avail.exceptions.AvailErrorCode.E_JAVA_MARSHALING_FAILED
import com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE
import com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS
import com.avail.interpreter.levelOne.L1Operation.L1_doCall
import com.avail.interpreter.levelOne.L1Operation.L1_doMakeTuple
import com.avail.interpreter.levelOne.L1Operation.L1_doPushLocal

/**
 * `PrimitiveHelper` aggregates utility functions for reuse by the various pojo
 * subsystem [primitives][Primitive].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object PrimitiveHelper
{
	/**
	 * Search for the requested Java [Method].
	 *
	 * @param pojoType
	 *   The pojo type (an [A_Type]) where the method is defined. The method may
	 *   also be in a superclass of this class.
	 * @param methodName
	 *   The name of the method.
	 * @param marshaledTypes
	 *   The array of [Class]es for the arguments, used to disambiguate
	 *   overloaded methods.
	 * @param errorOut
	 *   A [MutableOrNull] into which an [AvailErrorCode] can be written in the
	 *   event that a unique [Method] is not found.
	 * @return Either the successfully looked up [Method] or `null`.
	 * Note that either the return is non-null or the errorOut will
	 * have a non-null value written to it.
	 */
	internal fun lookupMethod(
		pojoType: A_Type,
		methodName: A_String,
		marshaledTypes: Array<Class<*>>,
		errorOut: MutableOrNull<AvailErrorCode>): Method?
	{
		// If pojoType is not a fused type, then it has an immediate class that
		// should be used to recursively look up the method.
		if (!pojoType.isPojoType || !pojoType.isPojoFusedType)
		{
			val javaClass = marshalDefiningType(pojoType)
			try
			{
				return javaClass.getMethod(
					methodName.asNativeString(), *marshaledTypes)
			}
			catch (e: NoSuchMethodException)
			{
				errorOut.value = E_JAVA_METHOD_NOT_AVAILABLE
				return null
			}

		}
		else
		{
			val methods = HashSet<Method>()
			val ancestors = pojoType.javaAncestors()
			for (ancestor in ancestors.keysAsSet())
			{
				val javaClass = marshalDefiningType(ancestor)
				try
				{
					methods.add(javaClass.getMethod(
						methodName.asNativeString(), *marshaledTypes))
				}
				catch (e: NoSuchMethodException)
				{
					// Ignore -- this is not unexpected.
				}

			}
			if (methods.isEmpty())
			{
				errorOut.value = E_JAVA_METHOD_NOT_AVAILABLE
				return null
			}
			if (methods.size > 1)
			{
				errorOut.value = E_JAVA_METHOD_REFERENCE_IS_AMBIGUOUS
				return null
			}
			return methods.iterator().next()
		}// If pojoType is a fused type, then iterate through its ancestry in an
		// attempt to uniquely resolve the method.
	}

	/**
	 * Search for the requested Java [Field].
	 *
	 * @param pojoType
	 *   The pojo type (an [A_Type]) where the field is defined. The field may
	 *   also be in a superclass of this class.
	 * @param fieldName
	 *   The name of the field.
	 * @param errorOut
	 *   A [MutableOrNull] into which an [AvailErrorCode] can be written in the
	 *   event that a unique [Field] is not found.
	 * @return Either the successfully looked up [Field] or `null`.
	 *   Note that either the return is non-null or the errorOut will have a
	 *   non-null value written to it.
	 */
	internal fun lookupField(
		pojoType: A_Type,
		fieldName: A_String,
		errorOut: MutableOrNull<AvailErrorCode>): Field?
	{
		// If pojoType is not a fused type, then it has an immediate class
		// that should be used to recursively look up the field.
		if (!pojoType.isPojoType || !pojoType.isPojoFusedType)
		{
			val javaClass = marshalDefiningType(pojoType)
			try
			{
				return javaClass.getField(fieldName.asNativeString())
			}
			catch (e: NoSuchFieldException)
			{
				errorOut.value = E_JAVA_FIELD_NOT_AVAILABLE
				return null
			}

		}
		else
		{
			val fields = HashSet<Field>()
			val ancestors = pojoType.javaAncestors()
			for (ancestor in ancestors.keysAsSet())
			{
				val javaClass = marshalDefiningType(ancestor)
				try
				{
					fields.add(javaClass.getField(
						fieldName.asNativeString()))
				}
				catch (e: NoSuchFieldException)
				{
					// Ignore -- this is not unexpected.
				}

			}
			if (fields.isEmpty())
			{
				errorOut.value = E_JAVA_FIELD_NOT_AVAILABLE
				return null
			}
			if (fields.size > 1)
			{
				errorOut.value = E_JAVA_FIELD_REFERENCE_IS_AMBIGUOUS
				return null
			}
			return fields.iterator().next()
		}// If pojoType is a fused type, then iterate through its ancestry in
		// an attempt to uniquely resolve the field.
	}

	/**
	 * Synthesize an [A_RawFunction].  It should have the given function type,
	 * and expect to be instantiated as an [A_Function] with the given types of
	 * outers.  It should also be [Primitive], with failure code to invoke the
	 * [HookType.RAISE_JAVA_EXCEPTION_IN_AVAIL] if a Java [Throwable] is caught
	 * and made available in the failure variable.
	 *
	 * @param primitive
	 *   The [Primitive] to invoke.
	 * @param functionType
	 *   The [A_Type] of the [A_Function]s that will be created from the
	 *   [A_RawFunction] that is produced here.
	 * @param outerTypes
	 *   The [A_Type]s of the outers that will be supplied later to the raw
	 *   function to make an [A_Function].
	 * @return An [A_RawFunction] with the exact given signature.
	 */
	fun rawPojoInvokerFunctionFromFunctionType(
		primitive: Primitive,
		functionType: A_Type,
		vararg outerTypes: A_Type): A_RawFunction
	{
		val argTypes = functionType.argsTupleType()
		val numArgs = argTypes.sizeRange().lowerBound().extractInt()
		val argTypesArray = Array<A_Type>(numArgs) {i ->
			argTypes.typeAtIndex(i)
		}
		val returnType = functionType.returnType()
		val writer = L1InstructionWriter(nil, 0, nil)
		writer.primitive = primitive
		writer.argumentTypes(*argTypesArray)
		writer.returnType = returnType
		// Produce failure code.  First declare the local that holds primitive
		// failure information.
		val failureLocal = writer.createLocal(
			variableTypeFor(pojoTypeForClass(Throwable::class.java)))
		assert(failureLocal == numArgs + 1)
		for (outerType in outerTypes)
		{
			writer.createOuter(outerType)
		}
		writer.write(
			0,
			L1_doCall,
			writer.addLiteral(
				SpecialMethodAtom.GET_RETHROW_JAVA_EXCEPTION.bundle),
			writer.addLiteral(RAISE_JAVA_EXCEPTION_IN_AVAIL.functionType))
		writer.write(0, L1_doPushLocal, failureLocal)
		writer.write(0, L1_doMakeTuple, 1)
		writer.write(
			0,
			L1_doCall,
			writer.addLiteral(APPLY.bundle),
			writer.addLiteral(bottom())
		)
		// TODO: [TLS] When functions can be made non-reflective, then make
		// this raw function non-reflective for safety.
		return writer.compiledCode()
	}

	/**
	 * Marshal the specified values using the provided
	 * [marshaling types][A_Type].
	 *
	 * @param marshaledTypes
	 *   The marshaled types.
	 * @param args
	 *   The values to marshal using the corresponding types.
	 * @param errorOut
	 *   A [MutableOrNull] into which an [AvailErrorCode] can  be written in the
	 *   event that marshaling fails for some value.
	 * @return The marshaled values.
	 */
	internal fun marshalValues(
		marshaledTypes: A_Tuple,
		args: A_Tuple,
		errorOut: MutableOrNull<AvailErrorCode>): Array<Any>?
	{
		assert(marshaledTypes.tupleSize() == args.tupleSize())
		var marshaled: Array<Any>? = null
		try
		{
			marshaled = arrayOf(Array(args.tupleSize()) { i ->
				args.tupleAt(i + 1).marshalToJava(marshaledTypes.tupleAt(i + 1).javaObjectNotNull<Class<*>>())
			})
		}
		catch (e: MarshalingException)
		{
			errorOut.value = E_JAVA_MARSHALING_FAILED
			return null
		}

		return marshaled
	}
}