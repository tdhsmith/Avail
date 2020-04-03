/*
 * P_MapToObjectType.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.objects

import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.objects.ObjectTypeDescriptor.mostGeneralObjectMeta
import com.avail.descriptor.objects.ObjectTypeDescriptor.objectTypeFromMap
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.InstanceMetaDescriptor.anyMeta
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.types.MapTypeDescriptor.mapTypeForSizesKeyTypeValueType
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*

/**
 * **Primitive:** Convert a [map][MapDescriptor] from fields ([instance
 * types][InstanceTypeDescriptor] of [atoms][AtomDescriptor]) to
 * [types][TypeDescriptor] into an [object type][ObjectTypeDescriptor].
 */
@Suppress("unused")
object P_MapToObjectType : Primitive(1, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val map = interpreter.argument(0)
		return interpreter.primitiveSuccess(objectTypeFromMap(map))
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mapTypeForSizesKeyTypeValueType(
					wholeNumbers(), ATOM.o(), anyMeta())),
			mostGeneralObjectMeta())
}
