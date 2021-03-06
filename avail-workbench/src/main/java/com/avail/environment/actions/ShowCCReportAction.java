/*
 * ShowCCReportAction.java
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

package com.avail.environment.actions;

import com.avail.AvailRuntime;
import com.avail.builder.AvailBuilder;
import com.avail.descriptor.CompiledCodeDescriptor.CodeCoverageReport;
import com.avail.descriptor.FiberDescriptor;
import com.avail.environment.AvailWorkbench;

import javax.annotation.Nullable;
import java.awt.event.ActionEvent;
import java.util.Collections;

import static com.avail.descriptor.CompiledCodeDescriptor.codeCoverageReportsThen;
import static com.avail.environment.AvailWorkbench.StreamStyle.INFO;

/**
 * A {@code DisplayCodeCoverageReportAction} instructs the {@linkplain
 * AvailBuilder Avail builder} to display the code coverage report.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
@SuppressWarnings("serial")
public final class ShowCCReportAction
extends AbstractWorkbenchAction
{
	/** The current runtime. */
	private final AvailRuntime runtime;

	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		runtime.execute(
			FiberDescriptor.commandPriority,
			() -> codeCoverageReportsThen(
				reports ->
				{
					// Order the report items using the natural sort defined
					// in the object.
					Collections.sort(reports);

					// Announce the beginning of the report dump.
					final StringBuilder builder = new StringBuilder();
					final String header =
						"Code Coverage Report - all functions\n" +
						'\n' +
						"KEY\n" +
						"r: Function has run\n" +
						"t: Function has been translated\n" +
						"m: Module name\n" +
						"l: Starting line number\n" +
						"f: Function name and sub-function ordinals\n" +
						'\n';
					builder.append(header);

					// Iterate over each report
					for (final CodeCoverageReport r : reports)
					{
						builder.append(r);
						builder.append('\n');
					}
					workbench.writeText(builder.toString(), INFO);
				}));
	}

	/**
	 * Construct a new {@link ShowCCReportAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 * @param runtime
	 *        The current {@link AvailRuntime}.
	 */
	public ShowCCReportAction (
		final AvailWorkbench workbench,
		final AvailRuntime runtime)
	{
		super(workbench, "Generate Code Coverage Report");
		this.runtime = runtime;
		putValue(
			SHORT_DESCRIPTION,
			"Generate and display the code coverage report.");
	}
}
