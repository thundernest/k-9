package com.fsck.k9.mail.store.imap

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.message
import assertk.assertions.prop
import com.fsck.k9.logging.Timber
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.AuthenticationFailedException
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.K9MailLib
import com.fsck.k9.mail.MissingCapabilityException
import com.fsck.k9.mail.SystemOutLogger
import com.fsck.k9.mail.XOAuth2ChallengeParserTest
import com.fsck.k9.mail.helpers.TestTrustedSocketFactory
import com.fsck.k9.mail.oauth.OAuth2TokenProvider
import com.fsck.k9.mail.ssl.TrustedSocketFactory
import com.fsck.k9.mail.store.imap.mockserver.MockImapServer
import java.io.IOException
import java.net.UnknownHostException
import okio.ByteString.Companion.encodeUtf8
import org.junit.Before
import org.junit.Test

private const val DEBUGGING = false

private const val USERNAME = "user"
private const val PASSWORD = "123456"

private const val SOCKET_CONNECT_TIMEOUT = 10000
private const val SOCKET_READ_TIMEOUT = 10000

private const val XOAUTH_TOKEN = "token"
private const val XOAUTH_TOKEN_2 = "token2"
private val XOAUTH_STRING = "user=$USERNAME\u0001auth=Bearer $XOAUTH_TOKEN\u0001\u0001".base64()
private val XOAUTH_STRING_RETRY = "user=$USERNAME\u0001auth=Bearer $XOAUTH_TOKEN_2\u0001\u0001".base64()
private val OAUTHBEARER_STRING = "n,a=$USERNAME,\u0001auth=Bearer $XOAUTH_TOKEN\u0001\u0001".base64()

class RealImapConnectionTest {
    private var socketFactory = TestTrustedSocketFactory.newInstance()
    private var oAuth2TokenProvider = TestTokenProvider()

    @Before
    fun setUp() {
        if (DEBUGGING) {
            Timber.logger = SystemOutLogger()
            K9MailLib.setDebug(true)
            K9MailLib.setDebugSensitive(true)
        }
    }

    @Test
    fun `open() with no capabilities in initial response should issue pre-auth capabilities command`() {
        val server = MockImapServer().apply {
            output("* OK example.org server")
            expect("1 CAPABILITY")
            output("* CAPABILITY IMAP4 IMAP4REV1 AUTH=PLAIN")
            output("1 OK CAPABILITY Completed")
            expect("2 AUTHENTICATE PLAIN")
            output("+")
            expect("\u0000$USERNAME\u0000$PASSWORD".base64())
            output("2 OK Success")
            postAuthenticationDialogRequestingCapabilities()
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with capabilities in initial response should not issue pre-auth capabilities command`() {
        val server = MockImapServer().apply {
            output("* OK [CAPABILITY IMAP4 IMAP4REV1 AUTH=PLAIN]")
            expect("1 AUTHENTICATE PLAIN")
            output("+")
            expect("\u0000$USERNAME\u0000$PASSWORD".base64())
            output("1 OK Success")
            postAuthenticationDialogRequestingCapabilities(tag = 2)
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH PLAIN`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=PLAIN")
            expect("2 AUTHENTICATE PLAIN")
            output("+")
            expect("\u0000$USERNAME\u0000$PASSWORD".base64())
            output("2 OK Success")
            postAuthenticationDialogRequestingCapabilities()
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() after close() was called should throw`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog()
            expect("2 LOGIN \"$USERNAME\" \"$PASSWORD\"")
            output("2 OK LOGIN completed")
            postAuthenticationDialogRequestingCapabilities()
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)
        imapConnection.open()
        imapConnection.close()

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("open() called after close(). Check wrapped exception to see where close() was called.")
    }

    @Test
    fun `open() AUTH PLAIN with login disabled should throw`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "LOGINDISABLED")
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<MissingCapabilityException>()
            .prop(MissingCapabilityException::capabilityName).isEqualTo("AUTH=PLAIN")

        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH PLAIN with authentication failure should fall back to LOGIN`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=PLAIN")
            expect("2 AUTHENTICATE PLAIN")
            output("+")
            expect("\u0000$USERNAME\u0000$PASSWORD".base64())
            output("2 NO Login Failure")
            expect("3 LOGIN \"$USERNAME\" \"$PASSWORD\"")
            output("3 OK LOGIN completed")
            postAuthenticationDialogRequestingCapabilities(tag = 4)
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH PLAIN and LOGIN fallback with authentication failure should throw`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=PLAIN")
            expect("2 AUTHENTICATE PLAIN")
            output("+")
            expect("\u0000$USERNAME\u0000$PASSWORD".base64())
            output("2 NO Login Failure")
            expect("3 LOGIN \"$USERNAME\" \"$PASSWORD\"")
            output("3 NO Go away")
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<AuthenticationFailedException>()
            .prop(AuthenticationFailedException::messageFromServer)
            .isEqualTo("Go away")

        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH PLAIN failure and disconnect should throw`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=PLAIN")
            expect("2 AUTHENTICATE PLAIN")
            output("+")
            expect("\u0000$USERNAME\u0000$PASSWORD".base64())
            output("2 NO [UNAVAILABLE] Maximum number of connections from user+IP exceeded")
            closeConnection()
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<NegativeImapResponseException>()
            .message().isNotNull().contains("Maximum number of connections from user+IP exceeded")

        assertThat(imapConnection.isConnected).isFalse()
        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH PLAIN with BYE response and connection close should throw AuthenticationFailedException`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=PLAIN")
            expect("2 AUTHENTICATE PLAIN")
            output("+")
            expect("\u0000$USERNAME\u0000$PASSWORD".base64())
            output("* BYE Go away")
            output("2 NO Login Failure")
            closeConnection()
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<AuthenticationFailedException>()
            .prop(AuthenticationFailedException::messageFromServer)
            .isEqualTo("Login Failure")

        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH PLAIN without AUTH PLAIN capability should use LOGIN command`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog()
            expect("2 LOGIN \"$USERNAME\" \"$PASSWORD\"")
            output("2 OK LOGIN completed")
            postAuthenticationDialogRequestingCapabilities()
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH CRAM-MD5`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=CRAM-MD5")
            expect("2 AUTHENTICATE CRAM-MD5")
            output("+ ${"<0000.000000000@example.org>".base64()}")
            expect("dXNlciA2ZjdiOTcyYjk5YTI4NDk4OTRhN2YyMmE3MGRhZDg0OQ==")
            output("2 OK Success")
            postAuthenticationDialogRequestingCapabilities()
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.CRAM_MD5)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH CRAM-MD5 with authentication failure should throw`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=CRAM-MD5")
            expect("2 AUTHENTICATE CRAM-MD5")
            output("+ ${"<0000.000000000@example.org>".base64()}")
            expect("dXNlciA2ZjdiOTcyYjk5YTI4NDk4OTRhN2YyMmE3MGRhZDg0OQ==")
            output("2 NO Who are you?")
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.CRAM_MD5)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<AuthenticationFailedException>()
            .prop(AuthenticationFailedException::messageFromServer)
            .isEqualTo("Who are you?")

        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH CRAM-MD5 without AUTH CRAM-MD5 capability should throw`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=PLAIN")
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.CRAM_MD5)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<MissingCapabilityException>()
            .prop(MissingCapabilityException::capabilityName).isEqualTo("AUTH=CRAM-MD5")

        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH OAUTHBEARER`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "SASL-IR AUTH=OAUTHBEARER")
            expect("2 AUTHENTICATE OAUTHBEARER $OAUTHBEARER_STRING")
            output("2 OK Success")
            postAuthenticationDialogRequestingCapabilities()
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.XOAUTH2)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH OAUTHBEARER when AUTH=XOAUTH2 and AUTH=OAUTHBEARER capabilities are present`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "SASL-IR AUTH=XOAUTH2 AUTH=OAUTHBEARER")
            expect("2 AUTHENTICATE OAUTHBEARER $OAUTHBEARER_STRING")
            output("2 OK Success")
            postAuthenticationDialogRequestingCapabilities()
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.XOAUTH2)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH XOAUTH2 with SASL-IR`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "SASL-IR AUTH=XOAUTH AUTH=XOAUTH2")
            expect("2 AUTHENTICATE XOAUTH2 $XOAUTH_STRING")
            output("2 OK Success")
            postAuthenticationDialogRequestingCapabilities()
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.XOAUTH2)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH XOAUTH2 with untagged CAPABILITY response after authentication`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "SASL-IR AUTH=XOAUTH AUTH=XOAUTH2")
            expect("2 AUTHENTICATE XOAUTH2 $XOAUTH_STRING")
            output("* CAPABILITY IMAP4rev1 X-GM-EXT-1")
            output("2 OK Success")
            simplePostAuthenticationDialog(tag = 3)
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.XOAUTH2)

        imapConnection.open()

        assertThat(imapConnection.hasCapability("X-GM-EXT-1")).isTrue()
        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH OAUTHBEARER with SASL-IR capability missing`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=OAUTHBEARER")
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.XOAUTH2)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<MissingCapabilityException>()
            .prop(MissingCapabilityException::capabilityName).isEqualTo("SASL-IR")

        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH OAUTHBEARER with AUTH=OAUTHBEARER capability missing`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "SASL-IR")
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.XOAUTH2)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<MissingCapabilityException>()
            .prop(MissingCapabilityException::capabilityName).isEqualTo("AUTH=OAUTHBEARER")

        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH XOAUTH2 throws exception on 401 response`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "SASL-IR AUTH=XOAUTH AUTH=XOAUTH2")
            expect("2 AUTHENTICATE XOAUTH2 $XOAUTH_STRING")
            output("+ ${XOAuth2ChallengeParserTest.STATUS_401_RESPONSE}")
            expect("")
            output("2 NO SASL authentication failed")
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.XOAUTH2)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<AuthenticationFailedException>()
            .prop(AuthenticationFailedException::messageFromServer)
            .isEqualTo("SASL authentication failed")
    }

    @Test
    fun `open() AUTH XOAUTH2 invalidates and retries new token on 400 response`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "SASL-IR AUTH=XOAUTH AUTH=XOAUTH2")
            expect("2 AUTHENTICATE XOAUTH2 $XOAUTH_STRING")
            output("+ ${XOAuth2ChallengeParserTest.STATUS_400_RESPONSE}")
            expect("")
            output("2 NO SASL authentication failed")
            expect("3 AUTHENTICATE XOAUTH2 $XOAUTH_STRING_RETRY")
            output("3 OK Success")
            postAuthenticationDialogRequestingCapabilities(tag = 4)
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.XOAUTH2)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH XOAUTH2 invalidates and retries new token on invalid JSON response`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "SASL-IR AUTH=XOAUTH AUTH=XOAUTH2")
            expect("2 AUTHENTICATE XOAUTH2 $XOAUTH_STRING")
            output("+ ${XOAuth2ChallengeParserTest.INVALID_RESPONSE}")
            expect("")
            output("2 NO SASL authentication failed")
            expect("3 AUTHENTICATE XOAUTH2 $XOAUTH_STRING_RETRY")
            output("3 OK Success")
            requestCapabilities(tag = 4)
            simplePostAuthenticationDialog(tag = 5)
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.XOAUTH2)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH XOAUTH2 invalidates and retries new token on missing status JSON response`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "SASL-IR AUTH=XOAUTH AUTH=XOAUTH2")
            expect("2 AUTHENTICATE XOAUTH2 $XOAUTH_STRING")
            output("+ ${XOAuth2ChallengeParserTest.MISSING_STATUS_RESPONSE}")
            expect("")
            output("2 NO SASL authentication failed")
            expect("3 AUTHENTICATE XOAUTH2 $XOAUTH_STRING_RETRY")
            output("3 OK Success")
            requestCapabilities(tag = 4)
            simplePostAuthenticationDialog(tag = 5)
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.XOAUTH2)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH XOAUTH2 with old token throws exception if retry fails`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "SASL-IR AUTH=XOAUTH AUTH=XOAUTH2")
            expect("2 AUTHENTICATE XOAUTH2 $XOAUTH_STRING")
            output("+ r3j3krj3irj3oir3ojo")
            expect("")
            output("2 NO SASL authentication failed")
            expect("3 AUTHENTICATE XOAUTH2 $XOAUTH_STRING_RETRY")
            output("+ 433ba3a3a")
            expect("")
            output("3 NO SASL authentication failed")
            postAuthenticationDialogRequestingCapabilities()
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.XOAUTH2)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<AuthenticationFailedException>()
            .prop(AuthenticationFailedException::messageFromServer)
            .isEqualTo("SASL authentication failed")
    }

    @Test
    fun `open() AUTH XOAUTH2 parses capabilities`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "SASL-IR AUTH=XOAUTH AUTH=XOAUTH2")
            expect("2 AUTHENTICATE XOAUTH2 $XOAUTH_STRING")
            output("2 OK [CAPABILITY IMAP4REV1 IDLE XM-GM-EXT-1]")
            simplePostAuthenticationDialog(tag = 3)
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.XOAUTH2)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
        assertThat(imapConnection.hasCapability("XM-GM-EXT-1")).isTrue()
    }

    @Test
    fun `open() AUTH EXTERNAL`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=EXTERNAL")
            expect("2 AUTHENTICATE EXTERNAL ${USERNAME.base64()}")
            output("2 OK Success")
            postAuthenticationDialogRequestingCapabilities()
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.EXTERNAL)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH EXTERNAL with authentication failure should throw`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=EXTERNAL")
            expect("2 AUTHENTICATE EXTERNAL ${USERNAME.base64()}")
            output("2 NO Bad certificate")
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.EXTERNAL)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<AuthenticationFailedException>()
            .prop(AuthenticationFailedException::messageFromServer)
            .isEqualTo("Bad certificate")

        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() AUTH EXTERNAL without AUTH EXTERNAL capability should throw`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=PLAIN")
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.EXTERNAL)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<MissingCapabilityException>()
            .prop(MissingCapabilityException::capabilityName).isEqualTo("AUTH=EXTERNAL")

        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with no post-auth CAPABILITY response should issue CAPABILITY command`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=PLAIN")
            expect("2 AUTHENTICATE PLAIN")
            output("+")
            expect("\u0000$USERNAME\u0000$PASSWORD".base64())
            output("2 OK Success")
            expect("3 CAPABILITY")
            output("* CAPABILITY IDLE")
            output("3 OK CAPABILITY Completed")
            simplePostAuthenticationDialog(tag = 4)
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
        assertThat(imapConnection.isIdleCapable).isTrue()
    }

    @Test
    fun `open() with untagged post-auth CAPABILITY response`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=PLAIN")
            expect("2 AUTHENTICATE PLAIN")
            output("+")
            expect("\u0000$USERNAME\u0000$PASSWORD".base64())
            output(
                "* CAPABILITY IMAP4rev1 UNSELECT IDLE QUOTA ID XLIST CHILDREN X-GM-EXT-1 UIDPLUS " +
                    "ENABLE MOVE CONDSTORE ESEARCH UTF8=ACCEPT LIST-EXTENDED LIST-STATUS LITERAL- SPECIAL-USE " +
                    "APPENDLIMIT=35651584",
            )
            output("2 OK")
            simplePostAuthenticationDialog(tag = 3)
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
        assertThat(imapConnection.isIdleCapable).isTrue()
    }

    @Test
    fun `open() with post-auth CAPABILITY response`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "AUTH=PLAIN")
            expect("2 AUTHENTICATE PLAIN")
            output("+")
            expect("\u0000$USERNAME\u0000$PASSWORD".base64())
            output("2 OK [CAPABILITY IDLE]")
            simplePostAuthenticationDialog(tag = 3)
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
        assertThat(imapConnection.isIdleCapable).isTrue()
    }

    @Test
    fun `open() with NAMESPACE capability should issue NAMESPACE command`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog(postAuthCapabilities = "NAMESPACE")
            expect("3 NAMESPACE")
            output("* NAMESPACE ((\"\" \"/\")) NIL NIL")
            output("3 OK command completed")
        }
        val imapConnection = startServerAndCreateImapConnection(server)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test(expected = IOException::class)
    fun `open() with connection error should throw`() {
        val settings = createImapSettings(host = "127.1.2.3")
        val imapConnection = createImapConnection(settings, socketFactory, oAuth2TokenProvider)

        imapConnection.open()
    }

    @Test
    fun `open() with invalid hostname should throw`() {
        val settings = createImapSettings(host = "host name")
        val imapConnection = createImapConnection(settings, socketFactory, oAuth2TokenProvider)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<UnknownHostException>()

        assertThat(imapConnection.isConnected).isFalse()
    }

    @Test
    fun `open() with STARTTLS capability should issue STARTTLS command`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "STARTTLS LOGINDISABLED")
            expect("2 STARTTLS")
            output("2 OK [CAPABILITY IMAP4REV1 NAMESPACE]")
            startTls()
            expect("3 CAPABILITY")
            output("* CAPABILITY IMAP4 IMAP4REV1")
            output("3 OK")
            expect("4 LOGIN \"$USERNAME\" \"$PASSWORD\"")
            output("4 OK [CAPABILITY NAMESPACE] LOGIN completed")
            expect("5 NAMESPACE")
            output("* NAMESPACE ((\"\" \"/\")) NIL NIL")
            output("5 OK command completed")
        }
        val imapConnection = startServerAndCreateImapConnection(
            server,
            connectionSecurity = ConnectionSecurity.STARTTLS_REQUIRED,
            authType = AuthType.PLAIN,
        )

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with STARTTLS but without STARTTLS capability should throw`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog()
        }
        val imapConnection = startServerAndCreateImapConnection(
            server,
            connectionSecurity = ConnectionSecurity.STARTTLS_REQUIRED,
        )

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<MissingCapabilityException>()
            .prop(MissingCapabilityException::capabilityName).isEqualTo("STARTTLS")

        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with untagged CAPABILITY after STARTTLS should not throw`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "STARTTLS LOGINDISABLED")
            expect("2 STARTTLS")
            output("2 OK Begin TLS negotiation now")
            startTls()
            output("* CAPABILITY IMAP4REV1 IMAP4")
            expect("3 CAPABILITY")
            output("* CAPABILITY IMAP4 IMAP4REV1")
            output("3 OK")
            expect("4 LOGIN \"$USERNAME\" \"$PASSWORD\"")
            output("4 OK [CAPABILITY IMAP4REV1] LOGIN completed")
            simplePostAuthenticationDialog(tag = 5)
        }
        val imapConnection = startServerAndCreateImapConnection(
            server,
            connectionSecurity = ConnectionSecurity.STARTTLS_REQUIRED,
            authType = AuthType.PLAIN,
        )

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with negative response to STARTTLS command should throw`() {
        val server = MockImapServer().apply {
            preAuthenticationDialog(capabilities = "STARTTLS")
            expect("2 STARTTLS")
            output("2 NO")
        }
        val imapConnection = startServerAndCreateImapConnection(
            server,
            connectionSecurity = ConnectionSecurity.STARTTLS_REQUIRED,
            authType = AuthType.PLAIN,
        )

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<NegativeImapResponseException>()
            .hasMessage("Command: STARTTLS; response: #2# [NO]")

        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with ENABLE capability should try to enable UTF8=ACCEPT`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog(postAuthCapabilities = "ENABLE")
            expect("3 ENABLE UTF8=ACCEPT")
            output("* ENABLED")
            output("3 OK")
            simplePostAuthenticationDialog(tag = 4)
        }
        val imapConnection = startServerAndCreateImapConnection(server, useCompression = true)

        imapConnection.open()
        assertThat(imapConnection.isUtf8AcceptCapable).isFalse()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with ENABLE and UTF8=ACCEPT capabilities should enable UTF8=ACCEPT`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog(postAuthCapabilities = "ENABLE UTF8=ACCEPT")
            expect("3 ENABLE UTF8=ACCEPT")
            output("* ENABLED UTF8=ACCEPT")
            output("3 OK")
            simplePostAuthenticationDialog(tag = 4)
        }
        val imapConnection = startServerAndCreateImapConnection(server, useCompression = true)

        imapConnection.open()
        assertThat(imapConnection.isUtf8AcceptCapable).isTrue()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with COMPRESS=DEFLATE capability should enable compression`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog(postAuthCapabilities = "COMPRESS=DEFLATE")
            expect("3 COMPRESS DEFLATE")
            output("3 OK")
            enableCompression()
            simplePostAuthenticationDialog(tag = 4)
        }
        val imapConnection = startServerAndCreateImapConnection(server, useCompression = true)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with negative response to COMPRESS command should continue`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog(postAuthCapabilities = "COMPRESS=DEFLATE")
            expect("3 COMPRESS DEFLATE")
            output("3 NO")
            simplePostAuthenticationDialog(tag = 4)
        }
        val imapConnection = startServerAndCreateImapConnection(server, useCompression = true)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with IOException during COMPRESS command should throw`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog(postAuthCapabilities = "COMPRESS=DEFLATE")
            expect("3 COMPRESS DEFLATE")
            closeConnection()
        }
        val imapConnection = startServerAndCreateImapConnection(server, useCompression = true)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<IOException>()

        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with IOException during LIST command should throw`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog()
            expect("3 LIST \"\" \"\"")
            output("* Now what?")
        }
        val imapConnection = startServerAndCreateImapConnection(server)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<IOException>()

        server.verifyConnectionClosed()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with negative response to LIST command`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog()
            expect("3 LIST \"\" \"\"")
            output("3 NO")
        }
        val imapConnection = startServerAndCreateImapConnection(server)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with ID capability and clientInfoAppName should send ID command`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog(postAuthCapabilities = "ID")
            expect("""3 ID ("name" "AppName" "version" "AppVersion")""")
            output("""* ID ("name" "CustomImapServer" "vendor" "Company, Inc." "version" "0.1")""")
            output("3 OK ID completed")
            simplePostAuthenticationDialog(tag = 4)
        }
        val imapConnection = startServerAndCreateImapConnection(
            server,
            clientInfo = ImapClientInfo(appName = "AppName", appVersion = "AppVersion"),
        )

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() without ID capability and clientInfoAppName set should send not ID command`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog()
            simplePostAuthenticationDialog(tag = 3)
        }
        val imapConnection = startServerAndCreateImapConnection(
            server,
            clientInfo = ImapClientInfo(appName = "AppName", appVersion = "AppVersion"),
        )

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with ID capability but empty clientInfoAppName should not send ID command`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog(postAuthCapabilities = "ID")
            simplePostAuthenticationDialog(tag = 3)
        }
        val imapConnection = startServerAndCreateImapConnection(server, clientInfo = null)

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with empty untagged ID response`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog(postAuthCapabilities = "ID")
            expect("""3 ID ("name" "AppName" "version" "AppVersion")""")
            output("""* ID NIL""")
            output("3 OK ID completed")
            simplePostAuthenticationDialog(tag = 4)
        }
        val imapConnection = startServerAndCreateImapConnection(
            server,
            clientInfo = ImapClientInfo(appName = "AppName", appVersion = "AppVersion"),
        )

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with missing untagged ID response`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog(postAuthCapabilities = "ID")
            expect("""3 ID ("name" "AppName" "version" "AppVersion")""")
            output("3 OK ID completed")
            simplePostAuthenticationDialog(tag = 4)
        }
        val imapConnection = startServerAndCreateImapConnection(
            server,
            clientInfo = ImapClientInfo(appName = "AppName", appVersion = "AppVersion"),
        )

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `open() with BAD response to ID command should not throw`() {
        val server = MockImapServer().apply {
            simplePreAuthAndLoginDialog(postAuthCapabilities = "ID")
            expect("""3 ID ("name" "AppName" "version" "AppVersion")""")
            output("3 BAD Server doesn't like the ID command")
            simplePostAuthenticationDialog(tag = 4)
        }
        val imapConnection = startServerAndCreateImapConnection(
            server,
            clientInfo = ImapClientInfo(appName = "AppName", appVersion = "AppVersion"),
        )

        imapConnection.open()

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `isConnected without previous open() should return false`() {
        val settings = createImapSettings()
        val imapConnection = createImapConnection(settings, socketFactory, oAuth2TokenProvider)

        val result = imapConnection.isConnected

        assertThat(result).isFalse()
    }

    @Test
    fun `isConnected after open() should return true`() {
        val server = MockImapServer()
        val imapConnection = simpleOpen(server)

        val result = imapConnection.isConnected

        assertThat(result).isTrue()
        server.verifyConnectionStillOpen()
        server.shutdown()
    }

    @Test
    fun isConnected_afterOpenAndClose_shouldReturnFalse() {
        val server = MockImapServer()
        val imapConnection = simpleOpen(server)
        imapConnection.close()

        val result = imapConnection.isConnected

        assertThat(result).isFalse()
        server.verifyConnectionClosed()
        server.shutdown()
    }

    @Test
    fun `close() without open() should not throw`() {
        val settings = createImapSettings()
        val imapConnection = createImapConnection(settings, socketFactory, oAuth2TokenProvider)

        imapConnection.close()
    }

    @Test
    fun `close() after open() should close connection`() {
        val server = MockImapServer()
        val imapConnection = simpleOpen(server)

        imapConnection.close()

        server.verifyConnectionClosed()
        server.shutdown()
    }

    @Test
    fun `isIdleCapable without IDLE capability should return false`() {
        val server = MockImapServer()
        val imapConnection = simpleOpen(server)

        val result = imapConnection.isIdleCapable

        assertThat(result).isFalse()
        server.shutdown()
    }

    @Test
    fun `isIdleCapable with IDLE capability should return true`() {
        val server = MockImapServer()
        val imapConnection = simpleOpenWithCapabilities(server, postAuthCapabilities = "IDLE")

        val result = imapConnection.isIdleCapable

        assertThat(result).isTrue()
        server.shutdown()
    }

    @Test
    fun `sendContinuation() should send line without tag`() {
        val server = MockImapServer().apply {
            simpleOpenDialog(postAuthCapabilities = "IDLE")
            expect("4 IDLE")
            output("+ idling")
            expect("DONE")
        }
        val imapConnection = startServerAndCreateImapConnection(server, authType = AuthType.PLAIN)

        imapConnection.open()
        imapConnection.sendCommand("IDLE", false)
        imapConnection.readResponse()
        imapConnection.sendContinuation("DONE")

        server.waitForInteractionToComplete()
        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `executeSimpleCommand() with OK response should return result`() {
        val server = MockImapServer().apply {
            simpleOpenDialog()
            expect("4 CREATE Folder")
            output("4 OK Folder created")
        }
        val imapConnection = startServerAndCreateImapConnection(server)

        val result = imapConnection.executeSimpleCommand("CREATE Folder")

        assertThat(result).hasSize(1)
        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `executeSimpleCommand() with NO response should throw NegativeImapResponseException`() {
        val server = MockImapServer().apply {
            simpleOpenDialog()
            expect("4 CREATE Folder")
            output("4 NO Folder exists")
        }
        val imapConnection = startServerAndCreateImapConnection(server)

        assertFailure {
            imapConnection.executeSimpleCommand("CREATE Folder")
        }.isInstanceOf<NegativeImapResponseException>()
            .prop(NegativeImapResponseException::lastResponse)
            .containsExactly("NO", "Folder exists")

        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `hasCapability() with not yet opened connection should connect and fetch capabilities`() {
        val server = MockImapServer().apply {
            simpleOpenDialog(postAuthCapabilities = "X-SOMETHING")
        }
        val imapConnection = startServerAndCreateImapConnection(server)

        val capabilityPresent = imapConnection.hasCapability("X-SOMETHING")

        assertThat(capabilityPresent).isTrue()
        server.verifyConnectionStillOpen()
        server.verifyInteractionCompleted()
    }

    @Test
    fun `disconnect during LOGIN fallback should throw AuthenticationFailedException`() {
        val server = MockImapServer().apply {
            output("* OK example.org server")
            expect("1 CAPABILITY")
            output("* CAPABILITY IMAP4 IMAP4REV1 AUTH=PLAIN")
            output("1 OK CAPABILITY Completed")
            expect("2 AUTHENTICATE PLAIN")
            output("+")
            expect("\u0000$USERNAME\u0000$PASSWORD".base64())
            output("2 NO AUTHENTICATE failed")
            expect("3 LOGIN \"$USERNAME\" \"$PASSWORD\"")
            output("* BYE IMAP server terminating connection")
            closeConnection()
        }
        val imapConnection = startServerAndCreateImapConnection(server)

        assertFailure {
            imapConnection.open()
        }.isInstanceOf<AuthenticationFailedException>()
            .prop(AuthenticationFailedException::messageFromServer)
            .isEqualTo("AUTHENTICATE failed")

        server.verifyInteractionCompleted()
    }

    private fun createImapConnection(
        settings: ImapSettings,
        socketFactory: TrustedSocketFactory,
        oAuth2TokenProvider: OAuth2TokenProvider,
    ): ImapConnection {
        val connectionGeneration = 1
        return RealImapConnection(
            settings,
            socketFactory,
            oAuth2TokenProvider,
            connectionGeneration,
            SOCKET_CONNECT_TIMEOUT,
            SOCKET_READ_TIMEOUT,
        )
    }

    private fun startServerAndCreateImapConnection(
        server: MockImapServer,
        connectionSecurity: ConnectionSecurity = ConnectionSecurity.NONE,
        authType: AuthType = AuthType.PLAIN,
        useCompression: Boolean = false,
        clientInfo: ImapClientInfo? = null,
    ): ImapConnection {
        server.start()

        val settings = SimpleImapSettings(
            host = server.host,
            port = server.port,
            connectionSecurity = connectionSecurity,
            authType = authType,
            username = USERNAME,
            password = PASSWORD,
            useCompression = useCompression,
            clientInfo = clientInfo,
        )

        return createImapConnection(settings, socketFactory, oAuth2TokenProvider)
    }

    private fun simpleOpen(server: MockImapServer): ImapConnection {
        return simpleOpenWithCapabilities(server, postAuthCapabilities = "")
    }

    private fun simpleOpenWithCapabilities(server: MockImapServer, postAuthCapabilities: String): ImapConnection {
        server.simpleOpenDialog(postAuthCapabilities)

        val imapConnection = startServerAndCreateImapConnection(server)
        imapConnection.open()

        return imapConnection
    }

    private fun MockImapServer.preAuthenticationDialog(capabilities: String = "") {
        output("* OK IMAP4rev1 Service Ready")
        expect("1 CAPABILITY")
        output("* CAPABILITY IMAP4 IMAP4REV1 $capabilities")
        output("1 OK CAPABILITY")
    }

    private fun MockImapServer.postAuthenticationDialogRequestingCapabilities(tag: Int = 3) {
        requestCapabilities(tag)
        simplePostAuthenticationDialog(tag + 1)
    }

    private fun MockImapServer.requestCapabilities(tag: Int) {
        expect("$tag CAPABILITY")
        output("* CAPABILITY IMAP4 IMAP4REV1 ")
        output("$tag OK CAPABILITY")
    }

    private fun MockImapServer.simplePostAuthenticationDialog(tag: Int) {
        expect("$tag LIST \"\" \"\"")
        output("* LIST () \"/\" foo/bar")
        output("$tag OK")
    }

    private fun MockImapServer.simpleOpenDialog(postAuthCapabilities: String = "") {
        simplePreAuthAndLoginDialog(postAuthCapabilities)
        simplePostAuthenticationDialog(3)
    }

    private fun MockImapServer.simplePreAuthAndLoginDialog(postAuthCapabilities: String = "") {
        preAuthenticationDialog()
        expect("2 LOGIN \"$USERNAME\" \"$PASSWORD\"")
        output("2 OK [CAPABILITY $postAuthCapabilities] LOGIN completed")
    }

    private fun createImapSettings(host: String = "irrelevant"): ImapSettings {
        return SimpleImapSettings(
            host = host,
            port = 143,
            authType = AuthType.PLAIN,
            username = "irrelevant",
        )
    }
}

class TestTokenProvider : OAuth2TokenProvider {
    private var invalidationCount = 0

    override fun getToken(timeoutMillis: Long): String {
        assertThat(timeoutMillis).isEqualTo(OAuth2TokenProvider.OAUTH2_TIMEOUT.toLong())

        return when (invalidationCount) {
            0 -> XOAUTH_TOKEN
            1 -> XOAUTH_TOKEN_2
            else -> {
                throw AuthenticationFailedException(
                    "Ran out of auth tokens. invalidateToken() called too often?",
                )
            }
        }
    }

    override fun invalidateToken() {
        invalidationCount++
    }
}

private fun String.base64() = this.encodeUtf8().base64()
