/*
 * BuildAction.java
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

import com.avail.builder.ResolvedModuleName;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.tasks.BuildTask;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import static com.avail.utility.Nulls.stripNull;
import static java.awt.Cursor.WAIT_CURSOR;
import static java.awt.Cursor.getPredefinedCursor;

/**
 * A {@code BuildAction} launches a {@linkplain BuildTask build task} in a
 * Swing worker thread.
 */
@SuppressWarnings("serial")
public final class BuildAction
extends AbstractWorkbenchAction
{
	/**
	 * Whether this action is for building the currently selected entry
	 * point module rather than the current selection in the module area.
	 */
	final boolean forEntryPointModule;

	@Override
	public void actionPerformed (final @Nullable ActionEvent event)
	{
		assert workbench.backgroundTask == null;
		final ResolvedModuleName selectedModule = stripNull(
			forEntryPointModule
				? workbench.selectedEntryPointModule()
				: workbench.selectedModule());

		// Update the UI.
		workbench.setCursor(getPredefinedCursor(WAIT_CURSOR));
		workbench.buildProgress.setValue(0);
		workbench.inputField.requestFocusInWindow();
		workbench.clearTranscript();

		// Clear the build input stream.
		workbench.inputStream().clear();

		// Build the target module in a Swing worker thread.
		final BuildTask task = new BuildTask(workbench, selectedModule);
		workbench.backgroundTask = task;
		workbench.availBuilder.checkStableInvariants();
		workbench.setEnablements();
		task.execute();
	}

	/**
	 * Construct a new {@code BuildAction}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 * @param forEntryPointModule
	 *        Whether this action is for the currently selected entry point
	 *        module rather than for the module tree's selection.
	 */
	public BuildAction (
		final AvailWorkbench workbench,
		final boolean forEntryPointModule)
	{
		super(workbench, "Build");
		this.forEntryPointModule = forEntryPointModule;
		putValue(
			SHORT_DESCRIPTION,
			"Build the selected module.");
		if (!forEntryPointModule)
		{
			putValue(
				ACCELERATOR_KEY,
				KeyStroke.getKeyStroke(
					KeyEvent.VK_ENTER,
					AvailWorkbench.menuShortcutMask));
		}
	}
}
