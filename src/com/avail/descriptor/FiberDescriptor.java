/**
 * FiberDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.FiberDescriptor.IntegerSlots.*;
import static com.avail.descriptor.FiberDescriptor.ObjectSlots.*;
import static com.avail.descriptor.FiberDescriptor.ExecutionState.*;
import static com.avail.descriptor.FiberDescriptor.InterruptRequestFlag.*;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import com.avail.*;
import com.avail.annotations.*;
import com.avail.interpreter.*;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.io.TextInterface;
import com.avail.utility.Generator;
import com.avail.utility.evaluation.*;
import com.avail.utility.json.JSONWriter;

/**
 * An Avail {@linkplain FiberDescriptor fiber} represents an independently
 * schedulable flow of control. Its primary feature is a continuation which is
 * repeatedly replaced with continuations representing successively more
 * advanced states, thereby effecting execution.
 *
 * <p>Fibers are effectively scheduled via the {@link AvailRuntime}'s
 * {@linkplain AvailRuntime#execute(AvailTask) executor}, which is a {@link
 * ThreadPoolExecutor}. A fiber scheduled in this way runs until it acknowledges
 * being interrupted for some reason or it completes its calculation.  If it is
 * interrupted, the {@link L2Chunk} machinery ensures the fiber first reaches a
 * state representing a consistent level one {@linkplain ContinuationDescriptor
 * continuation} before giving up its time-slice.</p>
 *
 * <p>This fiber pooling model allows a huge number of fibers to efficiently
 * and automatically take advantage the available CPUs and processing cores,
 * leading to a qualitatively different concurrency model than ones which are
 * mapped directly to operating system threads, such as Java, or extreme
 * lightweight models that cannot support simultaneous execution, such as
 * Smalltalk (e.g., VisualWorks).  Clearly, the latter does not scale to a
 * modern (2013) computing environment, and the former leaves one at the mercy
 * of the severe limitations and costs imposed by operating systems.</p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class FiberDescriptor
extends Descriptor
{
	/**
	 * Debug flag for tracing some mysterious fiber problems (MvG 2013.06.30).
	 */
	public static boolean debugFibers = false;

	/** A simple counter for identifying fibers by creation order. */
	public static AtomicInteger uniqueDebugCounter = new AtomicInteger(0);

	/** The priority of module tracing tasks. */
	public static final int tracerPriority = 50;

	/** The priority of compilation tasks. */
	public static final int compilerPriority = 50;

	/** The priority of loading tasks. */
	public static final int loaderPriority = 50;

	/** The priority of stringifying objects. */
	public static final int stringificationPriority = 50;

	/** The priority of command execution tasks. */
	public static final int commandPriority = 50;

	/**
	 * The advisory interrupt request flags. The flags declared as enumeration
	 * values within this {@code enum} are the interrupt request flags.
	 */
	public static enum InterruptRequestFlag
	{
		/**
		 * Termination of the target fiber has been requested.
		 */
		TERMINATION_REQUESTED (_TERMINATION_REQUESTED),

		/**
		 * Another fiber wants to know what this fiber's reified continuation
		 * is.
		 */
		REIFICATION_REQUESTED (_REIFICATION_REQUESTED);

		/** The {@linkplain BitField bit field}. */
		final BitField bitField;

		/**
		 * Construct a new {@link InterruptRequestFlag}.
		 *
		 * @param bitField
		 */
		private InterruptRequestFlag (final BitField bitField)
		{
			this.bitField = bitField;
		}
	}

	/**
	 * The synchronization flags. The flags declared as enumeration values
	 * within this {@code enum} are for synchronization-related conditions.
	 */
	public static enum SynchronizationFlag
	{
		/**
		 * The fiber is bound to an {@linkplain Interpreter interpreter}.
		 */
		BOUND (_BOUND),

		/**
		 * The fiber has been scheduled for resumption.
		 */
		SCHEDULED (_SCHEDULED),

		/**
		 * The parking permit is unavailable.
		 */
		PERMIT_UNAVAILABLE (_PERMIT_UNAVAILABLE);

		/** The {@linkplain BitField bit field}. */
		final BitField bitField;

		/**
		 * Construct a new {@link SynchronizationFlag}.
		 *
		 * @param bitField
		 */
		private SynchronizationFlag (final BitField bitField)
		{
			this.bitField = bitField;
		}
	}

	/**
	 * The trace flags. The flags declared as enumeration values within this
	 * {@code enum} are for system tracing modes.
	 */
	public static enum TraceFlag
	{
		/**
		 * Should the {@linkplain Interpreter interpreter} record which
		 * {@linkplain VariableDescriptor variables} are read before written
		 * while running this {@linkplain FiberDescriptor fiber}?
		 */
		TRACE_VARIABLE_READS_BEFORE_WRITES
			(_TRACE_VARIABLE_READS_BEFORE_WRITES),

		/**
		 * Should the {@linkplain Interpreter interpreter} record which
		 * {@linkplain VariableDescriptor variables} are written while running
		 * this {@linkplain FiberDescriptor fiber}?
		 */
		TRACE_VARIABLE_WRITES (_TRACE_VARIABLE_WRITES);

		/** The {@linkplain BitField bit field}. */
		final BitField bitField;

		/**
		 * Construct a new {@link TraceFlag}.
		 *
		 * @param bitField
		 */
		private TraceFlag (final BitField bitField)
		{
			this.bitField = bitField;
		}
	}

	/**
	 * The general flags. These are flags that are not otherwise grouped for
	 * semantic purposes, such as indicating {@linkplain InterruptRequestFlag
	 * interrupts requests} or {@linkplain SynchronizationFlag synchronization
	 * conditions}.
	 */
	public static enum GeneralFlag
	{
		/**
		 * Was the fiber started to apply a semantic restriction?
		 */
		CAN_REJECT_PARSE (_CAN_REJECT_PARSE);

		/** The {@linkplain BitField bit field}. */
		final transient BitField bitField;

		/**
		 * Construct a new {@link GeneralFlag}.
		 *
		 * @param bitField
		 */
		private GeneralFlag (final BitField bitField)
		{
			this.bitField = bitField;
		}
	}

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/** {@link BitField}s containing the hash and the debug unique id. */
		@HideFieldInDebugger
		HASH_AND_UNIQUE_ID,

		/** {@link BitField}s for the current priority and various flags. */
		PRIORITY_AND_FLAGS,

		/**
		 * The {@linkplain ExecutionState execution state} of the fiber,
		 * indicating whether the fiber is {@linkplain ExecutionState#RUNNING
		 * running}, {@linkplain ExecutionState#SUSPENDED suspended} or
		 * {@linkplain ExecutionState#TERMINATED terminated}.
		 */
		@EnumField(describedBy=ExecutionState.class)
		EXECUTION_STATE;

		/**
		 * The hash of this fiber, which is chosen randomly on the first demand.
		 */
		static final BitField HASH_OR_ZERO =
			bitField(HASH_AND_UNIQUE_ID, 0, 32);

		/**
		 * A unique value generated by a 32-bit monotonically increasing
		 * {@linkplain #uniqueDebugCounter counter}.
		 */
		public static final BitField DEBUG_UNIQUE_ID =
			bitField(HASH_AND_UNIQUE_ID, 32, 32);

		/**
		 * The priority of this fiber, where processes with larger values get
		 * at least as much opportunity to run as processes with lower values.
		 */
		static final BitField PRIORITY =
			bitField(PRIORITY_AND_FLAGS, 0, 32);

		/** See {@link InterruptRequestFlag#TERMINATION_REQUESTED}. */
		static final BitField _TERMINATION_REQUESTED = bitField(
			PRIORITY_AND_FLAGS, 32, 1);

		/** See {@link InterruptRequestFlag#REIFICATION_REQUESTED}. */
		static final BitField _REIFICATION_REQUESTED = bitField(
			PRIORITY_AND_FLAGS, 33, 1);

		/** See {@link SynchronizationFlag#BOUND}. */
		static final BitField _BOUND = bitField(
			PRIORITY_AND_FLAGS, 34, 1);

		/** See {@link SynchronizationFlag#SCHEDULED}. */
		static final BitField _SCHEDULED = bitField(
			PRIORITY_AND_FLAGS, 35, 1);

		/** See {@link SynchronizationFlag#PERMIT_UNAVAILABLE}. */
		static final BitField _PERMIT_UNAVAILABLE = bitField(
			PRIORITY_AND_FLAGS, 36, 1);

		/** See {@link TraceFlag#TRACE_VARIABLE_READS_BEFORE_WRITES}. */
		static final BitField _TRACE_VARIABLE_READS_BEFORE_WRITES = bitField(
			PRIORITY_AND_FLAGS, 37, 1);

		/** See {@link TraceFlag#TRACE_VARIABLE_WRITES}. */
		static final BitField _TRACE_VARIABLE_WRITES = bitField(
			PRIORITY_AND_FLAGS, 38, 1);

		/** See {@link GeneralFlag#CAN_REJECT_PARSE}. */
		static final BitField _CAN_REJECT_PARSE = bitField(
			PRIORITY_AND_FLAGS, 39, 1);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The current {@linkplain ContinuationDescriptor state of execution} of
		 * the fiber.
		 */
		CONTINUATION,

		/**
		 * The result type of this {@linkplain FiberDescriptor fiber}'s
		 * {@linkplain FiberTypeDescriptor type}.
		 */
		RESULT_TYPE,

		/**
		 * A map from {@linkplain AtomDescriptor atoms} to values. Each fiber
		 * has its own unique such map, which allows processes to record
		 * fiber-specific values. The atom identities ensure modularity and
		 * non-interference of these keys.
		 */
		FIBER_GLOBALS,

		/**
		 * A map from {@linkplain AtomDescriptor atoms} to heritable values.
		 * When a fiber forks a new fiber, the new fiber inherits this map. The
		 * atom identities ensure modularity and non-interference of these keys.
		 */
		HERITABLE_FIBER_GLOBALS,

		/**
		 * The result of running this {@linkplain FiberDescriptor fiber} to
		 * completion.
		 */
		RESULT,

		/**
		 * Not yet implemented. This will be a block that should be invoked
		 * after the fiber executes each nybblecode. Using {@linkplain
		 * NilDescriptor nil} here means run without this special
		 * single-stepping mode enabled.
		 */
		BREAKPOINT_BLOCK,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} wrapping an {@linkplain
		 * AvailLoader Avail loader}. This pertains only to load-time fibers,
		 * and indicates which loader originated the fiber.
		 */
		LOADER,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} wrapping the {@linkplain
		 * Continuation1 continuation} that should be called with the
		 * {@linkplain AvailObject result} of executing the fiber to its
		 * natural conclusion.
		 */
		RESULT_CONTINUATION,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} wrapping the {@linkplain
		 * Continuation1 continuation} that should be called with the
		 * {@linkplain Throwable throwable} responsible for the untimely death
		 * of the fiber.
		 */
		FAILURE_CONTINUATION,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain FiberDescriptor
		 * fibers} waiting to join the current fiber.
		 */
		JOINING_FIBERS,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} wrapping the {@linkplain
		 * TimerTask timer task} responsible for waking up the {@linkplain
		 * ExecutionState#ASLEEP sleeping} {@linkplain FiberDescriptor fiber}.
		 */
		WAKEUP_TASK,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} wrapping a {@linkplain
		 * WeakHashMap weak map} from {@linkplain VariableDescriptor variables}
		 * encountered during a {@linkplain
		 * TraceFlag#TRACE_VARIABLE_READS_BEFORE_WRITES variable access trace}
		 * to a {@linkplain Boolean boolean} that is {@code true} iff the
		 * variable was read before it was written.
		 */
		TRACED_VARIABLES,

		/**
		 * A {@linkplain SetDescriptor set} of {@linkplain RawPojoDescriptor raw
		 * pojos}, each of which wraps a {@link Continuation1} indicating what
		 * to do with the fiber's reified {@linkplain #CONTINUATION} when the
		 * fiber next reaches a suitable safe point.
		 *
		 * <p>The non-emptiness of this set must agree with the value of the
		 * {@link InterruptRequestFlag#REIFICATION_REQUESTED} flag.
		 */
		REIFICATION_WAITERS,

		/**
		 * A {@linkplain RawPojoDescriptor raw pojo} wrapping a {@linkplain
		 * TextInterface text interface}.
		 */
		TEXT_INTERFACE,

		/**
		 * A {@link RawPojoDescriptor raw pojo} holding a {@link Generator} of
		 * {@link A_String}.  The generator should avoid execution of Avail
		 * code, as that could easily lead to deadlocks.
		 */
		NAME_GENERATOR,

		/**
		 * The name of this fiber.  It's either an Avail {@linkplain A_String
		 * string} or {@code nil}.  If nil, asking for the name should cause the
		 * {@link #NAME_GENERATOR} to run, and the resulting string to be cached
		 * here.
		 */
		NAME_OR_NIL,

		/**
		 * The in-memory debug log for this fiber.  This reduces contention
		 * between fibers versus a global log.  The log is merely a {@link
		 * RawPojoDescriptor raw pojo} holding a StringBuilder.  We don't even
		 * bother making it circular, since fiber generally don't usually run
		 * for very long in Avail.
		 *
		 * <p>TODO [MvG] Remove when the succeed-twice bug is gone.</p>
		 */
		@HideFieldJustForPrinting
		DEBUG_LOG;
	}

	/**
	 * These are the possible execution states of a {@linkplain FiberDescriptor
	 * fiber}.
	 */
	public enum ExecutionState
	implements IntegerEnumSlotDescriptionEnum
	{
		/**
		 * The fiber has not been started.
		 */
		UNSTARTED
		{
			@Override
			public boolean indicatesSuspension ()
			{
				return true;
			}

			@Override
			protected Set<ExecutionState> privateSuccessors ()
			{
				return EnumSet.of(RUNNING);
			}
		},

		/**
		 * The fiber is running or waiting for another fiber to yield.
		 */
		RUNNING
		{
			@Override
			protected Set<ExecutionState> privateSuccessors ()
			{
				return EnumSet.of(
					SUSPENDED,
					INTERRUPTED,
					PARKED,
					TERMINATED,
					ABORTED);
			}
		},

		/**
		 * The fiber has been suspended.
		 */
		SUSPENDED
		{
			@Override
			public boolean indicatesSuspension ()
			{
				return true;
			}

			@Override
			protected Set<ExecutionState> privateSuccessors ()
			{
				return EnumSet.of(
					RUNNING,
					ABORTED,
					ASLEEP);
			}
		},

		/**
		 * The fiber has been interrupted.
		 */
		INTERRUPTED
		{
			@Override
			public boolean indicatesSuspension ()
			{
				return true;
			}

			@Override
			protected Set<ExecutionState> privateSuccessors ()
			{
				return EnumSet.of(RUNNING);
			}
		},

		/**
		 * The fiber has been parked.
		 */
		PARKED
		{
			@Override
			public boolean indicatesSuspension ()
			{
				return true;
			}

			@Override
			public boolean indicatesVoluntarySuspension ()
			{
				return true;
			}

			@Override
			protected Set<ExecutionState> privateSuccessors ()
			{
				return EnumSet.of(SUSPENDED);
			}
		},

		/**
		 * The fiber is asleep.
		 */
		ASLEEP
		{
			@Override
			public boolean indicatesSuspension ()
			{
				return true;
			}

			@Override
			public boolean indicatesVoluntarySuspension ()
			{
				return true;
			}

			@Override
			protected Set<ExecutionState> privateSuccessors ()
			{
				return EnumSet.of(SUSPENDED);
			}
		},

		/**
		 * The fiber has terminated successfully.
		 */
		TERMINATED
		{
			@Override
			public boolean indicatesTermination ()
			{
				return true;
			}

			@Override
			protected Set<ExecutionState> privateSuccessors ()
			{
				return EnumSet.of(ABORTED, RETIRED);
			}
		},

		/**
		 * The fiber has aborted (due to an exception).
		 */
		ABORTED
		{
			@Override
			public boolean indicatesTermination ()
			{
				return true;
			}

			@Override
			protected Set<ExecutionState> privateSuccessors ()
			{
				return EnumSet.of(RETIRED);
			}
		},

		/**
		 * The fiber has run either its {@linkplain
		 * AvailObject#resultContinuation() result} or {@linkplain
		 * AvailObject#failureContinuation() failure continuation}. This state
		 * is permanent.
		 */
		RETIRED
		{
			@Override
			public boolean indicatesTermination ()
			{
				return true;
			}
		};

		/** An array of all {@link ExecutionState} enumeration values. */
		private static ExecutionState[] all = values();

		/**
		 * Answer an array of all {@link ExecutionState} enumeration values.
		 *
		 * @return An array of all {@link ExecutionState} enum values.  Do not
		 *         modify the array.
		 */
		static ExecutionState[] all ()
		{
			return all;
		}

		/** The valid successor {@linkplain ExecutionState states}. */
		protected @Nullable Set<ExecutionState> successors = null;

		/**
		 * Determine if this is a valid successor state.
		 *
		 * @param newState The proposed successor state.
		 * @return Whether the transition is permitted.
		 */
		boolean mayTransitionTo (final ExecutionState newState)
		{
			Set<ExecutionState> allowed = successors;
			if (allowed == null)
			{
				allowed = privateSuccessors();
				successors = allowed;
			}
			return allowed.contains(newState);
		}

		/**
		 * Answer my legal successor {@linkplain ExecutionState states}.  None
		 * by default.
		 *
		 * @return A {@link Set} of {@link ExecutionState}s.
		 */
		protected Set<ExecutionState> privateSuccessors ()
		{
			return Collections.emptySet();
		}

		/**
		 * Does this {@linkplain ExecutionState execution state} indicate that
		 * a {@linkplain FiberDescriptor fiber} is suspended for some reason?
		 *
		 * @return {@code true} if the execution state represents suspension,
		 *         {@code false} otherwise.
		 */
		public boolean indicatesSuspension ()
		{
			return false;
		}

		/**
		 * Does this {@linkplain ExecutionState execution state} indicate that
		 * a {@linkplain FiberDescriptor fiber} suspended itself voluntarily
		 * for some reason?
		 *
		 * @return {@code true} if the execution state represents voluntary
		 *         suspension, {@code false} otherwise.
		 */
		public boolean indicatesVoluntarySuspension ()
		{
			return false;
		}

		/**
		 * Does this {@linkplain ExecutionState execution state} indicate that
		 * a {@linkplain FiberDescriptor fiber} has terminated for some reason?
		 *
		 * @return {@code true} if the execution state represents termination,
		 *         {@code false} otherwise.
		 */
		public boolean indicatesTermination ()
		{
			return false;
		}
	}

	/**
	 * Log concurrency-related information about a specific fiber or null.
	 *
	 * @param fiber The fiber that is affected, or null.
	 * @param level The logging level.
	 * @param format The message string.
	 * @param parameters The parameters with which to fill the message string.
	 */
	public static void log (
		final @Nullable A_Fiber fiber,
		final Level level,
		final String format,
		final Object... parameters)
	{
		Interpreter.log(
			fiber,
			level,
			format,
			parameters);
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		// Allow mutable access to all fiber slots.
		return true;
	}

	@Override @AvailMethod
	ExecutionState o_ExecutionState (final AvailObject object)
	{
		return ExecutionState.all()[(int)object.mutableSlot(EXECUTION_STATE)];
	}

	@Override @AvailMethod
	void o_ExecutionState (final AvailObject object, final ExecutionState value)
	{
		synchronized (object)
		{
			final ExecutionState current = ExecutionState.all()
				[(int)object.mutableSlot(EXECUTION_STATE)];
			assert current.mayTransitionTo(value);
			object.setSlot(EXECUTION_STATE, value.ordinal());
			if (debugFibers)
			{
				log(object, "ExecState: %s -> %s (protected/shared)",
					current, value);
			}
		}
	}


	/**
	 * Log concurrency-related information about a specific fiber or null.  The
	 * text is written to an in-memory StringBuilder that is truncated on the
	 * left when it grows too large.
	 *
	 * @param fiber The fiber that is affected, or null.
	 * @param pattern The pattern to log.
	 * @param arguments The values with which to populate the pattern.
	 */
	public static void log (
		final AvailObject fiber,
		final String pattern,
		final Object... arguments)
	{
		final StringBuilder builder =
			(StringBuilder)fiber.slot(DEBUG_LOG).javaObjectNotNull();
		synchronized (builder)
		{
			if (builder.length() > 1_000_000)
			{
				builder.replace(0, 800_000, "(TRUNCATED)...\n");
			}
			final Formatter formatter = new Formatter(builder);
			final Interpreter interpreter = Interpreter.currentOrNull();
			if (interpreter == null)
			{
				formatter.format(
					"NO INTERPRETER [Thread = %s]: ",
					Thread.currentThread());
			}
			else
			{
				formatter.format(
					"%2d: ",
					Interpreter.current().interpreterIndex);
			}
			formatter.format(pattern, arguments);
			builder.append("\n");
			formatter.close();
		}
	}

	@Override @AvailMethod
	int o_Priority (final AvailObject object)
	{
		return object.mutableSlot(PRIORITY);
	}

	@Override @AvailMethod
	void o_Priority (final AvailObject object, final int value)
	{
		object.setMutableSlot(PRIORITY, value);
	}

	@Override @AvailMethod
	boolean o_InterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		synchronized (object)
		{
			return object.slot(flag.bitField) == 1;
		}
	}

	@Override @AvailMethod
	void o_SetInterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		synchronized (object)
		{
			object.setSlot(flag.bitField, 1);
		}
	}

	@Override @AvailMethod
	boolean o_GetAndClearInterruptRequestFlag (
		final AvailObject object,
		final InterruptRequestFlag flag)
	{
		synchronized (object)
		{
			final int value = object.slot(flag.bitField);
			object.setSlot(flag.bitField, 0);
			return value == 1;
		}
	}

	@Override @AvailMethod
	boolean o_GetAndSetSynchronizationFlag (
		final AvailObject object,
		final SynchronizationFlag flag,
		final boolean newValue)
	{
		final int value;
		final int newBit = newValue ? 1 : 0;
		synchronized (object)
		{
			value = object.slot(flag.bitField);
			object.setSlot(flag.bitField, newBit);
		}
		if (debugFibers)
		{
			log(object, "Get & set synch flag: %s (%s -> %s)",
				flag, value, newBit);
		}
		return value == 1;
	}

	@Override @AvailMethod
	boolean o_GeneralFlag (final AvailObject object, final GeneralFlag flag)
	{
		final int value;
		synchronized (object)
		{
			value = object.slot(flag.bitField);
		}
		if (debugFibers)
		{
			log(object, "GET general flag: %s=%d", flag, value);
		}
		return value == 1;
	}

	@Override @AvailMethod
	void o_SetGeneralFlag (
		final AvailObject object,
		final GeneralFlag flag)
	{
		synchronized (object)
		{
			object.setSlot(flag.bitField, 1);
		}
		if (debugFibers)
		{
			log(object, "Set general flag: %s", flag);
		}
	}

	@Override @AvailMethod
	void o_ClearGeneralFlag (
		final AvailObject object,
		final GeneralFlag flag)
	{
		synchronized (object)
		{
			object.setSlot(flag.bitField, 0);
		}
		if (debugFibers)
		{
			log(object, "Clear general flag: %s", flag);
		}
	}

	@Override @AvailMethod
	boolean o_TraceFlag (final AvailObject object, final TraceFlag flag)
	{
		synchronized (object)
		{
			return object.slot(flag.bitField) == 1;
		}
	}

	@Override @AvailMethod
	void o_SetTraceFlag (
		final AvailObject object,
		final TraceFlag flag)
	{
		synchronized (object)
		{
			object.setSlot(flag.bitField, 1);
		}
		if (debugFibers)
		{
			log(object, "Set trace flag %s", flag);
		}
	}

	@Override @AvailMethod
	void o_ClearTraceFlag (
		final AvailObject object,
		final TraceFlag flag)
	{
		synchronized (object)
		{
			object.setSlot(flag.bitField, 0);
		}
		if (debugFibers)
		{
			log(object, "Clear trace flag: %s", flag);
		}
	}

	@Override @AvailMethod
	A_Continuation o_Continuation (final AvailObject object)
	{
		return object.mutableSlot(CONTINUATION);
	}

	@Override @AvailMethod
	void o_Continuation (final AvailObject object, final A_Continuation value)
	{
		// Use a special setter mechanism that allows the continuation to be
		// non-shared, even if the fiber it's to be plugged into is shared.
		object.setContinuationSlotOfFiber(CONTINUATION, value);
	}

	@Override @AvailMethod
	A_String o_FiberName (final AvailObject object)
	{
		A_String name = object.slot(NAME_OR_NIL);
		if (name.equalsNil())
		{
			// Compute it from the generator.
			final AvailObject pojo = object.mutableSlot(NAME_GENERATOR);
			@SuppressWarnings("unchecked")
			final Generator<A_String> generator =
				(Generator<A_String>) pojo.javaObjectNotNull();
			name = generator.value();
			// Save it for next time.
			object.setMutableSlot(NAME_OR_NIL, name);
		}
		return name;
	}

	@Override @AvailMethod
	void o_FiberNameGenerator (
		final AvailObject object,
		final Generator<A_String> generator)
	{
		object.setMutableSlot(
			NAME_GENERATOR,
			RawPojoDescriptor.identityWrap(generator));
		// And clear the cached name.
		object.setMutableSlot(NAME_OR_NIL, NilDescriptor.nil());
	}

	@Override @AvailMethod
	AvailObject o_FiberGlobals (final AvailObject object)
	{
		return object.mutableSlot(FIBER_GLOBALS);
	}

	@Override @AvailMethod
	void o_FiberGlobals (final AvailObject object, final A_Map globals)
	{
		object.setMutableSlot(FIBER_GLOBALS, globals);
	}

	@Override @AvailMethod
	AvailObject o_FiberResult (final AvailObject object)
	{
		return object.mutableSlot(RESULT);
	}

	@Override @AvailMethod
	void o_FiberResult (final AvailObject object, final A_BasicObject result)
	{
		object.setMutableSlot(RESULT, result);
	}

	@Override @AvailMethod
	A_Map o_HeritableFiberGlobals (final AvailObject object)
	{
		return object.mutableSlot(HERITABLE_FIBER_GLOBALS);
	}

	@Override @AvailMethod
	void o_HeritableFiberGlobals (
		final AvailObject object,
		final A_Map globals)
	{
		object.setMutableSlot(HERITABLE_FIBER_GLOBALS, globals);
	}

	@Override @AvailMethod
	AvailObject o_BreakpointBlock (final AvailObject object)
	{
		return object.mutableSlot(BREAKPOINT_BLOCK);
	}

	@Override @AvailMethod
	void o_BreakpointBlock (final AvailObject object, final AvailObject value)
	{
		object.setMutableSlot(BREAKPOINT_BLOCK, value);
	}

	@Override @AvailMethod
	@Nullable AvailLoader o_AvailLoader (final AvailObject object)
	{
		final AvailObject pojo = object.mutableSlot(LOADER);
		if (!pojo.equalsNil())
		{
			return (AvailLoader) pojo.javaObject();
		}
		return null;
	}

	@Override @AvailMethod
	void o_AvailLoader (
		final AvailObject object,
		final @Nullable AvailLoader loader)
	{
		object.setMutableSlot(
			LOADER,
			loader == null
			? NilDescriptor.nil()
			: RawPojoDescriptor.identityWrap(loader));
	}

	/**
	 * The default result continuation, answered when a {@linkplain
	 * FiberDescriptor fiber}'s result continuation is {@linkplain
	 * NilDescriptor nil}.
	 */
	private static final A_BasicObject defaultResultContinuation =
		RawPojoDescriptor.identityWrap(new Continuation1<AvailObject>()
		{
			@Override
			public void value (final @Nullable AvailObject ignored)
			{
				// Do nothing.
			}
		});

	@SuppressWarnings("unchecked")
	@Override @AvailMethod
	Continuation1<AvailObject> o_ResultContinuation (
		final AvailObject object)
	{
		final AvailObject pojo;
		synchronized (object)
		{
			pojo = object.slot(RESULT_CONTINUATION);
			assert !pojo.equalsNil() : "Fiber attempting to succeed twice!";
			object.setSlot(RESULT_CONTINUATION, NilDescriptor.nil());
			object.setSlot(FAILURE_CONTINUATION, NilDescriptor.nil());
		}
		if (debugFibers)
		{
			final StringBuilder b = new StringBuilder();
			for (final StackTraceElement frame :
				Thread.currentThread().getStackTrace())
			{
				b.append("\n\t");
				b.append(frame);
			}
			log(object, "Succeeded:%s", b);
		}
		return (Continuation1<AvailObject>) pojo.javaObject();
	}

	@Override @AvailMethod
	void o_ResultContinuation (
		final AvailObject object,
		final Continuation1<AvailObject> continuation)
	{
		synchronized (object)
		{
			final AvailObject oldPojo = object.slot(RESULT_CONTINUATION);
			assert oldPojo == defaultResultContinuation;
			object.setSlot(
				RESULT_CONTINUATION,
				RawPojoDescriptor.identityWrap(continuation));
		}
	}

	/**
	 * The default result continuation, answered when a {@linkplain
	 * FiberDescriptor fiber}'s result continuation is {@linkplain
	 * NilDescriptor nil}.
	 */
	private static final A_BasicObject defaultFailureContinuation =
		RawPojoDescriptor.identityWrap(new Continuation1<Throwable>()
		{
			@Override
			public void value (final @Nullable Throwable ignored)
			{
				// Do nothing; errors in fibers should be handled by Avail
				// code.
			}
		});

	@SuppressWarnings("unchecked")
	@Override @AvailMethod
	Continuation1<Throwable> o_FailureContinuation (
		final AvailObject object)
	{
		final AvailObject pojo;
		synchronized (object)
		{
			pojo = object.slot(FAILURE_CONTINUATION);
			assert !pojo.equalsNil();
			object.setSlot(FAILURE_CONTINUATION, NilDescriptor.nil());
			object.setSlot(RESULT_CONTINUATION, NilDescriptor.nil());
		}
		if (debugFibers)
		{
			final StringBuilder b = new StringBuilder();
			for (final StackTraceElement frame :
				Thread.currentThread().getStackTrace())
			{
				b.append("\n\t");
				b.append(frame);
			}
			log(object, "Failed:%s", b);
		}
		return (Continuation1<Throwable>) pojo.javaObject();
	}

	@Override @AvailMethod
	void o_FailureContinuation (
		final AvailObject object,
		final Continuation1<Throwable> continuation)
	{
		synchronized (object)
		{
			final AvailObject oldPojo = object.slot(FAILURE_CONTINUATION);
			assert oldPojo == defaultFailureContinuation;
			object.setSlot(
				FAILURE_CONTINUATION,
				RawPojoDescriptor.identityWrap(continuation));
		}
	}

	@Override @AvailMethod
	A_Set o_JoiningFibers (final AvailObject object)
	{
		return object.mutableSlot(JOINING_FIBERS);
	}

	@Override @AvailMethod
	void o_JoiningFibers (final AvailObject object, final A_Set joiners)
	{
		object.setMutableSlot(JOINING_FIBERS, joiners);
	}

	@Override @AvailMethod
	@Nullable TimerTask o_WakeupTask (final AvailObject object)
	{
		final AvailObject pojo = object.mutableSlot(WAKEUP_TASK);
		if (!pojo.equalsNil())
		{
			return (TimerTask) pojo.javaObject();
		}
		return null;
	}

	@Override @AvailMethod
	void o_WakeupTask (final AvailObject object, final @Nullable TimerTask task)
	{
		object.setMutableSlot(
			WAKEUP_TASK,
			task == null
			? NilDescriptor.nil()
			: RawPojoDescriptor.identityWrap(task));
	}

	@Override
	TextInterface o_TextInterface (final AvailObject object)
	{
		return (TextInterface) object.mutableSlot(TEXT_INTERFACE).javaObject();
	}

	@Override
	void o_TextInterface (
		final AvailObject object,
		final TextInterface textInterface)
	{
		final AvailObject pojo = RawPojoDescriptor.identityWrap(textInterface);
		object.setMutableSlot(TEXT_INTERFACE, pojo);
	}

	@Override @AvailMethod
	void o_RecordVariableAccess (
		final AvailObject object,
		final A_Variable var,
		final boolean wasRead)
	{
		assert object.mutableSlot(_TRACE_VARIABLE_READS_BEFORE_WRITES) == 1
			^ object.mutableSlot(_TRACE_VARIABLE_WRITES) == 1;
		final AvailObject rawPojo = object.slot(TRACED_VARIABLES);
		@SuppressWarnings("unchecked")
		final WeakHashMap<A_Variable, Boolean> map =
			(WeakHashMap<A_Variable, Boolean>) rawPojo.javaObjectNotNull();
		if (!map.containsKey(var))
		{
			map.put(var, wasRead);
		}
	}

	@Override @AvailMethod
	A_Set o_VariablesReadBeforeWritten (final AvailObject object)
	{
		assert object.mutableSlot(_TRACE_VARIABLE_READS_BEFORE_WRITES) != 1;
		final AvailObject rawPojo = object.slot(TRACED_VARIABLES);
		@SuppressWarnings("unchecked")
		final WeakHashMap<A_Variable, Boolean> map =
			(WeakHashMap<A_Variable, Boolean>) rawPojo.javaObjectNotNull();
		A_Set set = SetDescriptor.empty();
		for (final Map.Entry<A_Variable, Boolean> entry : map.entrySet())
		{
			if (entry.getValue())
			{
				set = set.setWithElementCanDestroy(entry.getKey(), true);
			}
		}
		map.clear();
		return set;
	}

	@Override @AvailMethod
	A_Set o_VariablesWritten (final AvailObject object)
	{
		assert object.mutableSlot(_TRACE_VARIABLE_WRITES) != 1;
		final AvailObject rawPojo = object.slot(TRACED_VARIABLES);
		@SuppressWarnings("unchecked")
		final WeakHashMap<A_Variable, Boolean> map =
			(WeakHashMap<A_Variable, Boolean>) rawPojo.javaObjectNotNull();
		A_Set set = SetDescriptor.empty();
		for (final Map.Entry<A_Variable, Boolean> entry : map.entrySet())
		{
			set = set.setWithElementCanDestroy(entry.getKey(), true);
		}
		map.clear();
		return set;
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		// Compare fibers by address (identity).
		return another.traversed().sameAddressAs(object);
	}

	/**
	 * Lazily compute and install the hash of the specified {@linkplain
	 * FiberDescriptor object}.  This should be protected by a synchronized
	 * section if there's a chance this fiber might be hashed by some other
	 * fiber.  If the fiber is not shared, this shouldn't be a problem.
	 *
	 * @param object An object.
	 * @return The hash.
	 */
	private int hash (final AvailObject object)
	{
		int hash = object.slot(HASH_OR_ZERO);
		if (hash == 0)
		{
			do
			{
				// This guarantees the uniqueness of fiber hashes (modulo 2^32),
				// but makes it play more nicely with sets (to prevent
				// clumping).
				hash = (AvailRuntime.nextFiberId() * multiplier) ^ 0x4058A781;
			}
			while (hash == 0);
			object.setSlot(HASH_OR_ZERO, hash);
		}
		return hash;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return hash(object);
			}
		}
		return hash(object);
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		return FiberTypeDescriptor.forResultType(object.slot(RESULT_TYPE));
	}

	@Override
	void o_WhenContinuationIsAvailableDo (
		final AvailObject object,
		final Continuation1<A_Continuation> whenReified)
	{
		object.lock(new Continuation0()
		{
			@Override
			public void value ()
			{
				switch (object.executionState())
				{
					case ABORTED:
					case ASLEEP:
					case INTERRUPTED:
					case PARKED:
					case RETIRED:
					case SUSPENDED:
					case TERMINATED:
					case UNSTARTED:
					{
						whenReified.value(object.continuation().makeShared());
						break;
					}
					case RUNNING:
					{
						final A_BasicObject pojo =
							RawPojoDescriptor.identityWrap(whenReified);
						final A_Set oldSet = object.slot(REIFICATION_WAITERS);
						final A_Set newSet =
							oldSet.setWithElementCanDestroy(pojo, true);
						object.setSlot(
							REIFICATION_WAITERS,
							newSet.makeShared());
						object.setInterruptRequestFlag(REIFICATION_REQUESTED);
						break;
					}
				}
			}
		});
	}

	@Override
	A_Set o_GetAndClearReificationWaiters (final AvailObject object)
	{
		final A_Set previousSet;
		synchronized (object)
		{
			previousSet = object.slot(REIFICATION_WAITERS);
			object.setSlot(REIFICATION_WAITERS, SetDescriptor.empty());
		}
		return previousSet;
	}

	@Override
	void o_RecordLatestPrimitive (
		final AvailObject object,
		final short primitiveNumber)
	{
		final Primitive p =
			Primitive.byPrimitiveNumberOrFail(primitiveNumber);
		log(object, "%s", p.name());
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("fiber");
		writer.write("fiber name");
		object.fiberName().writeTo(writer);
		writer.write("execution state");
		writer.write(object.executionState().name().toLowerCase());
		final AvailObject result = object.mutableSlot(RESULT);
		if (!result.equalsNil())
		{
			writer.write("result");
			result.writeSummaryTo(writer);
		}
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("fiber");
		writer.write("fiber name");
		object.fiberName().writeTo(writer);
		writer.write("execution state");
		writer.write(object.executionState().name().toLowerCase());
		writer.endObject();
	}

	/**
	 * The currently locked {@linkplain FiberDescriptor fiber}, or {@code null}
	 * if no fiber is currently locked. This information is used to detect
	 * deadlocks between fibers.
	 */
	private static final ThreadLocal<A_Fiber> currentlyLockedFiber =
		new ThreadLocal<>();

	/**
	 * Can the running {@linkplain Thread thread} safely lock the specified
	 * fiber without potential for deadlock?
	 *
	 * @param fiber
	 *        A fiber.
	 * @return {@code true} if the current thread can safely lock the specified
	 *         fiber, {@code false} otherwise.
	 */
	private boolean canSafelyLock (final A_Fiber fiber)
	{
		final A_Fiber lockedFiber = currentlyLockedFiber.get();
		return lockedFiber == null || lockedFiber == fiber;
	}

	@Override
	void o_Lock (final AvailObject object, final Continuation0 critical)
	{
		assert canSafelyLock(object);
		final A_Fiber lockedFiber = currentlyLockedFiber.get();
		currentlyLockedFiber.set(object);
		try
		{
			// A fiber always needs to acquire a lock, even if it's not mutable,
			// as this prevents races between two threads where one is exiting a
			// fiber and the other is resuming the same fiber.
			synchronized (object)
			{
				critical.value();
			}
		}
		finally
		{
			currentlyLockedFiber.set(lockedFiber);
		}
	}

	/**
	 * Look up the {@linkplain DeclarationNodeDescriptor declaration} with the
	 * given name in the current compiler scope.  This information is associated
	 * with the current {@link Interpreter}, and therefore the {@linkplain
	 * A_Fiber fiber} that it is executing.  If no such binding exists, answer
	 * {@code null}.  The module scope is not consulted by this mechanism.
	 *
	 * @param name
	 *        The name of the binding to look up in the current scope.
	 * @return The {@linkplain DeclarationNodeDescriptor declaration} that was
	 *         requested, or {@code null} if there is no binding in scope with
	 *         that name.
	 */
	public static @Nullable A_Phrase lookupBindingOrNull (
		final A_String name)
	{
		final A_Fiber fiber = current();
		final A_Map fiberGlobals = fiber.fiberGlobals();
		final A_Map clientData = fiberGlobals.mapAt(
			AtomDescriptor.clientDataGlobalKey());
		final A_Map bindings = clientData.mapAt(
			AtomDescriptor.compilerScopeMapKey());
		if (bindings.hasKey(name))
		{
			return bindings.mapAt(name);
		}
		return null;
	}

	/**
	 * Attempt to add the declaration to the compiler scope information within
	 * the client data stored in the current fiber.  If there is already a
	 * declaration by that name, return it; otherwise return {@code null}.
	 *
	 * @param declaration A {@link DeclarationNodeDescriptor declaration}.
	 * @return {@code Null} if successful, otherwise the existing {@link
	 *         DeclarationNodeDescriptor declaration} that was in conflict.
	 */
	public static @Nullable A_Phrase addDeclaration (
		final A_Phrase declaration)
	{
		final A_Atom clientDataGlobalKey =
			AtomDescriptor.clientDataGlobalKey();
		final A_Atom compilerScopeMapKey =
			AtomDescriptor.compilerScopeMapKey();
		final A_Fiber fiber = current();
		A_Map fiberGlobals = fiber.fiberGlobals();
		A_Map clientData = fiberGlobals.mapAt(clientDataGlobalKey);
		A_Map bindings = clientData.mapAt(compilerScopeMapKey);
		final A_String declarationName = declaration.token().string();
		assert declarationName.isString();
		if (bindings.hasKey(declarationName))
		{
			return bindings.mapAt(declarationName);
		}
		bindings = bindings.mapAtPuttingCanDestroy(
			declarationName, declaration, true);
		clientData = clientData.mapAtPuttingCanDestroy(
			compilerScopeMapKey, bindings, true);
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			clientDataGlobalKey, clientData, true);
		fiber.fiberGlobals(fiberGlobals.makeShared());
		return null;
	}

	/**
	 * Construct a new {@link FiberDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private FiberDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link FiberDescriptor}. */
	static final FiberDescriptor mutable =
		new FiberDescriptor(Mutability.MUTABLE);

	@Override
	FiberDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link FiberDescriptor}. */
	private static final FiberDescriptor immutable =
		new FiberDescriptor(Mutability.IMMUTABLE);

	@Override
	FiberDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link FiberDescriptor}. */
	private static final FiberDescriptor shared =
		new FiberDescriptor(Mutability.SHARED);

	@Override
	FiberDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Construct an {@linkplain ExecutionState#UNSTARTED unstarted} {@linkplain
	 * FiberDescriptor fiber} with the specified {@linkplain A_Type result type}
	 * and initial priority.
	 *
	 * @param resultType
	 *        The expected result type.
	 * @param priority
	 *        The initial priority.
	 * @param nameGenerator
	 *        A {@link Generator} that produces an Avail {@link A_String string}
	 *        to name this fiber on demand.  Please don't run Avail code to do
	 *        so, since if this is evaluated during fiber execution it will
	 *        cause the current {@link Thread}'s execution to block, potentially
	 *        starving the execution pool.
	 * @return The new fiber.
	 */
	public static A_Fiber newFiber (
		final A_Type resultType,
		final int priority,
		final Generator<A_String> nameGenerator)
	{
		final AvailObject fiber = FiberDescriptor.mutable.create();
		fiber.setSlot(RESULT_TYPE, resultType.makeImmutable());
		fiber.setSlot(
			NAME_GENERATOR, RawPojoDescriptor.identityWrap(nameGenerator));
		fiber.setSlot(NAME_OR_NIL, NilDescriptor.nil());
		fiber.setSlot(PRIORITY, priority);
		fiber.setSlot(CONTINUATION, NilDescriptor.nil());
		fiber.setSlot(EXECUTION_STATE, UNSTARTED.ordinal());
		fiber.setSlot(BREAKPOINT_BLOCK, NilDescriptor.nil());
		fiber.setSlot(FIBER_GLOBALS, MapDescriptor.empty());
		fiber.setSlot(HERITABLE_FIBER_GLOBALS, MapDescriptor.empty());
		fiber.setSlot(RESULT, NilDescriptor.nil());
		fiber.setSlot(LOADER, NilDescriptor.nil());
		fiber.setSlot(RESULT_CONTINUATION, defaultResultContinuation);
		fiber.setSlot(FAILURE_CONTINUATION, defaultFailureContinuation);
		fiber.setSlot(JOINING_FIBERS, SetDescriptor.empty());
		fiber.setSlot(WAKEUP_TASK, NilDescriptor.nil());
		fiber.setSlot(
			TRACED_VARIABLES,
			RawPojoDescriptor.identityWrap(
				new WeakHashMap<A_Variable, Boolean>()));
		fiber.setSlot(REIFICATION_WAITERS, SetDescriptor.empty());
		fiber.setSlot(
			TEXT_INTERFACE, AvailRuntime.current().textInterfacePojo());

		final int id = uniqueDebugCounter.incrementAndGet();
		fiber.setSlot(DEBUG_UNIQUE_ID, id);
		final AvailObject logPojo;
		if (debugFibers)
		{
			final StringBuilder builder = new StringBuilder(200);
			builder.append("new: ");
			builder.append(id);
			builder.append("\n");
			logPojo = RawPojoDescriptor.identityWrap(builder);
		}
		else
		{
			logPojo = RawPojoDescriptor.rawNullObject();
		}
		fiber.setSlot(DEBUG_LOG, logPojo);
		return fiber;
	}

	/**
	 * Construct an {@linkplain ExecutionState#UNSTARTED unstarted} {@linkplain
	 * FiberDescriptor fiber} with the specified {@linkplain A_Type result type}
	 * and {@linkplain AvailLoader Avail loader}. The priority is initially set
	 * to {@linkplain #loaderPriority}.
	 *
	 * @param resultType
	 *        The expected result type.
	 * @param loader
	 *        An Avail loader.
	 * @param nameGenerator
	 *        A {@link Generator} that produces an Avail {@link A_String string}
	 *        to name this fiber on demand.  Please don't run Avail code to do
	 *        so, since if this is evaluated during fiber execution it will
	 *        cause the current {@link Thread}'s execution to block, potentially
	 *        starving the execution pool.
	 * @return The new fiber.
	 */
	public static A_Fiber newLoaderFiber (
		final A_Type resultType,
		final AvailLoader loader,
		final Generator<A_String> nameGenerator)
	{
		final A_Module module = loader.module();
		assert module != null;
		final AvailObject fiber = FiberDescriptor.mutable.create();
		fiber.setSlot(RESULT_TYPE, resultType.makeImmutable());
		fiber.setSlot(
			NAME_GENERATOR, RawPojoDescriptor.identityWrap(nameGenerator));
		fiber.setSlot(PRIORITY, FiberDescriptor.loaderPriority);
		fiber.setSlot(CONTINUATION, NilDescriptor.nil());
		fiber.setSlot(EXECUTION_STATE, UNSTARTED.ordinal());
		fiber.setSlot(BREAKPOINT_BLOCK, NilDescriptor.nil());
		fiber.setSlot(FIBER_GLOBALS, MapDescriptor.empty());
		fiber.setSlot(HERITABLE_FIBER_GLOBALS, MapDescriptor.empty());
		fiber.setSlot(RESULT, NilDescriptor.nil());
		fiber.setSlot(LOADER, RawPojoDescriptor.identityWrap(loader));
		fiber.setSlot(RESULT_CONTINUATION, defaultResultContinuation);
		fiber.setSlot(FAILURE_CONTINUATION, defaultFailureContinuation);
		fiber.setSlot(JOINING_FIBERS, SetDescriptor.empty());
		fiber.setSlot(WAKEUP_TASK, NilDescriptor.nil());
		fiber.setSlot(
			TRACED_VARIABLES,
			RawPojoDescriptor.identityWrap(
				new WeakHashMap<A_Variable, Boolean>()));
		fiber.setSlot(REIFICATION_WAITERS, SetDescriptor.empty());
		fiber.setSlot(
			TEXT_INTERFACE, AvailRuntime.current().textInterfacePojo());
		final int id = uniqueDebugCounter.incrementAndGet();
		fiber.setSlot(DEBUG_UNIQUE_ID, id);
		final AvailObject logPojo;
		if (debugFibers)
		{
			final StringBuilder builder = new StringBuilder(200);
			builder.append("newLoader: ");
			builder.append(nameGenerator.value());
			builder.append("\n");
			logPojo = RawPojoDescriptor.identityWrap(builder);
		}
		else
		{
			logPojo = RawPojoDescriptor.rawNullObject();
		}
		fiber.setSlot(DEBUG_LOG, logPojo);
		return fiber;
	}

	/**
	 * Answer the {@linkplain FiberDescriptor fiber} currently bound to this
	 * {@link AvailThread}.
	 *
	 * @return A fiber.
	 */
	public static A_Fiber current ()
	{
		return ((AvailThread) Thread.currentThread()).interpreter.fiber();
	}

	/**
	 * Answer the {@linkplain FiberDescriptor fiber} currently bound to this
	 * {@link AvailThread}.
	 *
	 * @return A fiber, or {@code null} if no fiber is currently bound.
	 */
	public static @Nullable A_Fiber currentOrNull ()
	{
		final @Nullable Interpreter interpreter = Interpreter.currentOrNull();
		final @Nullable A_Fiber fiber = interpreter != null
			? interpreter.fiberOrNull()
			: null;
		return fiber;
	}
}