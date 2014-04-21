/**
 * AvailWorkbench.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.environment;

import static java.lang.Math.*;
import static java.lang.System.arraycopy;
import static javax.swing.SwingUtilities.*;
import static javax.swing.JScrollPane.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.tree.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.builder.*;
import com.avail.builder.AvailBuilder.LoadedModule;
import com.avail.descriptor.*;
import com.avail.environment.actions.*;
import com.avail.environment.nodes.*;
import com.avail.environment.tasks.*;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersion;
import com.avail.stacks.StacksGenerator;
import com.avail.utility.Mutable;
import com.avail.utility.evaluation.*;

/**
 * {@code AvailWorkbench} is a simple user interface for the {@linkplain
 * AvailBuilder Avail builder}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@SuppressWarnings("serial")
public class AvailWorkbench
extends JFrame
{
	/** Determine at startup whether we're on a Mac. */
	public static final boolean runningOnMac =
		System.getProperty("os.name").toLowerCase().matches("mac os x.*");

	/** Determine at startup whether we should show developer commands. */
	public static final boolean showDeveloperTools =
		"true".equalsIgnoreCase(System.getProperty("availDeveloper"));

	/** The {@linkplain Style style} to use for standard output. */
	public static final String outputStyleName = "output";

	/** The {@linkplain Style style} to use for standard error. */
	public static final String errorStyleName = "error";

	/** The {@linkplain Style style} to use for standard input. */
	public static final String inputStyleName = "input";

	/** The {@linkplain Style style} to use for notifications. */
	public static final String infoStyleName = "info";

	/** The {@linkplain Style style} to use for user-supplied commands. */
	public static final String commandStyleName = "command";

	/**
	 * The numeric mask for the modifier key suitable for the current platform.
	 */
	public static final int menuShortcutMask =
		Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

	/**
	 * An abstraction for all the workbench's actions.
	 */
	public abstract static class AbstractWorkbenchAction
	extends AbstractAction
	{
		/** The owning {@link AvailWorkbench}. */
		public final AvailWorkbench workbench;

		/**
		 * Construct a new {@link AbstractWorkbenchAction}.
		 *
		 * @param workbench The owning {@link AvailWorkbench}.
		 * @param name The action's name.
		 */
		public AbstractWorkbenchAction (
			final AvailWorkbench workbench,
			final String name)
		{
			super(name);
			this.workbench = workbench;
		}
	}

	/**
	 * {@code AbstractWorkbenchTask} is a foundation for long running {@link
	 * AvailBuilder} operations.
	 */
	public abstract static class AbstractWorkbenchTask
	extends SwingWorker<Void, Void>
	{
		/** The owning {@link AvailWorkbench}. */
		public final AvailWorkbench workbench;

		/**
		 * The resolved name of the target {@linkplain ModuleDescriptor module}.
		 */
		protected final @Nullable ResolvedModuleName targetModuleName;

		/**
		 * Construct a new {@link AbstractWorkbenchTask}.
		 *
		 * @param workbench
		 *        The owning {@link AvailWorkbench}.
		 * @param targetModuleName
		 *        The resolved name of the target {@linkplain ModuleDescriptor
		 *        module}.
		 */
		public AbstractWorkbenchTask (
			final AvailWorkbench workbench,
			final @Nullable ResolvedModuleName targetModuleName)
		{
			this.workbench = workbench;
			this.targetModuleName = targetModuleName;
		}

		/**
		 * Cancel the current {@linkplain AbstractWorkbenchTask task}.
		 */
		public final void cancel ()
		{
			workbench.availBuilder.cancel();
		}

		/** The start time. */
		private long startTimeMillis;

		/** The stop time. */
		private long stopTimeMillis;

		/** The {@linkplain Throwable exception} that terminated the build. */
		protected @Nullable Throwable terminator;

		/**
		 * Ensure the target module name is not null, then answer it.
		 *
		 * @return The non-null target module name.
		 */
		protected final ResolvedModuleName targetModuleName ()
		{
			final ResolvedModuleName name = targetModuleName;
			assert name != null;
			return name;
		}

		/**
		 * Report completion (and timing) to the {@linkplain #transcript
		 * transcript}.
		 */
		protected void reportDone ()
		{
			final StyledDocument doc = workbench.transcript.getStyledDocument();
			final long durationMillis = stopTimeMillis - startTimeMillis;
			final String status;
			if (workbench.availBuilder.shouldStopBuild)
			{
				status = "Canceled";
			}
			else if (terminator != null)
			{
				status = "Aborted";
			}
			else
			{
				status = "Done";
			}
			try
			{
				doc.insertString(
					doc.getLength(),
					String.format(
						"%s (%d.%03ds).%n",
						status,
						durationMillis / 1000,
						durationMillis % 1000),
					doc.getStyle(infoStyleName));
			}
			catch (final BadLocationException e)
			{
				// Shouldn't happen.
				assert false;
			}
		}

		@Override
		protected final @Nullable Void doInBackground () throws Exception
		{
			startTimeMillis = System.currentTimeMillis();
			try
			{
				// Reopen the repositories if necessary.
				for (final ModuleRoot root :
					workbench.resolver.moduleRoots().roots())
				{
					root.repository().reopenIfNecessary();
				}
				executeTask();
				return null;
			}
			finally
			{
				// Close all the repositories.
				for (final ModuleRoot root :
					workbench.resolver.moduleRoots().roots())
				{
					root.repository().close();
				}
				stopTimeMillis = System.currentTimeMillis();
			}
		}

		/**
		 * Execute this {@link AbstractWorkbenchTask}.
		 *
		 * @throws Exception
		 *         If anything goes wrong.
		 */
		protected abstract void executeTask () throws Exception;
	}

	/**
	 * {@linkplain BuildOutputStream} intercepts writes and updates the UI's
	 * {@linkplain #transcript}.
	 */
	private final class BuildOutputStream
	extends ByteArrayOutputStream
	{
		/**
		 * The {@linkplain StyledDocument styled document} underlying the
		 * {@linkplain #transcript}.
		 */
		final StyledDocument doc;

		/** The print {@linkplain Style style}. */
		final String style;

		/**
		 * Update the {@linkplain #transcript}.
		 */
		private void updateTranscript ()
		{
			final String text = toString();
			reset();
			invokeLater(new Runnable()
			{
				@Override
				public void run ()
				{
					try
					{
						final JScrollBar verticalScrollBar =
							transcriptScrollArea.getVerticalScrollBar();
						final int min = verticalScrollBar.getValue() +
							verticalScrollBar.getVisibleAmount();
						final int max = verticalScrollBar.getMaximum();
						doc.insertString(
							doc.getLength(),
							text,
							doc.getStyle(style));
						if (max == min)
						{
							verticalScrollBar.setValue(
								verticalScrollBar.getMaximum());
						}
					}
					catch (final BadLocationException e)
					{
						// Shouldn't happen.
						assert false;
					}
				}
			});
		}

		@Override
		public synchronized void write (final int b)
		{
			super.write(b);
			updateTranscript();
		}

		@Override
		public void write (final @Nullable byte[] b) throws IOException
		{
			super.write(b);
			updateTranscript();
		}

		@Override
		public synchronized void write (
			final @Nullable byte[] b,
			final int off,
			final int len)
		{
			super.write(b, off, len);
			updateTranscript();
		}

		/**
		 * Construct a new {@link BuildOutputStream}.
		 *
		 * @param isErrorStream
		 *        Is this an error stream?
		 */
		public BuildOutputStream (final boolean isErrorStream)
		{
			super(65536);
			this.doc = transcript.getStyledDocument();
			this.style = isErrorStream ? errorStyleName : outputStyleName;
		}
	}

	/**
	 * {@linkplain BuildInputStream} satisfies reads from the UI's {@linkplain
	 * #inputField input field}. It blocks reads unless some data is available.
	 */
	public final class BuildInputStream
	extends ByteArrayInputStream
	{
		/**
		 * The {@linkplain StyledDocument styled document} underlying the
		 * {@linkplain #transcript}.
		 */
		final StyledDocument doc;

		/**
		 * Clear the {@linkplain BuildInputStream input stream}. All pending
		 * data is discarded and the stream position is reset to zero
		 * ({@code 0}).
		 */
		public synchronized void clear ()
		{
			count = 0;
			pos = 0;
		}

		/**
		 * Update the contents of the {@linkplain BuildInputStream stream} with
		 * data from the {@linkplain #inputField input field}.
		 */
		public synchronized void update ()
		{
			final String text = inputField.getText() + "\n";
			final byte[] bytes = text.getBytes();
			if (pos + bytes.length >= buf.length)
			{
				final int newSize = max(
					buf.length << 1, bytes.length + buf.length);
				final byte[] newBuf = new byte[newSize];
				arraycopy(buf, 0, newBuf, 0, buf.length);
				buf = newBuf;
			}
			arraycopy(bytes, 0, buf, count, bytes.length);
			count += bytes.length;
			try
			{
				doc.insertString(
					doc.getLength(),
					text,
					doc.getStyle(inputStyleName));
			}
			catch (final BadLocationException e)
			{
				// Should never happen.
				assert false;
			}
			inputField.setText("");
			notifyAll();
		}

		/**
		 * The specified command string was just entered.  Present it in the
		 * {@link #commandStyleName}.  Force a leading new line if necessary to
		 * keep the text area from looking stupid.  Always end with a new line.
		 * The passed command should not itself have a new line included.
		 *
		 * @param commandText
		 *        The command that was entered, with no leading or trailing line
		 *        breaks.
		 */
		public synchronized void feedbackForCommand (
			final String commandText)
		{
			try
			{
				String textToInsert = commandText + "\n";
				final int length = doc.getLength();
				if (length > 0 && !doc.getText(length - 1, 1).equals("\n"))
				{
					textToInsert = "\n" + textToInsert;
				}
				doc.insertString(
					doc.getLength(),
					textToInsert,
					doc.getStyle(commandStyleName));
			}
			catch (final BadLocationException e)
			{
				// Should never happen.
				assert false;
			}
		}

		@Override
		public boolean markSupported ()
		{
			return false;
		}

		@Override
		public void mark (final int readAheadLimit)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public synchronized void reset ()
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public synchronized int read ()
		{
			// Block until data is available.
			try
			{
				while (pos == count)
				{
					wait();
				}
			}
			catch (final InterruptedException e)
			{
				return -1;
			}
			final int next = buf[pos++] & 0xFF;
			return next;
		}

		@Override
		public synchronized int read (
			final @Nullable byte[] readBuffer,
			final int start,
			final int requestSize)
		{
			if (requestSize <= 0)
			{
				return 0;
			}
			// Block until data is available.
			try
			{
				while (pos == count)
				{
					wait();
				}
			}
			catch (final InterruptedException e)
			{
				return -1;
			}
			final int bytesToTransfer = min(requestSize, count - pos);
			arraycopy(buf, pos, readBuffer, start, bytesToTransfer);
			pos += bytesToTransfer;
			return bytesToTransfer;
		}

		/**
		 * Construct a new {@link BuildInputStream}.
		 */
		public BuildInputStream ()
		{
			super(new byte[1024]);
			this.count = 0;
			this.doc = transcript.getStyledDocument();
		}
	}

	/*
	 * Model components.
	 */

	/** The {@linkplain ModuleNameResolver module name resolver}. */
	public final ModuleNameResolver resolver;

	/** The current {@linkplain AbstractWorkbenchTask background task}. */
	public volatile @Nullable AbstractWorkbenchTask backgroundTask;

	/**
	 * The documentation {@linkplain Path path} for the {@linkplain
	 * StacksGenerator Stacks generator}.
	 */
	public Path documentationPath =
		StacksGenerator.defaultDocumentationPath;

	/** The {@linkplain BuildInputStream standard input stream}. */
	private @Nullable BuildInputStream inputStream;

	/**
	 * Answer the {@linkplain BuildInputStream standard input stream}.
	 *
	 * @return The input stream.
	 */
	public BuildInputStream inputStream ()
	{
		final BuildInputStream stream = inputStream;
		assert stream != null;
		return stream;
	}

	/** The {@linkplain PrintStream standard error stream}. */
	private @Nullable PrintStream errorStream;

	/**
	 * Answer the {@linkplain PrintStream standard error stream}.
	 *
	 * @return The error stream.
	 */
	public PrintStream errorStream ()
	{
		final PrintStream stream = errorStream;
		assert stream != null;
		return stream;
	}

	/** The {@linkplain PrintStream standard output stream}. */
	private @Nullable PrintStream outputStream;

	/**
	 * Answer the {@linkplain PrintStream standard output stream}.
	 *
	 * @return The output stream.
	 */
	public PrintStream outputStream ()
	{
		final PrintStream stream = outputStream;
		assert stream != null;
		return stream;
	}

	/* UI components. */

	/** The {@linkplain ModuleDescriptor module} {@linkplain JTree tree}. */
	public final JTree moduleTree;

	/** The {@linkplain JTree tree} of module {@linkplain A_Module#entryPoints()
	 * entry points}. */
	public final JTree entryPointsTree;

	/**
	 * The {@link AvailBuilder} used by this user interface.
	 */
	public final AvailBuilder availBuilder;

	/**
	 * The {@linkplain JProgressBar progress bar} that displays the overall
	 * build progress.
	 */
	public final JProgressBar buildProgress;

	/**
	 * The {@linkplain JTextPane text area} that displays the {@linkplain
	 * AvailBuilder build} transcript.
	 */
	public final JTextPane transcript;

	/** The {@linkplain JScrollPane scroll bars} for the {@link #transcript}. */
	public final JScrollPane transcriptScrollArea;

	/**
	 * The {@linkplain JLabel label} that describes the current function of the
	 * {@linkplain #inputField input field}.
	 */
	public final JLabel inputLabel;

	/** The {@linkplain JTextField text field} that accepts standard input. */
	public final JTextField inputField;

	/**
	 * Keep track of recent commands in a history buffer.  Each submitted
	 * command is added to the end of the list.  Cursor-up retrieves the most
	 * recent selected line, and subsequent cursors-up retrieve previous lines,
	 * back to the first entry, then an empty command line, then the last entry
	 * again an so on.  An initial cursor-down selects the first entry and goes
	 * from there.
	 */
	public final List<String> commandHistory = new ArrayList<>();

	/**
	 * Which command was most recently retrieved by a cursor key since the last
	 * command was submitted.  -1 indicates no command has been retrieved by a
	 * cursor key, or that the entire list has been cycled an integral number of
	 * times (and the command line was blanked upon reaching -1).
	 */
	public int commandHistoryIndex = -1;

	/** Cycle one step backward in the command history. */
	final RetrievePreviousCommand retrievePreviousAction =
		new RetrievePreviousCommand(this);

	/** Cycle one step forward in the command history. */
	final RetrieveNextCommand retrieveNextAction =
		new RetrieveNextCommand(this);

	/* Actions. */

	/** The {@linkplain RefreshAction refresh action}. */
	@InnerAccess final RefreshAction refreshAction = new RefreshAction(this);

	/** The {@linkplain AboutAction "about Avail" action}. */
	@InnerAccess final AboutAction aboutAction = new AboutAction(this);

	/** The {@linkplain BuildAction build action}. */
	@InnerAccess final BuildAction buildAction = new BuildAction(this, false);

	/** The {@linkplain UnloadAction unload action}. */
	@InnerAccess final UnloadAction unloadAction = new UnloadAction(this);

	/** The {@linkplain UnloadAllAction unload-all action}. */
	@InnerAccess final UnloadAllAction unloadAllAction =
		new UnloadAllAction(this);

	/** The {@linkplain CancelAction cancel action}. */
	@InnerAccess final CancelAction cancelAction = new CancelAction(this);

	/** The {@linkplain CleanAction clean action}. */
	@InnerAccess final CleanAction cleanAction = new CleanAction(this);

	/**
	 * The {@linkplain GenerateDocumentationAction generate documentation
	 * action}.
	 */
	@InnerAccess final GenerateDocumentationAction documentAction =
		new GenerateDocumentationAction(this);

	/** The {@linkplain GenerateGraphAction generate graph action}. */
	@InnerAccess final GenerateGraphAction graphAction =
		new GenerateGraphAction(this);

	/**
	 * The {@linkplain SetDocumentationPathAction documentation path dialog
	 * action}.
	 */
	@InnerAccess final SetDocumentationPathAction setDocumentationPathAction =
		new SetDocumentationPathAction(this);

	/** The {@linkplain ReportAction report action}. */
	@InnerAccess final ReportAction reportAction = new ReportAction(this);

	/** The {@linkplain ClearReportAction clear report action}. */
	@InnerAccess final ClearReportAction clearReportAction =
		new ClearReportAction(this);

	/** The {@linkplain ClearTranscriptAction clear transcript action}. */
	@InnerAccess final ClearTranscriptAction clearTranscriptAction =
		new ClearTranscriptAction(this);

	/** The {@linkplain InsertEntryPointAction insert entry point action}. */
	@InnerAccess final InsertEntryPointAction insertEntryPointAction =
		new InsertEntryPointAction(this);

	/** The {@linkplain BuildAction action to build an entry point module}. */
	@InnerAccess final BuildAction buildEntryPointModuleAction =
		new BuildAction(this, true);

	/** Whether an entry point invocation (command line) is executing. */
	public boolean isRunning = false;

	/**
	 * Enable or disable controls and menu items based on the current state.
	 */
	public void setEnablements ()
	{
		final boolean busy = backgroundTask != null || isRunning;
		buildProgress.setEnabled(busy);
		buildProgress.setVisible(backgroundTask instanceof BuildTask);
		inputField.setEnabled(!busy || isRunning);
		retrievePreviousAction.setEnabled(!busy);
		retrieveNextAction.setEnabled(!busy);
		cancelAction.setEnabled(busy);
		buildAction.setEnabled(!busy && selectedModule() != null);
		unloadAction.setEnabled(!busy && selectedModuleIsLoaded());
		unloadAllAction.setEnabled(!busy);
		cleanAction.setEnabled(!busy);
		refreshAction.setEnabled(!busy);
		setDocumentationPathAction.setEnabled(!busy);
		documentAction.setEnabled(!busy && selectedModule() != null);
		graphAction.setEnabled(!busy && selectedModule() != null);
		insertEntryPointAction.setEnabled(
			!busy && selectedEntryPoint() != null);
		buildEntryPointModuleAction.setEnabled(
			!busy && selectedEntryPointModule() != null);
		inputLabel.setText(isRunning
			? "Console Input:"
			: "Command:");
		inputField.setBackground(isRunning
			? new Color(192, 255, 192)
			: null);
	}

	/**
	 * Clear the {@linkplain #transcript transcript}.
	 */
	public void clearTranscript ()
	{
		final StyledDocument doc = transcript.getStyledDocument();
		try
		{
			doc.remove(0, doc.getLength());
		}
		catch (final BadLocationException e)
		{
			// Shouldn't happen.
			assert false;
		}
	}

	/**
	 * Answer a {@link FileVisitor} suitable for recursively exploring an
	 * Avail root. A new {@code FileVisitor} should be obtained for each Avail
	 * root.
	 *
	 * @param stack
	 *        The stack on which to place Avail roots and packages.
	 * @param moduleRoot
	 *        The {@link ModuleRoot} within which to scan recursively.
	 * @return A {@code FileVisitor}.
	 */
	private FileVisitor<Path> moduleTreeVisitor (
		final Deque<DefaultMutableTreeNode> stack,
		final ModuleRoot moduleRoot)
	{
		final String extension = ModuleNameResolver.availExtension;
		final Mutable<Boolean> isRoot = new Mutable<Boolean>(true);
		final FileVisitor<Path> visitor = new FileVisitor<Path>()
		{
			@Override
			public FileVisitResult preVisitDirectory (
					final @Nullable Path dir,
					final @Nullable BasicFileAttributes unused)
				throws IOException
			{
				assert dir != null;
				final DefaultMutableTreeNode parentNode = stack.peekFirst();
				if (isRoot.value)
				{
					// Add a ModuleRoot.
					isRoot.value = false;
					assert stack.size() == 1;
					final ModuleRootNode node =
						new ModuleRootNode(availBuilder, moduleRoot);
					parentNode.add(node);
					stack.addFirst(node);
					return FileVisitResult.CONTINUE;
				}
				final String fileName = dir.getFileName().toString();
				if (fileName.endsWith(extension))
				{
					final String localName = fileName.substring(
						0, fileName.length() - extension.length());
					final ModuleName moduleName;
					if (parentNode instanceof ModuleRootNode)
					{
						// Add a top-level package.
						final ModuleRootNode strongParentNode =
							(ModuleRootNode)parentNode;
						final ModuleRoot thisRoot =
							strongParentNode.moduleRoot();
						assert thisRoot == moduleRoot;
						moduleName = new ModuleName(
							"/" + moduleRoot.name() + "/" + localName);
					}
					else
					{
						// Add a non-top-level package.
						assert parentNode instanceof ModuleOrPackageNode;
						final ModuleOrPackageNode strongParentNode =
							(ModuleOrPackageNode)parentNode;
						assert strongParentNode.isPackage();
						final ResolvedModuleName parentModuleName =
							strongParentNode.resolvedModuleName();
						moduleName = new ModuleName(
							parentModuleName.qualifiedName(), localName);
					}
					final ResolvedModuleName resolved;
					try
					{
						resolved = resolver.resolve(moduleName, null);
					}
					catch (final UnresolvedDependencyException e)
					{
						throw new RuntimeException(e);
					}
					final ModuleOrPackageNode node =
						new ModuleOrPackageNode(availBuilder, resolved, true);
					parentNode.add(node);
					stack.addFirst(node);
					return FileVisitResult.CONTINUE;
				}
				return FileVisitResult.SKIP_SUBTREE;
			}

			@Override
			public FileVisitResult postVisitDirectory (
					final @Nullable Path dir,
					final @Nullable IOException ex)
				throws IOException
			{
				assert dir != null;
				// Pop the node from the stack.
				stack.removeFirst();
				if (ex != null)
				{
					ex.printStackTrace();
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile (
					final @Nullable Path file,
					final @Nullable BasicFileAttributes attrs)
				throws IOException
			{
				assert file != null;
				final DefaultMutableTreeNode parentNode = stack.peekFirst();
				if (isRoot.value)
				{
					throw new IOException("Avail root should be a directory");
				}
				final String fileName = file.getFileName().toString();
				if (fileName.endsWith(extension))
				{
					final String localName = fileName.substring(
						0, fileName.length() - extension.length());
					final ModuleName moduleName;
					if (parentNode instanceof ModuleRootNode)
					{
						// Add a top-level module (directly in a root).
						final ModuleRootNode strongParentNode =
							(ModuleRootNode)parentNode;
						final ModuleRoot thisRoot =
							strongParentNode.moduleRoot();
						assert thisRoot == moduleRoot;
						moduleName = new ModuleName(
							"/" + moduleRoot.name() + "/" + localName);
					}
					else
					{
						// Add a non-top-level module.
						assert parentNode instanceof ModuleOrPackageNode;
						final ModuleOrPackageNode strongParentNode =
							(ModuleOrPackageNode)parentNode;
						assert strongParentNode.isPackage();
						final ResolvedModuleName parentModuleName =
							strongParentNode.resolvedModuleName();
						moduleName = new ModuleName(
							parentModuleName.qualifiedName(), localName);
					}
					final ResolvedModuleName resolved;
					try
					{
						resolved = resolver.resolve(moduleName, null);
					}
					catch (final UnresolvedDependencyException e)
					{
						throw new RuntimeException(e);
					}
					final ModuleOrPackageNode node =
						new ModuleOrPackageNode(availBuilder, resolved, false);
					if (!resolved.isPackage())
					{
						parentNode.add(node);
					}
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed (
					final @Nullable Path file,
					final @Nullable IOException ex)
				throws IOException
			{
				if (ex != null)
				{
					System.err.printf("couldn't visit \"%s\"", file);
					ex.printStackTrace();
				}
				return FileVisitResult.CONTINUE;
			}
		};
		return visitor;
	}

	/**
	 * Answer a {@linkplain TreeNode tree node} that represents the (invisible)
	 * root of the Avail module tree.
	 *
	 * @return The (invisible) root of the module tree.
	 */
	public TreeNode newModuleTree ()
	{
		final ModuleRoots roots = resolver.moduleRoots();
		final DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode(
			"(packages hidden root)");
		// Put the invisible root onto the work stack.
		final Deque<DefaultMutableTreeNode> stack = new ArrayDeque<>();
		stack.add(treeRoot);
		for (final ModuleRoot root : roots.roots())
		{
			// Obtain the path associated with the module root.
			assert root != null;
			root.repository().reopenIfNecessary();
			final File rootDirectory = root.sourceDirectory();
			assert rootDirectory != null;
			try
			{
				Files.walkFileTree(
					Paths.get(rootDirectory.getAbsolutePath()),
					EnumSet.of(FileVisitOption.FOLLOW_LINKS),
					Integer.MAX_VALUE,
					moduleTreeVisitor(stack, root));
			}
			catch (final IOException e)
			{
				e.printStackTrace();
				stack.clear();
				stack.add(treeRoot);
			}
		}
		return treeRoot;
	}

	/**
	 * Answer a {@linkplain TreeNode tree node} that represents the (invisible)
	 * root of the Avail entry points tree.
	 *
	 * @return The (invisible) root of the entry points tree.
	 */
	public TreeNode newEntryPointsTree ()
	{
		final Object mutex = new Object();
		final Map<String, DefaultMutableTreeNode> moduleNodes = new HashMap<>();
		availBuilder.traceDirectories(
			new Continuation2<ResolvedModuleName, ModuleVersion>()
			{
				@Override
				public void value (
					final @Nullable ResolvedModuleName resolvedName,
					final @Nullable ModuleVersion moduleVersion)
				{
					assert resolvedName != null;
					assert moduleVersion != null;
					final List<String> entryPoints =
						moduleVersion.getEntryPoints();
					if (!entryPoints.isEmpty())
					{
						final EntryPointModuleNode moduleNode =
							new EntryPointModuleNode(
								availBuilder, resolvedName);
						for (final String entryPoint : entryPoints)
						{
							final EntryPointNode entryPointNode =
								new EntryPointNode(
									availBuilder, resolvedName, entryPoint);
							moduleNode.add(entryPointNode);
						}
						synchronized (mutex)
						{
							moduleNodes.put(
								resolvedName.qualifiedName(), moduleNode);
						}
					}
				}
			});
		final String [] mapKeys = moduleNodes.keySet().toArray(
			new String [moduleNodes.size()]);
		Arrays.sort(mapKeys);
		final DefaultMutableTreeNode entryPointsTreeRoot =
			new DefaultMutableTreeNode("(entry points hidden root)");
		for (final String moduleLabel : mapKeys)
		{
			entryPointsTreeRoot.add(moduleNodes.get(moduleLabel));
		}
		return entryPointsTreeRoot;
	}

	/**
	 * Answer the {@linkplain TreePath path} to the specified module name in the
	 * {@linkplain #moduleTree module tree}.
	 *
	 * @param moduleName A module name.
	 * @return A tree path, or {@code null} if the module name is not present in
	 *         the tree.
	 */
	@SuppressWarnings("unchecked")
	public @Nullable TreePath modulePath (final String moduleName)
	{
		final String[] path = moduleName.split("/");
		final TreeModel model = moduleTree.getModel();
		final DefaultMutableTreeNode treeRoot =
			(DefaultMutableTreeNode) model.getRoot();
		Enumeration<DefaultMutableTreeNode> nodes = treeRoot.children();
		int index = 1;
		while (nodes.hasMoreElements())
		{
			final AbstractBuilderFrameTreeNode node =
				(AbstractBuilderFrameTreeNode) nodes.nextElement();
			if (node.isSpecifiedByString(path[index]))
			{
				index++;
				if (index == path.length)
				{
					return new TreePath(node.getPath());
				}
				nodes = node.children();
			}
		}
		return null;
	}

	/**
	 * Answer the currently selected {@linkplain ModuleOrPackageNode module
	 * node}.
	 *
	 * @return A module node, or {@code null} if no module is selected.
	 */
	@InnerAccess @Nullable ModuleOrPackageNode selectedModuleNode ()
	{
		final TreePath path = moduleTree.getSelectionPath();
		if (path == null)
		{
			return null;
		}
		final DefaultMutableTreeNode selection =
			(DefaultMutableTreeNode) path.getLastPathComponent();
		if (selection instanceof ModuleOrPackageNode)
		{
			return (ModuleOrPackageNode) selection;
		}
		return null;
	}

	/**
	 * Is the selected {@linkplain ModuleDescriptor module} loaded?
	 *
	 * @return {@code true} if the selected module is loaded, {@code false} if
	 *         no module is selected or the selected module is not loaded.
	 */
	@InnerAccess boolean selectedModuleIsLoaded ()
	{
		final ModuleOrPackageNode node = selectedModuleNode();
		if (node == null)
		{
			return false;
		}
		return node.isLoaded();
	}

	/**
	 * Answer the {@linkplain ResolvedModuleName name} of the currently selected
	 * {@linkplain ModuleDescriptor module}.
	 *
	 * @return A fully-qualified module name, or {@code null} if no module is
	 *         selected.
	 */
	public @Nullable ResolvedModuleName selectedModule ()
	{
		final ModuleOrPackageNode node = selectedModuleNode();
		if (node == null)
		{
			return null;
		}
		return node.resolvedModuleName();
	}

	/**
	 * Answer the currently selected entry point, or {@code null} if none.
	 *
	 * @return An entry point name, or {@code null} if no entry point is
	 *         selected.
	 */
	public @Nullable String selectedEntryPoint ()
	{
		final TreePath path = entryPointsTree.getSelectionPath();
		if (path == null)
		{
			return null;
		}
		final DefaultMutableTreeNode selection =
			(DefaultMutableTreeNode) path.getLastPathComponent();
		if (!(selection instanceof EntryPointNode))
		{
			return null;
		}
		final EntryPointNode strongSelection = (EntryPointNode) selection;
		return strongSelection.entryPointString();
	}

	/**
	 * Answer the resolved name of the module selected in the {@link
	 * #entryPointsTree}, or the module defining the entry point that's
	 * selected, or {@code null} if none.
	 *
	 * @return A {@link ResolvedModuleName} or {@code null}.
	 */
	public @Nullable ResolvedModuleName selectedEntryPointModule ()
	{
		final TreePath path = entryPointsTree.getSelectionPath();
		if (path == null)
		{
			return null;
		}
		final DefaultMutableTreeNode selection =
			(DefaultMutableTreeNode) path.getLastPathComponent();
		if (selection instanceof EntryPointNode)
		{
			return ((EntryPointNode)selection).resolvedModuleName();
		}
		else if (selection instanceof EntryPointModuleNode)
		{
			return ((EntryPointModuleNode)selection).resolvedModuleName();
		}
		return null;
	}

	/**
	 * Redirect the standard streams.
	 */
	@InnerAccess void redirectStandardStreams ()
	{
		outputStream = new PrintStream(new BuildOutputStream(false));
		errorStream = new PrintStream(new BuildOutputStream(true));
		inputStream = new BuildInputStream();
		System.setOut(outputStream);
		System.setErr(errorStream);
		System.setIn(inputStream);
	}

	/**
	 * Update the {@linkplain #buildProgress build progress bar}.
	 *
	 * @param moduleName
	 *        The {@linkplain ModuleDescriptor module} undergoing compilation.
	 * @param position
	 *        The parse position, in bytes.
	 * @param globalCodeSize
	 *        The module size, in bytes.
	 */
	public void updateBuildProgress (
		final ModuleName moduleName,
		final Long position,
		final Long globalCodeSize)
	{
		final int percent = (int) ((position * 100) / globalCodeSize);
		buildProgress.setValue(percent);
		buildProgress.setString(String.format(
			"Build Progress: %,d / %,d bytes (%,d%%)",
			position,
			globalCodeSize,
			percent));
	}

	/** The user-specific {@link Preferences} for this application to use. */
	@InnerAccess final Preferences basePreferences =
		Preferences.userNodeForPackage(getClass());

	/** The key under which to organize all placement information. */
	final String placementByMonitorLayoutString = "placementByMonitorLayout";

	/** The leaf key under which to store a single window placement. */
	final String placementLeafKeyString = "placement";

	/**
	 * Answer a {@link List} of {@link Rectangle}s corresponding with the
	 * physical monitors into which {@link Frame}s may be positioned.
	 *
	 * @return The list of rectangles to which physical screens are mapped.
	 */
	@InnerAccess
	List<Rectangle> allMonitorRectangles ()
	{
		final GraphicsEnvironment graphicsEnvironment =
			GraphicsEnvironment.getLocalGraphicsEnvironment();
		final GraphicsDevice[] screens = graphicsEnvironment.getScreenDevices();
		final List<Rectangle> allRectangles = new ArrayList<>();
		for (final GraphicsDevice screen : screens)
		{
			for (final GraphicsConfiguration gc : screen.getConfigurations())
			{
				allRectangles.add(gc.getBounds());
			}
		}
		return allRectangles;
	}

	/**
	 * Answer the {@link Preferences} node responsible for holding the default
	 * window position and size for the current monitor configuration.
	 *
	 * @param monitorRectangles
	 *            The relative locations of all physical screens.
	 *
	 * @return The {@code Preferences} node in which placement information for
	 *         the current monitor configuration can be stored and retrieved.
	 */
	@InnerAccess
	Preferences placementPreferencesNodeForRectangles (
		final List<Rectangle> monitorRectangles)
	{
		final StringBuilder allBoundsString = new StringBuilder();
		for (final Rectangle rectangle : monitorRectangles)
		{
			allBoundsString.append(
				String.format(
					"%d,%d,%d,%d;",
					rectangle.x,
					rectangle.y,
					rectangle.width,
					rectangle.height));
		}
		return basePreferences.node(
			placementByMonitorLayoutString + "/" + allBoundsString);
	}

	/**
	 * Information about the window layout.
	 */
	private static class LayoutConfiguration
	{
		/** The preferred location and size of the window, if specified. */
		@Nullable Rectangle placement = null;

		/**
		 * The width of the left region of the builder frame in pixels, if
		 * specified
		 */
		@Nullable Integer leftSectionWidth = null;

		/**
		 * Answer this configuration's recommended width in pixels for the left
		 * region of the window, supplying a suitable default if necessary.
		 *
		 * @return The recommended width of the left part.
		 */
		int leftSectionWidth ()
		{
			final Integer w = leftSectionWidth;
			return w != null ? w : 200;
		}

		/**
		 * The proportion, if specified, as a float between {@code 0.0} and
		 * {@code 1.0} of the height of the top left module region in relative
		 * proportional to the height of the entire builder frame.
		 */
		@Nullable Double moduleVerticalProportion = null;

		/**
		 * Add this configuration's recommended proportion of height of the
		 * modules list versus the entire frame's height, supplying a default
		 * if necessary.  It must be between 0.0 and 1.0 inclusive.
		 *
		 * @return The vertical proportion of the modules area.
		 */
		double moduleVerticalProportion ()
		{
			final Double h = moduleVerticalProportion;
			return h != null ? max(0.0, min(1.0, h)) : 0.5;
		}

		/**
		 * Answer a string representation of this configuration that is suitable
		 * for being stored and restored via the {@link
		 * LayoutConfiguration#LayoutConfiguration(String)} constructor.
		 *
		 * <p>
		 * The layout should be fairly stable to avoid treating older versions
		 * as malformed.  To that end, we use a simple list of strings, adding
		 * entries for new purposes to the end, and never removing or changing
		 * the meaning of existing entries.
		 * </p>
		 *
		 * @return A string.
		 */
		public String stringToStore ()
		{
			final String [] strings = new String [6];
			final Rectangle p = placement;
			if (p != null)
			{
				strings[0] = Integer.toString(p.x);
				strings[1] = Integer.toString(p.y);
				strings[2] = Integer.toString(p.width);
				strings[3] = Integer.toString(p.height);
			}
			final Integer w = leftSectionWidth;
			if (w != null)
			{
				strings[4] = Integer.toString(w);
			}
			final Double h = moduleVerticalProportion;
			if (h != null)
			{
				strings[5] = Double.toString(h);
			}
			final StringBuilder builder = new StringBuilder();
			boolean first = true;
			for (final String string : strings)
			{
				if (!first)
				{
					builder.append(',');
				}
				if (string != null)
				{
					builder.append(string);
				}
				first = false;
			}
			return builder.toString();
		}

		/**
		 * Construct a new {@link AvailWorkbench.LayoutConfiguration} with
		 * no preferences specified.
		 */
		public LayoutConfiguration ()
		{
			// all null
		}

		/**
		 * Construct a new {@link AvailWorkbench.LayoutConfiguration} with
		 * preferences specified by some private encoding in the provided {@link
		 * String}.
		 *
		 * @param input
		 *        A string in some encoding compatible with that produced
		 *        by {@link #stringToStore()}.
		 */
		public LayoutConfiguration (final String input)
		{
			final String [] substrings = input.split(",");
			try
			{
				if (substrings.length >= 4)
				{
					final int x = Integer.parseInt(substrings[0]);
					final int y = Integer.parseInt(substrings[1]);
					final int w = Integer.parseInt(substrings[2]);
					final int h = Integer.parseInt(substrings[3]);
					placement = new Rectangle(x, y, w, h);
				}
			}
			catch (final NumberFormatException e)
			{
				// ignore
			}
			try
			{
				if (substrings.length >= 5)
				{
					leftSectionWidth = Integer.parseInt(substrings[4]);
				}
			}
			catch (final NumberFormatException e)
			{
				// ignore
			}
			try
			{
				if (substrings.length >= 6)
				{
					moduleVerticalProportion =
						Double.parseDouble(substrings[5]);
				}
			}
			catch (final NumberFormatException e)
			{
				// ignore
			}
		}
	}

	/**
	 * Figure out how to initially lay out the frame, based on previously saved
	 * preference information.
	 *
	 * @return The initial {@link LayoutConfiguration}.
	 */
	@InnerAccess LayoutConfiguration getInitialConfiguration ()
	{
		final Preferences preferences =
			placementPreferencesNodeForRectangles(allMonitorRectangles());
		final String configurationString = preferences.get(
			placementLeafKeyString,
			null);
		if (configurationString == null)
		{
			return new LayoutConfiguration();
		}
		return new LayoutConfiguration(configurationString);
	}

	/**
	 * The {@link DefaultTreeCellRenderer} that knows how to render tree nodes
	 * for my {@link #moduleTree} and my {@link #entryPointsTree}.
	 */
	final DefaultTreeCellRenderer treeRenderer = new DefaultTreeCellRenderer()
	{
		@Override
		public Component getTreeCellRendererComponent(
			final @Nullable JTree tree,
			final @Nullable Object value,
			final boolean selected1,
			final boolean expanded,
			final boolean leaf,
			final int row,
			final boolean hasFocus1)
		{
			assert value != null;
			if (value instanceof AbstractBuilderFrameTreeNode)
			{
				assert tree != null;
				final AbstractBuilderFrameTreeNode node =
					(AbstractBuilderFrameTreeNode) value;
				final Icon icon = node.icon(tree.getRowHeight());
				setLeafIcon(icon);
				setOpenIcon(icon);
				setClosedIcon(icon);
				String html = node.htmlText(selected1);
				html = "<html>" + html + "</html>";
				final JComponent component =
					(JComponent) super.getTreeCellRendererComponent(
						tree,
						html,
						selected1,
						expanded,
						leaf,
						row,
						hasFocus1);
				return component;
			}
			return super.getTreeCellRendererComponent(
				tree, value, selected1, expanded, leaf, row, hasFocus1);
		}
	};

	/**
	 * Construct a new {@link AvailWorkbench}.
	 *
	 * @param resolver
	 *        The {@linkplain ModuleNameResolver module name resolver}.
	 * @param initialTarget
	 *        The initial target {@linkplain ModuleName module}, possibly the
	 *        empty string.
	 */
	@InnerAccess AvailWorkbench (
		final ModuleNameResolver resolver,
		final String initialTarget)
	{
		// Set module components.
		this.resolver = resolver;
		final AvailRuntime runtime = new AvailRuntime(resolver);
		availBuilder = new AvailBuilder(runtime);

		// Get the existing preferences early for plugging in at the right
		// times during construction.
		final LayoutConfiguration configuration = getInitialConfiguration();

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// Set *just* the window title...
		setTitle("Avail Workbench");
		setResizable(true);

		// Create the menu bar and its menus.
		final JMenuBar menuBar = new JMenuBar();
		final JMenu buildMenu = new JMenu("Build");
		if (!runningOnMac)
		{
			buildMenu.add(new JMenuItem(aboutAction));
			buildMenu.addSeparator();
		}
		buildMenu.add(new JMenuItem(buildAction));
		buildMenu.add(new JMenuItem(cancelAction));
		buildMenu.addSeparator();
		buildMenu.add(new JMenuItem(unloadAction));
		buildMenu.add(new JMenuItem(unloadAllAction));
		buildMenu.add(new JMenuItem(cleanAction));
		buildMenu.addSeparator();
		buildMenu.add(new JMenuItem(refreshAction));
		menuBar.add(buildMenu);
		final JMenu documentationMenu = new JMenu("Document");
		documentationMenu.add(new JMenuItem(documentAction));
		documentationMenu.addSeparator();
		documentationMenu.add(new JMenuItem(setDocumentationPathAction));
		menuBar.add(documentationMenu);
		final JMenu runMenu = new JMenu("Run");
		runMenu.add(new JMenuItem(insertEntryPointAction));
		runMenu.addSeparator();
		runMenu.add(new JMenuItem(clearTranscriptAction));
		menuBar.add(runMenu);
		if (showDeveloperTools)
		{
			final JMenu devMenu = new JMenu("Developer");
			devMenu.add(new JMenuItem(reportAction));
			devMenu.add(new JMenuItem(clearReportAction));
			devMenu.addSeparator();
			devMenu.add(new JMenuItem(graphAction));
			menuBar.add(devMenu);
		}
		setJMenuBar(menuBar);

		final JPopupMenu buildPopup = new JPopupMenu("Modules");
		buildPopup.add(new JMenuItem(buildAction));
		buildPopup.add(new JMenuItem(documentAction));
		buildPopup.addSeparator();
		buildPopup.add(new JMenuItem(unloadAction));
		buildPopup.addSeparator();
		buildPopup.add(new JMenuItem(refreshAction));
		// The refresh item needs a little help ...
		InputMap inputMap = getRootPane().getInputMap(
			JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap actionMap = getRootPane().getActionMap();
		inputMap.put(KeyStroke.getKeyStroke("F5"), "refresh");
		actionMap.put("refresh", refreshAction);

		final JPopupMenu entryPointsPopup = new JPopupMenu("Entry points");
		entryPointsPopup.add(new JMenuItem(buildEntryPointModuleAction));
		entryPointsPopup.addSeparator();
		entryPointsPopup.add(new JMenuItem(insertEntryPointAction));
		entryPointsPopup.addSeparator();
		entryPointsPopup.add(new JMenuItem(refreshAction));

		final JPopupMenu transcriptPopup = new JPopupMenu("Transcript");
		transcriptPopup.add(new JMenuItem(clearTranscriptAction));

		// Collect the modules and entry points.
		final TreeNode modules = newModuleTree();
		final TreeNode entryPoints = newEntryPointsTree();

		// Create the module tree.
		final JScrollPane moduleTreeScrollArea = new JScrollPane();
		moduleTreeScrollArea.setHorizontalScrollBarPolicy(
			HORIZONTAL_SCROLLBAR_AS_NEEDED);
		moduleTreeScrollArea.setVerticalScrollBarPolicy(
			VERTICAL_SCROLLBAR_AS_NEEDED);
		moduleTreeScrollArea.setMinimumSize(new Dimension(100, 0));
		moduleTree = new JTree(modules);
		moduleTree.setToolTipText(
			"All modules, organized by module root.");
		moduleTree.setComponentPopupMenu(buildPopup);
		moduleTree.setEditable(false);
		moduleTree.setEnabled(true);
		moduleTree.setFocusable(true);
		moduleTree.getSelectionModel().setSelectionMode(
			TreeSelectionModel.SINGLE_TREE_SELECTION);
		moduleTree.setToggleClickCount(0);
		moduleTree.setShowsRootHandles(true);
		moduleTree.setRootVisible(false);
		moduleTree.addTreeSelectionListener(new TreeSelectionListener()
		{
			@Override
 			public void valueChanged (final @Nullable TreeSelectionEvent event)
			{
				setEnablements();
			}
		});
		moduleTree.setCellRenderer(treeRenderer);
		moduleTree.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked (final @Nullable MouseEvent e)
			{
				assert e != null;
				if (buildAction.isEnabled()
					&& e.getClickCount() == 2
					&& e.getButton() == MouseEvent.BUTTON1)
				{
					e.consume();
					buildAction.actionPerformed(
						new ActionEvent(moduleTree, -1, "Build"));
				}
			}
		});
		inputMap = moduleTree.getInputMap(
			JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		actionMap = moduleTree.getActionMap();
		inputMap.put(KeyStroke.getKeyStroke("ENTER"), "build");
		actionMap.put("build", buildAction);
		// Expand rows bottom-to-top to expand only the root nodes.
		for (int i = moduleTree.getRowCount() - 1; i >= 0; i--)
		{
			moduleTree.expandRow(i);
		}
		moduleTreeScrollArea.setViewportView(moduleTree);

		// Create the entry points tree.
		final JScrollPane entryPointsScrollArea = new JScrollPane();
		entryPointsScrollArea.setHorizontalScrollBarPolicy(
			HORIZONTAL_SCROLLBAR_AS_NEEDED);
		entryPointsScrollArea.setVerticalScrollBarPolicy(
			VERTICAL_SCROLLBAR_AS_NEEDED);
		entryPointsScrollArea.setMinimumSize(new Dimension(100, 0));
		entryPointsTree = new JTree(entryPoints);
		entryPointsTree.setToolTipText(
			"All entry points, organized by defining module.");
		entryPointsTree.setComponentPopupMenu(entryPointsPopup);
		entryPointsTree.setEditable(false);
		entryPointsTree.setEnabled(true);
		entryPointsTree.setFocusable(true);
		entryPointsTree.getSelectionModel().setSelectionMode(
			TreeSelectionModel.SINGLE_TREE_SELECTION);
		entryPointsTree.setToggleClickCount(0);
		entryPointsTree.setShowsRootHandles(true);
		entryPointsTree.setRootVisible(false);
		entryPointsTree.addTreeSelectionListener(new TreeSelectionListener()
		{
			@Override
 			public void valueChanged (final @Nullable TreeSelectionEvent event)
			{
				setEnablements();
			}
		});
		entryPointsTree.setCellRenderer(treeRenderer);
		entryPointsTree.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked (final @Nullable MouseEvent e)
			{
				assert e != null;
				if (selectedEntryPoint() != null)
				{
					if (insertEntryPointAction.isEnabled()
						&& e.getClickCount() == 2
						&& e.getButton() == MouseEvent.BUTTON1)
					{
						e.consume();
						final ActionEvent actionEvent = new ActionEvent(
							entryPointsTree, -1, "Insert entry point");
						insertEntryPointAction.actionPerformed(actionEvent);
					}
				}
				else if (selectedEntryPointModule() != null)
				{
					if (buildEntryPointModuleAction.isEnabled()
						&& e.getClickCount() == 2
						&& e.getButton() == MouseEvent.BUTTON1)
					{
						e.consume();
						final ActionEvent actionEvent = new ActionEvent(
							entryPointsTree, -1, "Build entry point module");
						buildEntryPointModuleAction.actionPerformed(
							actionEvent);
					}
				}
			}
		});
		inputMap = entryPointsTree.getInputMap(
			JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		actionMap = entryPointsTree.getActionMap();
		inputMap.put(KeyStroke.getKeyStroke("ENTER"), "build");
		actionMap.put("build", buildAction);
		for (int i = 0; i < entryPointsTree.getRowCount(); i++)
		{
			entryPointsTree.expandRow(i);
		}
		entryPointsScrollArea.setViewportView(entryPointsTree);

		// Create the build progress bar.
		buildProgress = new JProgressBar(0, 100);
		buildProgress.setToolTipText(
			"Progress indicator for the build.");
		buildProgress.setDoubleBuffered(true);
		buildProgress.setEnabled(false);
		buildProgress.setFocusable(false);
		buildProgress.setIndeterminate(false);
		buildProgress.setStringPainted(true);
		buildProgress.setString("Build Progress:");
		buildProgress.setValue(0);

		// Create the transcript.
		final JLabel outputLabel = new JLabel("Transcript:");
		transcriptScrollArea = new JScrollPane();
		transcriptScrollArea.setHorizontalScrollBarPolicy(
			HORIZONTAL_SCROLLBAR_AS_NEEDED);
		transcriptScrollArea.setVerticalScrollBarPolicy(
			VERTICAL_SCROLLBAR_AS_NEEDED);
		// Make this row and column be where the excess space goes.
		// And reset the weights...
		transcript = new JTextPane();
		transcript.setBorder(BorderFactory.createEtchedBorder());
		transcript.setComponentPopupMenu(transcriptPopup);
		transcript.setEditable(false);
		transcript.setEnabled(true);
		transcript.setFocusable(true);
		transcript.setPreferredSize(new Dimension(0, 500));
		transcriptScrollArea.setViewportView(transcript);

		// Create the input area.
		inputLabel = new JLabel("Command:");
		inputField = new JTextField();
		inputField.setToolTipText(
			"Enter commands and interact with Avail programs.  Press "
			+ "ENTER to submit.");
		inputField.setAction(new SubmitInputAction(this));
		inputMap = inputField.getInputMap(JComponent.WHEN_FOCUSED);
		actionMap = inputField.getActionMap();
		inputMap.put(KeyStroke.getKeyStroke("UP"), "up");
		actionMap.put("up", retrievePreviousAction);
		inputMap.put(KeyStroke.getKeyStroke("DOWN"), "down");
		actionMap.put("down", retrieveNextAction);
		inputField.setColumns(60);
		inputField.setEditable(true);
		inputField.setEnabled(true);
		inputField.setFocusable(true);

		// Subscribe to module loading events.
		availBuilder.subscribeToModuleLoading(
			new Continuation2<LoadedModule, Boolean>()
			{
				@Override
				public void value (
					@Nullable final LoadedModule loadedModule,
					@Nullable final Boolean loaded)
				{
					assert loadedModule != null;
					moduleTree.repaint();
					if (loadedModule.entryPoints().size() > 0)
					{
						entryPointsTree.repaint();
					}
				}
			});

		// Set up styles for the transcript.
		final StyledDocument doc = transcript.getStyledDocument();
		final SimpleAttributeSet attributes = new SimpleAttributeSet();
		final TabStop[] tabStops = new TabStop[500];
		for (int i = 0; i < tabStops.length; i++)
		{
			tabStops[i] = new TabStop(
				32.0f * (i + 1),
				TabStop.ALIGN_LEFT,
				TabStop.LEAD_NONE);
		}
		final TabSet tabSet = new TabSet(tabStops);
		StyleConstants.setTabSet(attributes, tabSet);
		doc.setParagraphAttributes(0, doc.getLength(), attributes, false);
		final Style defaultStyle =
			StyleContext.getDefaultStyleContext().getStyle(
				StyleContext.DEFAULT_STYLE);
		defaultStyle.addAttributes(attributes);
		StyleConstants.setFontFamily(defaultStyle, "Monospaced");
		final Style outputStyle = doc.addStyle(outputStyleName, defaultStyle);
		StyleConstants.setForeground(outputStyle, Color.BLACK);
		final Style errorStyle = doc.addStyle(errorStyleName, defaultStyle);
		StyleConstants.setForeground(errorStyle, Color.RED);
		final Style inputStyle = doc.addStyle(inputStyleName, defaultStyle);
		StyleConstants.setForeground(inputStyle, new Color(32, 144, 32));
		final Style infoStyle = doc.addStyle(infoStyleName, defaultStyle);
		StyleConstants.setForeground(infoStyle, Color.BLUE);
		final Style commandStyle = doc.addStyle(commandStyleName, defaultStyle);
		StyleConstants.setForeground(commandStyle, Color.MAGENTA);

		// Redirect the standard streams.
		redirectStandardStreams();
		runtime.setStandardStreams(System.out, System.err, null);

		final JSplitPane leftPane = new JSplitPane(
			JSplitPane.VERTICAL_SPLIT,
			true,
			moduleTreeScrollArea,
			entryPointsScrollArea);
		leftPane.setDividerLocation(configuration.moduleVerticalProportion());
		leftPane.setResizeWeight(configuration.moduleVerticalProportion());
		final JPanel rightPane = new JPanel();
		final GroupLayout rightPaneLayout = new GroupLayout(rightPane);
		rightPane.setLayout(rightPaneLayout);
		rightPaneLayout.setAutoCreateGaps(true);
		rightPaneLayout.setHorizontalGroup(
			rightPaneLayout.createParallelGroup()
				.addComponent(buildProgress)
				.addComponent(outputLabel)
				.addComponent(transcriptScrollArea)
				.addComponent(inputLabel)
				.addComponent(inputField));
		rightPaneLayout.setVerticalGroup(
			rightPaneLayout.createSequentialGroup()
				.addGroup(rightPaneLayout.createSequentialGroup()
					.addComponent(buildProgress))
				.addGroup(rightPaneLayout.createSequentialGroup()
					.addComponent(outputLabel)
					.addComponent(transcriptScrollArea))
				.addGroup(rightPaneLayout.createSequentialGroup()
					.addComponent(inputLabel)
					.addComponent(
						inputField,
						GroupLayout.PREFERRED_SIZE,
						GroupLayout.PREFERRED_SIZE,
						GroupLayout.PREFERRED_SIZE)));

		final JSplitPane mainSplit = new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,
			true,
			leftPane,
			rightPane);
		mainSplit.setDividerLocation(configuration.leftSectionWidth());
		getContentPane().add(mainSplit);
		pack();
		if (configuration.placement != null)
		{
			setBounds(configuration.placement);
		}

		// Save placement when closing.
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing (final @Nullable WindowEvent e)
			{
				final Preferences preferences =
					placementPreferencesNodeForRectangles(
						allMonitorRectangles());
				final LayoutConfiguration saveConfiguration =
					new LayoutConfiguration();
				saveConfiguration.placement = getBounds();
				saveConfiguration.leftSectionWidth =
					mainSplit.getDividerLocation();
				saveConfiguration.moduleVerticalProportion =
					leftPane.getDividerLocation()
						/ max(leftPane.getHeight(), 1.0);
				preferences.put(
					placementLeafKeyString,
					saveConfiguration.stringToStore());
				super.windowClosing(e);
			}
		});
		if (runningOnMac)
		{
			OSXUtility.setQuitHandler(
				new Transformer1<Object, Boolean>()
				{
					@Override
					public @Nullable Boolean value (
						final @Nullable Object event)
					{
						// Quit was pressed.  Close the workbench, which should
						// save window position state then exit.
						// Apple's apple.eawt.quitStrategy has never worked, to
						// the best of my knowledge.  It's a trick.  We must
						// close the workbench window explicitly to give it a
						// chance to save.
						final WindowEvent closeEvent =
							new WindowEvent(
								AvailWorkbench.this,
								WindowEvent.WINDOW_CLOSING);
						dispatchEvent(closeEvent);
						return true;
					}
				});
			OSXUtility.setAboutHandler(
				new Transformer1<Object, Boolean>()
				{
					@Override
					public @Nullable Boolean value (
						@Nullable final Object event)
					{
						aboutAction.showDialog();
						return true;
					}
				});
		}

		// Select an initial module if specified.
		validate();
		if (!initialTarget.isEmpty())
		{
			final TreePath path = modulePath(initialTarget);
			if (path != null)
			{
				moduleTree.setSelectionPath(path);
				moduleTree.scrollRowToVisible(moduleTree.getRowForPath(path));
			}
		}
		setEnablements();
	}

	/**
	 * Make the workbench behave more like a Mac application.
	 */
	private static void setUpForMac ()
	{
		assert runningOnMac;
		try
		{

//			OSXUtility.setPreferencesHandler(
//				this, getClass().getDeclaredMethod("preferences", (Class[])null));
//			OSXUtility.setFileHandler(
//				this, getClass().getDeclaredMethod("loadImageFile", new Class[] { String.class }));

			System.setProperty("apple.laf.useScreenMenuBar", "true");
			System.setProperty(
				"com.apple.mrj.application.apple.menu.about.name",
				"Avail Workbench");
			System.setProperty(
				"com.apple.awt.graphics.UseQuartz",
				"true");

			final Class<?> appClass = Class.forName(
				"com.apple.eawt.Application",
				true,
				AvailWorkbench.class.getClassLoader());
			final Object application =
				appClass.getMethod("getApplication").invoke(null);
			final Image image =
				new ImageIcon("resources/workbench/AvailHammer.png").getImage();
			appClass.getMethod("setDockIconImage", Image.class).invoke(
				application,
				image);
			appClass.getMethod("setDockIconBadge", String.class).invoke(
				application,
				"DEV");
		}
		catch (final Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Launch the {@linkplain AvailBuilder Avail builder} {@linkplain
	 * AvailWorkbench UI}.
	 *
	 * @param args
	 *        The command line arguments.
	 * @throws Exception
	 *         If something goes wrong.
	 */
	public static void main (final String[] args)
	throws Exception
	{
		if (runningOnMac)
		{
			setUpForMac();
		}

		final ModuleRoots roots = new ModuleRoots(
			System.getProperty("availRoots", ""));
		final String renames = System.getProperty("availRenames");
		final String initial;
		if (args.length > 0)
		{
			initial = args[0];
		}
		else
		{
			initial = "";
		}
		final Reader reader;
		if (renames == null)
		{
			reader = new StringReader("");
		}
		else
		{
			final File renamesFile = new File(renames);
			reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(renamesFile), StandardCharsets.UTF_8));
		}
		final RenamesFileParser renameParser = new RenamesFileParser(
			reader, roots);
		final ModuleNameResolver resolver = renameParser.parse();

		// Display the UI.
		invokeLater(new Runnable()
		{
			@Override
			public void run ()
			{
				final AvailWorkbench frame =
					new AvailWorkbench(resolver, initial);
				frame.setVisible(true);
				if (!initial.isEmpty())
				{
					final TreePath path = frame.modulePath(initial);
					if (path != null)
					{
						frame.moduleTree.setSelectionPath(path);
						frame.moduleTree.scrollPathToVisible(path);
					}
				}
			}
		});
	}
}
