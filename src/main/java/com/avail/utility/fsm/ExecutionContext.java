/*
 * ExecutionContext.java
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

package com.avail.utility.fsm;

import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Transformer1;

import javax.annotation.Nullable;

import static com.avail.utility.Nulls.stripNull;

/**
 * A {@code ExecutionContext} represents a running {@linkplain StateMachine
 * finite state machine}. In order to execute a step, it maintains a reference
 * to the executor, the current state, and a client-provided memento which will
 * be passed as the sole argument of every {@linkplain Continuation1 action}
 * invocation.
 *
 * <p>To obtain a new execution context, a client should call
 * {@link StateMachine#createExecutionContext(Object)} on
 * an established FSM.</p>
 *
 * <p>To execute a step, a client should call {@link #handleEvent(Enum)} with an
 * appropriate event.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @param <State> The state type.
 * @param <Event> The event type.
 * @param <GuardKey> The guard key type.
 * @param <ActionKey> The action key type.
 * @param <Memento> The memento type.
 */
public final class ExecutionContext <
	State extends Enum<State>,
	Event extends Enum<Event>,
	GuardKey extends Enum<GuardKey>,
	ActionKey extends Enum<ActionKey>,
	Memento>
{
	/** The {@linkplain StateMachine state machine}. */
	private final StateMachine<State, Event, GuardKey, ActionKey, Memento>
		machine;

	/** The current state. */
	@SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
	private @Nullable State currentState;

	/**
	 * The memento to pass to each {@linkplain Continuation1 action} that runs.
	 */
	private final Memento memento;

	/**
	 * Construct a new {@code ExecutionContext}.
	 *
	 * @param stateMachine
	 *        The {@linkplain StateMachine state machine} to instantiate as an
	 *        {@code ExecutionContext}.
	 * @param memento
	 *        The object which should be passed to all {@linkplain Continuation1
	 *        actions}.
	 */
	ExecutionContext (
		final StateMachine<State, Event, GuardKey, ActionKey, Memento>
			stateMachine,
		final Memento memento)
	{
		this.machine = stateMachine;
		this.currentState = stateMachine.initialState();
		this.memento = memento;
	}

	/**
	 * Set the receiver's state without executing the <em>enter</em>
	 * {@linkplain Continuation1 action}.
	 *
	 * @param state
	 *        The new current state.
	 */
	void justSetState (final @Nullable State state)
	{
		currentState = state;
	}

	/**
	 * Answer the current state. This is only legal for a {@linkplain Thread
	 * thread} synchronized with the {@code ExecutionContext}.
	 *
	 * @return The current state.
	 */
	public @Nullable State currentState ()
	{
		assert Thread.holdsLock(this);
		return currentState;
	}

	/**
	 * Test the given {@linkplain Transformer1 guard}.
	 *
	 * @param guard
	 *        The guard to invoke, or {@code null} for the trivial always-true
	 *        guard.
	 * @return Whether the guard is satisfied.
	 */
	boolean testGuard (
		final @Nullable Transformer1<? super Memento, Boolean> guard)
	{
		if (guard == null)
		{
			return true;
		}
		return stripNull(guard.value(memento));
	}

	/**
	 * Execute the given {@linkplain Continuation1 action}.
	 *
	 * @param action
	 *        The {@linkplain Continuation1 action} to invoke, or {@code null}
	 *        if no action should be performed.
	 */
	void executeAction (
		final @Nullable Continuation1<? super Memento> action)
	{
		if (action != null)
		{
			action.value(memento);
		}
	}

	/**
	 * Handle the specified event.
	 *
	 * @param event An event.
	 * @throws InvalidContextException
	 *         If the {@code ExecutionContext} was rendered invalid by an
	 *         {@linkplain Exception exception} thrown during a previous state
	 *         transition.
	 * @throws InvalidTransitionException
	 *         If the current states does not have a transition on the
	 *         specified event.
	 */
	public synchronized void handleEvent (final Event event)
		throws InvalidContextException, InvalidTransitionException
	{
		machine.handleEvent(event, this);
	}
}
