package com.fsck.k9.mail.store.imap

import java.io.OutputStream
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal open class TestImapConnection(val timeout: Long, override val connectionGeneration: Int = 1) : ImapConnection {
    override val logId: String = "testConnection"
    override var isConnected: Boolean = false
        protected set
    override val outputStream: OutputStream
        get() = TODO("Not yet implemented")
    override val isUidPlusCapable: Boolean = true
    override val isUtf8AcceptCapable: Boolean = false
    override var isIdleCapable: Boolean = true
        protected set

    val defaultSocketReadTimeout = 30 * 1000
    var currentSocketReadTimeout = defaultSocketReadTimeout
        protected set

    @Volatile
    private var tag: Int = 0

    private val receivedCommands = LinkedBlockingDeque<String>()
    private val responses = LinkedBlockingDeque<Response>()

    private val readResponseLock = ReentrantLock()
    private val readResponseLockCondition = readResponseLock.newCondition()

    override fun open() {
        isConnected = true
    }

    override fun close() {
        isConnected = false
    }

    override fun hasCapability(capability: String): Boolean {
        throw UnsupportedOperationException("not implemented")
    }

    override fun executeSimpleCommand(command: String): List<ImapResponse> {
        throw UnsupportedOperationException("not implemented")
    }

    override fun executeCommandWithIdSet(
        commandPrefix: String,
        commandSuffix: String,
        ids: Set<Long>,
    ): List<ImapResponse> {
        throw UnsupportedOperationException("not implemented")
    }

    override fun sendCommand(command: String, sensitive: Boolean): String {
        val tag = ++tag
        println(">>> $tag $command")

        receivedCommands.add(command)
        return tag.toString()
    }

    override fun sendContinuation(continuation: String) {
        println(">>> $continuation")
        receivedCommands.add(continuation)
    }

    override fun readResponse(): ImapResponse {
        readResponseLock.withLock {
            readResponseLockCondition.signal()
        }
        val imapResponse = when (val response = responses.take()) {
            is Response.Continuation -> ImapResponseHelper.createImapResponse("+ ${response.text}")
            is Response.Tagged -> ImapResponseHelper.createImapResponse("$tag ${response.response}")
            is Response.Untagged -> ImapResponseHelper.createImapResponse("* ${response.response}")
            is Response.Action -> response.action()
        }

        println("<<< $imapResponse")

        return imapResponse
    }

    override fun readResponse(callback: ImapResponseCallback?): ImapResponse {
        throw UnsupportedOperationException("not implemented")
    }

    override fun setSocketDefaultReadTimeout() {
        currentSocketReadTimeout = defaultSocketReadTimeout
    }

    override fun setSocketReadTimeout(timeout: Int) {
        currentSocketReadTimeout = timeout
    }

    fun waitForCommand(command: String) {
        do {
            val receivedCommand = receivedCommands.poll(timeout, TimeUnit.SECONDS) ?: throw AssertionError("Timeout")
        } while (receivedCommand != command)
    }

    fun waitForBlockingRead() {
        readResponseLock.withLock {
            readResponseLockCondition.await(timeout, TimeUnit.SECONDS)
        }
    }

    fun throwOnRead(block: () -> Nothing) {
        responses.add(Response.Action(block))
    }

    fun enqueueTaggedServerResponse(response: String) {
        responses.add(Response.Tagged(response))
    }

    fun enqueueUntaggedServerResponse(response: String) {
        responses.add(Response.Untagged(response))
    }

    fun enqueueContinuationServerResponse(text: String = "") {
        responses.add(Response.Continuation(text))
    }

    fun setIdleNotSupported() {
        isIdleCapable = false
    }
}

private sealed class Response {
    class Tagged(val response: String) : Response()
    class Untagged(val response: String) : Response()
    class Continuation(val text: String) : Response()
    class Action(val action: () -> ImapResponse) : Response()
}
