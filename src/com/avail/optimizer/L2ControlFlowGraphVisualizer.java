/*
 * L2ControlFlowGraphVisualizer.java
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

package com.avail.optimizer;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2NamedOperandType;
import com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2Operand;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operation.L2_UNREACHABLE_CODE;
import com.avail.interpreter.levelTwo.register.L2Register;
import com.avail.utility.dot.DotWriter;
import com.avail.utility.dot.DotWriter.GraphWriter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.COMMENT;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.utility.dot.DotWriter.DefaultAttributeBlockType.EDGE;
import static com.avail.utility.dot.DotWriter.DefaultAttributeBlockType.NODE;
import static com.avail.utility.dot.DotWriter.node;

/**
 * An {@code L2ControlFlowGraphVisualizer} generates a {@code dot} source file
 * that visualizes an {@link L2ControlFlowGraph}. It is intended to aid in
 * debugging {@link L2Chunk}s.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2ControlFlowGraphVisualizer
{
	/**
	 * The name of the {@code dot} file.
	 */
	private final String fileName;

	/**
	 * The {@linkplain L2Chunk#name() name} of the {@link L2Chunk}, to be used
	 * as the name of the graph.
	 */
	private final String name;

	/**
	 * The number of characters to emit per line. Only applies to formatting
	 * of block comments.
	 */
	private int charactersPerLine;

	/**
	 * The {@link L2ControlFlowGraph} that should be visualized by a {@code dot}
	 * renderer.
	 */
	private final L2ControlFlowGraph controlFlowGraph;

	/**
	 * The {@linkplain Appendable accumulator} for the generated {@code dot}
	 * source text.
	 */
	private final Appendable accumulator;

	/**
	 * Construct a new {@code L2ControlFlowGraphVisualizer} for the specified
	 * {@link L2ControlFlowGraph}.
	 *
	 * @param fileName
	 *        The name of the {@code dot} file.
	 * @param name
	 *        The {@linkplain L2Chunk#name() name} of the {@link L2Chunk}, to be
	 *        used as the name of the graph.
	 * @param charactersPerLine
	 *        The number of characters to emit per line. Only applies to
	 *        formatting of block comments.
	 * @param controlFlowGraph
	 *        The {@code L2ControlFlowGraph}.
	 * @param accumulator
	 *        The {@linkplain Appendable accumulator} for the generated {@code
	 *        dot} source text.
	 */
	public L2ControlFlowGraphVisualizer (
		final String fileName,
		final String name,
		final int charactersPerLine,
		final L2ControlFlowGraph controlFlowGraph,
		final Appendable accumulator)
	{
		this.fileName = fileName;
		this.name = name;
		this.charactersPerLine = charactersPerLine;
		this.controlFlowGraph = controlFlowGraph;
		this.accumulator = accumulator;
	}

	/**
	 * Emit a banner.
	 *
	 * @param writer
	 *        The {@link DotWriter}.
	 * @throws IOException
	 *         If emission fails.
	 */
	private void banner (final DotWriter writer) throws IOException
	{
		writer.blockComment(String.format(
			"\n"
			+ "%s.dot\n"
			+ "Copyright © %s, %s.\n"
			+ "All rights reserved.\n"
			+ "\n"
			+ "Generated by %s - do not modify!\n"
			+ "\n",
			fileName,
			System.getProperty("user.name"),
			LocalDateTime.ofInstant(
				Instant.now(),
				ZoneId.systemDefault()).getYear(),
			L2ControlFlowGraphVisualizer.class.getSimpleName()));
	}

	/**
	 * Compute a unique name for the specified {@link L2BasicBlock}.
	 *
	 * @param basicBlock
	 *        The {@code L2BasicBlock}.
	 * @return A unique name that includes the {@code L2BasicBlock}'s
	 *         {@linkplain L2BasicBlock#offset() program counter} and its
	 *         non-unique semantic {@linkplain L2BasicBlock#name() name}.
	 */
	private String basicBlockName (final L2BasicBlock basicBlock)
	{
		return String.format(
			"[pc: %d] %s", basicBlock.offset(),
			basicBlock.name());
	}

	/**
	 * Escape the specified text for inclusion into an HTML-like identifier.
	 *
	 * @param s
	 *        Some arbitrary text.
	 * @return The escaped text.
	 */
	private String escape (final String s)
	{
		final int limit = s.length();
		final StringBuilder builder = new StringBuilder(limit);
		for (int i = 0; i < limit;)
		{
			final int cp = s.codePointAt(i);
			if (cp > 127 || cp == '"' || cp == '<' || cp == '>' || cp == '&')
			{
				builder.append("&#");
				builder.append(cp);
				builder.append(';');
			}
			else if (cp == '\n')
			{
				builder.append("<br/>");
			}
			else if (cp == '\t')
			{
				builder.append("&nbsp;&nbsp;&nbsp;&nbsp;");
			}
			else
			{
				builder.appendCodePoint(cp);
			}
			i += Character.charCount(cp);
		}
		return builder.toString();
	}

	/**
	 * Compute a reasonable description of the specified {@link L2Instruction}.
	 * Any {@link L2PcOperand}s will be ignored in the rendition of the
	 * {@code L2Instruction}, as they will be described along the edges instead
	 * of within the nodes.
	 *
	 * @param instruction
	 *        An {@code L2Instruction}.
	 * @return The requested description.
	 */
	private String instruction (final L2Instruction instruction)
	{
		final StringBuilder builder = new StringBuilder();
		// Hoist a comment operand, if one is present.
		for (final L2Operand operand : instruction.operands)
		{
			if (operand.operandType() == COMMENT)
			{
				// The selection of Helvetica as the font is important. Some
				// renderers, like Viz.js, only seem to fully support a small
				// number of standard, widely available fonts:
				//
				// https://github.com/mdaines/viz.js/issues/82
				//
				// In particular, Courier, Arial, Helvetica, and Times are
				// supported.
				builder.append("<font face=\"Helvetica\" color=\"gray\"><i>");
				builder.append(operand.toString());
				builder.append("</i></font><br/>");
				// There should never be a second comment. If there is — tough.
				break;
			}
		}
		// Make a note of the current length of the builder. We will need to
		// escape everything after this point.
		final int escapeIndex = builder.length();
		final Set<L2OperandType> desiredTypes =
			EnumSet.complementOf(EnumSet.of(PC, COMMENT));
		instruction.operation.toString(instruction, desiredTypes, builder);
		// Escape everything since the saved position.
		return builder.replace(
			escapeIndex,
			builder.length(),
			escape(builder.substring(escapeIndex))).toString();
	}

	/**
	 * Emit the specified {@link L2BasicBlock}.
	 *
	 * @param basicBlock
	 *        A {@code L2BasicBlock}.
	 * @param writer
	 *        The {@link GraphWriter} for emission.
	 */
	private void basicBlock (
		final L2BasicBlock basicBlock,
		final GraphWriter writer)
	{
		final StringBuilder builder = new StringBuilder();
		builder.append(
			"<table border=\"0\" cellspacing=\"0\">");
		final L2Instruction first =
			basicBlock.instructions().get(0);
		final String bgcolor;
		final String fontcolor;
		if (basicBlock.instructions().stream().anyMatch(
			i -> i.operation == L2_UNREACHABLE_CODE.instance))
		{
			bgcolor = "#000000";
			fontcolor = "#ffffff";
		}
		else if (first.operation.isEntryPoint(first))
		{
			bgcolor = "#ffd394";
			fontcolor = "#000000";
		}
		else
		{
			bgcolor = "#c1f0f6";
			fontcolor = "#000000";
		}
		// The selection of Helvetica as the font is important. Some
		// renderers, like Viz.js, only seem to fully support a small number
		// of standard, widely available fonts:
		//
		// https://github.com/mdaines/viz.js/issues/82
		//
		// In particular, Courier, Arial, Helvetica, and Times are supported.
		builder.append(String.format(
			"<tr>"
				+ "<td align=\"left\" balign=\"left\" border=\"1\" "
				+ "bgcolor=\"%s\">"
					+ "<font face=\"Helvetica\" color=\"%s\"><b><i>"
						+ "%s</i></b></font>"
				+ "</td>"
			+ "</tr>",
			bgcolor,
			fontcolor,
			escape(basicBlock.name())));
		for (final L2Instruction instruction : basicBlock.instructions())
		{
			builder.append(String.format(
				"<tr><td align=\"left\" balign=\"left\" border=\"1\" "
				+ "port=\"%d\" valign=\"top\">",
				instruction.offset()));
			builder.append(instruction(instruction));
			builder.append("</td></tr>");
		}
		builder.append("</table>");
		try
		{
			writer.node(
				basicBlockName(basicBlock),
				attr -> attr.attribute("label", builder.toString()));
		}
		catch (final IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Emit all incoming control edges for the specified {@link L2BasicBlock}.
	 *
	 * @param targetBlock
	 *        A {@code L2BasicBlock}.
	 * @param writer
	 *        The {@link GraphWriter} for emission.
	 */
	private void edges (
		final L2BasicBlock targetBlock,
		final GraphWriter writer)
	{
		for (final L2PcOperand edge : targetBlock.predecessorEdges())
		{
			final StringBuilder builder = new StringBuilder();
			final L2BasicBlock sourceBlock = edge.sourceBlock();
			final L2Instruction last = sourceBlock.finalInstruction();
			final L2NamedOperandType[] types = last.operation.operandTypes();
			final L2Operand[] operands = last.operands;
			int i;
			for (i = 0; i < operands.length; i++)
			{
				// Find the L2PcOperand corresponding to this edge.
				final L2Operand operand = operands[i];
				if (operand.operandType() == PC
					&& ((L2PcOperand) operand).targetBlock() == targetBlock)
				{
					break;
				}
			}
			assert i < operands.length : "didn't find the control edge";
			final L2NamedOperandType type = types[i];
			// The selection of Helvetica as the font is important. Some
			// renderers, like Viz.js, only seem to fully support a small number
			// of standard, widely available fonts:
			//
			// https://github.com/mdaines/viz.js/issues/82
			//
			// In particular, Courier, Arial, Helvetica, and Times are
			// supported.
			builder.append(
				"<table border=\"0\" cellspacing=\"0\">"
					+ "<tr><td balign=\"left\">"
						+ "<font face=\"Helvetica\"><b>");
			builder.append(type.name());
			builder.append("</b></font><br/>");
			if (!edge.alwaysLiveInRegisters.isEmpty())
			{
				builder.append(
					"<font face=\"Helvetica\"><i>always live-in:</i></font>"
					+ "<br/><b>&nbsp;&nbsp;&nbsp;&nbsp;");
				edge.alwaysLiveInRegisters.stream()
					.sorted(Comparator.comparingInt(L2Register::finalIndex))
					.forEach(
						r -> builder.append(escape(r.toString())).append(", "));
				builder.setLength(builder.length() - 2);
				builder.append("</b><br/>");
			}
			final Set<L2Register> notAlwaysLiveInRegisters =
				new HashSet<>(edge.sometimesLiveInRegisters);
			notAlwaysLiveInRegisters.removeAll(edge.alwaysLiveInRegisters);
			if (!notAlwaysLiveInRegisters.isEmpty())
			{
				builder.append(
					"<font face=\"Helvetica\"><i>sometimes live-in:</i></font>"
					+ "<br/><b>&nbsp;&nbsp;&nbsp;&nbsp;");
				notAlwaysLiveInRegisters.stream()
					.sorted(Comparator.comparingInt(L2Register::finalIndex))
					.forEach(
						r -> builder.append(escape(r.toString())).append(", "));
				builder.setLength(builder.length() - 2);
				builder.append("</b><br/>");
			}
			builder.append("</td></tr></table>");
			try
			{
				writer.edge(
					node(
						basicBlockName(sourceBlock),
						Integer.toString(
							sourceBlock.finalInstruction().offset())),
					node(
						basicBlockName(targetBlock),
						Integer.toString(
							targetBlock.offset())),
					attr ->
					{
						final @Nullable Purpose purpose = type.purpose();
						assert purpose != null;
						switch (purpose)
						{
							case SUCCESS:
								// Nothing. The default styling will be fine.
								break;
							case FAILURE:
								attr.attribute("color", "#e54545");
								break;
							case OFF_RAMP:
								attr.attribute("style", "dashed");
								break;
							case ON_RAMP:
								attr.attribute("style", "dashed");
								attr.attribute("color", "#6aaf6a");
								break;
						}
						attr.attribute("label", builder.toString());
					});
			}
			catch (final IOException e)
			{
				throw new UncheckedIOException(e);
			}
		}
	}

	/**
	 * Visualize the {@link L2ControlFlowGraph} by {@linkplain DotWriter
	 * writing} an appropriate {@code dot} source file to the {@linkplain
	 * #accumulator}.
	 */
	public void visualize ()
	{
		final DotWriter writer = new DotWriter(
			name, true, charactersPerLine, accumulator);
		try
		{
			banner(writer);
			// The selection of Courier as the font is important. Some
			// renderers, like Viz.js, only seem to fully support a small number
			// of standard, widely available fonts:
			//
			// https://github.com/mdaines/viz.js/issues/82
			//
			// In particular, Courier, Arial, Helvetica, and Times are
			// supported.
			writer.graph(graph ->
			{
				graph.attribute("rankdir", "LR");
				graph.attribute("newrank", "true");
				graph.attribute("overlap", "false");
				graph.attribute("splines", "true");
				graph.defaultAttributeBlock(NODE, attr ->
				{
					attr.attribute("fixedsize", "false");
					attr.attribute("fontname", "Courier");
					attr.attribute("fontsize", "8");
					attr.attribute("fontcolor", "#000000");
					attr.attribute("shape", "none");
				});
				graph.defaultAttributeBlock(EDGE, attr ->
				{
					attr.attribute("fontname", "Courier");
					attr.attribute("fontsize", "6");
					attr.attribute("fontcolor", "#000000");
					attr.attribute("style", "solid");
					attr.attribute("color", "#000000");
				});
				controlFlowGraph.basicBlockOrder.forEach(
					basicBlock -> basicBlock(basicBlock, graph));
				controlFlowGraph.basicBlockOrder.forEach(
					basicBlock -> edges(basicBlock, graph));
			});
		}
		catch (final IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}
}
