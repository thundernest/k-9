package com.fsck.k9.mail.store.imap

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.cause
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.fsck.k9.mail.filter.FixedLengthInputStream
import com.fsck.k9.mail.filter.PeekableInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Test

class ImapResponseParserTest {
    private var peekableInputStream: PeekableInputStream? = null

    @Test
    fun `readResponse() with untagged OK response`() {
        val parser = createParserWithResponses("* OK")

        val response = parser.readResponse()

        assertThat(response).containsExactly("OK")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with untagged OK response containing text`() {
        val parser = createParserWithResponses("* OK Some text here")

        val response = parser.readResponse()

        assertThat(response).containsExactly("OK", "Some text here")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with untagged OK response containing resp-text code`() {
        val parser = createParserWithResponses("* OK [UIDVALIDITY 3857529045]")

        val response = parser.readResponse()

        assertThat(response).hasSize(2)
        assertThat(response).index(0).isEqualTo("OK")
        assertThat(response).index(1).isInstanceOf<ImapList>().containsExactly("UIDVALIDITY", "3857529045")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with untagged OK response containing resp-text code and text`() {
        val parser = createParserWithResponses("* OK [token1 token2] {x} test [...]")

        val response = parser.readResponse()

        assertThat(response).hasSize(3)
        assertThat(response).index(0).isEqualTo("OK")
        assertThat(response).index(1).isInstanceOf<ImapList>().containsExactly("token1", "token2")
        assertThat(response).index(2).isEqualTo("{x} test [...]")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readStatusResponse() with OK response`() {
        val parser = createParserWithResponses(
            "* COMMAND BAR\tBAZ",
            "TAG OK COMMAND completed",
        )

        val responses = parser.readStatusResponse("TAG", null, null, null)

        assertThat(responses).hasSize(2)
        assertThat(responses).index(0).containsExactly("COMMAND", "BAR", "BAZ")
        assertThat(responses).index(1).containsExactly("OK", "COMMAND completed")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readStatusResponse() should only deliver untagged responses to UntaggedHandler`() {
        val parser = createParserWithResponses(
            "* UNTAGGED",
            "A2 OK COMMAND completed",
        )
        val untaggedHandler = TestUntaggedHandler()

        parser.readStatusResponse("A2", null, null, untaggedHandler)

        assertThat(untaggedHandler.responses).hasSize(1)
        assertThat(untaggedHandler.responses).index(0).containsExactly("UNTAGGED")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readStatusResponse() should skip tagged response that does not match tag`() {
        val parser = createParserWithResponses(
            "* UNTAGGED",
            "* 0 EXPUNGE",
            "* 42 EXISTS",
            "A1 COMMAND BAR BAZ",
            "A2 OK COMMAND completed",
        )
        val untaggedHandler = TestUntaggedHandler()

        val responses = parser.readStatusResponse("A2", null, null, untaggedHandler)

        assertThat(responses).hasSize(3)
        assertThat(responses).index(0).containsExactly("0", "EXPUNGE")
        assertThat(responses).index(1).containsExactly("42", "EXISTS")
        assertThat(responses).index(2).containsExactly("OK", "COMMAND completed")

        assertThat(untaggedHandler.responses).hasSize(3)
        assertThat(untaggedHandler.responses).index(0).containsExactly("UNTAGGED")
        assertThat(untaggedHandler.responses).index(1).containsExactly("0", "EXPUNGE")
        assertThat(untaggedHandler.responses).index(2).containsExactly("42", "EXISTS")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readStatusResponse() should deliver untagged responses to UntaggedHandler even on negative tagged response`() {
        val parser = createParserWithResponses(
            "* untagged",
            "A2 NO Bad response",
        )
        val untaggedHandler = TestUntaggedHandler()

        try {
            parser.readStatusResponse("A2", null, null, untaggedHandler)
        } catch (ignored: NegativeImapResponseException) {
        }

        assertThat(untaggedHandler.responses).hasSize(1)
        assertThat(untaggedHandler.responses).index(0).containsExactly("untagged")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readStatusResponse() with error response should throw`() {
        val parser = createParserWithResponses(
            "* COMMAND BAR BAZ",
            "TAG ERROR COMMAND errored",
        )

        assertFailure {
            parser.readStatusResponse("TAG", null, null, null)
        }.isInstanceOf<NegativeImapResponseException>()
    }

    @Test
    fun `readResponse() with resp-text code containing a list`() {
        val parser = createParserWithResponses(
            """* OK [PERMANENTFLAGS (\Answered \Flagged \Deleted \Seen \Draft NonJunk ${"$"}MDNSent \*)] """ +
                "Flags permitted.",
        )

        val response = parser.readResponse()

        assertThat(response).hasSize(3)
        assertThat(response).index(0).isEqualTo("OK")
        assertThat(response).index(1).isInstanceOf<ImapList>().all {
            index(0).isEqualTo("PERMANENTFLAGS")
            index(1).isInstanceOf<ImapList>().containsExactly(
                """\Answered""",
                """\Flagged""",
                """\Deleted""",
                """\Seen""",
                """\Draft""",
                "NonJunk",
                "\$MDNSent",
                """\*""",
            )
        }

        assertThat(response).index(2).isEqualTo("Flags permitted.")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with untagged EXISTS response`() {
        val parser = createParserWithResponses("* 23 EXISTS")

        val response = parser.readResponse()

        assertThat(response).hasSize(2)
        assertThat(response).transform { it.getNumber(0) }.isEqualTo(23)
        assertThat(response).transform { it.getString(1) }.isEqualTo("EXISTS")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() should throw if stream ends before end of line is found`() {
        val parser = createParserWithData("* OK Some text ")

        assertFailure {
            parser.readResponse()
        }.isInstanceOf<IOException>()
    }

    @Test
    fun `readResponse() with command continuation`() {
        val parser = createParserWithResponses("+ Ready for additional command text")

        val response = parser.readResponse()

        assertThat(response.isContinuationRequested).isTrue()
        assertThat(response).containsExactly("Ready for additional command text")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with literal`() {
        val parser = createParserWithResponses("* {4}\r\ntest")

        val response = parser.readResponse()

        assertThat(response).containsExactly("test")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with empty literal`() {
        val parser = createParserWithResponses("* {0}\r\n")

        val response = parser.readResponse()

        assertThat(response).containsExactly("")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with literal containing negative size`() {
        val parser = createParserWithResponses("* {-1}")

        assertFailure {
            parser.readResponse()
        }.isInstanceOf<ImapResponseParserException>()
            .hasMessage("Invalid value for size of literal string")
    }

    @Test
    fun `readResponse() with literal size exceeding Int`() {
        val parser = createParserWithResponses("* {2147483648}")

        assertFailure {
            parser.readResponse()
        }.isInstanceOf<ImapResponseParserException>()
            .hasMessage("Invalid value for size of literal string")
    }

    @Test
    fun `readResponse() with invalid characters for literal size`() {
        val parser = createParserWithResponses("* {invalid}")

        assertFailure {
            parser.readResponse()
        }.isInstanceOf<ImapResponseParserException>()
            .hasMessage("Invalid value for size of literal string")
    }

    @Test
    fun `readResponse() should throw when end of stream is reached while reading literal`() {
        val parser = createParserWithData("* {4}\r\nabc")

        assertFailure {
            parser.readResponse()
        }.isInstanceOf<IOException>()
    }

    @Test
    fun `readResponse() with literal should include return value of ImapResponseCallback_foundLiteral() in response`() {
        val parser = createParserWithResponses("* {4}\r\ntest")
        val callback = TestImapResponseCallback.readBytesAndReturn(4, "replacement value")

        val response = parser.readResponse(callback)

        assertThat(response).containsExactly("replacement value")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with literal should read literal when ImapResponseCallback_foundLiteral() returns null`() {
        val parser = createParserWithResponses("* {4}\r\ntest")
        val callback = TestImapResponseCallback.readBytesAndReturn(0, null)

        val response = parser.readResponse(callback)

        assertThat(response).containsExactly("test")
        assertThat(callback.foundLiteralCalled).isTrue()
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with partly consuming callback returning null should throw`() {
        val parser = createParserWithResponses("* {4}\r\ntest")
        val callback = TestImapResponseCallback.readBytesAndReturn(2, null)

        assertFailure {
            parser.readResponse(callback)
        }.isInstanceOf<AssertionError>()
            .hasMessage("Callback consumed some data but returned no result")
    }

    @Test
    fun `readResponse() with partly consuming callback that throws should read all data and throw`() {
        val parser = createParserWithResponses("* {4}\r\ntest")
        val callback = TestImapResponseCallback.readBytesAndThrow(2)

        assertFailure {
            parser.readResponse(callback)
        }.isInstanceOf<ImapResponseParserException>()
            .all {
                hasMessage("readResponse(): Exception in callback method")
                cause().isNotNull().isInstanceOf<ImapResponseParserTestException>()
            }
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with callback that throws repeatedly should consume all input and throw first exception`() {
        val parser = createParserWithResponses("* {3}\r\none {3}\r\ntwo")
        val callback = TestImapResponseCallback.readBytesAndThrow(3)

        assertFailure {
            parser.readResponse(callback)
        }.isInstanceOf<ImapResponseParserException>()
            .all {
                hasMessage("readResponse(): Exception in callback method")
                cause().isNotNull().isInstanceOf<ImapResponseParserTestException>()
                    .prop(ImapResponseParserTestException::instanceNumber).isEqualTo(0)
            }
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with callback not consuming the entire literal should skip the rest of the literal`() {
        val parser = createParserWithResponses("* {3}\r\none two")
        val callback = TestImapResponseCallback.readBytesAndReturn(2, "replacement value")

        val response = parser.readResponse(callback)

        assertThat(response).containsExactly("replacement value", "two")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with callback not consuming and throwing should read response and throw`() {
        val parser = createParserWithResponses("* {4}\r\ntest")
        val callback = TestImapResponseCallback.readBytesAndThrow(0)

        assertFailure {
            parser.readResponse(callback)
        }.isInstanceOf<ImapResponseParserException>()
            .hasMessage("readResponse(): Exception in callback method")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with callback throwing IOException should re-throw that exception`() {
        val parser = createParserWithResponses("* {4}\r\ntest")
        val exception = IOException()
        val callback = ImapResponseCallback { _, _ -> throw exception }

        assertFailure {
            parser.readResponse(callback)
        }.isSameInstanceAs(exception)
    }

    @Test
    fun `readResponse() with quoted string containing an escaped quote character`() {
        val parser = createParserWithResponses("""* "qu\"oted"""")

        val response = parser.readResponse()

        assertThat(response).containsExactly("""qu"oted""")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with UTF-8 data in quoted string`() {
        val parser = createParserWithResponses("""* "quöted"""")

        val response = parser.readResponse()

        assertThat(response).containsExactly("quöted")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() should throw when end of stream is reached before end of quoted string`() {
        val parser = createParserWithResponses("* \"abc")

        assertFailure {
            parser.readResponse()
        }.isInstanceOf<IOException>()
    }

    @Test
    fun `readResponse() should throw if end of stream is reached before end of atom`() {
        val parser = createParserWithData("* abc")

        assertFailure {
            parser.readResponse()
        }.isInstanceOf<IOException>()
    }

    @Test
    fun `readResponse() should throw if untagged response indicator is not followed by a space`() {
        val parser = createParserWithResponses("*")

        assertFailure {
            parser.readResponse()
        }.isInstanceOf<IOException>()
    }

    @Test
    fun `readResponse() with LIST response containing folder name with brackets`() {
        val parser = createParserWithResponses("""* LIST (\HasNoChildren) "." [FolderName]""")

        val response = parser.readResponse()

        assertThat(response).hasSize(4)
        assertThat(response).index(0).isEqualTo("LIST")
        assertThat(response).index(1).isInstanceOf<ImapList>().containsExactly("""\HasNoChildren""")
        assertThat(response).index(2).isEqualTo(".")
        assertThat(response).index(3).isEqualTo("[FolderName]")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with LIST response containing folder name with UTF8`() {
        val parser = createParserWithResponses("""* LIST (\HasNoChildren) "." "萬里長城"""")

        val response = parser.readResponse()

        assertThat(response).hasSize(4)
        assertThat(response).index(3).isEqualTo("萬里長城")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with LIST response containing folder name with parentheses should throw`() {
        val parser = createParserWithResponses("""* LIST (\NoInferiors) "/" Root/Folder/Subfolder()""")

        assertFailure {
            parser.readResponse()
        }.isInstanceOf<IOException>()
    }

    @Test
    fun `readResponse() should read whole LIST response line`() {
        val parser = createParserWithResponses(
            """* LIST (\HasNoChildren) "." [FolderName]""",
            "TAG OK [List complete]",
        )
        parser.readResponse()

        val responseTwo = parser.readResponse()

        assertThat(responseTwo.tag).isEqualTo("TAG")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with LIST response containing NIL`() {
        val parser = createParserWithResponses("""* LIST (\NoInferiors) NIL INBOX""")

        val response = parser.readResponse()

        assertThat(response).hasSize(4)
        assertThat(response).index(0).isEqualTo("LIST")
        assertThat(response).index(1).isInstanceOf<ImapList>().containsExactly("""\NoInferiors""")
        assertThat(response).index(2).isNull()
        assertThat(response).index(3).isEqualTo("INBOX")
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readResponse() with tagged response missing completion code should throw`() {
        val parser = createParserWithResponses("tag ")

        assertFailure {
            parser.readResponse()
        }.isInstanceOf<ImapResponseParserException>()
            .hasMessage("Unexpected non-string token")
    }

    @Test
    fun `readResponse() with list as first token should throw`() {
        val parser = createParserWithResponses("* [1 2] 3")

        assertFailure {
            parser.readResponse()
        }.isInstanceOf<ImapResponseParserException>()
            .hasMessage("Unexpected non-string token")
    }

    @Test
    fun `readResponse() with FETCH response`() {
        val parser = createParserWithResponses(
            "* 1 FETCH (" +
                "UID 23 " +
                """INTERNALDATE "01-Jul-2015 12:34:56 +0200" """ +
                "RFC822.SIZE 3456 " +
                """BODY[HEADER.FIELDS (date subject from)] "<headers>" """ +
                """FLAGS (\Seen)""" +
                ")",
        )

        val response = parser.readResponse()

        assertThat(response).hasSize(3)
        assertThat(response).index(0).isEqualTo("1")
        assertThat(response).index(1).isEqualTo("FETCH")
        assertThat(response).index(2).isInstanceOf<ImapList>().all {
            hasSize(11)
            index(0).isEqualTo("UID")
            index(1).isEqualTo("23")
            index(2).isEqualTo("INTERNALDATE")
            index(3).isEqualTo("01-Jul-2015 12:34:56 +0200")
            index(4).isEqualTo("RFC822.SIZE")
            index(5).isEqualTo("3456")
            index(6).isEqualTo("BODY")
            index(7).isInstanceOf<ImapList>().all {
                hasSize(2)
                index(0).isEqualTo("HEADER.FIELDS")
                index(1).isInstanceOf<ImapList>().containsExactly("date", "subject", "from")
            }
            index(8).isEqualTo("<headers>")
            index(9).isEqualTo("FLAGS")
            index(10).isInstanceOf<ImapList>().containsExactly("""\Seen""")
        }
        assertThatAllInputWasConsumed()
    }

    @Test
    fun `readStatusResponse() with NO response should throw`() {
        val parser = createParserWithResponses("1 NO")

        assertFailure {
            parser.readStatusResponse("1", "COMMAND", "[logId]", null)
        }.isInstanceOf<NegativeImapResponseException>()
            .hasMessage("Command: COMMAND; response: #1# [NO]")
    }

    @Test
    fun `readStatusResponse() with NO response and alert text should throw with alert text`() {
        val parser = createParserWithResponses("1 NO [ALERT] Access denied\r\n")

        assertFailure {
            parser.readStatusResponse("1", "COMMAND", "[logId]", null)
        }.isInstanceOf<NegativeImapResponseException>()
            .prop(NegativeImapResponseException::alertText).isEqualTo("Access denied")
    }

    private fun createParserWithResponses(vararg responses: String): ImapResponseParser {
        val response = responses.joinToString(separator = "\r\n", postfix = "\r\n")
        return createParserWithData(response)
    }

    private fun createParserWithData(response: String): ImapResponseParser {
        val byteArrayInputStream = ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
        peekableInputStream = PeekableInputStream(byteArrayInputStream)

        return ImapResponseParser(peekableInputStream)
    }

    private fun assertThatAllInputWasConsumed() {
        assertThat(peekableInputStream).isNotNull().prop(PeekableInputStream::available).isEqualTo(0)
    }
}

private class TestImapResponseCallback(
    private val readNumberOfBytes: Long,
    private val returnValue: Any?,
    private val throwException: Boolean,
) : ImapResponseCallback {
    private var exceptionCount = 0
    var foundLiteralCalled = false

    override fun foundLiteral(response: ImapResponse, literal: FixedLengthInputStream): Any? {
        foundLiteralCalled = true

        var skipBytes = readNumberOfBytes
        while (skipBytes > 0) {
            skipBytes -= literal.skip(skipBytes)
        }

        if (throwException) {
            throw ImapResponseParserTestException(exceptionCount++)
        }

        return returnValue
    }

    companion object {
        fun readBytesAndReturn(readNumberOfBytes: Int, returnValue: Any?): TestImapResponseCallback {
            return TestImapResponseCallback(readNumberOfBytes.toLong(), returnValue, false)
        }

        fun readBytesAndThrow(readNumberOfBytes: Int): TestImapResponseCallback {
            return TestImapResponseCallback(readNumberOfBytes.toLong(), null, true)
        }
    }
}

private class ImapResponseParserTestException(val instanceNumber: Int) : RuntimeException()

private class TestUntaggedHandler : UntaggedHandler {
    val responses = mutableListOf<ImapResponse>()

    override fun handleAsyncUntaggedResponse(response: ImapResponse) {
        responses.add(response)
    }
}
