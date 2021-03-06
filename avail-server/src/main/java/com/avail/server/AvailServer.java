/*
 * AvailServer.java
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

package com.avail.server;

import com.avail.AvailRuntime;
import com.avail.annotations.InnerAccess;
import com.avail.builder.AvailBuilder;
import com.avail.builder.ModuleName;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.ModuleRoot;
import com.avail.builder.ModuleRoots;
import com.avail.builder.RenamesFileParserException;
import com.avail.builder.ResolvedModuleName;
import com.avail.builder.UnresolvedDependencyException;
import com.avail.compiler.AvailCompiler.CompilerProgressReporter;
import com.avail.compiler.AvailCompiler.GlobalProgressReporter;
import com.avail.compiler.problems.ProblemHandler;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.interpreter.Interpreter;
import com.avail.persistence.IndexedFileException;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.server.configuration.AvailServerConfiguration;
import com.avail.server.configuration.CommandLineConfigurator;
import com.avail.server.configuration.EnvironmentConfigurator;
import com.avail.server.io.AvailServerChannel;
import com.avail.server.io.AvailServerChannel.ProtocolState;
import com.avail.server.io.ServerInputChannel;
import com.avail.server.io.WebSocketAdapter;
import com.avail.server.messages.*;
import com.avail.utility.IO;
import com.avail.utility.Mutable;
import com.avail.utility.MutableOrNull;
import com.avail.utility.configuration.ConfigurationException;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Continuation2NotNull;
import com.avail.utility.evaluation.Continuation3NotNull;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

import static java.util.Collections.*;

/**
 * A {@code AvailServer} manages an Avail environment.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class AvailServer
{
	/** The {@linkplain Logger logger}. */
	public static final Logger logger = Logger.getLogger(
		AvailServer.class.getName());

	/** The current server protocol version. */
	public static final int protocolVersion = 4;

	/** The supported client protocol versions. */
	public static final Set<Integer> supportedProtocolVersions =
		unmodifiableSet(new HashSet<>(singletonList(protocolVersion)));

	/** The {@linkplain AvailServerConfiguration configuration}. */
	@InnerAccess final AvailServerConfiguration configuration;

	/**
	 * Answer the {@linkplain AvailServerConfiguration configuration}.
	 *
	 * @return The configuration.
	 */
	public AvailServerConfiguration configuration ()
	{
		return configuration;
	}

	/**
	 * The {@linkplain AvailRuntime Avail runtime} managed by this {@linkplain
	 * AvailServer server}.
	 */
	@InnerAccess final AvailRuntime runtime;

	/**
	 * Answer the {@linkplain AvailRuntime runtime} managed by this {@linkplain
	 * AvailServer server}.
	 *
	 * @return The managed runtime.
	 */
	public AvailRuntime runtime ()
	{
		return runtime;
	}

	/**
	 * The {@linkplain AvailBuilder Avail builder} responsible for managing
	 * build and execution tasks.
	 */
	@InnerAccess final AvailBuilder builder;

	/**
	 * Answer the {@linkplain AvailBuilder Avail builder} responsible for
	 * managing build and execution tasks.
	 *
	 * @return The builder.
	 */
	public AvailBuilder builder ()
	{
		return builder;
	}

	/**
	 * Construct a new {@code AvailServer} that manages the given {@linkplain
	 * AvailRuntime Avail runtime}.
	 *
	 * @param configuration
	 *        An {@linkplain AvailServerConfiguration configuration}.
	 * @param runtime
	 *        An Avail runtime.
	 */
	public AvailServer (
		final AvailServerConfiguration configuration,
		final AvailRuntime runtime)
	{
		this.configuration = configuration;
		this.runtime = runtime;
		this.builder = new AvailBuilder(runtime);
	}

	/**
	 * The catalog of pending upgrade requests, as a {@linkplain Map map} from
	 * {@link UUID}s to the {@linkplain Continuation3NotNull continuations} that
	 * should be invoked to proceed after the client has satisfied an upgrade
	 * request. The continuation is invoked with the upgraded {@linkplain
	 * AvailServerChannel channel}, the {@code UUID}, and another {@linkplain
	 * Continuation0 continuation} that permits the {@code AvailServer} to
	 * continue processing {@linkplain Message messages} for the upgraded
	 * channel.
	 */
	private final Map
			<
				UUID,
				Continuation3NotNull<AvailServerChannel, UUID, Continuation0>
			>
		pendingUpgrades = new HashMap<>();

	/**
	 * Record an upgrade request issued by this {@code AvailServer} in response
	 * to a {@linkplain Command command}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} that requested the
	 *        upgrade.
	 * @param uuid
	 *        The UUID that identifies the upgrade request.
	 * @param continuation
	 *        What to do with the upgraded {@linkplain AvailServerChannel
	 *        channel}.
	 */
	public void recordUpgradeRequest (
		final AvailServerChannel channel,
		final UUID uuid,
		final Continuation3NotNull
				<AvailServerChannel, UUID, Continuation0>
			continuation)
	{
		synchronized (pendingUpgrades)
		{
			pendingUpgrades.put(uuid, continuation);
		}
		channel.recordUpgradeRequest(uuid);
	}

	/**
	 * Discontinue the specified pending upgrade requests.
	 *
	 * @param uuids
	 *        The {@link UUID}s of the pending upgrades that should be
	 *        discontinued.
	 */
	public void discontinueUpgradeRequests (final Set<UUID> uuids)
	{
		synchronized (pendingUpgrades)
		{
			for (final UUID uuid : uuids)
			{
				pendingUpgrades.remove(uuid);
			}
		}
	}

	/**
	 * Write an {@code "ok"} field into the JSON object being written.
	 *
	 * @param ok
	 *        {@code true} if the operation succeeded, {@code false} otherwise.
	 * @param writer
	 *        A {@link JSONWriter}.
	 */
	@InnerAccess
	static void writeStatusOn (
		final boolean ok,
		final JSONWriter writer)
	{
		writer.write("ok");
		writer.write(ok);
	}

	/**
	 * Write a {@code "command"} field into the JSON object being written.
	 *
	 * @param command
	 *        The {@linkplain Command command}.
	 * @param writer
	 *        A {@link JSONWriter}.
	 */
	private static void writeCommandOn (
		final Command command,
		final JSONWriter writer)
	{
		writer.write("command");
		writer.write(command.name().toLowerCase().replace('_', ' '));
	}

	/**
	 * Write an {@code "id"} field into the JSON object being written.
	 *
	 * @param commandId
	 *        The command identifier.
	 * @param writer
	 *        A {@link JSONWriter}.
	 */
	private static void writeCommandIdentifierOn (
		final long commandId,
		final JSONWriter writer)
	{
		writer.write("id");
		writer.write(commandId);
	}

	/**
	 * Answer an error {@linkplain Message message} that incorporates the
	 * specified reason.
	 *
	 * @param command
	 *        The {@linkplain CommandMessage command} that failed, or {@code
	 *        null} if the command could not be determined.
	 * @param reason
	 *        The reason for the failure.
	 * @param closeAfterSending
	 *        {@code true} if the {@linkplain AvailServerChannel channel} should
	 *        be {@linkplain AvailServerChannel#close() closed} after
	 *        transmitting this message.
	 * @return A message.
	 */
	static @InnerAccess Message newErrorMessage (
		final @Nullable CommandMessage command,
		final String reason,
		final boolean closeAfterSending)
	{
		final JSONWriter writer = new JSONWriter();
		writer.startObject();
		writeStatusOn(false, writer);
		if (command != null)
		{
			writeCommandOn(command.command(), writer);
			writeCommandIdentifierOn(command.commandId(), writer);
		}
		writer.write("reason");
		writer.write(reason);
		writer.endObject();
		return new Message(writer.toString(), closeAfterSending);
	}

	/**
	 * Answer an error {@linkplain Message message} that incorporates the
	 * specified reason.
	 *
	 * @param command
	 *        The {@linkplain CommandMessage command} that failed, or {@code
	 *        null} if the command could not be determined.
	 * @param reason
	 *        The reason for the failure.
	 * @return A message.
	 */
	@InnerAccess static Message newErrorMessage (
		final @Nullable CommandMessage command,
		final String reason)
	{
		return newErrorMessage(command, reason, false);
	}

	/**
	 * Answer a simple {@linkplain Message message} that just affirms success.
	 *
	 * @param command
	 *        The {@linkplain CommandMessage command} for which this is a
	 *        response.
	 * @return A message.
	 */
	@InnerAccess static Message newSimpleSuccessMessage (
		final CommandMessage command)
	{
		final JSONWriter writer = new JSONWriter();
		writer.startObject();
		writeStatusOn(true, writer);
		writeCommandOn(command.command(), writer);
		writeCommandIdentifierOn(command.commandId(), writer);
		writer.endObject();
		return new Message(writer.toString());
	}

	/**
	 * Answer a success {@linkplain Message message} that incorporates the
	 * specified generated content.
	 *
	 * @param command
	 *        The {@linkplain CommandMessage command} for which this is a
	 *        response.
	 * @param content
	 *        How to write the content of the message.
	 * @return A message.
	 */
	@InnerAccess static Message newSuccessMessage (
		final CommandMessage command,
		final Continuation1NotNull<JSONWriter> content)
	{
		final JSONWriter writer = new JSONWriter();
		writer.startObject();
		writeStatusOn(true, writer);
		writeCommandOn(command.command(), writer);
		writeCommandIdentifierOn(command.commandId(), writer);
		writer.write("content");
		content.value(writer);
		writer.endObject();
		return new Message(writer.toString());
	}

	/**
	 * Answer an I/O upgrade request {@linkplain Message message} that
	 * incorporates the specified {@link UUID}.
	 *
	 * @param command
	 *        The {@linkplain CommandMessage command} on whose behalf the
	 *        upgrade is requested.
	 * @param uuid
	 *        The {@code UUID} that denotes the I/O connection.
	 * @return A message.
	 */
	@InnerAccess static Message newIOUpgradeRequestMessage (
		final CommandMessage command,
		final UUID uuid)
	{
		final JSONWriter writer = new JSONWriter();
		writer.startObject();
		writeStatusOn(true, writer);
		writeCommandOn(command.command(), writer);
		writeCommandIdentifierOn(command.commandId(), writer);
		writer.write("upgrade");
		writer.write(uuid.toString());
		writer.endObject();
		return new Message(writer.toString());
	}

	/**
	 * Receive a {@linkplain Message message} from the specified {@linkplain
	 * AvailServerChannel channel}.
	 *
	 * @param message
	 *        A message.
	 * @param channel
	 *        The channel on which the message was received.
	 * @param receiveNext
	 *        How to receive the next message from the channel (when the {@code
	 *        AvailServer} has processed this message sufficiently).
	 */
	public static void receiveMessageThen (
		final Message message,
		final AvailServerChannel channel,
		final Continuation0 receiveNext)
	{
		switch (channel.state())
		{
			case VERSION_NEGOTIATION:
			{
				final @Nullable CommandMessage command = Command.VERSION.parse(
					message.content());
				if (command != null)
				{
					command.setCommandId(channel.nextCommandId());
					command.processThen(channel, receiveNext);
				}
				else
				{
					final Message rebuttal = newErrorMessage(
						null,
						"must negotiate version before issuing other commands",
						true);
					channel.enqueueMessageThen(rebuttal, receiveNext);
				}
				break;
			}
			case ELIGIBLE_FOR_UPGRADE:
				try
				{
					final CommandMessage command = Command.parse(message);
					command.setCommandId(channel.nextCommandId());
					command.processThen(channel, receiveNext);
				}
				catch (final CommandParseException e)
				{
					final Message rebuttal =
						newErrorMessage(null, e.getLocalizedMessage());
					channel.enqueueMessageThen(rebuttal, receiveNext);
				}
				finally
				{
					// Only allow a single opportunity to upgrade the channel,
					// even if the command was gibberish.
					if (channel.state().eligibleForUpgrade())
					{
						channel.setState(ProtocolState.COMMAND);
					}
				}
				break;
			case COMMAND:
				try
				{
					final CommandMessage command = Command.parse(message);
					command.setCommandId(channel.nextCommandId());
					command.processThen(channel, receiveNext);
				}
				catch (final CommandParseException e)
				{
					final Message rebuttal =
						newErrorMessage(null, e.getLocalizedMessage());
					channel.enqueueMessageThen(rebuttal, receiveNext);
				}
				break;
			case IO:
			{
				final ServerInputChannel input = (ServerInputChannel)
					channel.textInterface().inputChannel();
				input.receiveMessageThen(message, receiveNext);
				break;
			}
		}
	}

	/**
	 * Negotiate a version. If the {@linkplain VersionCommandMessage#version()
	 * requested version} is {@linkplain #supportedProtocolVersions supported},
	 * then echo this version back to the client. Otherwise, send a list of the
	 * supported versions for the client to examine. If the client cannot (or
	 * does not wish to) deal with the requested versions, then it must
	 * disconnect.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        A {@link Command#VERSION VERSION} command message.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public static void negotiateVersionThen (
		final AvailServerChannel channel,
		final VersionCommandMessage command,
		final Continuation0 continuation)
	{
		if (channel.state().versionNegotiated())
		{
			final Message message = newErrorMessage(
				command, "version already negotiated");
			channel.enqueueMessageThen(message, continuation);
			return;
		}
		final int version = command.version();
		final Message message;
		if (supportedProtocolVersions.contains(version))
		{
			message = newSuccessMessage(
				command, writer -> writer.write(version));
		}
		else
		{
			message = newSuccessMessage(
				command,
				writer ->
				{
					writer.startObject();
					writer.write("supported");
					writer.startArray();
					for (final int supported : supportedProtocolVersions)
					{
						writer.write(supported);
					}
					writer.endArray();
					writer.endObject();
				});
		}
		// Transition to the next state. If the client cannot handle any of the
		// specified versions, then it must disconnect.
		channel.setState(ProtocolState.ELIGIBLE_FOR_UPGRADE);
		channel.enqueueMessageThen(message, continuation);
	}

	/**
	 * List syntax guides for all of the {@linkplain Command commands}
	 * understood by the {@code AvailServer}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        A {@link Command#COMMANDS COMMANDS} command message.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public static void commandsThen (
		final AvailServerChannel channel,
		final SimpleCommandMessage command,
		final Continuation0 continuation)
	{
		assert command.command() == Command.COMMANDS;
		final Message message = newSuccessMessage(
			command,
			writer ->
			{
				final Command[] commands = Command.all();
				final List<String> help = new ArrayList<>(commands.length);
				for (final Command c : commands)
				{
					help.add(c.syntaxHelp());
				}
				sort(help);
				writer.startArray();
				for (final String h : help)
				{
					writer.write(h);
				}
				writer.endArray();
			});
		channel.enqueueMessageThen(message, continuation);
	}

	/**
	 * List all {@linkplain ModuleRoot module roots}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        A {@link Command#MODULE_ROOTS MODULE_ROOTS} command message.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public void moduleRootsThen (
		final AvailServerChannel channel,
		final SimpleCommandMessage command,
		final Continuation0 continuation)
	{
		assert command.command() == Command.MODULE_ROOTS;
		final Message message = newSuccessMessage(
			command, writer -> runtime.moduleRoots().writeOn(writer));
		channel.enqueueMessageThen(message, continuation);
	}

	/**
	 * List all {@linkplain ModuleRoots#writePathsOn(JSONWriter) module root
	 * paths}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        A {@link Command#MODULE_ROOT_PATHS MODULE_ROOT_PATHS} command
	 *        message.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public void moduleRootPathsThen (
		final AvailServerChannel channel,
		final SimpleCommandMessage command,
		final Continuation0 continuation)
	{
		assert command.command() == Command.MODULE_ROOT_PATHS;
		final Message message = newSuccessMessage(
			command, writer -> runtime.moduleRoots().writePathsOn(writer));
		channel.enqueueMessageThen(message, continuation);
	}

	/**
	 * Answer the {@linkplain ModuleRoots#modulePath() module roots path}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        A {@link Command#MODULE_ROOT_PATHS MODULE_ROOT_PATHS} command
	 *        message.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public void moduleRootsPathThen (
		final AvailServerChannel channel,
		final SimpleCommandMessage command,
		final Continuation0 continuation)
	{
		assert command.command() == Command.MODULE_ROOTS_PATH;
		final Message message = newSuccessMessage(
			command,
			writer -> writer.write(runtime.moduleRoots().modulePath()));
		channel.enqueueMessageThen(message, continuation);
	}

	/**
	 * A {@code ModuleNode} represents a node in a module tree.
	 */
	@InnerAccess static final class ModuleNode
	{
		/** The name associated with the {@linkplain ModuleNode node}. */
		final String name;

		/** The children of the {@linkplain ModuleNode node}. */
		@Nullable List<ModuleNode> modules;

		/**
		 * Add the specified {@code ModuleNode} as a module.
		 *
		 * @param node
		 *        The child node.
		 */
		void addModule (final ModuleNode node)
		{
			@Nullable List<ModuleNode> list = modules;
			if (list == null)
			{
				list = new ArrayList<>();
				modules = list;
			}
			list.add(node);
		}

		/** The resources of the {@linkplain ModuleNode node}. */
		@Nullable List<ModuleNode> resources;

		/**
		 * Add the specified {@code ModuleNode} as a resource.
		 *
		 * @param node
		 *        The child node.
		 */
		void addResource (final ModuleNode node)
		{
			@Nullable List<ModuleNode> list = resources;
			if (list == null)
			{
				list = new ArrayList<>();
				resources = list;
			}
			list.add(node);
		}

		/**
		 * The {@linkplain Throwable exception} that prevented evaluation of
		 * this {@linkplain ModuleNode node}.
		 */
		@Nullable Throwable exception;

		/**
		 * Set the {@linkplain Throwable exception} that prevented evaluation of
		 * this {@code ModuleNode}.
		 *
		 * @param exception
		 *        An exception.
		 */
		void setException (final Throwable exception)
		{
			this.exception = exception;
		}

		/**
		 * Construct a new {@code ModuleNode}.
		 *
		 * @param name
		 *        The name.
		 */
		ModuleNode (final String name)
		{
			this.name = name;
		}

		/**
		 * Recursively write the {@code ModuleNode} to the supplied {@link
		 * JSONWriter}.
		 *
		 * @param isRoot
		 *        {@code true} if the receiver represents a {@linkplain
		 *        ModuleNode module root}, {@code false} otherwise.
		 * @param isResource
		 *        {@code true} if the receiver represents a resource, {@code
		 *        false} otherwise.
		 * @param writer
		 *        A {@code JSONWriter}.
		 */
		private void recursivelyWriteOn (
			final boolean isRoot,
			final boolean isResource,
			final JSONWriter writer)
		{
			writer.startObject();
			writer.write("text");
			writer.write(name);
			if (isRoot)
			{
				writer.write("isRoot");
				writer.write(true);
			}
			final @Nullable List<ModuleNode> mods = modules;
			final boolean isPackage = !isRoot && mods != null;
			if (isPackage)
			{
				writer.write("isPackage");
				writer.write(true);
			}
			if (isResource)
			{
				writer.write("isResource");
				writer.write(true);
			}
			final @Nullable List<ModuleNode> res = resources;
			if (mods != null || res != null)
			{
				writer.write("state");
				writer.startObject();
				writer.write("opened");
				writer.write(isRoot);
				writer.endObject();
				boolean missingRepresentative = !isResource;
				writer.write("children");
				writer.startArray();
				if (mods != null)
				{
					for (final ModuleNode mod : mods)
					{
						mod.recursivelyWriteOn(false, false, writer);
						if (mod.name.equals(name))
						{
							missingRepresentative = false;
						}
					}
				}
				if (res != null)
				{
					for (final ModuleNode r : res)
					{
						r.recursivelyWriteOn(false, true, writer);
					}
				}
				writer.endArray();
				if (missingRepresentative)
				{
					writer.write("missingRepresentative");
					writer.write(true);
				}
			}
			final @Nullable Throwable e = exception;
			if (e != null)
			{
				writer.write("error");
				writer.write(e.getLocalizedMessage());
			}
			writer.endObject();
		}

		/**
		 * Write the {@code ModuleNode} to the supplied {@link JSONWriter}.
		 *
		 * @param writer
		 *        A {@code JSONWriter}.
		 */
		void writeOn (final JSONWriter writer)
		{
			recursivelyWriteOn(true, false, writer);
		}
	}

	/**
	 * Answer a {@linkplain FileVisitor visitor} able to visit every source
	 * module beneath the specified {@linkplain ModuleRoot module root}.
	 *
	 * @param root
	 *        A module root.
	 * @param tree
	 *        The {@linkplain MutableOrNull holder} for the resultant tree of
	 *        {@linkplain ModuleNode modules}.
	 * @return A {@code FileVisitor}.
	 */
	@InnerAccess
	static FileVisitor<Path> sourceModuleVisitor (
		final ModuleRoot root,
		final MutableOrNull<ModuleNode> tree)
	{
		final String extension = ModuleNameResolver.availExtension;
		final Mutable<Boolean> isRoot = new Mutable<>(true);
		final Deque<ModuleNode> stack = new ArrayDeque<>();
		return new FileVisitor<Path>()
		{
			@Override
			public FileVisitResult preVisitDirectory (
				final @Nullable Path dir,
				final @Nullable BasicFileAttributes attrs)
			{
				assert dir != null;
				if (isRoot.value)
				{
					isRoot.value = false;
					final ModuleNode node = new ModuleNode(root.name());
					tree.value = node;
					stack.add(node);
					return FileVisitResult.CONTINUE;
				}
				final String fileName = dir.getFileName().toString();
				if (fileName.endsWith(extension))
				{
					final String localName = fileName.substring(
						0, fileName.length() - extension.length());
					final ModuleNode node = new ModuleNode(localName);
					stack.peekFirst().addModule(node);
					stack.addFirst(node);
					return FileVisitResult.CONTINUE;
				}
				// This is a resource.
				final ModuleNode node = new ModuleNode(fileName);
				stack.peekFirst().addResource(node);
				stack.addFirst(node);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory (
				final @Nullable Path dir,
				final @Nullable IOException e)
			{
				stack.removeFirst();
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile (
				final @Nullable Path file,
				final @Nullable BasicFileAttributes attrs)
			{
				assert file != null;
				// The root should be a directory, not a file.
				if (isRoot.value)
				{
					tree.value = new ModuleNode(root.name());
					return FileVisitResult.TERMINATE;
				}
				final String fileName = file.getFileName().toString();
				if (fileName.endsWith(extension))
				{
					final String localName = fileName.substring(
						0, fileName.length() - extension.length());
					final ModuleNode node = new ModuleNode(localName);
					stack.peekFirst().addModule(node);
				}
				else
				{
					final ModuleNode node = new ModuleNode(fileName);
					stack.peekFirst().addResource(node);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed (
				final @Nullable Path file,
				final @Nullable IOException e)
			{
				assert file != null;
				final String fileName = file.getFileName().toString();
				if (fileName.endsWith(extension))
				{
					final String localName = fileName.substring(
						0, fileName.length() - extension.length());
					final ModuleNode node = new ModuleNode(localName);
					node.exception = e;
					stack.peekFirst().addModule(node);
				}
				else
				{
					final ModuleNode node = new ModuleNode(fileName);
					node.exception = e;
					stack.peekFirst().addResource(node);
				}
				return FileVisitResult.CONTINUE;
			}
		};
	}

	/**
	 * List all source modules reachable from the {@linkplain ModuleRoots
	 * module roots}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        A {@link Command#SOURCE_MODULES SOURCE_MODULES} command message.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public void sourceModulesThen (
		final AvailServerChannel channel,
		final SimpleCommandMessage command,
		final Continuation0 continuation)
	{
		assert command.command() == Command.SOURCE_MODULES;
		final Message message = newSuccessMessage(
			command,
			writer ->
			{
				final ModuleRoots roots = runtime.moduleRoots();
				writer.startArray();
				for (final ModuleRoot root : roots)
				{
					final MutableOrNull<ModuleNode> tree =
						new MutableOrNull<>();
					final @Nullable File directory = root.sourceDirectory();
					if (directory != null)
					{
						try
						{
							Files.walkFileTree(
								Paths.get(directory.getAbsolutePath()),
								EnumSet.of(FileVisitOption.FOLLOW_LINKS),
								Integer.MAX_VALUE,
								sourceModuleVisitor(root, tree));
						}
						catch (final IOException e)
						{
							// This shouldn't happen, since we never raise
							// any exceptions in the visitor.
						}
					}
					tree.value().writeOn(writer);
				}
				writer.endArray();
			});
		channel.enqueueMessageThen(message, continuation);
	}

	/**
	 * List all source modules reachable from the {@linkplain ModuleRoots
	 * module roots}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        A {@link Command#ENTRY_POINTS ENTRY_POINTS} command message.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public void entryPointsThen (
		final AvailServerChannel channel,
		final SimpleCommandMessage command,
		final Continuation0 continuation)
	{
		assert command.command() == Command.ENTRY_POINTS;
		final Message message = newSuccessMessage(
			command,
			writer ->
			{
				final Map<String, List<String>> map =
					synchronizedMap(new HashMap<>());
				builder.traceDirectories(
					(name, version, after) ->
					{
						final List<String> entryPoints =
							version.getEntryPoints();
						if (!entryPoints.isEmpty())
						{
							map.put(name.qualifiedName(), entryPoints);
						}
						after.value();
					});
				writer.startArray();
				for (final Entry<String, List<String>> entry :
					map.entrySet())
				{
					writer.startObject();
					writer.write(entry.getKey());
					writer.startArray();
					for (final String entryPoint : entry.getValue())
					{
						writer.write(entryPoint);
					}
					writer.endArray();
					writer.endObject();
				}
				writer.endArray();
			});
		channel.enqueueMessageThen(message, continuation);
	}

	/**
	 * Clear all {@linkplain IndexedRepositoryManager binary module
	 * repositories}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        A {@link Command#CLEAR_REPOSITORIES CLEAR_REPOSITORIES} command
	 *        message.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public void clearRepositoriesThen (
		final AvailServerChannel channel,
		final SimpleCommandMessage command,
		final Continuation0 continuation)
	{
		assert command.command() == Command.CLEAR_REPOSITORIES;
		Message message;
		try
		{
			for (final ModuleRoot root :
				runtime.moduleNameResolver().moduleRoots().roots())
			{
				root.clearRepository();
			}
			message = newSimpleSuccessMessage(command);
		}
		catch (final IndexedFileException e)
		{
			message = newErrorMessage(command, e.getLocalizedMessage());
		}
		channel.enqueueMessageThen(message, continuation);
	}

	/**
	 * Upgrade the specified {@linkplain AvailServerChannel channel}.
	 *
	 * @param channel
	 *        The channel on which the {@linkplain CommandMessage response}
	 *        should be sent.
	 * @param command
	 *        An {@link Command#UPGRADE UPGRADE} {@linkplain
	 *        UpgradeCommandMessage command message}.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public void upgradeThen (
		final AvailServerChannel channel,
		final UpgradeCommandMessage command,
		final Continuation0 continuation)
	{
		if (!channel.state().eligibleForUpgrade())
		{
			final Message message = newErrorMessage(
				command, "channel not eligible for upgrade");
			channel.enqueueMessageThen(message, continuation);
			return;
		}
		final @Nullable Continuation3NotNull
				<AvailServerChannel, UUID, Continuation0>
			upgrader;
		synchronized (pendingUpgrades)
		{
			upgrader = pendingUpgrades.remove(command.uuid());
		}
		if (upgrader == null)
		{
			final Message message = newErrorMessage(
				command, "no such upgrade");
			channel.enqueueMessageThen(message, continuation);
			return;
		}
		upgrader.value(channel, command.uuid(), continuation);
	}

	/**
	 * Request new I/O-upgraded {@linkplain AvailServerChannel channels}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        The {@linkplain CommandMessage command} on whose behalf the
	 *        upgrade should be requested.
	 * @param afterUpgraded
	 *        What to do after the upgrades have been completed by the client.
	 *        The argument is the upgraded channel.
	 * @param afterEnqueuing
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	private void requestUpgradesThen (
		final AvailServerChannel channel,
		final CommandMessage command,
		final Continuation1NotNull<AvailServerChannel> afterUpgraded,
		final Continuation0 afterEnqueuing)
	{
		final UUID uuid = UUID.randomUUID();
		recordUpgradeRequest(
			channel,
			uuid,
			(upgradedChannel, receivedUUID, resumeUpgrader) ->
			{
				assert uuid.equals(receivedUUID);
				upgradedChannel.upgradeToIOChannel();
				resumeUpgrader.value();
				afterUpgraded.value(upgradedChannel);
			});
		channel.enqueueMessageThen(
			newIOUpgradeRequestMessage(command, uuid),
			afterEnqueuing);
	}

	/**
	 * Request new I/O-upgraded {@linkplain AvailServerChannel channels} to
	 * support {@linkplain AvailBuilder#buildTarget(ModuleName,
	 * CompilerProgressReporter, GlobalProgressReporter, ProblemHandler) module
	 * loading}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        A {@link Command#LOAD_MODULE LOAD_MODULE} {@linkplain
	 *        LoadModuleCommandMessage command message}.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public void requestUpgradesForLoadModuleThen (
		final AvailServerChannel channel,
		final LoadModuleCommandMessage command,
		final Continuation0 continuation)
	{
		requestUpgradesThen(
			channel,
			command,
			ioChannel -> loadModule(channel, ioChannel, command),
			continuation);
	}

	/**
	 * The progress interval for {@linkplain #loadModule(
	 * AvailServerChannel, AvailServerChannel, LoadModuleCommandMessage)
	 * building}, in milliseconds.
	 */
	private static final int buildProgressIntervalMillis = 100;

	/**
	 * Load the specified {@linkplain ModuleName module}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param ioChannel
	 *        The upgraded I/O channel.
	 * @param command
	 *        A {@link Command#LOAD_MODULE LOAD_MODULE} {@linkplain
	 *        LoadModuleCommandMessage command message}.
	 */
	@InnerAccess void loadModule (
		final AvailServerChannel channel,
		final AvailServerChannel ioChannel,
		final LoadModuleCommandMessage command)
	{
		assert !channel.state().generalTextIO();
		assert ioChannel.state().generalTextIO();
		final Continuation0 nothing = () ->
		{
			// Do nothing.
		};
		channel.enqueueMessageThen(
			newSuccessMessage(command, writer -> writer.write("begin")),
			nothing);
		final List<JSONWriter> localUpdates = new ArrayList<>();
		final List<JSONWriter> globalUpdates = new ArrayList<>();
		final TimerTask updater = new TimerTask()
		{
			@Override
			public void run ()
			{
				final List<JSONWriter> locals;
				synchronized (localUpdates)
				{
					locals = new ArrayList<>(localUpdates);
					localUpdates.clear();
				}
				final List<JSONWriter> globals;
				synchronized (globalUpdates)
				{
					globals = new ArrayList<>(globalUpdates);
					globalUpdates.clear();
				}
				if (!locals.isEmpty() && !globals.isEmpty())
				{
					final Message message = newSuccessMessage(
						command,
						writer ->
						{
							writer.startObject();
							writer.write("local");
							writer.startArray();
							for (final JSONWriter local : locals)
							{
								writer.write(local);
							}
							writer.endArray();
							writer.write("global");
							writer.startArray();
							for (final JSONWriter global : globals)
							{
								writer.write(global);
							}
							writer.endArray();
							writer.endObject();
						});
					channel.enqueueMessageThen(message, nothing);
				}
			}
		};
		runtime.timer.schedule(
			updater,
			buildProgressIntervalMillis,
			buildProgressIntervalMillis);
		builder.setTextInterface(ioChannel.textInterface());
		builder.buildTarget(
			command.target(),
			(name, moduleSize, position) ->
			{
				final JSONWriter writer = new JSONWriter();
				writer.startObject();
				writer.write("module");
				writer.write(name.qualifiedName());
				writer.write("position");
				writer.write(position);
				writer.endObject();
				synchronized (localUpdates)
				{
					localUpdates.add(writer);
				}
			},
			(bytesSoFar, totalBytes) ->
			{
				final JSONWriter writer = new JSONWriter();
				writer.startObject();
				writer.write("bytesSoFar");
				writer.write(bytesSoFar);
				writer.write("totalBytes");
				writer.write(totalBytes);
				writer.endObject();
				synchronized (globalUpdates)
				{
					globalUpdates.add(writer);
				}
			},
			builder.buildProblemHandler);
		updater.cancel();
		updater.run();
		assert localUpdates.isEmpty();
		assert globalUpdates.isEmpty();
		channel.enqueueMessageThen(
			newSuccessMessage(command, writer -> writer.write("end")),
			() -> IO.close(ioChannel));
	}

	/**
	 * Request new I/O-upgraded {@linkplain AvailServerChannel channels} to
	 * support {@linkplain AvailBuilder#unloadTarget(ResolvedModuleName)
	 * module unloading}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        A {@link Command#LOAD_MODULE LOAD_MODULE} {@linkplain
	 *        LoadModuleCommandMessage command message}.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public void requestUpgradesForUnloadModuleThen (
		final AvailServerChannel channel,
		final UnloadModuleCommandMessage command,
		final Continuation0 continuation)
	{
		final ResolvedModuleName moduleName;
		try
		{
			moduleName = runtime.moduleNameResolver().resolve(
				command.target(), null);
		}
		catch (final UnresolvedDependencyException e)
		{
			final Message message = newErrorMessage(command, e.toString());
			channel.enqueueMessageThen(
				message,
				() ->
				{
					// Do nothing.
				});
			return;
		}
		requestUpgradesThen(
			channel,
			command,
			ioChannel -> unloadModule(channel, ioChannel, command, moduleName),
			continuation);
	}

	/**
	 * Request new I/O-upgraded {@linkplain AvailServerChannel channels} to
	 * support {@linkplain AvailBuilder#unloadTarget(ResolvedModuleName)
	 * builder} unloading all modules}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        An {@link Command#UNLOAD_ALL_MODULES UNLOAD_ALL_MODULES}
	 *        {@linkplain SimpleCommandMessage command message}.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public void requestUpgradesForUnloadAllModulesThen (
		final AvailServerChannel channel,
		final SimpleCommandMessage command,
		final Continuation0 continuation)
	{
		assert command.command() == Command.UNLOAD_ALL_MODULES;
		requestUpgradesThen(
			channel,
			command,
			ioChannel -> unloadModule(channel, ioChannel, command, null),
			continuation);
	}

	/**
	 * Unload the specified {@linkplain ResolvedModuleName module}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param ioChannel
	 *        The upgraded I/O channel.
	 * @param command
	 *        An {@link Command#UNLOAD_MODULE UNLOAD_MODULE} or {@linkplain
	 *        Command#UNLOAD_ALL_MODULES UNLOAD_ALL_MODULES} {@linkplain
	 *        CommandMessage command message}.
	 * @param target
	 *        The resolved name of the target {@linkplain A_Module module}, or
	 *        {@code null} if all modules should be unloaded.
	 */
	@InnerAccess void unloadModule (
		final AvailServerChannel channel,
		final AvailServerChannel ioChannel,
		final CommandMessage command,
		final @Nullable ResolvedModuleName target)
	{
		assert !channel.state().generalTextIO();
		assert ioChannel.state().generalTextIO();
		channel.enqueueMessageThen(
			newSuccessMessage(command, writer -> writer.write("begin")),
			() ->
			{
				// Do nothing.
			});
		builder.setTextInterface(ioChannel.textInterface());
		builder.unloadTarget(target);
		channel.enqueueMessageThen(
			newSuccessMessage(command, writer -> writer.write("end")),
			() -> IO.close(ioChannel));
	}

	/**
	 * Request new I/O-upgraded {@linkplain AvailServerChannel channels} to
	 * support {@linkplain AvailBuilder#attemptCommand(String,
	 * Continuation2NotNull, Continuation2NotNull, Continuation0) builder}
	 * command execution}.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        A {@link Command#RUN_ENTRY_POINT RUN_ENTRY_POINT} {@linkplain
	 *        RunEntryPointCommandMessage command message}.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public void requestUpgradesForRunThen (
		final AvailServerChannel channel,
		final RunEntryPointCommandMessage command,
		final Continuation0 continuation)
	{
		requestUpgradesThen(
			channel,
			command,
			ioChannel -> run(channel, ioChannel, command),
			continuation);
	}

	/**
	 * Run the specified command (i.e., entry point expression).
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param ioChannel
	 *        The upgraded I/O channel.
	 * @param command
	 *        A {@link Command#RUN_ENTRY_POINT RUN_ENTRY_POINT} {@linkplain
	 *        RunEntryPointCommandMessage command message}.
	 */
	@InnerAccess void run (
		final AvailServerChannel channel,
		final AvailServerChannel ioChannel,
		final RunEntryPointCommandMessage command)
	{
		assert !channel.state().generalTextIO();
		assert ioChannel.state().generalTextIO();
		builder.setTextInterface(ioChannel.textInterface());
		builder.attemptCommand(
			command.expression(),
			(list, decider) ->
			{
				// TODO: [TLS] Disambiguate.
			},
			(value, cleanup) ->
			{
				if (value.equalsNil())
				{
					final Message message = newSuccessMessage(
						command,
						writer ->
						{
							writer.startObject();
							writer.write("expression");
							writer.write(command.expression());
							writer.write("result");
							writer.writeNull();
							writer.endObject();
						});
					channel.enqueueMessageThen(
						message,
						() -> cleanup.value(() -> IO.close(ioChannel)));
					return;
				}
				Interpreter.stringifyThen(
					runtime,
					ioChannel.textInterface(),
					value,
					string ->
					{
						final Message message = newSuccessMessage(
							command,
							writer ->
							{
								writer.startObject();
								writer.write("expression");
								writer.write(command.expression());
								writer.write("result");
								writer.write(string);
								writer.endObject();
							});
						channel.enqueueMessageThen(
							message,
							() -> cleanup.value(() -> IO.close(ioChannel)));
					});
			},
			() -> IO.close(ioChannel));
	}

	/**
	 * Report all {@linkplain A_Fiber fibers} that have not yet {@linkplain
	 * ExecutionState#RETIRED retired} and been reclaimed by garbage collection.
	 *
	 * @param channel
	 *        The {@linkplain AvailServerChannel channel} on which the
	 *        {@linkplain CommandMessage response} should be sent.
	 * @param command
	 *        A {@link Command#ALL_FIBERS ALL_FIBERS} command message.
	 * @param continuation
	 *        What to do when sufficient processing has occurred (and the {@code
	 *        AvailServer} wishes to begin receiving messages again).
	 */
	public void allFibersThen (
		final AvailServerChannel channel,
		final SimpleCommandMessage command,
		final Continuation0 continuation)
	{
		assert command.command() == Command.ALL_FIBERS;
		final Set<A_Fiber> allFibers = runtime.allFibers();
		final Message message = newSuccessMessage(
			command,
			writer ->
			{
				writer.startArray();
				for (final A_Fiber fiber : allFibers)
				{
					writer.startObject();
					writer.write("id");
					writer.write(fiber.uniqueId());
					writer.write("name");
					writer.write(fiber.fiberName());
					writer.endObject();
				}
				writer.endArray();
			});
		channel.enqueueMessageThen(message, continuation);
	}

	/**
	 * Obtain the {@linkplain AvailServerConfiguration configuration} of the
	 * {@code AvailServer}.
	 *
	 * @param args
	 *        The command-line arguments.
	 * @return A viable configuration.
	 * @throws ConfigurationException
	 *         If configuration fails for any reason.
	 */
	private static AvailServerConfiguration configure (final String[] args)
		throws ConfigurationException
	{
		final AvailServerConfiguration configuration =
			new AvailServerConfiguration();
		final EnvironmentConfigurator environmentConfigurator =
			new EnvironmentConfigurator(configuration);
		environmentConfigurator.updateConfiguration();
		final CommandLineConfigurator commandLineConfigurator =
			new CommandLineConfigurator(configuration, args, System.out);
		commandLineConfigurator.updateConfiguration();
		return configuration;
	}

	/**
	 * The entry point for command-line invocation of the {@linkplain
	 * AvailServer Avail server}.
	 *
	 * @param args
	 *        The command-line arguments.
	 */
	public static void main (final String[] args)
	{
		final AvailServerConfiguration configuration;
		final ModuleNameResolver resolver;
		try
		{
			configuration = configure(args);
			resolver = configuration.moduleNameResolver();
		}
		catch (
			final ConfigurationException
			| FileNotFoundException
			| RenamesFileParserException e)
		{
			System.err.println(e.getMessage());
			return;
		}
		final AvailRuntime runtime = new AvailRuntime(resolver);
		final AvailServer server = new AvailServer(configuration, runtime);
		try
		{
			@SuppressWarnings({"unused", "resource"})
			final WebSocketAdapter adapter = new WebSocketAdapter(
				server,
				new InetSocketAddress(configuration.serverPort()),
				configuration.serverAuthority());
			// Prevent the Avail server from exiting.
			new Semaphore(0).acquire();
		}
		catch (
			final NumberFormatException
			| IOException
			| InterruptedException e)
		{
			e.printStackTrace();
		}
		finally
		{
			runtime.destroy();
		}
	}
}
