package com.fsck.k9.mail.store.imap

import com.fsck.k9.logging.Timber
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.Authentication
import com.fsck.k9.mail.AuthenticationFailedException
import com.fsck.k9.mail.CertificateValidationException
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.K9MailLib
import com.fsck.k9.mail.MessagingException
import com.fsck.k9.mail.MissingCapabilityException
import com.fsck.k9.mail.NetworkTimeouts.SOCKET_CONNECT_TIMEOUT
import com.fsck.k9.mail.NetworkTimeouts.SOCKET_READ_TIMEOUT
import com.fsck.k9.mail.filter.Base64
import com.fsck.k9.mail.filter.PeekableInputStream
import com.fsck.k9.mail.oauth.OAuth2TokenProvider
import com.fsck.k9.mail.oauth.XOAuth2ChallengeParser
import com.fsck.k9.mail.ssl.CertificateChainExtractor
import com.fsck.k9.mail.ssl.TrustedSocketFactory
import com.fsck.k9.sasl.buildOAuthBearerInitialClientResponse
import com.jcraft.jzlib.JZlib
import com.jcraft.jzlib.ZOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.UnknownHostException
import java.security.GeneralSecurityException
import java.security.Security
import java.util.regex.Pattern
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.net.ssl.SSLException
import org.apache.commons.io.IOUtils

/**
 * A cacheable class that stores the details for a single IMAP connection.
 */
internal class RealImapConnection(
    private val settings: ImapSettings,
    private val socketFactory: TrustedSocketFactory,
    private val oauthTokenProvider: OAuth2TokenProvider?,
    override val connectionGeneration: Int,
    private val socketConnectTimeout: Int = SOCKET_CONNECT_TIMEOUT,
    private val socketReadTimeout: Int = SOCKET_READ_TIMEOUT,
) : ImapConnection {
    private var socket: Socket? = null
    private var inputStream: PeekableInputStream? = null
    private var imapOutputStream: OutputStream? = null
    private var responseParser: ImapResponseParser? = null
    private var nextCommandTag = 0
    private var capabilities = emptySet<String>()
    private var enabled = emptySet<String>()
    private var stacktraceForClose: Exception? = null
    private var open = false
    private var retryOAuthWithNewToken = true

    @get:Synchronized
    override val outputStream: OutputStream
        get() = checkNotNull(imapOutputStream)

    @Synchronized
    @Throws(IOException::class, MessagingException::class)
    override fun open() {
        if (open) {
            return
        } else if (stacktraceForClose != null) {
            throw IllegalStateException(
                "open() called after close(). Check wrapped exception to see where close() was called.",
                stacktraceForClose,
            )
        }

        open = true
        var authSuccess = false
        nextCommandTag = 1

        adjustDNSCacheTTL()

        try {
            socket = connect()
            configureSocket()
            setUpStreamsAndParserFromSocket()

            readInitialResponse()
            requestCapabilitiesIfNecessary()

            upgradeToTlsIfNecessary()

            val responses = authenticate()
            authSuccess = true

            extractOrRequestCapabilities(responses)

            enableCompressionIfRequested()
            sendClientInfoIfSupported()
            enableCapabilitiesIfSupported()

            retrievePathPrefixIfNecessary()
            retrievePathDelimiterIfNecessary()
        } catch (e: SSLException) {
            handleSslException(e)
        } catch (e: GeneralSecurityException) {
            throw MessagingException("Unable to open connection to IMAP server due to security error.", e)
        } finally {
            if (!authSuccess) {
                Timber.e("Failed to login, closing connection for %s", logId)
                close()
            }
        }
    }

    private fun handleSslException(e: SSLException) {
        val certificateChain = CertificateChainExtractor.extract(e)
        if (certificateChain != null) {
            throw CertificateValidationException(certificateChain, e)
        } else {
            throw e
        }
    }

    @get:Synchronized
    override val isConnected: Boolean
        get() {
            return inputStream != null && imapOutputStream != null &&
                socket.let { socket ->
                    socket != null && socket.isConnected && !socket.isClosed
                }
        }

    private fun adjustDNSCacheTTL() {
        try {
            Security.setProperty("networkaddress.cache.ttl", "0")
        } catch (e: Exception) {
            Timber.w(e, "Could not set DNS ttl to 0 for %s", logId)
        }

        try {
            Security.setProperty("networkaddress.cache.negative.ttl", "0")
        } catch (e: Exception) {
            Timber.w(e, "Could not set DNS negative ttl to 0 for %s", logId)
        }
    }

    private fun connect(): Socket {
        val inetAddresses = InetAddress.getAllByName(settings.host)

        var connectException: Exception? = null
        for (address in inetAddresses) {
            connectException = try {
                return connectToAddress(address)
            } catch (e: IOException) {
                Timber.w(e, "Could not connect to %s", address)
                e
            }
        }

        throw connectException ?: UnknownHostException()
    }

    private fun connectToAddress(address: InetAddress): Socket {
        val host = settings.host
        val port = settings.port
        val clientCertificateAlias = settings.clientCertificateAlias

        if (K9MailLib.isDebug() && K9MailLib.DEBUG_PROTOCOL_IMAP) {
            Timber.d("Connecting to %s as %s", host, address)
        }

        val socketAddress: SocketAddress = InetSocketAddress(address, port)
        val socket = if (settings.connectionSecurity == ConnectionSecurity.SSL_TLS_REQUIRED) {
            socketFactory.createSocket(null, host, port, clientCertificateAlias)
        } else {
            Socket()
        }

        socket.connect(socketAddress, socketConnectTimeout)

        return socket
    }

    private fun configureSocket() {
        setSocketDefaultReadTimeout()
    }

    override fun setSocketDefaultReadTimeout() {
        setSocketReadTimeout(socketReadTimeout)
    }

    @Synchronized
    override fun setSocketReadTimeout(timeout: Int) {
        socket?.soTimeout = timeout
    }

    private fun setUpStreamsAndParserFromSocket() {
        val socket = checkNotNull(socket)

        setUpStreamsAndParser(socket.getInputStream(), socket.getOutputStream())
    }

    private fun setUpStreamsAndParser(input: InputStream, output: OutputStream) {
        inputStream = PeekableInputStream(BufferedInputStream(input, BUFFER_SIZE))
        responseParser = ImapResponseParser(inputStream)
        imapOutputStream = BufferedOutputStream(output, BUFFER_SIZE)
    }

    private fun readInitialResponse() {
        val responseParser = checkNotNull(responseParser)

        val initialResponse = responseParser.readResponse()

        if (K9MailLib.isDebug() && K9MailLib.DEBUG_PROTOCOL_IMAP) {
            Timber.v("%s <<< %s", logId, initialResponse)
        }

        extractCapabilities(listOf(initialResponse))
    }

    private fun extractCapabilities(responses: List<ImapResponse>): Boolean {
        val capabilityResponse = CapabilityResponse.parse(responses) ?: return false
        val receivedCapabilities = capabilityResponse.capabilities

        Timber.d("Saving %s capabilities for %s", receivedCapabilities, logId)
        capabilities = receivedCapabilities

        return true
    }

    private fun extractOrRequestCapabilities(responses: List<ImapResponse>) {
        if (!extractCapabilities(responses)) {
            Timber.i("Did not get capabilities in post-auth banner, requesting CAPABILITY for %s", logId)
            requestCapabilities()
        }
    }

    private fun requestCapabilitiesIfNecessary() {
        if (capabilities.isNotEmpty()) return

        if (K9MailLib.isDebug()) {
            Timber.i("Did not get capabilities in banner, requesting CAPABILITY for %s", logId)
        }

        requestCapabilities()
    }

    private fun requestCapabilities() {
        val responses = executeSimpleCommand(Commands.CAPABILITY)

        if (!extractCapabilities(responses)) {
            throw MessagingException("Invalid CAPABILITY response received")
        }
    }

    private fun enableCapabilitiesIfSupported() {
        if (!hasCapability(Capabilities.ENABLE)) {
	    return;
	}

        try {
            val responses = executeSimpleCommand(Commands.ENABLE)
	    val enabledResponse = EnabledResponse.parse(responses) ?: return
	    enabled = enabledResponse.capabilities
        } catch (e: NegativeImapResponseException) {
            Timber.d(e, "Ignoring negative response to ENABLE command")
        }
    }

    private fun upgradeToTlsIfNecessary() {
        if (settings.connectionSecurity == ConnectionSecurity.STARTTLS_REQUIRED) {
            upgradeToTls()
        }
    }

    private fun upgradeToTls() {
        if (!hasCapability(Capabilities.STARTTLS)) {
            throw MissingCapabilityException(Capabilities.STARTTLS)
        }

        startTls()
    }

    private fun startTls() {
        executeSimpleCommand(Commands.STARTTLS)

        val host = settings.host
        val port = settings.port
        val clientCertificateAlias = settings.clientCertificateAlias
        socket = socketFactory.createSocket(socket, host, port, clientCertificateAlias)

        configureSocket()
        setUpStreamsAndParserFromSocket()

        // Per RFC 2595 (3.1):  Once TLS has been started, reissue CAPABILITY command
        if (K9MailLib.isDebug()) {
            Timber.i("Updating capabilities after STARTTLS for %s", logId)
        }

        requestCapabilities()
    }

    private fun authenticate(): List<ImapResponse> {
        return when (settings.authType) {
            AuthType.XOAUTH2 -> {
                if (oauthTokenProvider == null) {
                    throw MessagingException("No OAuthToken Provider available.")
                } else if (!hasCapability(Capabilities.SASL_IR)) {
                    throw MissingCapabilityException(Capabilities.SASL_IR)
                } else if (hasCapability(Capabilities.AUTH_OAUTHBEARER)) {
                    authWithOAuthToken(OAuthMethod.OAUTHBEARER)
                } else if (hasCapability(Capabilities.AUTH_XOAUTH2)) {
                    authWithOAuthToken(OAuthMethod.XOAUTH2)
                } else {
                    throw MissingCapabilityException(Capabilities.AUTH_OAUTHBEARER)
                }
            }
            AuthType.CRAM_MD5 -> {
                if (hasCapability(Capabilities.AUTH_CRAM_MD5)) {
                    authCramMD5()
                } else {
                    throw MissingCapabilityException(Capabilities.AUTH_CRAM_MD5)
                }
            }
            AuthType.PLAIN -> {
                if (hasCapability(Capabilities.AUTH_PLAIN)) {
                    saslAuthPlainWithLoginFallback()
                } else if (!hasCapability(Capabilities.LOGINDISABLED)) {
                    login()
                } else {
                    throw MissingCapabilityException(Capabilities.AUTH_PLAIN)
                }
            }
            AuthType.EXTERNAL -> {
                if (hasCapability(Capabilities.AUTH_EXTERNAL)) {
                    saslAuthExternal()
                } else {
                    throw MissingCapabilityException(Capabilities.AUTH_EXTERNAL)
                }
            }
            else -> {
                throw MessagingException("Unhandled authentication method found in the server settings (bug).")
            }
        }
    }

    private fun authWithOAuthToken(method: OAuthMethod): List<ImapResponse> {
        val oauthTokenProvider = checkNotNull(oauthTokenProvider)
        retryOAuthWithNewToken = true

        return try {
            attemptOAuth(method)
        } catch (e: NegativeImapResponseException) {
            // TODO: Check response code so we don't needlessly invalidate the token.
            oauthTokenProvider.invalidateToken()

            if (!retryOAuthWithNewToken) {
                throw handlePermanentOAuthFailure(e)
            } else {
                handleTemporaryOAuthFailure(method, e)
            }
        }
    }

    private fun handlePermanentOAuthFailure(e: NegativeImapResponseException): AuthenticationFailedException {
        Timber.v(e, "Permanent failure during authentication using OAuth token")

        return AuthenticationFailedException(
            message = "Authentication failed",
            throwable = e,
            messageFromServer = e.responseText,
        )
    }

    private fun handleTemporaryOAuthFailure(method: OAuthMethod, e: NegativeImapResponseException): List<ImapResponse> {
        val oauthTokenProvider = checkNotNull(oauthTokenProvider)

        // We got a response indicating a retry might succeed after token refresh
        // We could avoid this if we had a reasonable chance of knowing
        // if a token was invalid before use (e.g. due to expiry). But we don't
        // This is the intended behaviour per AccountManager
        Timber.v(e, "Temporary failure - retrying with new token")

        return try {
            attemptOAuth(method)
        } catch (e2: NegativeImapResponseException) {
            // Okay, we failed on a new token.
            // Invalidate the token anyway but assume it's permanent.
            Timber.v(e, "Authentication exception for new token, permanent error assumed")

            oauthTokenProvider.invalidateToken()

            throw handlePermanentOAuthFailure(e2)
        }
    }

    private fun attemptOAuth(method: OAuthMethod): List<ImapResponse> {
        val oauthTokenProvider = checkNotNull(oauthTokenProvider)
        val responseParser = checkNotNull(responseParser)

        val token = oauthTokenProvider.getToken(OAuth2TokenProvider.OAUTH2_TIMEOUT.toLong())

        val authString = method.buildInitialClientResponse(settings.username, token)
        val tag = sendSaslIrCommand(method.command, authString, true)

        return responseParser.readStatusResponse(tag, method.command, logId, ::handleOAuthUntaggedResponse)
    }

    private fun handleOAuthUntaggedResponse(response: ImapResponse) {
        if (!response.isContinuationRequested) return

        val imapOutputStream = checkNotNull(imapOutputStream)

        if (response.isString(0)) {
            retryOAuthWithNewToken = XOAuth2ChallengeParser.shouldRetry(response.getString(0), settings.host)
        }

        imapOutputStream.write('\r'.code)
        imapOutputStream.write('\n'.code)
        imapOutputStream.flush()
    }

    private fun authCramMD5(): List<ImapResponse> {
        val command = Commands.AUTHENTICATE_CRAM_MD5
        val tag = sendCommand(command, false)

        val imapOutputStream = checkNotNull(imapOutputStream)
        val responseParser = checkNotNull(responseParser)

        val response = readContinuationResponse(tag)
        if (response.size != 1 || !response.isString(0)) {
            throw MessagingException("Invalid Cram-MD5 nonce received")
        }

        val b64Nonce = response.getString(0).toByteArray()
        val b64CRAM = Authentication.computeCramMd5Bytes(settings.username, settings.password, b64Nonce)

        imapOutputStream.write(b64CRAM)
        imapOutputStream.write('\r'.code)
        imapOutputStream.write('\n'.code)
        imapOutputStream.flush()

        return try {
            responseParser.readStatusResponse(tag, command, logId, null)
        } catch (e: NegativeImapResponseException) {
            throw handleAuthenticationFailure(e)
        }
    }

    private fun saslAuthPlainWithLoginFallback(): List<ImapResponse> {
        return try {
            saslAuthPlain()
        } catch (e: AuthenticationFailedException) {
            if (!isConnected) {
                throw e
            }

            loginOrThrow(e)
        }
    }

    @Suppress("ThrowsCount")
    private fun loginOrThrow(originalException: AuthenticationFailedException): List<ImapResponse> {
        return try {
            login()
        } catch (e: AuthenticationFailedException) {
            throw e
        } catch (e: IOException) {
            Timber.d(e, "LOGIN fallback failed")
            throw originalException
        } catch (e: MessagingException) {
            Timber.d(e, "LOGIN fallback failed")
            throw originalException
        }
    }

    private fun saslAuthPlain(): List<ImapResponse> {
        val command = Commands.AUTHENTICATE_PLAIN
        val tag = sendCommand(command, false)

        val imapOutputStream = checkNotNull(imapOutputStream)
        val responseParser = checkNotNull(responseParser)

        readContinuationResponse(tag)

        val credentials = "\u0000" + settings.username + "\u0000" + settings.password
        val encodedCredentials = Base64.encodeBase64(credentials.toByteArray())

        imapOutputStream.write(encodedCredentials)
        imapOutputStream.write('\r'.code)
        imapOutputStream.write('\n'.code)
        imapOutputStream.flush()

        return try {
            responseParser.readStatusResponse(tag, command, logId, null)
        } catch (e: NegativeImapResponseException) {
            throw handleAuthenticationFailure(e)
        }
    }

    private fun login(): List<ImapResponse> {
        val password = checkNotNull(settings.password)

        /*
         * Use quoted strings which permit spaces and quotes. (Using IMAP
         * string literals would be better, but some servers are broken
         * and don't parse them correctly.)
         */

        // escape double-quotes and backslash characters with a backslash
        val pattern = Pattern.compile("[\\\\\"]")
        val replacement = "\\\\$0"
        val encodedUsername = pattern.matcher(settings.username).replaceAll(replacement)
        val encodedPassword = pattern.matcher(password).replaceAll(replacement)

        return try {
            val command = String.format(Commands.LOGIN + " \"%s\" \"%s\"", encodedUsername, encodedPassword)
            executeSimpleCommand(command, true)
        } catch (e: NegativeImapResponseException) {
            throw handleAuthenticationFailure(e)
        }
    }

    private fun saslAuthExternal(): List<ImapResponse> {
        return try {
            val command = Commands.AUTHENTICATE_EXTERNAL + " " + Base64.encode(settings.username)
            executeSimpleCommand(command, false)
        } catch (e: NegativeImapResponseException) {
            throw handleAuthenticationFailure(e)
        }
    }

    private fun handleAuthenticationFailure(
        negativeResponseException: NegativeImapResponseException,
    ): MessagingException {
        val lastResponse = negativeResponseException.lastResponse
        val responseCode = ResponseCodeExtractor.getResponseCode(lastResponse)

        // If there's no response code we simply assume it was an authentication failure.
        return if (responseCode == null || responseCode == ResponseCodeExtractor.AUTHENTICATION_FAILED) {
            if (negativeResponseException.wasByeResponseReceived()) {
                close()
            }

            AuthenticationFailedException(
                message = "Authentication failed",
                throwable = negativeResponseException,
                messageFromServer = negativeResponseException.responseText,
            )
        } else {
            close()

            negativeResponseException
        }
    }

    private fun enableCompressionIfRequested() {
        if (hasCapability(Capabilities.COMPRESS_DEFLATE) && settings.useCompression) {
            enableCompression()
        }
    }

    private fun sendClientInfoIfSupported() {
        val clientInfo = settings.clientInfo

        if (hasCapability(Capabilities.ID) && clientInfo != null) {
            val encodedAppName = ImapUtility.encodeString(clientInfo.appName)
            val encodedAppVersion = ImapUtility.encodeString(clientInfo.appVersion)

            try {
                executeSimpleCommand("""ID ("name" $encodedAppName "version" $encodedAppVersion)""")
            } catch (e: NegativeImapResponseException) {
                Timber.d(e, "Ignoring negative response to ID command")
            }
        }
    }

    private fun enableCompression() {
        try {
            executeSimpleCommand(Commands.COMPRESS_DEFLATE)
        } catch (e: NegativeImapResponseException) {
            Timber.d(e, "Unable to negotiate compression: ")
            return
        }

        try {
            val socket = checkNotNull(socket)
            val input = InflaterInputStream(socket.getInputStream(), Inflater(true))
            val output = ZOutputStream(socket.getOutputStream(), JZlib.Z_BEST_SPEED, true)
            output.flushMode = JZlib.Z_PARTIAL_FLUSH

            setUpStreamsAndParser(input, output)

            if (K9MailLib.isDebug()) {
                Timber.i("Compression enabled for %s", logId)
            }
        } catch (e: IOException) {
            close()
            Timber.e(e, "Error enabling compression")
        }
    }

    private fun retrievePathPrefixIfNecessary() {
        if (settings.pathPrefix != null) return

        if (hasCapability(Capabilities.NAMESPACE)) {
            if (K9MailLib.isDebug()) {
                Timber.i("pathPrefix is unset and server has NAMESPACE capability")
            }

            handleNamespace()
        } else {
            if (K9MailLib.isDebug()) {
                Timber.i("pathPrefix is unset but server does not have NAMESPACE capability")
            }

            settings.pathPrefix = ""
        }
    }

    private fun handleNamespace() {
        val responses = executeSimpleCommand(Commands.NAMESPACE)

        val namespaceResponse = NamespaceResponse.parse(responses) ?: return

        settings.pathPrefix = namespaceResponse.prefix
        settings.pathDelimiter = namespaceResponse.hierarchyDelimiter
        settings.setCombinedPrefix(null)

        if (K9MailLib.isDebug()) {
            Timber.d("Got path '%s' and separator '%s'", namespaceResponse.prefix, namespaceResponse.hierarchyDelimiter)
        }
    }

    private fun retrievePathDelimiterIfNecessary() {
        if (settings.pathDelimiter == null) {
            retrievePathDelimiter()
        }
    }

    private fun retrievePathDelimiter() {
        val listResponses = try {
            executeSimpleCommand(Commands.LIST + " \"\" \"\"")
        } catch (e: NegativeImapResponseException) {
            Timber.d(e, "Error getting path delimiter using LIST command")
            return
        }

        for (response in listResponses) {
            if (isListResponse(response)) {
                val hierarchyDelimiter = response.getString(2)

                settings.pathDelimiter = hierarchyDelimiter
                settings.setCombinedPrefix(null)

                if (K9MailLib.isDebug()) {
                    Timber.d("Got path delimiter '%s' for %s", hierarchyDelimiter, logId)
                }

                break
            }
        }
    }

    private fun isListResponse(response: ImapResponse): Boolean {
        if (response.size < 4) return false

        val isListResponse = ImapResponseParser.equalsIgnoreCase(response[0], Responses.LIST)
        val hierarchyDelimiterValid = response.isString(2)

        return isListResponse && hierarchyDelimiterValid
    }

    override fun hasCapability(capability: String): Boolean {
        if (!open) {
            open()
        }

        return capabilities.contains(capability.uppercase())
    }

    private val isCondstoreCapable: Boolean
        get() = hasCapability(Capabilities.CONDSTORE)

    override val isIdleCapable: Boolean
        get() {
            if (K9MailLib.isDebug()) {
                Timber.v("Connection %s has %d capabilities", logId, capabilities.size)
            }

            return capabilities.contains(Capabilities.IDLE)
        }

    override val isUidPlusCapable: Boolean
        get() = capabilities.contains(Capabilities.UID_PLUS)

    override val isUtf8AcceptCapable: Boolean
        get() = enabled.contains(Capabilities.UTF8_ACCEPT)

    @Synchronized
    override fun close() {
        if (!open) return

        open = false

        stacktraceForClose = Exception()

        IOUtils.closeQuietly(inputStream)
        IOUtils.closeQuietly(imapOutputStream)
        IOUtils.closeQuietly(socket)

        inputStream = null
        imapOutputStream = null
        socket = null
    }

    override val logId: String
        get() = "conn" + hashCode()

    @Synchronized
    @Throws(IOException::class, MessagingException::class)
    override fun executeSimpleCommand(command: String): List<ImapResponse> {
        return executeSimpleCommand(command, false)
    }

    @Throws(IOException::class, MessagingException::class)
    fun executeSimpleCommand(command: String, sensitive: Boolean): List<ImapResponse> {
        var commandToLog = command
        if (sensitive && !K9MailLib.isDebugSensitive()) {
            commandToLog = "*sensitive*"
        }

        val tag = sendCommand(command, sensitive)

        val responseParser = checkNotNull(responseParser)
        return try {
            responseParser.readStatusResponse(tag, commandToLog, logId, null)
        } catch (e: IOException) {
            close()
            throw e
        }
    }

    @Synchronized
    @Throws(IOException::class, MessagingException::class)
    override fun executeCommandWithIdSet(
        commandPrefix: String,
        commandSuffix: String,
        ids: Set<Long>,
    ): List<ImapResponse> {
        val groupedIds = IdGrouper.groupIds(ids)
        val splitCommands = ImapCommandSplitter.splitCommand(
            commandPrefix,
            commandSuffix,
            groupedIds,
            lineLengthLimit,
        )

        return splitCommands.flatMap { splitCommand ->
            executeSimpleCommand(splitCommand)
        }
    }

    @Throws(IOException::class, MessagingException::class)
    fun sendSaslIrCommand(command: String, initialClientResponse: String, sensitive: Boolean): String {
        try {
            open()

            val outputStream = checkNotNull(imapOutputStream)

            val tag = (nextCommandTag++).toString()
            val commandToSend = "$tag $command $initialClientResponse\r\n"

            outputStream.write(commandToSend.toByteArray())
            outputStream.flush()

            if (K9MailLib.isDebug() && K9MailLib.DEBUG_PROTOCOL_IMAP) {
                if (sensitive && !K9MailLib.isDebugSensitive()) {
                    Timber.v("%s>>> [Command Hidden, Enable Sensitive Debug Logging To Show]", logId)
                } else {
                    Timber.v("%s>>> %s %s %s", logId, tag, command, initialClientResponse)
                }
            }

            return tag
        } catch (e: IOException) {
            close()
            throw e
        } catch (e: MessagingException) {
            close()
            throw e
        }
    }

    @Synchronized
    @Throws(MessagingException::class, IOException::class)
    override fun sendCommand(command: String, sensitive: Boolean): String {
        try {
            open()

            val outputStream = checkNotNull(imapOutputStream)

            val tag = (nextCommandTag++).toString()
            val commandToSend = "$tag $command\r\n"

            outputStream.write(commandToSend.toByteArray())
            outputStream.flush()

            if (K9MailLib.isDebug() && K9MailLib.DEBUG_PROTOCOL_IMAP) {
                if (sensitive && !K9MailLib.isDebugSensitive()) {
                    Timber.v("%s>>> [Command Hidden, Enable Sensitive Debug Logging To Show]", logId)
                } else {
                    Timber.v("%s>>> %s %s", logId, tag, command)
                }
            }

            return tag
        } catch (e: IOException) {
            close()
            throw e
        } catch (e: MessagingException) {
            close()
            throw e
        }
    }

    @Synchronized
    @Throws(IOException::class)
    override fun sendContinuation(continuation: String) {
        val outputStream = checkNotNull(imapOutputStream)

        outputStream.write(continuation.toByteArray())
        outputStream.write('\r'.code)
        outputStream.write('\n'.code)
        outputStream.flush()

        if (K9MailLib.isDebug() && K9MailLib.DEBUG_PROTOCOL_IMAP) {
            Timber.v("%s>>> %s", logId, continuation)
        }
    }

    @Throws(IOException::class)
    override fun readResponse(): ImapResponse {
        return readResponse(null)
    }

    @Throws(IOException::class)
    override fun readResponse(callback: ImapResponseCallback?): ImapResponse {
        try {
            val responseParser = checkNotNull(responseParser)

            val response = responseParser.readResponse(callback)

            if (K9MailLib.isDebug() && K9MailLib.DEBUG_PROTOCOL_IMAP) {
                Timber.v("%s<<<%s", logId, response)
            }

            return response
        } catch (e: IOException) {
            close()
            throw e
        }
    }

    private fun readContinuationResponse(tag: String): ImapResponse {
        var response: ImapResponse
        do {
            response = readResponse()

            val responseTag = response.tag
            if (responseTag != null) {
                if (responseTag.equals(tag, ignoreCase = true)) {
                    throw MessagingException("Command continuation aborted: $response")
                } else {
                    Timber.w(
                        "After sending tag %s, got tag response from previous command %s for %s",
                        tag,
                        response,
                        logId,
                    )
                }
            }
        } while (!response.isContinuationRequested)

        return response
    }

    @get:Throws(IOException::class, MessagingException::class)
    val lineLengthLimit: Int
        get() = if (isCondstoreCapable) LENGTH_LIMIT_WITH_CONDSTORE else LENGTH_LIMIT_WITHOUT_CONDSTORE

    private enum class OAuthMethod {
        XOAUTH2 {
            override val command: String = Commands.AUTHENTICATE_XOAUTH2

            override fun buildInitialClientResponse(username: String, token: String): String {
                return Authentication.computeXoauth(username, token)
            }
        },
        OAUTHBEARER {
            override val command: String = Commands.AUTHENTICATE_OAUTHBEARER

            override fun buildInitialClientResponse(username: String, token: String): String {
                return buildOAuthBearerInitialClientResponse(username, token)
            }
        },
        ;

        abstract val command: String
        abstract fun buildInitialClientResponse(username: String, token: String): String
    }

    companion object {
        private const val BUFFER_SIZE = 1024

        /* The below limits are 20 octets less than the recommended limits, in order to compensate for
         * the length of the command tag, the space after the tag and the CRLF at the end of the command
         * (these are not taken into account when calculating the length of the command). For more
         * information, refer to section 4 of RFC 7162.
         *
         * The length limit for servers supporting the CONDSTORE extension is large in order to support
         * the QRESYNC parameter to the SELECT/EXAMINE commands, which accept a list of known message
         * sequence numbers as well as their corresponding UIDs.
         */
        private const val LENGTH_LIMIT_WITHOUT_CONDSTORE = 980
        private const val LENGTH_LIMIT_WITH_CONDSTORE = 8172
    }
}
