package com.fsck.k9.mail.store.imap

import com.fsck.k9.mail.MessagingException
import java.io.IOException
import java.io.OutputStream
import java.net.SocketException

internal interface ImapConnection {
    val logId: String
    val connectionGeneration: Int
    val isConnected: Boolean
    val outputStream: OutputStream
    val isUidPlusCapable: Boolean
    val isUtf8AcceptCapable: Boolean
    val isIdleCapable: Boolean

    @Throws(IOException::class, MessagingException::class)
    fun open()

    fun close()

    @Throws(IOException::class, MessagingException::class)
    fun hasCapability(capability: String): Boolean

    @Throws(IOException::class, MessagingException::class)
    fun executeSimpleCommand(command: String): List<ImapResponse>

    @Throws(IOException::class, MessagingException::class)
    fun executeCommandWithIdSet(commandPrefix: String, commandSuffix: String, ids: Set<Long>): List<ImapResponse>

    @Throws(MessagingException::class, IOException::class)
    fun sendCommand(command: String, sensitive: Boolean): String

    @Throws(IOException::class)
    fun sendContinuation(continuation: String)

    @Throws(IOException::class)
    fun readResponse(): ImapResponse

    @Throws(IOException::class)
    fun readResponse(callback: ImapResponseCallback?): ImapResponse

    @Throws(SocketException::class)
    fun setSocketDefaultReadTimeout()

    @Throws(SocketException::class)
    fun setSocketReadTimeout(timeout: Int)
}
