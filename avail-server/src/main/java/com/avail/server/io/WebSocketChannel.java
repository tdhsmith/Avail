/*
 * WebSocketChannel.java
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

package com.avail.server.io;

import com.avail.annotations.InnerAccess;
import com.avail.server.messages.Message;
import com.avail.utility.IO;

import java.nio.channels.AsynchronousSocketChannel;

/**
 * A {@code WebSocketChannel} encapsulates an {@link AsynchronousSocketChannel}
 * created by a {@code WebSocketAdapter}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class WebSocketChannel
extends AbstractTransportChannel<AsynchronousSocketChannel>
{
	/**
	 * The {@link WebSocketAdapter} that created this {@linkplain
	 * WebSocketChannel channel}.
	 */
	@InnerAccess final WebSocketAdapter adapter;

	@Override
	public WebSocketAdapter adapter ()
	{
		return adapter;
	}

	/**
	 * The {@linkplain AsynchronousSocketChannel channel} used by the associated
	 * {@link WebSocketAdapter}.
	 */
	private final AsynchronousSocketChannel transport;

	@Override
	public AsynchronousSocketChannel transport ()
	{
		return transport;
	}

	@Override
	public boolean isOpen ()
	{
		return transport.isOpen();
	}

	/**
	 * Construct a new {@code WebSocketChannel}.
	 *
	 * @param adapter
	 *        The {@link WebSocketAdapter}.
	 * @param transport
	 *        The {@linkplain AsynchronousSocketChannel channel}.
	 */
	WebSocketChannel (
		final WebSocketAdapter adapter,
		final AsynchronousSocketChannel transport)
	{
		this.adapter = adapter;
		this.transport = transport;
	}

	/**
	 * The maximum number of {@linkplain Message messages} permitted on the
	 * {@linkplain #sendQueue queue}.
	 */
	private static final int MAX_QUEUE_DEPTH = 10;

	@Override
	protected int maximumSendQueueDepth ()
	{
		return MAX_QUEUE_DEPTH;
	}

	@Override
	protected int maximumReceiveQueueDepth ()
	{
		return MAX_QUEUE_DEPTH;
	}

	/**
	 * {@code true} if the WebSocket handshake succeeded, {@code false}
	 * otherwise.
	 */
	private boolean handshakeSucceeded;

	/**
	 * Record the fact that the WebSocket handshake succeeded.
	 */
	public void handshakeSucceeded ()
	{
		handshakeSucceeded = true;
	}

	@Override
	public void close ()
	{
		if (handshakeSucceeded)
		{
			synchronized (sendQueue)
			{
				if (!sendQueue.isEmpty())
				{
					closeAfterEmptyingSendQueue();
				}
				else
				{
					adapter.sendClose(this);
				}
			}
		}
		else
		{
			IO.close(transport);
		}
	}
}
