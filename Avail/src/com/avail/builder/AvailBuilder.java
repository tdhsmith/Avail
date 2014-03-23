/**
 * AvailBuilder.java
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
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
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

package com.avail.builder;

import static java.nio.file.FileVisitResult.*;
import static com.avail.compiler.problems.ProblemType.*;
import static com.avail.descriptor.FiberDescriptor.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import com.avail.*;
import com.avail.annotations.*;
import com.avail.compiler.*;
import com.avail.compiler.AbstractAvailCompiler.CompilerProgressReporter;
import com.avail.compiler.AbstractAvailCompiler.ModuleHeader;
import com.avail.compiler.AbstractAvailCompiler.ModuleImport;
import com.avail.compiler.problems.Problem;
import com.avail.compiler.problems.ProblemHandler;
import com.avail.compiler.problems.ProblemType;
import com.avail.compiler.scanning.AvailScanner;
import com.avail.compiler.scanning.AvailScannerException;
import com.avail.compiler.scanning.AvailScannerResult;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.persistence.IndexedRepositoryManager.*;
import com.avail.serialization.*;
import com.avail.stacks.StacksGenerator;
import com.avail.utility.*;
import com.avail.utility.evaluation.*;

/**
 * An {@code AvailBuilder} {@linkplain AbstractAvailCompiler compiles} and
 * installs into an {@linkplain AvailRuntime Avail runtime} a target
 * {@linkplain ModuleDescriptor module} and each of its dependencies.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class AvailBuilder
{
	/**
	 * Whether to debug the builder.
	 */
	@InnerAccess static final boolean debugBuilder = false;

	/**
	 * The maximum age, in milliseconds, that changes should be left uncommitted
	 * in the repository.  A higher value saves space by causing the updated
	 * metadata to be rewritten at a slower rate, but the next build may have to
	 * repeat a bit more work if the previous build attempt failed before its
	 * data could be committed.
	 */
	@InnerAccess static final long maximumStaleRepositoryMs = 2000L;

	/**
	 * The file extension for an Avail source {@linkplain ModuleDescriptor
	 * module}.
	 */
	@InnerAccess static final String availExtension =
		ModuleNameResolver.availExtension;

	/**
	 * The {@linkplain AvailRuntime runtime} into which the
	 * {@linkplain AvailBuilder builder} will install the target
	 * {@linkplain ModuleDescriptor module} and its dependencies.
	 */
	public final AvailRuntime runtime;

	/**
	 * A {@link Graph} of {@link ResolvedModuleName}s, representing the
	 * relationships between all modules currently loaded or involved in the
	 * current build action.  Modules are only added here after they have been
	 * locally traced successfully.
	 */
	public final Graph<ResolvedModuleName> moduleGraph =
		new Graph<ResolvedModuleName>();

	/**
	 * A map from each {@link ResolvedModuleName} to its currently loaded
	 * {@link LoadedModule}.
	 */
	private final Map<ResolvedModuleName, LoadedModule> allLoadedModules =
		new HashMap<ResolvedModuleName, LoadedModule>();

	/** Who to notify when modules load and unload. */
	private final Set<Continuation2<LoadedModule, Boolean>> subscriptions =
		new HashSet<>();

	/**
	 * Record a new party to notify about module loading and unloading.
	 *
	 * @param subscription What to invoke during loads and unloads.
	 */
	public void subscribeToModuleLoading (
		final Continuation2<LoadedModule, Boolean> subscription)
	{
		subscriptions.add(subscription);
	}

	/**
	 * No longer notify the specified party about module loading and unloading.
	 *
	 * @param subscription What to no longer invoke during loads and unloads.
	 */
	public void unsubscribeToModuleLoading (
		final Continuation2<LoadedModule, Boolean> subscription)
	{
		subscriptions.remove(subscription);
	}

	/**
	 * Return a list of modules that are currently loaded.  The returned list is
	 * a snapshot of the state and does not change due to subsequent loads or
	 * unloads.
	 *
	 * @return The list of modules currently loaded.
	 */
	public synchronized List<LoadedModule> loadedModulesCopy ()
	{
		return new ArrayList<>(allLoadedModules.values());
	}

	/**
	 * Look up the currently loaded module with the specified {@linkplain
	 * ResolvedModuleName resolved module name}.  Return {@code null} if the
	 * module is not currently loaded.
	 *
	 * @param resolvedModuleName The name of the module to locate.
	 * @return The loaded module or null.
	 */
	public synchronized @Nullable LoadedModule getLoadedModule (
		final ResolvedModuleName resolvedModuleName)
	{
		return allLoadedModules.get(resolvedModuleName);
	}

	/**
	 * Record a freshly loaded module.  Notify subscribers.
	 *
	 * @param resolvedModuleName The module's resolved name.
	 * @param loadedModule The loaded module.
	 */
	@InnerAccess synchronized void putLoadedModule (
		final ResolvedModuleName resolvedModuleName,
		final LoadedModule loadedModule)
	{
		allLoadedModules.put(resolvedModuleName, loadedModule);
		for (final Continuation2<LoadedModule, Boolean> subscription
			: subscriptions)
		{
			subscription.value(loadedModule, true);
		}
	}

	/**
	 * Record the fresh unloading of a module with the given name.  Notify
	 * subscribers.
	 *
	 * @param resolvedModuleName The unloaded module's resolved name.
	 */
	@InnerAccess synchronized void removeLoadedModule (
		final ResolvedModuleName resolvedModuleName)
	{
		final LoadedModule loadedModule =
			allLoadedModules.get(resolvedModuleName);
		allLoadedModules.remove(resolvedModuleName);
		for (final Continuation2<LoadedModule, Boolean> subscription
			: subscriptions)
		{
			subscription.value(loadedModule, false);
		}
	}

	/**
	 * Whether the current build should stop, versus continuing with problems.
	 */
	public volatile boolean shouldStopBuild = false;

	/**
	 * Cancel the build at the next convenient stopping point for each module.
	 */
	public void cancel ()
	{
		shouldStopBuild = true;
	}

	/**
	 * Given a byte array, compute the {@link CRC32} checksum and append
	 * the {@code int} value as four bytes (Big Endian), answering the
	 * new augmented byte array.
	 *
	 * @param bytes The input bytes.
	 * @return The bytes followed by the checksum.
	 */
	@InnerAccess byte[] appendCRC (final byte[] bytes)
	{
		final CRC32 checksum = new CRC32();
		checksum.update(bytes);
		final int checksumInt = (int)checksum.getValue();
		final ByteBuffer combined = ByteBuffer.allocate(bytes.length + 4);
		combined.put(bytes);
		combined.putInt(checksumInt);
		final byte[] combinedBytes = new byte[bytes.length + 4];
		combined.flip();
		combined.get(combinedBytes);
		return combinedBytes;
	}

	/**
	 * Given an array of bytes, check that the last four bytes, when
	 * treated as a Big Endian unsigned int, agree with the {@link
	 * CRC32} checksum of the bytes excluding the last four.  Fail if
	 * they disagree.  Answer a ByteArrayInputStream on the bytes
	 * excluding the last four.
	 *
	 * @param bytes An array of bytes.
	 * @return A ByteArrayInputStream on the non-CRC portion of the
	 *         bytes.
	 * @throws MalformedSerialStreamException If the CRC check fails.
	 */
	@InnerAccess ByteArrayInputStream validatedBytesFrom (final byte[] bytes)
		throws MalformedSerialStreamException
	{
		final int storedChecksum =
			ByteBuffer.wrap(bytes).getInt(bytes.length - 4);
		final Checksum checksum = new CRC32();
		checksum.update(bytes, 0, bytes.length - 4);
		if ((int)checksum.getValue() != storedChecksum)
		{
			throw new MalformedSerialStreamException(null);
		}
		return new ByteArrayInputStream(bytes, 0, bytes.length - 4);
	}

	/**
	 * Serialize the specified {@linkplain ModuleHeader module header} into the
	 * {@linkplain ModuleVersion module version}.
	 *
	 * @param header
	 *        A module header.
	 * @param version
	 *        A module version.
	 */
	@InnerAccess void serialize (
		final ModuleHeader header,
		final ModuleVersion version)
	{
		final ByteArrayOutputStream out = new ByteArrayOutputStream(1000);
		final Serializer serializer = new Serializer(out);
		header.serializeHeaderOn(serializer);
		final byte[] bytes = appendCRC(out.toByteArray());
		version.putModuleHeader(bytes);
	}

	/**
	 * The {@code BuilderProblemHandler} handles {@linkplain Problem problems}
	 * encountered during a build.
	 */
	@InnerAccess class BuilderProblemHandler
	extends ProblemHandler
	{
		/**
		 * The {@linkplain Formatter pattern} with which to format {@linkplain
		 * Problem problem} reports. The pattern will be applied to the
		 * following problem components:
		 *
		 * <ol>
		 * <li>The {@linkplain ProblemType problem type}.</li>
		 * <li>The {@linkplain Problem#moduleName module name}, or {@code null}
		 *     if there is no specific module in context.</li>
		 * <li>The {@linkplain Problem#lineNumber line number} in the source at
		 *     which the problem occurs.</li>
		 * <li>A {@linkplain Problem#toString() general description} of the
		 *     problem.</li>
		 * </ol>
		 */
		final String pattern;

		/**
		 * Construct a new {@link BuilderProblemHandler}.  The supplied pattern
		 * is used to format the problem text as specified {@linkplain #pattern
		 * here}.
		 *
		 * @param pattern The {@link String} with which to report the problem.
		 */
		public BuilderProblemHandler (final String pattern)
		{
			this.pattern = pattern;
		}

		@Override
		public boolean handleWarning (final Problem problem)
		{
			return handleGeneric(problem);
		}

		@Override
		public boolean handleTrace (final Problem problem)
		{
			return handleGeneric(problem);
		}

		@Override
		public boolean handleParse (final Problem problem)
		{
			return handleGeneric(problem);
		}

		@Override
		public boolean handleInternal (final Problem problem)
		{
			return handleGeneric(problem);
		}

		@Override
		public boolean handleInformation (final Problem problem)
		{
			return handleGeneric(problem);
		}

		@Override
		public boolean handleExecution (final Problem problem)
		{
			return handleGeneric(problem);
		}

		/**
		 * Handle a problem generically.
		 *
		 * @param problem The {@link Problem} being reported.
		 * @return Whether to attempt to continue parsing.
		 */
		protected synchronized boolean handleGeneric (final Problem problem)
		{
			final Formatter formatter = new Formatter();
			formatter.format(
				pattern,
				problem.type,
				problem.moduleName,
				problem.lineNumber,
				problem.toString());
			System.err.print(formatter);
			// Abort the build.  This may change as the builder becomes more
			// sophisticated.
			shouldStopBuild = true;
			return false;
		}
	}

	/**
	 * How to handle problems during a build.
	 */
	@InnerAccess final ProblemHandler buildProblemHandler =
		new BuilderProblemHandler("[%s]: module \"%s\", line %d:%n%s%n");

	/**
	 * How to handle problems during command execution.
	 */
	@InnerAccess final ProblemHandler commandProblemHandler =
		new BuilderProblemHandler("[%1$s]: %4$s%n");

	/**
	 * A LoadedModule holds state about what the builder knows about a currently
	 * loaded Avail module.
	 */
	public static class LoadedModule
	{
		/**
		 * The resolved name of this module.
		 */
		final @InnerAccess ResolvedModuleName name;

		/**
		 * The cryptographic {@link ModuleArchive#digestForFile(
		 * ResolvedModuleName) digest} of this module's source code when it was
		 * compiled.
		 */
		final @InnerAccess byte [] sourceDigest;

		/**
		 * The actual {@link A_Module} that was plugged into the {@link
		 * AvailRuntime}.
		 */
		final @InnerAccess A_Module module;

		/** This module's version, which corresponds to the source code. */
		final ModuleVersion version;

		/**
		 * The {@link ModuleCompilation} which was loaded for this module.  This
		 * indicates when the compilation happened, and where in the {@linkplain
		 * IndexedRepositoryManager repository} we can find the {@link
		 * Serializer serialized} module content.
		 */
		final ModuleCompilation compilation;

		/**
		 * Whether this module has been flagged for deletion by the {@link
		 * BuildUnloader}.
		 */
		boolean deletionRequest = false;

		/**
		 * Answer the entry points defined by this loaded module.  Since the
		 * header structure does not depend on syntax declared in other modules,
		 * the entry points are a property of the {@link ModuleVersion}.  That's
		 * the entity associated with particular module source code.
		 *
		 * @return The {@link List} of {@link String}s that are entry points.
		 */
		public List<String> entryPoints ()
		{
			return version.getEntryPoints();
		}

		/**
		 * Construct a new {@link AvailBuilder.LoadedModule} to represent
		 * information about an Avail module that has been loaded.
		 *
		 * @param name The {@linkplain ResolvedModuleName name} of the module.
		 * @param sourceDigest The module source's cryptographic digest.
		 * @param module The actual {@link A_Module} loaded in the {@link
		 *        AvailRuntime}.
		 * @param version The version of the module source.
		 * @param compilation Information about the specific {@link
		 *        ModuleCompilation} that is loaded.
		 */
		public LoadedModule (
			final ResolvedModuleName name,
			final byte [] sourceDigest,
			final A_Module module,
			final ModuleVersion version,
			final ModuleCompilation compilation)
		{
			this.name = name;
			this.sourceDigest = sourceDigest;
			this.module = module;
			this.version = version;
			this.compilation = compilation;
		}
	}

	/**
	 * Used for scanning all modules in all visible Avail directories and their
	 * subdirectories.
	 */
	@InnerAccess class BuildDirectoryTracer
	{
		/**
		 * The number of trace requests that have been scheduled.
		 */
		@InnerAccess int traceRequests;

		/**
		 * The number of trace requests that have been completed.
		 */
		@InnerAccess int traceCompletions;

		/**
		 * Schedule a hierarchical tracing of all module files in all visible
		 * subdirectories.  Do not resolve the imports.  Ignore any modules that
		 * have syntax errors in their headers.  Update the repositories with
		 * the latest module version information, or at least cause the version
		 * caches to treat the current versions as having been accessed most
		 * recently.
		 *
		 * <p>When a module header parsing starts, increment traceRequests.
		 * When a module header parsing is complete, increment traceCompletions,
		 * and if the two are now equal, send {@link #notifyAll()} to the {@link
		 * BuildTracer}.</p>
		 *
		 * <p>{@linkplain IndexedRepositoryManager#commit() Commit} all affected
		 * repositories at the end.  Return only after all relevant files have
		 * been scanned or looked up in the repositories, or failed somehow.</p>
		 *
		 * @param moduleAction
		 *        What to do each time we've extracted or replayed a
		 *        {@link ModuleVersion} from a valid module file.
		 */
		@InnerAccess void traceAllModuleHeaders (
			final Continuation2<ResolvedModuleName, ModuleVersion> moduleAction)
		{
			traceRequests = 0;
			traceCompletions = 0;
			final ModuleRoots moduleRoots = runtime.moduleRoots();
			for (final ModuleRoot moduleRoot : moduleRoots)
			{
				final File rootDirectory = moduleRoot.sourceDirectory();
				assert rootDirectory != null;
				final Path rootPath = rootDirectory.toPath();
				final FileVisitor<Path> visitor = new FileVisitor<Path>()
				{
					@Override
					public FileVisitResult preVisitDirectory (
							final @Nullable Path dir,
							final @Nullable BasicFileAttributes unused)
						throws IOException
					{
						assert dir != null;
						if (dir.equals(rootPath))
						{
							// The base directory doesn't have the .avail
							// extension.
							return CONTINUE;
						}
						final String localName = dir.toFile().getName();
						if (localName.endsWith(availExtension))
						{
							return CONTINUE;
						}
						return SKIP_SUBTREE;
					}

					@Override
					public FileVisitResult visitFile (
							final @Nullable Path file,
							final @Nullable BasicFileAttributes unused)
						throws IOException
					{
						assert file != null;
						final String localName = file.toFile().getName();
						if (!localName.endsWith(availExtension))
						{
							return CONTINUE;
						}
						// It's a module file.
						synchronized (BuildDirectoryTracer.this)
						{
							traceRequests++;
						}
						runtime.execute(new AvailTask(0)
						{
							@Override
							public void value ()
							{
								final StringBuilder builder =
									new StringBuilder(100);
								builder.append("/");
								builder.append(moduleRoot.name());
								final Path relative = rootPath.relativize(file);
								for (final Path element : relative)
								{
									final String part = element.toString();
									builder.append("/");
									part.endsWith(availExtension);
									final String noExtension =
										part.substring(
											0,
											part.length()
												- availExtension.length());
									builder.append(noExtension);
								}
								final ModuleName moduleName =
									new ModuleName(builder.toString());
								final ResolvedModuleName resolved =
									new ResolvedModuleName(
										moduleName,
										moduleRoot);
								traceOneModuleHeader(resolved, moduleAction);
							}
						});
						return CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed (
							final @Nullable Path file,
							final @Nullable IOException exception)
						throws IOException
					{
						// Ignore the exception and continue.  We're just
						// trying to populate the list of entry points, so it's
						// not something worth reporting.
						return CONTINUE;
					}

					@Override
					public FileVisitResult postVisitDirectory (
							final @Nullable Path dir,
							final @Nullable IOException e)
						throws IOException
					{
						return CONTINUE;
					}
				};
				try
				{
					Files.walkFileTree(
						rootPath,
						Collections.singleton(FileVisitOption.FOLLOW_LINKS),
						Integer.MAX_VALUE,
						visitor);
				}
				catch (final IOException e)
				{
					// Ignore it.
				}
			}
			boolean interrupted = false;
			synchronized (BuildDirectoryTracer.this)
			{
				while (traceRequests != traceCompletions)
				{
					try
					{
						wait();
					}
					catch (final InterruptedException e)
					{
						interrupted = true;
					}
				}
			}
			// Force each repository to commit, since we may have changed the
			// access order of some of the caches.
			for (final ModuleRoot root : moduleRoots.roots())
			{
				root.repository().commit();
			}
			if (interrupted)
			{
				Thread.currentThread().interrupt();
			}
		}

		/**
		 * Examine the specified file, adding information about its header to
		 * its associated repository.  If this particular file version has
		 * already been traced, or if an error is encountered while fetching or
		 * parsing the header of the file, simply invoke {@link
		 * #indicateTraceCompleted()}.  Otherwise update the repository and then
		 * invoke {@code #indicateTraceCompleted()}.
		 *
		 * @param resolvedName
		 *        The resolved name of the module file to examine.
		 * @param action
		 *        A {@link Continuation2} to perform with each encountered
		 *        ResolvedModuleName and the associated {@link ModuleVersion},
		 *        if one can be produced without error by parsing or replaying
		 *        from the repository.
		 */
		@InnerAccess void traceOneModuleHeader (
			final ResolvedModuleName resolvedName,
			final Continuation2<ResolvedModuleName, ModuleVersion> action)
		{
			final IndexedRepositoryManager repository =
				resolvedName.repository();
			repository.commitIfStaleChanges(maximumStaleRepositoryMs);
			final File sourceFile = resolvedName.sourceReference();
			assert sourceFile != null;
			final ModuleArchive archive = repository.getArchive(
				resolvedName.rootRelativeName());
			final byte [] digest = archive.digestForFile(resolvedName);
			final ModuleVersionKey versionKey =
				new ModuleVersionKey(resolvedName, digest);
			final ModuleVersion existingVersion =
				archive.getVersion(versionKey);
			if (existingVersion != null)
			{
				// This version was already traced and recorded for a
				// subsequent replay... like right now.  Reuse it.
				action.value(resolvedName, existingVersion);
				indicateTraceCompleted();
				return;
			}
			// Trace the source and write it back to the repository.
			AbstractAvailCompiler.create(
				resolvedName,
				true,
				new Continuation1<AbstractAvailCompiler>()
				{
					@Override
					public void value (
						final @Nullable AbstractAvailCompiler compiler)
					{
						assert compiler != null;
						compiler.parseModuleHeader(
							new Continuation1<ModuleHeader>()
							{
								@Override
								public void value (
									final @Nullable ModuleHeader header)
								{
									assert header != null;
									final List<String> importNames =
										header.importedModuleNames();
									final List<String> entryPoints =
										header.entryPointNames();
									final ModuleVersion newVersion =
										repository.new ModuleVersion(
											sourceFile.length(),
											importNames,
											entryPoints);
									serialize(header, newVersion);
									archive.putVersion(
										versionKey,
										newVersion);
									action.value(resolvedName, newVersion);
									indicateTraceCompleted();
								}
							},
							new Continuation0()
							{
								@Override
								public void value ()
								{
									indicateTraceCompleted();
								}
							});
					}
				},
				new Continuation0()
				{
					@Override
					public void value ()
					{
						indicateTraceCompleted();
					}
				},
				new BuilderProblemHandler("")
				{
					@Override
					protected synchronized boolean handleGeneric (
						final Problem problem)
					{
						// Simply ignore all problems when all we're doing is
						// trying to locate the entry points within any
						// syntactically valid modules.
						return false;
					}
				});
		}

		/**
		 * A module was just traced, so record that fact.  Note that the
		 * trace was either successful or unsuccessful.
		 */
		@InnerAccess synchronized void indicateTraceCompleted ()
		{
			traceCompletions++;
			if (debugBuilder)
			{
				System.out.println(
					String.format(
						"Build-directory traced one (%d/%d)",
						traceCompletions,
						traceRequests));
			}
			// Avoid spurious wake-ups.
			if (traceRequests == traceCompletions)
			{
				notifyAll();
			}
		}
	}

	/**
	 * Used for unloading changed modules prior to tracing.
	 */
	class BuildUnloader
	{
		/**
		 * Find all loaded modules that have changed since compilation, then
		 * unload them and all successors in reverse dependency order.
		 */
		@InnerAccess void unload ()
		{
			// Scan modules in dependency order, checking if either that module
			// file has changed since loading, or a predecessor is already
			// flagged for unloading.  We do the propagation here to avoid
			// having to look at the actual files downstream from a module known
			// to have changed.
			moduleGraph.parallelVisit(
				new Continuation2<ResolvedModuleName, Continuation0>()
				{
					@Override
					public void value (
						final @Nullable ResolvedModuleName moduleName,
						final @Nullable Continuation0 completionAction)
					{
						assert moduleName != null;
						assert completionAction != null;
						runtime.execute(
							new AvailTask(loaderPriority)
							{
								@Override
								public void value()
								{
									boolean dirty = false;
									for (final ResolvedModuleName predecessor
										: moduleGraph.predecessorsOf(
											moduleName))
									{
										final LoadedModule loadedModule =
											getLoadedModule(predecessor);
										assert loadedModule != null;
										if (loadedModule.deletionRequest)
										{
											dirty = true;
											break;
										}
									}
									if (!dirty)
									{
										// Look at the file to determine if it's
										// changed.
										final LoadedModule loadedModule =
											getLoadedModule(moduleName);
										assert loadedModule != null;
										final IndexedRepositoryManager
											repository =
												moduleName.repository();
										final ModuleArchive archive =
											repository.getArchive(
												moduleName.rootRelativeName());
										final byte [] latestDigest =
											archive.digestForFile(moduleName);
										dirty = !Arrays.equals(
											latestDigest,
											loadedModule.sourceDigest);
									}
									getLoadedModule(moduleName)
										.deletionRequest = dirty;
									completionAction.value();
								}
							});
					}
				});
			// We now have the set of modules to be unloaded.  Do so in reverse
			// dependency order.
			moduleGraph.reverse().parallelVisit(
				new Continuation2<ResolvedModuleName, Continuation0>()
				{
					@Override
					public void value (
						final @Nullable ResolvedModuleName moduleName,
						final @Nullable Continuation0 completionAction)
					{
						// No need to lock dirtyModules any more, since it's
						// purely read-only at this point.
						assert moduleName != null;
						assert completionAction != null;
						runtime.whenLevelOneSafeDo(new AvailTask(loaderPriority)
						{
							@Override
							public void value()
							{
								final LoadedModule loadedModule =
									getLoadedModule(moduleName);
								assert loadedModule != null;
								if (loadedModule.deletionRequest)
								{
									if (debugBuilder)
									{
										System.out.println(
											"Beginning unload of: "
											+ moduleName);
									}
									final A_Module module = loadedModule.module;
									assert module != null;
									// It's legal to just create a loader
									// here, since it won't have any pending
									// forwards to remove.
									module.removeFrom(
										AvailLoader.forUnloading(module),
										new Continuation0()
										{
											@Override
											public void value ()
											{
												runtime.unlinkModule(module);
												if (debugBuilder)
												{
													System.out.println(
														"Done unload of: "
														+ moduleName);
												}
												completionAction.value();
											}
										});
								}
								else
								{
									completionAction.value();
								}
							}
						});
					}
				});
			// Unloading of each A_Module is complete.  Update my local
			// structures to agree.
			for (final LoadedModule loadedModule : loadedModulesCopy())
			{
				if (loadedModule.deletionRequest)
				{
					final ResolvedModuleName moduleName = loadedModule.name;
					removeLoadedModule(moduleName);
					moduleGraph.exciseVertex(moduleName);
				}
			}
		}
	}

	/**
	 * Used for constructing the module dependency graph.
	 */
	@InnerAccess class BuildTracer
	{
		/**
		 * The number of trace requests that have been scheduled.
		 */
		private int traceRequests;

		/**
		 * The number of trace requests that have been completed.
		 */
		private int traceCompletions;

		/**
		 * Schedule tracing of the imports of the {@linkplain
		 * ModuleDescriptor module} specified by the given {@linkplain
		 * ModuleName module name}.  The {@link #traceRequests} counter has
		 * been incremented already for this tracing, and the {@link
		 * #traceCompletions} will eventually be incremented by this method,
		 * but only <em>after</em> increasing the {@link #traceRequests} for
		 * each recursive trace that is scheduled here.  That ensures the
		 * two counters won't accidentally be equal at any time except after
		 * the last trace has completed.
		 *
		 * <p>When traceCompletions finally does reach traceRequests, a
		 * {@link #notifyAll()} will be sent to the {@link BuildTracer}.</p>
		 *
		 * @param qualifiedName
		 *        A fully-qualified {@linkplain ModuleName module name}.
		 * @param resolvedSuccessor
		 *        The resolved name of the module using or extending this
		 *        module, or {@code null} if this module is the start of the
		 *        recursive resolution (i.e., it will be the last one compiled).
		 * @param recursionSet
		 *        An insertion-ordered {@linkplain Set set} that remembers
		 *        all modules visited along this branch of the trace.
		 */
		private void scheduleTraceModuleImports (
			final ModuleName qualifiedName,
			final @Nullable ResolvedModuleName resolvedSuccessor,
			final LinkedHashSet<ResolvedModuleName> recursionSet)
		{
			runtime.execute(new AvailTask(tracerPriority)
			{
				@Override
				public void value ()
				{
					if (!shouldStopBuild)
					{
						ResolvedModuleName resolvedName = null;
						try
						{
							if (debugBuilder)
							{
								System.out.println(
									"Resolve: " + qualifiedName);
							}
							resolvedName =
								runtime.moduleNameResolver().resolve(
									qualifiedName, resolvedSuccessor);
						}
						catch (final Exception e)
						{
							if (debugBuilder)
							{
								System.out.println(
									"Fail resolution: " + e);
							}
							shouldStopBuild = true;
							final Problem problem = new Problem (
								resolvedSuccessor != null
									? resolvedSuccessor
									: qualifiedName,
								TokenDescriptor.createSyntheticStart(),
								ProblemType.TRACE,
								"Module resolution problem:\n{0}",
								e)
							{
								@Override
								protected void abortCompilation ()
								{
									// Ignore problems during trace.
								}
							};
							buildProblemHandler.handle(problem);
							indicateTraceCompleted();
							return;
						}
						if (debugBuilder)
						{
							System.out.println("Trace: " + resolvedName);
						}
						traceModuleImports(
							resolvedName,
							resolvedSuccessor,
							recursionSet);
					}
				}
			});
		}

		/**
		 * Trace the imports of the {@linkplain ModuleDescriptor module}
		 * specified by the given {@linkplain ModuleName module name}.  If a
		 * {@link Problem} occurs, log it and set {@link #shouldStopBuild}.
		 * Whether a success or failure happens, end by invoking {@link
		 * #indicateTraceCompleted()}.
		 *
		 * @param resolvedName
		 *        A resolved {@linkplain ModuleName module name} to trace.
		 * @param resolvedSuccessor
		 *        The resolved name of the module using or extending this
		 *        module, or {@code null} if this module is the start of the
		 *        recursive resolution (i.e., it will be the last one
		 *        compiled).
		 * @param recursionSet
		 *        A {@link LinkedHashSet} that remembers all modules visited
		 *        along this branch of the trace, and the order they were
		 *        encountered.
		 */
		@InnerAccess void traceModuleImports (
			final ResolvedModuleName resolvedName,
			final @Nullable ResolvedModuleName resolvedSuccessor,
			final LinkedHashSet<ResolvedModuleName> recursionSet)
		{
			// Detect recursion into this module.
			if (recursionSet.contains(resolvedName))
			{
				final Problem problem = new Problem(
					resolvedName,
					TokenDescriptor.createSyntheticStart(),
					ProblemType.TRACE,
					"Recursive module dependency:\n{0}",
					recursionSet)
				{
					@Override
					protected void abortCompilation ()
					{
						shouldStopBuild = true;
					}
				};
				buildProblemHandler.handle(problem);
				indicateTraceCompleted();
				return;
			}
			final boolean alreadyTraced;
			synchronized (AvailBuilder.this)
			{
				alreadyTraced = moduleGraph.includesVertex(resolvedName);
				if (!alreadyTraced)
				{
					moduleGraph.addVertex(resolvedName);
				}
				if (resolvedSuccessor != null)
				{
					// Note that a module can be both Extended and Used from
					// the same module.  That's to support selective import
					// and renames.
					moduleGraph.includeEdge(resolvedName, resolvedSuccessor);
				}
			}
			if (alreadyTraced)
			{
				indicateTraceCompleted();
				return;
			}
			final IndexedRepositoryManager repository =
				resolvedName.repository();
			repository.commitIfStaleChanges(maximumStaleRepositoryMs);
			final File sourceFile = resolvedName.sourceReference();
			assert sourceFile != null;
			final ModuleArchive archive = repository.getArchive(
				resolvedName.rootRelativeName());
			final byte [] digest = archive.digestForFile(resolvedName);
			final ModuleVersionKey versionKey =
				new ModuleVersionKey(resolvedName, digest);
			final ModuleVersion version = archive.getVersion(versionKey);
			if (version != null)
			{
				// This version was already traced and recorded for a
				// subsequent replay... like right now.  Reuse it.
				final List<String> importNames = version.getImports();
				traceModuleNames(resolvedName, importNames, recursionSet);
				indicateTraceCompleted();
				return;
			}
			// Trace the source and write it back to the repository.
			AbstractAvailCompiler.create(
				resolvedName,
				true,
				new Continuation1<AbstractAvailCompiler>()
				{
					@Override
					public void value (
						final @Nullable AbstractAvailCompiler compiler)
					{
						assert compiler != null;
						compiler.parseModuleHeader(
							new Continuation1<ModuleHeader>()
							{
								@Override
								public void value (
									final @Nullable ModuleHeader header)
								{
									assert header != null;
									final List<String> importNames =
										header.importedModuleNames();
									final List<String> entryPoints =
										header.entryPointNames();
									final ModuleVersion newVersion =
										repository.new ModuleVersion(
											sourceFile.length(),
											importNames,
											entryPoints);
									serialize(header, newVersion);
									archive.putVersion(versionKey, newVersion);
									traceModuleNames(
										resolvedName,
										importNames,
										recursionSet);
									indicateTraceCompleted();
								}
							},
							new Continuation0()
							{
								@Override
								public void value ()
								{
									indicateTraceCompleted();
								}
							});
					}
				},
				new Continuation0()
				{
					@Override
					public void value ()
					{
						indicateTraceCompleted();
					}
				},
				buildProblemHandler);
		}

		/**
		 * Trace the imports of the {@linkplain ResolvedModuleName
		 * specified} {@linkplain ModuleDescriptor module}.  Return only
		 * when these new <em>requests</em> have been accounted for, so that
		 * the current request can be considered completed in the caller.
		 *
		 * @param moduleName
		 *        The name of the module being traced.
		 * @param importNames
		 *        The local names of the modules referenced by the current
		 *        one.
		 * @param recursionSet
		 *        An insertion-ordered {@linkplain Set set} that remembers
		 *        all modules visited along this branch of the trace.
		 */
		@InnerAccess void traceModuleNames (
			final ResolvedModuleName moduleName,
			final List<String> importNames,
			final LinkedHashSet<ResolvedModuleName> recursionSet)
		{
			// Copy the recursion set to ensure the independence of each
			// path of the tracing algorithm.
			final LinkedHashSet<ResolvedModuleName> newSet =
				new LinkedHashSet<>(recursionSet);
			newSet.add(moduleName);

			synchronized (this)
			{
				traceRequests += importNames.size();
			}

			// Recurse in parallel into each import.
			for (final String localImport : importNames)
			{
				final ModuleName importName =
					moduleName.asSibling(localImport);
				scheduleTraceModuleImports(importName, moduleName, newSet);
			}
		}

		/**
		 * A module was just traced, so record that fact.  Note that the
		 * trace was either successful or unsuccessful.
		 */
		@InnerAccess synchronized void indicateTraceCompleted ()
		{
			traceCompletions++;
			if (debugBuilder)
			{
				System.out.println(
					String.format(
						"Traced one (%d/%d)",
						traceCompletions,
						traceRequests));
			}
			// Avoid spurious wake-ups.
			if (traceRequests == traceCompletions)
			{
				notifyAll();
			}
		}

		/**
		 * Determine the ancestry graph of the indicated module, recording it in
		 * the {@link #moduleGraph}.
		 *
		 * @param target The ultimate module to load.
		 */
		@InnerAccess void trace (final ModuleName target)
		{
			// Clear all information about modules that have been traced.
			// This graph will be rebuilt below if successful, or cleared on
			// failure.  The synchronization is probably unnecessary.
			synchronized (this)
			{
				moduleGraph.clear();
				traceRequests = 1;
				traceCompletions = 0;
			}
			scheduleTraceModuleImports(
				target,
				null,
				new LinkedHashSet<ResolvedModuleName>());
			// Wait until the parallel recursive trace completes.
			synchronized (this)
			{
				while (traceRequests != traceCompletions)
				{
					try
					{
						wait();
					}
					catch (final InterruptedException e)
					{
						shouldStopBuild = true;
					}
				}
				runtime.moduleNameResolver().commitRepositories();
			}
			if (shouldStopBuild)
			{
				moduleGraph.clear();
			}
			else
			{
				if (debugBuilder)
				{
					System.out.println(
						String.format(
							"Traced %d modules (%d edges)",
							moduleGraph.size(),
							traceCompletions));
				}
			}
		}
	}

	/**
	 * Used for parallel-loading modules in the {@linkplain #moduleGraph module
	 * graph}.
	 */
	class BuildLoader
	{
		/**
		 * A {@linkplain Continuation4 continuation} that is updated to show
		 * progress while compiling or loading a module.  It accepts:
		 * <ol>
		 * <li>the name of the module currently undergoing {@linkplain
		 * AbstractAvailCompiler compilation} as part of the recursive build
		 * of target,</li>
		 * <li>the current line number within the current module,</li>
		 * <li>the position of the ongoing parse (in bytes), and</li>
		 * <li>the size of the module in bytes.</li>
		 */
		@InnerAccess final Continuation4<ModuleName, Long, Long, Long>
			localTracker;

		/**
		 * A {@linkplain Continuation3} that is updated to show global progress
		 * while compiling or loading modules.  It accepts:
		 * <ol>
		 * <li>the name of the module undergoing compilation,</li>
		 * <li>the number of bytes globally processed, and</li>
		 * <li>the global size (in bytes) of all modules that will be
		 * built.</li>
		 */
		@InnerAccess final Continuation3<ModuleName, Long, Long> globalTracker;

		/**
		 * Construct a new {@link BuildLoader}.
		 *
		 * @param localTracker
		 *        A {@linkplain CompilerProgressReporter continuation} that
		 *        accepts
		 *        <ol>
		 *        <li>the name of the module currently undergoing {@linkplain
		 *        AbstractAvailCompiler compilation} as part of the recursive build
		 *        of target,</li>
		 *        <li>the current line number within the current module,</li>
		 *        <li>the position of the ongoing parse (in bytes), and</li>
		 *        <li>the size of the module in bytes.</li>
		 *        </ol>
		 * @param globalTracker
		 *        A {@linkplain Continuation3 continuation} that accepts
		 *        <ol>
		 *        <li>the name of the module undergoing compilation,</li>
		 *        <li>the number of bytes globally processed, and</li>
		 *        <li>the global size (in bytes) of all modules that will be
		 *        built.</li>
		 *        </ol>
		 */
		public BuildLoader (
			final CompilerProgressReporter localTracker,
			final Continuation3<ModuleName, Long, Long> globalTracker)
		{
			this.localTracker = localTracker;
			this.globalTracker = globalTracker;
		}

		/** The size, in bytes, of all source files that will be built. */
		private long globalCodeSize = 0L;

		/** The number of bytes compiled so far. */
		@InnerAccess final AtomicLong bytesCompiled = new AtomicLong(0L);

		/**
		 * Answer the size, in bytes, of all source files that will be
		 * built.
		 *
		 * @return The number of bytes in all source files that will be
		 *         built.
		 */
		@InnerAccess synchronized long globalCodeSize ()
		{
			return globalCodeSize;
		}

		/**
		 * Schedule a build of the specified {@linkplain ModuleDescriptor
		 * module}, on the assumption that its predecessors have already
		 * been built.
		 *
		 * @param target
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that should be loaded.
		 * @param completionAction
		 *        The {@linkplain Continuation0 action} to perform after
		 *        this module has been loaded.
		 */
		@InnerAccess void scheduleLoadModule (
			final ResolvedModuleName target,
			final Continuation0 completionAction)
		{
			// Avoid scheduling new tasks if an exception has happened.
			if (shouldStopBuild)
			{
				postLoad(target, 0L);
				completionAction.value();
				return;
			}
			runtime.execute(new AvailTask(loaderPriority)
			{
				@Override
				public void value ()
				{
					if (shouldStopBuild)
					{
						// An exception has been encountered since the
						// earlier check.  Exit quickly.
						completionAction.value();
					}
					else
					{
						loadModule(target, completionAction);
					}
				}
			});
		}

		/**
		 * Load the specified {@linkplain ModuleDescriptor module} into the
		 * {@linkplain AvailRuntime Avail runtime}. If a current compiled
		 * module is available from the {@linkplain IndexedRepositoryManager
		 * repository}, then simply load it. Otherwise, {@linkplain
		 * AbstractAvailCompiler compile} the module, store it into the
		 * repository, and then load it.
		 *
		 * <p>
		 * Note that the predecessors of this module must have already been
		 * loaded.
		 * </p>
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that should be loaded.
		 * @param completionAction
		 *        What to do after loading the module successfully.
		 */
		@InnerAccess void loadModule (
				final ResolvedModuleName moduleName,
				final Continuation0 completionAction)
		{
			globalTracker.value(
				moduleName, bytesCompiled.get(), globalCodeSize());
			// If the module is already loaded into the runtime, then we
			// must not reload it.
			final boolean isLoaded;
			synchronized (AvailBuilder.this)
			{
				isLoaded = getLoadedModule(moduleName) != null;
			}
			assert isLoaded == runtime.includesModuleNamed(
				StringDescriptor.from(moduleName.qualifiedName()));
			if (isLoaded)
			{
				// The module is already loaded.
				if (debugBuilder)
				{
					System.out.println(
						String.format(
							"Already loaded: %s",
							moduleName.qualifiedName()));
				}
				postLoad(moduleName, 0L);
				completionAction.value();
			}
			else
			{
				final IndexedRepositoryManager repository =
					moduleName.repository();
				final ModuleArchive archive = repository.getArchive(
					moduleName.rootRelativeName());
				final byte [] digest = archive.digestForFile(moduleName);
				final ModuleVersionKey versionKey =
					new ModuleVersionKey(moduleName, digest);
				final ModuleVersion version = archive.getVersion(versionKey);
				assert version != null
					: "Version should have been populated during tracing";
				final Map<String, LoadedModule> loadedModulesByName =
					new HashMap<>();
				for (final ResolvedModuleName predecessorName :
					moduleGraph.predecessorsOf(moduleName))
				{
					final String localName = predecessorName.localName();
					final LoadedModule loadedPredecessor =
						getLoadedModule(predecessorName);
					assert loadedPredecessor != null;
					loadedModulesByName.put(localName, loadedPredecessor);
				}
				final List<String> imports = version.getImports();
				final long [] predecessorCompilationTimes =
					new long [imports.size()];
				for (int i = 0; i < predecessorCompilationTimes.length; i++)
				{
					final LoadedModule loadedPredecessor =
						loadedModulesByName.get(imports.get(i));
					predecessorCompilationTimes[i] =
						loadedPredecessor.compilation.compilationTime;
				}
				final ModuleCompilationKey compilationKey =
					new ModuleCompilationKey(predecessorCompilationTimes);
				final ModuleCompilation compilation =
					version.getCompilation(compilationKey);
				if (compilation != null)
				{
					// The current version of the module is already
					// compiled, so load the repository's version.
					loadRepositoryModule(
						moduleName,
						version,
						compilation,
						versionKey.sourceDigest,
						completionAction);
				}
				else
				{
					// Compile the module and cache its compiled form.
					compileModule(
						moduleName,
						compilationKey,
						completionAction);
				}
			}
		}

		/**
		 * Load the specified {@linkplain ModuleDescriptor module} from the
		 * {@linkplain IndexedRepositoryManager repository} and into the
		 * {@linkplain AvailRuntime Avail runtime}.
		 *
		 * <p>
		 * Note that the predecessors of this module must have already been
		 * loaded.
		 * </p>
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that should be loaded.
		 * @param version
		 *        The {@link ModuleVersion} containing information about this
		 *        module.
		 * @param compilation
		 *        The {@link ModuleCompilation} containing information about
		 *        the particular stored compilation of this module in the
		 *        repository.
		 * @param sourceDigest
		 *        The cryptographic digest of the module's source code.
		 * @param completionAction
		 *        What to do after loading the module successfully.
		 */
		private void loadRepositoryModule (
				final ResolvedModuleName moduleName,
				final ModuleVersion version,
				final ModuleCompilation compilation,
				final byte[] sourceDigest,
				final Continuation0 completionAction)
		{
			localTracker.value(moduleName, -1L, -1L, -1L);
			final A_Module module = ModuleDescriptor.newModule(
				StringDescriptor.from(moduleName.qualifiedName()));
			final AvailLoader availLoader = new AvailLoader(module);
			final Continuation1<Throwable> fail =
				new Continuation1<Throwable>()
				{
					@Override
					public void value (final @Nullable Throwable e)
					{
						assert e != null;
						module.removeFrom(
							availLoader,
							new Continuation0()
							{
								@Override
								public void value ()
								{
									moduleGraph.removeVertex(moduleName);
									postLoad(moduleName, 0L);
									final Problem problem = new Problem(
										moduleName,
										TokenDescriptor.createSyntheticStart(),
										ProblemType.EXECUTION,
										"Problem loading module: {0}",
										e.getLocalizedMessage())
									{
										@Override
										public void abortCompilation ()
										{
											shouldStopBuild = true;
											completionAction.value();
										}
									};
									buildProblemHandler.handle(problem);
								}
							});
					}
				};
			// Read the module header from the repository.
			try
			{
				final byte[] bytes = version.getModuleHeader();
				assert bytes != null;
				final ByteArrayInputStream inputStream =
					validatedBytesFrom(bytes);
				final Deserializer deserializer =
					new Deserializer(inputStream, runtime);
				final ModuleHeader header = new ModuleHeader(moduleName);
				header.deserializeHeaderFrom(deserializer);
				module.isSystemModule(header.isSystemModule);
				final String errorString = header.applyToModule(
					module, runtime);
				if (errorString != null)
				{
					throw new RuntimeException(errorString);
				}
			}
			catch (final MalformedSerialStreamException | RuntimeException e)
			{
				fail.value(e);
				return;
			}
			final Deserializer deserializer;
			try
			{
				// Read the module data from the repository.
				final byte[] bytes = compilation.getBytes();
				assert bytes != null;
				final ByteArrayInputStream inputStream =
					validatedBytesFrom(bytes);
				deserializer = new Deserializer(inputStream, runtime);
				deserializer.currentModule(module);
			}
			catch (final MalformedSerialStreamException | RuntimeException e)
			{
				fail.value(e);
				return;
			}
			availLoader.createFilteredBundleTree();

			// Run each zero-argument block, one after another.
			final MutableOrNull<Continuation1<AvailObject>> runNext =
				new MutableOrNull<>();
			runNext.value = new Continuation1<AvailObject>()
			{
				@Override
				public void value (final @Nullable AvailObject ignored)
				{
					A_Function function = null;
					try
					{
						if (!shouldStopBuild)
						{
							function = deserializer.deserialize();
						}
					}
					catch (
						final MalformedSerialStreamException
							| RuntimeException e)
					{
						fail.value(e);
						return;
					}
					if (function != null)
					{
						final A_RawFunction code = function.code();
						final A_Fiber fiber = newLoaderFiber(
							function.kind().returnType(),
							availLoader,
							StringDescriptor.format(
								"Load repo module %s, in %s:%d",
								code.methodName(),
								code.module().moduleName(),
								code.startingLineNumber()));
						fiber.resultContinuation(runNext.value());
						fiber.failureContinuation(fail);
						Interpreter.runOutermostFunction(
							runtime,
							fiber,
							function,
							Collections.<AvailObject>emptyList());
					}
					else if (shouldStopBuild)
					{
						module.removeFrom(
							availLoader,
							new Continuation0()
							{
								@Override
								public void value ()
								{
									postLoad(moduleName, 0L);
									completionAction.value();
								}
							});
					}
					else
					{
						runtime.addModule(module);
						synchronized (AvailBuilder.this)
						{
							final LoadedModule loadedModule = new LoadedModule(
								moduleName,
								sourceDigest,
								module,
								version,
								compilation);
							putLoadedModule(moduleName, loadedModule);
						}
						postLoad(moduleName, 0L);
						completionAction.value();
					}
				}
			};
			// The argument is ignored, so it doesn't matter what gets
			// passed.
			runNext.value().value(NilDescriptor.nil());
		}

		/**
		 * Compile the specified {@linkplain ModuleDescriptor module}, store it
		 * into the {@linkplain IndexedRepositoryManager repository}, and then
		 * load it into the {@linkplain AvailRuntime Avail runtime}.
		 *
		 * <p>
		 * Note that the predecessors of this module must have already been
		 * loaded.
		 * </p>
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that should be loaded.
		 * @param compilationKey
		 *        The circumstances of compilation of this module.  Currently
		 *        this is just the compilation times ({@code long}s) of the
		 *        module's currently loaded predecessors, listed in the same
		 *        order as the module's {@linkplain ModuleHeader#importedModules
		 *        imports}.
		 * @param completionAction
		 *        What to do after loading the module successfully or
		 *        unsuccessfully.
		 */
		private void compileModule (
			final ResolvedModuleName moduleName,
			final ModuleCompilationKey compilationKey,
			final Continuation0 completionAction)
		{
			final IndexedRepositoryManager repository = moduleName.repository();
			final ModuleArchive archive = repository.getArchive(
				moduleName.rootRelativeName());
			final byte[] digest = archive.digestForFile(moduleName);
			final ModuleVersionKey versionKey =
				new ModuleVersionKey(moduleName, digest);
			final Mutable<Long> lastPosition = new Mutable<>(0L);
			final Continuation1<AbstractAvailCompiler> continuation =
				new Continuation1<AbstractAvailCompiler>()
				{
					@Override
					public void value (
						final @Nullable AbstractAvailCompiler compiler)
					{
						assert compiler != null;
						compiler.parseModule(
							new CompilerProgressReporter()
							{
								@Override
								public void value (
									final @Nullable ModuleName moduleName2,
									final @Nullable Long lineNumber,
									final @Nullable Long localPosition,
									final @Nullable Long moduleSize)
								{
									assert moduleName.equals(moduleName2);
									assert lineNumber != null;
									assert localPosition != null;
									assert moduleSize != null;
									assert moduleName.equals(moduleName2);
									localTracker.value(
										moduleName,
										lineNumber,
										localPosition,
										moduleSize);
									globalTracker.value(
										moduleName,
										bytesCompiled.addAndGet(
											localPosition - lastPosition.value),
										globalCodeSize());
									lastPosition.value = localPosition;
								}
							},
							new Continuation1<AvailCompilerResult>()
							{
								@Override
								public void value (
									final @Nullable AvailCompilerResult result)
								{
									assert result != null;
									final ByteArrayOutputStream stream =
										compiler.serializerOutputStream;
									// This is the moment of compilation.
									final long compilationTime =
										System.currentTimeMillis();
									final ModuleCompilation compilation =
										repository.new ModuleCompilation(
											compilationTime,
											appendCRC(stream.toByteArray()));
									archive.putCompilation(
										versionKey,
										compilationKey,
										compilation);

									// Serialize the Stacks comments.
									final ByteArrayOutputStream out =
										new ByteArrayOutputStream(5000);
									final Serializer serializer =
										new Serializer(out);
									final A_Tuple comments =
										TupleDescriptor.fromList(
											result.commentTokens());
									serializer.serialize(comments);
									final ModuleVersion version =
										archive.getVersion(versionKey);
									assert version != null;
									version.putComments(appendCRC(
										out.toByteArray()));

									repository.commitIfStaleChanges(
										maximumStaleRepositoryMs);
									postLoad(moduleName, lastPosition.value);
									putLoadedModule(
										moduleName,
										new LoadedModule(
											moduleName,
											versionKey.sourceDigest,
											result.module(),
											version,
											compilation));
									completionAction.value();
								}
							},
							new Continuation0()
							{
								@Override
								public void value ()
								{
									postLoad(moduleName, lastPosition.value);
									completionAction.value();
								}
							});
					}
				};
			AbstractAvailCompiler.create(
				moduleName,
				false,
				continuation,
				new Continuation0()
				{
					@Override
					public void value ()
					{
						postLoad(moduleName, lastPosition.value);
						completionAction.value();
					}
				},
				buildProblemHandler);
		}

		/**
		 * Report progress related to this module.  In particular, note that the
		 * current module has advanced from its provided lastPosition to the
		 * end of the module.
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName resolved name} of the
		 *        module that just finished loading.
		 * @param lastPosition
		 *        The last local file position previously reported.
		 */
		@InnerAccess void postLoad (
			final ResolvedModuleName moduleName,
			final long lastPosition)
		{
			globalTracker.value(
				moduleName,
				bytesCompiled.addAndGet(moduleName.moduleSize() - lastPosition),
				globalCodeSize());
		}

		/**
		 * Load the modules in the {@linkplain #moduleGraph module graph}.
		 */
		@InnerAccess void load ()
		{
			globalCodeSize = 0L;
			for (final ResolvedModuleName mod : moduleGraph.vertices())
			{
				globalCodeSize += mod.moduleSize();
			}
			bytesCompiled.set(0L);
			moduleGraph.parallelVisit(
				new Continuation2<ResolvedModuleName, Continuation0>()
				{
					@Override
					public void value (
						final @Nullable ResolvedModuleName moduleName,
						final @Nullable Continuation0 completionAction)
					{
						assert moduleName != null;
						assert completionAction != null;
						scheduleLoadModule(moduleName, completionAction);
					}
				});
			runtime.moduleNameResolver().commitRepositories();
			// Parallel load has now completed or failed.
			if (shouldStopBuild)
			{
				// Clean up any modules that didn't load.  There can be no
				// loaded successors of unloaded modules, so they can all be
				// excised safely.
				for (final ResolvedModuleName moduleName :
					new ArrayList<>(moduleGraph.vertices()))
				{
					if (!runtime.includesModuleNamed(
						StringDescriptor.from(moduleName.qualifiedName())))
					{
						moduleGraph.exciseVertex(moduleName);
					}
				}
			}
		}
	}

	/**
	 * Used for parallel documentation generation.
	 */
	class DocumentationTracer
	{
		/**
		 * The {@linkplain StacksGenerator Stacks documentation generator}.
		 */
		private final StacksGenerator generator;

		/**
		 * Construct a new {@link DocumentationTracer}.
		 *
		 * @param documentationPath
		 *        The {@linkplain Path path} to the output {@linkplain
		 *        BasicFileAttributes#isDirectory() directory} for documentation
		 *        and data files.
		 */
		DocumentationTracer (final Path documentationPath)
		{
			generator = new StacksGenerator(documentationPath,
				runtime.moduleNameResolver());
		}

		/**
		 * Get the {@linkplain ModuleVersion module version} for the {@linkplain
		 * ResolvedModuleName named} {@linkplain ModuleDescriptor module}.
		 *
		 * @param moduleName
		 *        A resolved module name.
		 * @return A module version, or {@code null} if no version was
		 *         available.
		 */
		private @Nullable ModuleVersion getVersion (
			final ResolvedModuleName moduleName)
		{
			final IndexedRepositoryManager repository =
				moduleName.repository();
			final ModuleArchive archive = repository.getArchive(
				moduleName.rootRelativeName());
			final byte [] digest = archive.digestForFile(moduleName);
			final ModuleVersionKey versionKey =
				new ModuleVersionKey(moduleName, digest);
			final ModuleVersion version = archive.getVersion(versionKey);
			return version;
		}

		/**
		 * Load {@linkplain CommentTokenDescriptor comments} for the {@linkplain
		 * ResolvedModuleName named} {@linkplain ModuleDescriptor module} into
		 * the {@linkplain StacksGenerator Stacks documentation generator}.
		 *
		 * @param moduleName
		 *        A module name.
		 * @param completionAction
		 *        What to do when comments have been loaded for the named
		 *        module (or an error occurs).
		 */
		@InnerAccess void loadComments (
			final ResolvedModuleName moduleName,
			final Continuation0 completionAction)
		{
			final ModuleVersion version = getVersion(moduleName);
			if (version == null || version.getComments() == null)
			{
				final Problem problem = new Problem(
					moduleName,
					TokenDescriptor.createSyntheticStart(),
					ProblemType.TRACE,
					"Module \"{0}\" should have been compiled already",
					moduleName)
				{
					@Override
					public void abortCompilation ()
					{
						shouldStopBuild = true;
						completionAction.value();
					}
				};
				buildProblemHandler.handle(problem);
				return;
			}
			final A_Tuple tuple;
			try
			{
				final byte[] bytes = version.getComments();
				assert bytes != null;
				final ByteArrayInputStream in = validatedBytesFrom(bytes);
				final Deserializer deserializer = new Deserializer(in, runtime);
				tuple = deserializer.deserialize();
				assert tuple != null;
				assert tuple.isTuple();
				assert deserializer.deserialize() == null;
			}
			catch (final MalformedSerialStreamException e)
			{
				final Problem problem = new Problem(
					moduleName,
					TokenDescriptor.createSyntheticStart(),
					ProblemType.INTERNAL,
					"Couldn''t deserialize comment tuple for module \"{0}\"",
					moduleName)
				{
					@Override
					public void abortCompilation ()
					{
						shouldStopBuild = true;
						completionAction.value();
					}
				};
				buildProblemHandler.handle(problem);
				return;
			}
			final ModuleHeader header;
			try
			{
				final ByteArrayInputStream in =
					validatedBytesFrom(version.getModuleHeader());
				final Deserializer deserializer = new Deserializer(in, runtime);
				header = new ModuleHeader(moduleName);
				header.deserializeHeaderFrom(deserializer);
			}
			catch (final MalformedSerialStreamException e)
			{
				final Problem problem = new Problem(
					moduleName,
					TokenDescriptor.createSyntheticStart(),
					ProblemType.INTERNAL,
					"Couldn''t deserialize header for module \"{0}\"",
					moduleName)
				{
					@Override
					public void abortCompilation ()
					{
						shouldStopBuild = true;
						completionAction.value();
					}
				};
				buildProblemHandler.handle(problem);
				return;
			}
			generator.add(header, tuple);
			completionAction.value();
		}

		/**
		 * Schedule a load of the {@linkplain CommentTokenDescriptor comments}
		 * for the {@linkplain ResolvedModuleName named} {@linkplain
		 * ModuleDescriptor module}.
		 *
		 * @param moduleName
		 *        A module name.
		 * @param completionAction
		 *        What to do when comments have been loaded for the named
		 *        module.
		 */
		@InnerAccess void scheduleLoadComments (
			final ResolvedModuleName moduleName,
			final Continuation0 completionAction)
		{
			// Avoid scheduling new tasks if an exception has happened.
			if (shouldStopBuild)
			{
				completionAction.value();
				return;
			}
			runtime.execute(new AvailTask(loaderPriority)
			{
				@Override
				public void value ()
				{
					if (shouldStopBuild)
					{
						// An exception has been encountered since the
						// earlier check.  Exit quickly.
						completionAction.value();
					}
					else
					{
						loadComments(moduleName, completionAction);
					}
				}
			});
		}

		/**
		 * Load the {@linkplain CommentTokenDescriptor comments} for all
		 * {@linkplain ModuleDescriptor modules} in the {@linkplain
		 * #moduleGraph module graph}.
		 */
		void load ()
		{
			moduleGraph.parallelVisit(
				new Continuation2<ResolvedModuleName, Continuation0>()
				{
					@Override
					public void value (
						final @Nullable ResolvedModuleName moduleName,
						final @Nullable Continuation0 completionAction)
					{
						assert moduleName != null;
						assert completionAction != null;
						scheduleLoadComments(moduleName, completionAction);
					}
				});
		}

		/**
		 * Generate Stacks documentation.
		 *
		 * @param target
		 *        The outermost {@linkplain ModuleDescriptor module} for the
		 *        generation request.
		 */
		void generate (final ModuleName target)
		{
			try
			{
				generator.generate(target);
			}
			catch (final IllegalArgumentException e)
			{
				final Problem problem = new Problem(
					target,
					TokenDescriptor.createSyntheticStart(),
					ProblemType.TRACE,
					"Could not generate Stacks documentation: {0}",
					e.getLocalizedMessage())
				{
					@Override
					public void abortCompilation ()
					{
						shouldStopBuild = true;
					}
				};
				buildProblemHandler.handle(problem);
			}
		}
	}

	/**
	 * Construct an {@link AvailBuilder} for the provided runtime.
	 *
	 * @param runtime
	 *        The {@link AvailRuntime} in which to load modules and execute
	 *        commands.
	 */
	public AvailBuilder (final AvailRuntime runtime)
	{
		this.runtime = runtime;
	}

	/**
	 * Build the {@linkplain ModuleDescriptor target} and its dependencies.
	 *
	 * @param target
	 *        The {@linkplain ModuleName canonical name} of the module that the
	 *        {@linkplain AvailBuilder builder} must (recursively) load into the
	 *        {@linkplain AvailRuntime runtime}.
	 * @param localTracker
	 *        A {@linkplain CompilerProgressReporter continuation} that accepts
	 *        <ol>
	 *        <li>the name of the module currently undergoing {@linkplain
	 *        AbstractAvailCompiler compilation} as part of the recursive build
	 *        of target,</li>
	 *        <li>the current line number within the current module,</li>
	 *        <li>the position of the ongoing parse (in bytes), and</li>
	 *        <li>the size of the module in bytes.</li>
	 *        </ol>
	 * @param globalTracker
	 *        A {@linkplain Continuation3 continuation} that accepts
	 *        <ol>
	 *        <li>the name of the module undergoing compilation,</li>
	 *        <li>the number of bytes globally processed, and</li>
	 *        <li>the global size (in bytes) of all modules that will be
	 *        built.</li>
	 *        </ol>
	 */
	public void buildTarget (
		final ModuleName target,
		final CompilerProgressReporter localTracker,
		final Continuation3<ModuleName, Long, Long> globalTracker)
	{
		shouldStopBuild = false;
		new BuildUnloader().unload();
		if (!shouldStopBuild)
		{
			new BuildTracer().trace(target);
		}
		if (!shouldStopBuild)
		{
			new BuildLoader(localTracker, globalTracker).load();
		}
	}

	/**
	 * Generate Stacks documentation for the {@linkplain ModuleDescriptor
	 * target} and its dependencies.
	 *
	 * @param target
	 *        The {@linkplain ModuleName canonical name} of the module for which
	 *        the {@linkplain AvailBuilder builder} must (recursively) generate
	 *        documentation.
	 * @param documentationPath
	 *        The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for Stacks
	 *        documentation and data files.
	 */
	public void generateDocumentation (
		final ModuleName target,
		final Path documentationPath)
	{
		shouldStopBuild = false;
		final BuildTracer tracer = new BuildTracer();
		tracer.trace(target);
		final DocumentationTracer documentationTracer =
			new DocumentationTracer(documentationPath);
		if (!shouldStopBuild)
		{
			documentationTracer.load();
		}
		if (!shouldStopBuild)
		{
			documentationTracer.generate(target);
		}
	}

	/**
	 * Scan all module files in all visible source directories, passing
	 * each {@link ResolvedModuleName} and corresponding {@link ModuleVersion}
	 * to the provided {@link Continuation2}.
	 *
	 * <p>Note that the action may be invoked from multiple {@link Thread}s
	 * simultaneously, so the client may need to provide suitable
	 * synchronization.</p>
	 *
	 * @param action What to do with each module version.
	 */
	public void traceDirectories (
		final Continuation2<ResolvedModuleName, ModuleVersion> action)
	{
		final BuildDirectoryTracer tracer = new BuildDirectoryTracer();
		tracer.traceAllModuleHeaders(action);
	}

	/**
	 * Attempt to unambiguously parse a command.  Each currently loaded module
	 * that defines at least one entry point takes a shot at parsing the
	 * command.  If more than one is successful, report the ambiguity via the
	 * {@code onFailure} continuation.  If none are successful, report the
	 * failure.  If there was exactly one, compile it into a function and invoke
	 * it in a new fiber.  If the function evaluation succeeds, run the {@code
	 * onSuccess} continuation with the function's result, except that if the
	 * function has static type ⊤ always pass {@code nil} instead of the actual
	 * value returned by the function.  If the function evaluation failed,
	 * report the failure.
	 *
	 * @param command
	 *        The command to attempt to parse and run.
	 * @param onSuccess
	 *        What to do if the command parsed and ran to completion.  It should
	 *        be passed both the result of execution and a {@linkplain
	 *        Continuation1 cleanup continuation} to invoke with a {@linkplain
	 *        Continuation0 post-cleanup continuation}.
	 * @param onFailure What to do otherwise.
	 */
	public void attemptCommand (
		final String command,
		final Continuation2<
			AvailObject, Continuation1<Continuation0>> onSuccess,
		final Continuation0 onFailure)
	{
		runtime.execute(new AvailTask(commandPriority)
		{
			@Override
			public void value ()
			{
				scheduleAttemptCommand(command, onSuccess, onFailure);
			}
		});

	}

	/**
	 * Schedule an attempt to unambiguously parse a command. Each currently
	 * loaded module that defines at least one entry point takes a shot at
	 * parsing the command.  If more than one is successful, report the
	 * ambiguity via the {@code onFailure} continuation.  If none are
	 * successful, report the failure.  If there was exactly one, compile it
	 * into a function and invoke it in a new fiber.  If the function evaluation
	 * succeeds, run the {@code onSuccess} continuation with the function's
	 * result, except that if the function has static type ⊤ always pass {@code
	 * nil} instead of the actual value returned by the function.  If the
	 * function evaluation failed, report the failure.
	 *
	 * @param command
	 *        The command to attempt to parse and run.
	 * @param onSuccess
	 *        What to do if the command parsed and ran to completion.  It should
	 *        be passed both the result of execution and a {@linkplain
	 *        Continuation1 cleanup continuation} to invoke with a {@linkplain
	 *        Continuation0 post-cleanup continuation}.
	 * @param onFailure
	 *        What to do otherwise.
	 */
	@InnerAccess void scheduleAttemptCommand (
		final String command,
		final Continuation2<
			AvailObject, Continuation1<Continuation0>> onSuccess,
		final Continuation0 onFailure)
	{
		final Set<LoadedModule> modulesWithEntryPoints = new HashSet<>();
		for (final LoadedModule loadedModule : loadedModulesCopy())
		{
			if (!loadedModule.entryPoints().isEmpty())
			{
				modulesWithEntryPoints.add(loadedModule);
			}
		}
		if (modulesWithEntryPoints.isEmpty())
		{
			final Problem problem = new Problem(
				null,
				1,
				1,
				ProblemType.EXECUTION,
				"No entry points are defined by loaded modules")
			{
				@Override
				public void abortCompilation ()
				{
					onFailure.value();
				}
			};
			commandProblemHandler.handle(problem);
			return;
		}

		final AvailScannerResult scanResult;
		try
		{
			scanResult = AvailScanner.scanString(
				command,
				"synthetic module for commands",
				false);
		}
		catch (final AvailScannerException e)
		{
			final Problem problem = new Problem(
				null,
				e.failureLineNumber(),
				e.failurePosition(),
				PARSE,
				"Scanner error: {0}",
				e.getMessage())
			{
				@Override
				public void abortCompilation ()
				{
					onFailure.value();
				}
			};
			commandProblemHandler.handle(problem);
			return;
		}

		final Map<LoadedModule, List<A_Phrase>> allSolutions = new HashMap<>();
		final List<Continuation1<Continuation0>> allCleanups =
			new ArrayList<>();
		final Map<LoadedModule, List<Problem>> allProblems = new HashMap<>();
		final Continuation0 decrement = new Continuation0()
		{
			private int outstanding = modulesWithEntryPoints.size();

			@Override
			public synchronized void value ()
			{
				if (--outstanding == 0)
				{
					processParsedCommand(
						allSolutions,
						allProblems,
						onSuccess,
						parallelCombine(allCleanups),
						onFailure);
				}
			}
		};

		for (final LoadedModule loadedModule : modulesWithEntryPoints)
		{
			final A_Module module = ModuleDescriptor.anonymousModule();
			final ModuleImport moduleImport =
				ModuleImport.extend(loadedModule.module);
			// TODO: This should really be some kind of fake ModuleName…
			final ModuleHeader header = new ModuleHeader(loadedModule.name);
			header.importedModules.add(moduleImport);
			header.applyToModule(module, runtime);
			for (final MapDescriptor.Entry entry :
				loadedModule.module.entryPoints().mapIterable())
			{
				module.addImportedName(entry.value());
			}
			final AbstractAvailCompiler compiler = new AvailSystemCompiler(
				module,
				scanResult,
				new BuilderProblemHandler("«collection only»")
				{
					@Override
					protected synchronized boolean handleGeneric (
						final Problem problem)
					{
						// Clone the problem message into a new problem to
						// avoid running any cleanup associated with aborting
						// the problem a second time.
						final Problem copy = new Problem(
							problem.moduleName,
							problem.lineNumber,
							problem.characterInFile,
							problem.type,
							"{0}",
							problem.toString())
						{
							@Override
							protected void abortCompilation ()
							{
								// Do nothing.
							}
						};
						List<Problem> problems;
						synchronized (allProblems)
						{
							problems = allProblems.get(loadedModule);
							if (problems == null)
							{
								problems = new ArrayList<>();
								allProblems.put(loadedModule, problems);
							}
						}
						problems.add(copy);
						return false;
					}

					@Override
					public boolean handleInternal (final Problem problem)
					{
						// First, report it immediately to the user.
						System.err.println(problem.toString());
						System.err.flush();
						// Now pass it along to report normally.
						return handleGeneric(problem);
					}
				});
			compiler.parseCommand(
				new Continuation2<
					List<A_Phrase>, Continuation1<Continuation0>>()
				{
					@Override
					public void value (
						final @Nullable List<A_Phrase> solutions,
						final @Nullable Continuation1<Continuation0> cleanup)
					{
						assert solutions != null;
						synchronized (allSolutions)
						{
							allSolutions.put(loadedModule, solutions);
							allCleanups.add(cleanup);
						}
						decrement.value();
					}
				},
				decrement);
		}
	}

	/**
	 * Given a {@linkplain Collection collection} of {@linkplain Continuation1
	 * continuation}s, each of which expects a {@linkplain Continuation0
	 * continuation} (called the post-continuation activity) that instructs it
	 * on how to proceed when it has completed, produce a single continuation
	 * that evaluates this collection in parallel and defers the
	 * post-continuation activity until every member has completed.
	 *
	 * @param continuations
	 *        A collection of continuations.
	 * @return The combined continuation.
	 */
	@InnerAccess Continuation1<Continuation0> parallelCombine (
		final Collection<Continuation1<Continuation0>> continuations)
	{
		return new Continuation1<Continuation0>()
		{
			@Override
			public void value (final @Nullable Continuation0 postAction)
			{
				assert postAction != null;

				final Continuation0 decrement = new Continuation0()
				{
					private int count = continuations.size();

					@Override
					public synchronized void value ()
					{
						if (--count == 0)
						{
							postAction.value();
						}
					}
				};
				for (final Continuation1<Continuation0> continuation :
					continuations)
				{
					runtime.execute(new AvailTask(commandPriority)
					{
						@Override
						public void value ()
						{
							continuation.value(decrement);
						}
					});
				}
			}
		};
	}

	/**
	 * Process a parsed command, executing it if there is a single
	 * unambiguous entry point send.
	 *
	 * @param solutions
	 *        A {@linkplain Map map} from {@linkplain LoadedModule loaded
	 *        modules} to the {@linkplain A_Phrase solutions} that they
	 *        produced.
	 * @param problems
	 *        A map from loaded modules to the {@linkplain Problem problems}
	 *        that they encountered.
	 * @param onSuccess
	 *        What to do with the result of a successful unambiguous command.
	 * @param postSuccessCleanup
	 *        How to cleanup after running a successful unambiguous command.
	 * @param onFailure
	 *        What to do after a failure.
	 */
	@InnerAccess void processParsedCommand (
		final Map<LoadedModule, List<A_Phrase>> solutions,
		final Map<LoadedModule, List<Problem>> problems,
		final Continuation2<
			AvailObject, Continuation1<Continuation0>> onSuccess,
		final Continuation1<Continuation0> postSuccessCleanup,
		final Continuation0 onFailure)
	{
		// If there were no solutions, then report every problem that was
		// encountered.
		if (solutions.isEmpty())
		{
			for (final Map.Entry<LoadedModule, List<Problem>> entry :
				problems.entrySet())
			{
				for (final Problem problem : entry.getValue())
				{
					buildProblemHandler.handle(problem);
				}
			}
			onFailure.value();
			return;
		}
		// Filter the solutions to invocations of entry points.
		final List<A_Phrase> entryPointSends = new ArrayList<>();
		final Set<String> namesOfModulesWithValidSends = new HashSet<>();
		for (final Map.Entry<LoadedModule, List<A_Phrase>> entry :
			solutions.entrySet())
		{
			final List<String> moduleEntryPoints = entry.getKey().entryPoints();
			for (final A_Phrase solution : entry.getValue())
			{
				if (solution.isInstanceOfKind(SEND_NODE.mostGeneralType()))
				{
					final A_Bundle bundle = solution.bundle();
					final A_Atom name = bundle.message();
					final String nameString = name.atomName().asNativeString();
					if (moduleEntryPoints.contains(nameString))
					{
						entryPointSends.add(solution);
						namesOfModulesWithValidSends.add(
							entry.getKey().name.qualifiedName());
					}
				}
			}
		}
		// If there were no entry point sends, then report a problem.
		if (entryPointSends.isEmpty())
		{
			final Problem problem = new Problem(
				null,
				1,
				1,
				PARSE,
				"The command could be parsed, but not as an invocation of "
				+ "an entry point.")
			{
				@Override
				public void abortCompilation ()
				{
					// do nothing.
				}
			};
			commandProblemHandler.handle(problem);
			onFailure.value();
			return;
		}
		// If the entry point send was ambiguous, then report a problem.
		if (entryPointSends.size() > 1)
		{
			final Problem problem = new Problem(
				null,
				1,
				1,
				PARSE,
				"The command could be parsed in multiple ways, using entry "
				+ "points of the following modules:\n{0}",
				namesOfModulesWithValidSends)
			{
				@Override
				public void abortCompilation ()
				{
					// do nothing.
				}
			};
			commandProblemHandler.handle(problem);
			onFailure.value();
			return;
		}

		// Right! We have a single interpretation of the command. Compile the
		// command and execute it on a fiber.
		assert entryPointSends.size() == 1;
		final A_Phrase phrase = entryPointSends.get(0);
		final A_Function function = FunctionDescriptor.createFunctionForPhrase(
			phrase, NilDescriptor.nil(), 1);
		final A_Fiber fiber = FiberDescriptor.newFiber(
			function.kind().returnType(),
			FiberDescriptor.commandPriority,
			StringDescriptor.format(
				"Running command: %s",
				phrase));
		fiber.resultContinuation(
			new Continuation1<AvailObject>()
			{
				@Override
				public void value (final @Nullable AvailObject result)
				{
					assert result != null;
					onSuccess.value(result, postSuccessCleanup);
				}
			});
		fiber.failureContinuation(new Continuation1<Throwable>()
		{
			@Override
			public void value (@Nullable final Throwable e)
			{
				assert e != null;
				if (!(e instanceof FiberTerminationException))
				{
					final CharArrayWriter trace = new CharArrayWriter();
					e.printStackTrace(new PrintWriter(trace));
					final Problem problem = new Problem(
						null,
						1,
						1,
						EXECUTION,
						"Error executing command: {0}\n{1}",
						e.getMessage(),
						trace)
					{
						@Override
						public void abortCompilation ()
						{
							// do nothing.
						}
					};
					commandProblemHandler.handle(problem);
				}
				onFailure.value();
			}
		});
		Interpreter.runOutermostFunction(
			runtime,
			fiber,
			function,
			Collections.<AvailObject>emptyList());
	}
}
