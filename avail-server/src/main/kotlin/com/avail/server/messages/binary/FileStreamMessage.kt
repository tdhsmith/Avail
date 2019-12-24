/*
 * FileStreamMessage.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.server.messages.binary

import com.avail.server.io.AvailServerChannel
import com.avail.server.io.files.FileManager
import com.avail.server.messages.Message
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * `FileStreamMessage` is a [BinaryMessage] request to open a file from the
 * [FileManager].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct an [OpenFileMessage].
 *
 * @param commandId
 *   The identifier of the [message][BinaryMessage]. This identifier should
 *   appear in any responses to this message.
 * @param fileId
 *   The [UUID] that uniquely identifies the target file in the [FileManager].
 * @param file
 *   The [ByteArray] that represents the file to send to the client.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
internal class FileStreamMessage constructor(
	override var commandId: Long,
	fileId: UUID,
	mime: String,
	file: ByteArray): BinaryMessage()
{
	override val command = BinaryCommand.FILE_STREAM
	override val message: Message

	init
	{
		val mimeBytes = mime.toByteArray(StandardCharsets.UTF_8)
		// BinaryCommand.id = 4
		// commandId = 8
		// UUID = 16
		// mimeSize = 4
		// file size = 4
		val bufferSize = 36 + mimeBytes.size + file.size
		val buffer = ByteBuffer.allocate(bufferSize)
		buffer.putInt(command.id)
		buffer.putLong(commandId)
		buffer.putLong(fileId.mostSignificantBits)
		buffer.putLong(fileId.leastSignificantBits)
		buffer.putInt(mimeBytes.size)
		buffer.put(mimeBytes)
		buffer.putInt(file.size)
		buffer.put(file)
		buffer.flip()
		val content = ByteArray(bufferSize)
		buffer.get(content)
		this.message = Message(content, AvailServerChannel.ProtocolState.BINARY)
	}

	override fun processThen(channel: AvailServerChannel, continuation: () -> Unit)
	{
		channel.enqueueMessageThen(message, continuation)
	}
}