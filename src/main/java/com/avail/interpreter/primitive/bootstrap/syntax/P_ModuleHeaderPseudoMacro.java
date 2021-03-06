/*
 * P_ModuleHeaderPseudoMacro.java
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

package com.avail.interpreter.primitive.bootstrap.syntax;

import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.ExpressionAsStatementPhraseDescriptor.newExpressionAsStatement;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.ListPhraseDescriptor.newListNode;
import static com.avail.descriptor.ListPhraseTypeDescriptor.*;
import static com.avail.descriptor.MethodDescriptor.SpecialMethodAtom.MODULE_HEADER;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromArray;
import static com.avail.descriptor.PhraseTypeDescriptor.Constants.stringLiteralType;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LITERAL_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.STATEMENT_PHRASE;
import static com.avail.descriptor.SendPhraseDescriptor.newSendNode;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * The {@code P_ModuleHeaderPseudoMacro} primitive is used to parse module
 * headers.  When this primitive is invoked, it should yield a {@link
 * PhraseKind#STATEMENT_PHRASE}.  The method is private, and used to parse the
 * headers of modules with the same machinery used for the bodies.
 *
 * <p>The name of the module header method is given in {@link
 * SpecialMethodAtom#MODULE_HEADER}.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_ModuleHeaderPseudoMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ModuleHeaderPseudoMacro().init(
			6, Private, Bootstrap, CannotFail, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(6);
		final A_Phrase moduleNameLiteral = interpreter.argument(0);
		final A_Phrase optionalVersions = interpreter.argument(1);
		final A_Phrase allImports = interpreter.argument(2);
		final A_Phrase optionalNames = interpreter.argument(3);
		final A_Phrase optionalEntries = interpreter.argument(4);
		final A_Phrase optionalPragmas = interpreter.argument(5);

		return interpreter.primitiveSuccess(
			newExpressionAsStatement(
				newSendNode(
					// Don't bother collecting tokens in header.
					emptyTuple(),
					MODULE_HEADER.bundle,
					newListNode(
						tupleFromArray(
							moduleNameLiteral,
							optionalVersions,
							allImports,
							optionalNames,
							optionalEntries,
							optionalPragmas)),
					TOP.o())));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tupleFromArray(
				/* Module name */
				stringLiteralType,
				/* Optional versions */
				zeroOrOneList(zeroOrMoreList(stringLiteralType)),
				/* All imports */
				zeroOrMoreList(
					list(
						LITERAL_PHRASE.create(
							inclusive(1, 2)),
						zeroOrMoreList(
							list(
								// Imported module name
								stringLiteralType,
								// Imported module versions
								zeroOrOneList(
									zeroOrMoreList(stringLiteralType)),
								// Imported names
								zeroOrOneList(
									list(
										zeroOrMoreList(
											list(
												// Negated import
												LITERAL_PHRASE.create(
													booleanType()),
												// Name
												stringLiteralType,
												// Replacement name
												zeroOrOneList(
													stringLiteralType))),
										// Final ellipsis (import all the rest)
										LITERAL_PHRASE.create(
											booleanType()))))))),
				/* Optional names */
				zeroOrOneList(zeroOrMoreList(stringLiteralType)),
				/* Optional entries */
				zeroOrOneList(zeroOrMoreList(stringLiteralType)),
				/* Optional pragma */
				zeroOrOneList(zeroOrMoreList(stringLiteralType))),
			/* Shouldn't be invoked, so always fail. */
			STATEMENT_PHRASE.mostGeneralType());
	}
}
