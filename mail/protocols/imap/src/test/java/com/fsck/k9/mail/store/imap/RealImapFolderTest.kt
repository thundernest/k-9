package com.fsck.k9.mail.store.imap

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.cause
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.extracting
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.fail
import com.fsck.k9.mail.Body
import com.fsck.k9.mail.DefaultBodyFactory
import com.fsck.k9.mail.FetchProfile
import com.fsck.k9.mail.Flag
import com.fsck.k9.mail.MessageRetrievalListener
import com.fsck.k9.mail.MessagingException
import com.fsck.k9.mail.Part
import com.fsck.k9.mail.internet.BinaryTempFileBody
import com.fsck.k9.mail.internet.MimeHeader
import com.fsck.k9.mail.store.imap.ImapResponseHelper.createImapResponse
import com.fsck.k9.mail.store.imap.ImapResponseHelper.createImapResponseList
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Date
import java.util.TimeZone
import okio.Buffer
import org.apache.james.mime4j.util.MimeUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anySet
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever

class RealImapFolderTest {
    private val imapStoreConfig = FakeImapStoreConfig()
    private val internalImapStore = object : InternalImapStore {
        override val logLabel = "Account"
        override val config = imapStoreConfig
        override fun getCombinedPrefix() = ""
        override fun getPermanentFlagsIndex() = mutableSetOf<Flag>()
    }
    private val imapConnection = mock<ImapConnection>()
    private val testConnectionManager = TestConnectionManager(imapConnection)

    private lateinit var tempDirectory: File

    @Before
    fun setUp() {
        tempDirectory = Files.createTempDirectory("RealImapFolderTest").toFile()
        BinaryTempFileBody.setTempDirectory(tempDirectory)
    }

    @After
    fun tearDown() {
        tempDirectory.deleteRecursively()
    }

    @Test
    fun open_readWrite_shouldOpenFolder() {
        val imapFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)

        imapFolder.open(OpenMode.READ_WRITE)

        assertThat(imapFolder.isOpen).isTrue()
    }

    @Test
    fun open_readOnly_shouldOpenFolder() {
        val imapFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)

        imapFolder.open(OpenMode.READ_ONLY)

        assertThat(imapFolder.isOpen).isTrue()
    }

    @Test
    fun open_shouldFetchMessageCount() {
        val imapFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)

        imapFolder.open(OpenMode.READ_WRITE)

        assertThat(imapFolder.messageCount).isEqualTo(23)
    }

    @Test
    fun open_readWrite_shouldMakeGetModeReturnReadWrite() {
        val imapFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)

        imapFolder.open(OpenMode.READ_WRITE)

        assertThat(imapFolder.mode).isEqualTo(OpenMode.READ_WRITE)
    }

    @Test
    fun open_readOnly_shouldMakeGetModeReturnReadOnly() {
        val imapFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)

        imapFolder.open(OpenMode.READ_ONLY)

        assertThat(imapFolder.mode).isEqualTo(OpenMode.READ_ONLY)
    }

    @Test
    fun open_shouldMakeExistReturnTrueWithoutExecutingAdditionalCommands() {
        val imapFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)

        imapFolder.open(OpenMode.READ_WRITE)

        assertThat(imapFolder.exists()).isTrue()
        verify(imapConnection, times(1)).executeSimpleCommand(anyString())
    }

    @Test
    fun open_calledTwice_shouldReuseSameImapConnection() {
        val imapFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        imapFolder.open(OpenMode.READ_WRITE)

        imapFolder.open(OpenMode.READ_WRITE)

        assertThat(testConnectionManager.numberOfGetConnectionCalls).isEqualTo(1)
    }

    @Test
    fun open_withConnectionThrowingOnReUse_shouldCreateNewImapConnection() {
        val imapFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        imapFolder.open(OpenMode.READ_WRITE)

        doThrow(IOException::class).whenever(imapConnection).executeSimpleCommand(Commands.NOOP)
        imapFolder.open(OpenMode.READ_WRITE)

        assertThat(testConnectionManager.numberOfGetConnectionCalls).isEqualTo(2)
    }

    @Test
    fun open_withIoException_shouldThrowMessagingException() {
        val imapFolder = createFolder("Folder")
        doThrow(IOException::class).whenever(imapConnection).executeSimpleCommand("SELECT \"Folder\"")

        assertFailure {
            imapFolder.open(OpenMode.READ_WRITE)
        }.isInstanceOf<MessagingException>()
            .cause().isNotNull().isInstanceOf<IOException>()
    }

    @Test
    fun open_withMessagingException_shouldThrowMessagingException() {
        val imapFolder = createFolder("Folder")
        doThrow(MessagingException::class).whenever(imapConnection).executeSimpleCommand("SELECT \"Folder\"")

        assertFailure {
            imapFolder.open(OpenMode.READ_WRITE)
        }.isInstanceOf<MessagingException>()
    }

    @Test
    fun open_withoutExistsResponse_shouldThrowMessagingException() {
        val imapFolder = createFolder("Folder")
        val selectResponses = listOf(
            createImapResponse("* OK [UIDNEXT 57576] Predicted next UID"),
            createImapResponse("2 OK [READ-WRITE] Select completed."),
        )
        whenever(imapConnection.executeSimpleCommand("SELECT \"Folder\"")).thenReturn(selectResponses)

        assertFailure {
            imapFolder.open(OpenMode.READ_WRITE)
        }.isInstanceOf<MessagingException>()
            .hasMessage("Did not find message count during open")
    }

    @Test
    fun close_shouldCloseImapFolder() {
        val imapFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        imapFolder.open(OpenMode.READ_WRITE)

        imapFolder.close()

        assertThat(imapFolder.isOpen).isFalse()
    }

    @Test
    fun exists_withClosedFolder_shouldOpenConnectionAndIssueStatusCommand() {
        val imapFolder = createFolder("Folder")

        imapFolder.exists()

        verify(imapConnection).executeSimpleCommand("STATUS \"Folder\" (UIDVALIDITY)")
    }

    @Test
    fun exists_withoutNegativeImapResponse_shouldReturnTrue() {
        val imapFolder = createFolder("Folder")

        val folderExists = imapFolder.exists()

        assertThat(folderExists).isTrue()
    }

    @Test
    fun exists_withNegativeImapResponse_shouldReturnFalse() {
        val imapFolder = createFolder("Folder")
        doThrow(NegativeImapResponseException::class)
            .whenever(imapConnection).executeSimpleCommand("STATUS \"Folder\" (UIDVALIDITY)")

        val folderExists = imapFolder.exists()

        assertThat(folderExists).isFalse()
    }

    @Test
    fun create_withClosedFolder_shouldOpenConnectionAndIssueCreateCommand() {
        val imapFolder = createFolder("Folder")

        imapFolder.create()

        verify(imapConnection).executeSimpleCommand("CREATE \"Folder\"")
    }

    @Test
    fun create_withoutNegativeImapResponse_shouldReturnTrue() {
        val imapFolder = createFolder("Folder")

        val success = imapFolder.create()

        assertThat(success).isTrue()
    }

    @Test
    fun create_withNegativeImapResponse_shouldReturnFalse() {
        val imapFolder = createFolder("Folder")
        doThrow(NegativeImapResponseException::class).whenever(imapConnection).executeSimpleCommand("CREATE \"Folder\"")

        val success = imapFolder.create()

        assertThat(success).isFalse()
    }

    @Test
    fun copyMessages_withEmptyMessageList_shouldReturnNull() {
        val sourceFolder = createFolder("Source")
        val destinationFolder = createFolder("Destination")
        val messages = emptyList<ImapMessage>()

        val uidMapping = sourceFolder.copyMessages(messages, destinationFolder)

        assertThat(uidMapping).isNull()
    }

    @Test
    fun copyMessages_withClosedFolder_shouldThrow() {
        val sourceFolder = createFolder("Source")
        val destinationFolder = createFolder("Destination")
        val messages = listOf(mock<ImapMessage>())

        assertFailure {
            sourceFolder.copyMessages(messages, destinationFolder)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Source' is not open.")
    }

    @Test
    fun copyMessages() {
        val sourceFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        val destinationFolder = createFolder("Destination")
        val messages = listOf(createImapMessage("1"))
        setupCopyResponse("x OK [COPYUID 23 1 101] Success")
        sourceFolder.open(OpenMode.READ_WRITE)

        val uidMapping = sourceFolder.copyMessages(messages, destinationFolder)

        assertThat(uidMapping).isNotNull().containsOnly("1" to "101")
    }

    @Test
    fun `moveMessages() on closed folder should throw`() {
        val sourceFolder = createFolder("Source")
        val destinationFolder = createFolder("Destination")
        val messages = listOf(createImapMessage("1"))

        assertFailure {
            sourceFolder.moveMessages(messages, destinationFolder)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Source' is not open.")

        verifyNoMoreInteractions(imapConnection)
    }

    @Test
    fun `moveMessages() on folder opened as read-only should throw`() {
        val sourceFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        sourceFolder.open(OpenMode.READ_ONLY)
        val destinationFolder = createFolder("Destination")
        val messages = listOf(createImapMessage("1"))

        assertFailure {
            sourceFolder.moveMessages(messages, destinationFolder)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' needs to be opened for read-write access.")
    }

    @Test
    fun `moveMessages() with MOVE extension and tagged COPYUID response`() {
        val sourceFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        imapConnection.stub {
            on { hasCapability(Capabilities.MOVE) } doReturn true
        }
        val destinationFolder = createFolder("Destination")
        val messages = listOf(createImapMessage("1"))
        setupMoveResponses("x OK [COPYUID 23 1 101] Success")
        sourceFolder.open(OpenMode.READ_WRITE)

        val uidMapping = sourceFolder.moveMessages(messages, destinationFolder)

        assertCommandWithIdsIssued("UID MOVE 1 \"Destination\"")
        assertThat(uidMapping).isNotNull().containsOnly("1" to "101")
    }

    @Test
    fun `moveMessages() with MOVE extension and untagged COPYUID response`() {
        val sourceFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        imapConnection.stub {
            on { hasCapability(Capabilities.MOVE) } doReturn true
        }
        val destinationFolder = createFolder("Destination")
        val messages = listOf(createImapMessage("1"))
        setupMoveResponses(
            "* OK [COPYUID 23 1 101]",
            "* 1 EXPUNGE",
            "x OK MOVE completed",
        )
        sourceFolder.open(OpenMode.READ_WRITE)

        val uidMapping = sourceFolder.moveMessages(messages, destinationFolder)

        assertCommandWithIdsIssued("UID MOVE 1 \"Destination\"")
        assertThat(uidMapping).isNotNull().containsOnly("1" to "101")
    }

    @Test
    fun moveMessages_shouldCopyMessages() {
        val sourceFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        val destinationFolder = createFolder("Destination")
        val messages = listOf(createImapMessage("1"))
        setupCopyResponse("x OK [COPYUID 23 1 101] Success")
        sourceFolder.open(OpenMode.READ_WRITE)

        val uidMapping = sourceFolder.moveMessages(messages, destinationFolder)

        assertThat(uidMapping).isNotNull().containsOnly("1" to "101")
    }

    @Test
    fun `moveMessages() with expungeImmediately = true should delete and expunge`() {
        imapStoreConfig.expungeImmediately = true
        val sourceFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        imapConnection.stub {
            on { isUidPlusCapable } doReturn false
        }
        val destinationFolder = createFolder("Destination")
        val messages = listOf(createImapMessage("1"))
        sourceFolder.open(OpenMode.READ_WRITE)

        sourceFolder.moveMessages(messages, destinationFolder)

        assertCommandWithIdsIssued("UID STORE 1 +FLAGS.SILENT (\\Deleted)")
        assertCommandIssued("EXPUNGE")
    }

    @Test
    fun `moveMessages() with expungeImmediately = false should delete but not expunge`() {
        imapStoreConfig.expungeImmediately = false
        val sourceFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        imapConnection.stub {
            on { isUidPlusCapable } doReturn false
        }
        val destinationFolder = createFolder("Destination")
        val messages = listOf(createImapMessage("1"))
        sourceFolder.open(OpenMode.READ_WRITE)

        sourceFolder.moveMessages(messages, destinationFolder)

        assertCommandWithIdsIssued("UID STORE 1 +FLAGS.SILENT (\\Deleted)")
        verify(imapConnection, never()).executeSimpleCommand("EXPUNGE")
    }

    @Test
    fun `moveMessages() should delete messages from source folder and issue UID EXPUNGE command when available`() {
        val sourceFolder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        imapConnection.stub {
            on { isUidPlusCapable } doReturn true
        }
        val destinationFolder = createFolder("Destination")
        val messages = listOf(createImapMessage("1"))
        sourceFolder.open(OpenMode.READ_WRITE)

        sourceFolder.moveMessages(messages, destinationFolder)

        assertCommandWithIdsIssued("UID STORE 1 +FLAGS.SILENT (\\Deleted)")
        assertCommandWithIdsIssued("UID EXPUNGE 1")
    }

    @Test
    fun moveMessages_withEmptyMessageList_shouldReturnNull() {
        val sourceFolder = createFolder("Source")
        val destinationFolder = createFolder("Destination")
        val messages = emptyList<ImapMessage>()

        val uidMapping = sourceFolder.moveMessages(messages, destinationFolder)

        assertThat(uidMapping).isNull()
    }

    @Test
    fun getUnreadMessageCount_withClosedFolder_shouldThrow() {
        val folder = createFolder("FolderName")

        assertFailure {
            folder.unreadMessageCount
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'FolderName' is not open.")
    }

    @Test
    fun getUnreadMessageCount_connectionThrowsIOException_shouldThrowMessagingException() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        whenever(imapConnection.executeSimpleCommand("SEARCH 1:* UNSEEN NOT DELETED")).thenThrow(IOException())
        folder.open(OpenMode.READ_WRITE)

        assertFailure {
            folder.unreadMessageCount
        }.isInstanceOf<MessagingException>()
            .hasMessage("IO Error")
    }

    @Test
    fun getUnreadMessageCount() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        val imapResponses = listOf(createImapResponse("* SEARCH 1 2 3"))
        whenever(imapConnection.executeSimpleCommand("SEARCH 1:* UNSEEN NOT DELETED")).thenReturn(imapResponses)
        folder.open(OpenMode.READ_WRITE)

        val unreadMessageCount = folder.unreadMessageCount

        assertThat(unreadMessageCount).isEqualTo(3)
    }

    @Test
    fun getFlaggedMessageCount_withClosedFolder_shouldThrow() {
        val folder = createFolder("FolderName")

        assertFailure {
            folder.flaggedMessageCount
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'FolderName' is not open.")
    }

    @Test
    fun getFlaggedMessageCount() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        val imapResponses = listOf(
            createImapResponse("* SEARCH 1 2"),
            createImapResponse("* SEARCH 23 42"),
        )
        whenever(imapConnection.executeSimpleCommand("SEARCH 1:* FLAGGED NOT DELETED")).thenReturn(imapResponses)
        folder.open(OpenMode.READ_WRITE)

        val flaggedMessageCount = folder.flaggedMessageCount

        assertThat(flaggedMessageCount).isEqualTo(4)
    }

    @Test
    fun getHighestUid() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        setupUidSearchResponses("* SEARCH 42")
        folder.open(OpenMode.READ_WRITE)

        val highestUid = folder.highestUid

        assertThat(highestUid).isEqualTo(42L)
    }

    @Test
    fun getHighestUid_imapConnectionThrowsNegativesResponse_shouldReturnMinusOne() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        doThrow(NegativeImapResponseException::class).whenever(imapConnection).executeSimpleCommand("UID SEARCH *:*")
        folder.open(OpenMode.READ_WRITE)

        val highestUid = folder.highestUid

        assertThat(highestUid).isEqualTo(-1L)
    }

    @Test
    fun getHighestUid_imapConnectionThrowsIOException_shouldThrowMessagingException() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        doThrow(IOException::class).whenever(imapConnection).executeSimpleCommand("UID SEARCH *:*")
        folder.open(OpenMode.READ_WRITE)

        assertFailure {
            folder.highestUid
        }.isInstanceOf<MessagingException>()
            .hasMessage("IO Error")
    }

    @Test
    fun getMessages_withoutDateConstraint() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        setupUidSearchResponses("* SEARCH 3", "* SEARCH 5", "* SEARCH 6")
        folder.open(OpenMode.READ_WRITE)

        val messages = folder.getMessages(1, 10, null, null)

        assertThat(messages).isNotNull()
            .extracting { it.uid }.containsOnly("3", "5", "6")
    }

    @Test
    fun getMessages_withDateConstraint() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        setupUidSearchResponses("* SEARCH 47", "* SEARCH 18")
        folder.open(OpenMode.READ_WRITE)

        val messages = folder.getMessages(1, 10, Date(1454719826000L), null)

        assertThat(messages).isNotNull()
            .extracting { it.uid }.containsOnly("18", "47")
    }

    @Test
    fun getMessages_withListener_shouldCallListener() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        setupUidSearchResponses("* SEARCH 99")
        folder.open(OpenMode.READ_WRITE)
        val listener = createMessageRetrievalListener()

        val messages = folder.getMessages(1, 10, null, listener)

        verify(listener).messageFinished(messages[0])
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun getMessages_withInvalidStartArgument_shouldThrow() {
        val folder = createFolder("Folder")

        assertFailure {
            folder.getMessages(0, 10, null, null)
        }.isInstanceOf<MessagingException>()
            .hasMessage("Invalid message set 0 10")
    }

    @Test
    fun getMessages_withInvalidEndArgument_shouldThrow() {
        val folder = createFolder("Folder")

        assertFailure {
            folder.getMessages(10, 0, null, null)
        }.isInstanceOf<MessagingException>()
            .hasMessage("Invalid message set 10 0")
    }

    @Test
    fun getMessages_withEndArgumentSmallerThanStartArgument_shouldThrow() {
        val folder = createFolder("Folder")

        assertFailure {
            folder.getMessages(10, 5, null, null)
        }.isInstanceOf<MessagingException>()
            .hasMessage("Invalid message set 10 5")
    }

    @Test
    fun getMessages_withClosedFolder_shouldThrow() {
        val folder = createFolder("FolderName")

        assertFailure {
            folder.getMessages(1, 5, null, null)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'FolderName' is not open.")
    }

    @Test
    fun getMessages_sequenceNumbers_withClosedFolder_shouldThrow() {
        val folder = createFolder("FolderName")

        assertFailure {
            folder.getMessages(setOf(1L, 2L, 5L), false, null)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'FolderName' is not open.")
    }

    @Test
    fun getMessages_sequenceNumbers() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        setupUidSearchResponses("* SEARCH 17", "* SEARCH 18", "* SEARCH 49")
        folder.open(OpenMode.READ_WRITE)

        val messages = folder.getMessages(setOf(1L, 2L, 5L), false, null)

        assertThat(messages).isNotNull()
            .extracting { it.uid }.containsOnly("17", "18", "49")
    }

    @Test
    fun getMessages_sequenceNumbers_withListener_shouldCallListener() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        setupUidSearchResponses("* SEARCH 99")
        folder.open(OpenMode.READ_WRITE)
        val listener = createMessageRetrievalListener()

        val messages = folder.getMessages(setOf(1L), true, listener)

        verify(listener).messageFinished(messages[0])
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun getMessagesFromUids_withClosedFolder_shouldThrow() {
        val folder = createFolder("FolderName")

        assertFailure {
            folder.getMessagesFromUids(listOf("11", "22", "25"))
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'FolderName' is not open.")
    }

    @Test
    fun getMessagesFromUids() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        setupUidSearchResponses("* SEARCH 11", "* SEARCH 22", "* SEARCH 25")
        folder.open(OpenMode.READ_WRITE)

        val messages = folder.getMessagesFromUids(listOf("11", "22", "25"))

        assertThat(messages).isNotNull()
            .extracting { it.uid }.containsOnly("11", "22", "25")
    }

    @Test
    fun areMoreMessagesAvailable_withClosedFolder_shouldThrow() {
        val folder = createFolder("FolderName")

        assertFailure {
            folder.areMoreMessagesAvailable(10, Date())
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'FolderName' is not open.")
    }

    @Test
    fun areMoreMessagesAvailable_withAdditionalMessages_shouldReturnTrue() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        setupSearchResponses("* SEARCH 42")
        folder.open(OpenMode.READ_WRITE)

        val areMoreMessagesAvailable = folder.areMoreMessagesAvailable(10, null)

        assertThat(areMoreMessagesAvailable).isTrue()
    }

    @Test
    fun areMoreMessagesAvailable_withoutAdditionalMessages_shouldReturnFalse() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        setupSearchResponses("1 OK SEARCH completed")
        folder.open(OpenMode.READ_WRITE)

        val areMoreMessagesAvailable = folder.areMoreMessagesAvailable(600, null)

        assertThat(areMoreMessagesAvailable).isFalse()
    }

    @Test
    fun areMoreMessagesAvailable_withIndexOfOne_shouldReturnFalseWithoutPerformingSearch() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        folder.open(OpenMode.READ_WRITE)

        val areMoreMessagesAvailable = folder.areMoreMessagesAvailable(1, null)

        assertThat(areMoreMessagesAvailable).isFalse()
        // SELECT during OPEN and no more
        verify(imapConnection, times(1)).executeSimpleCommand(anyString())
    }

    @Test
    fun areMoreMessagesAvailable_withoutAdditionalMessages_shouldIssueSearchCommandsUntilAllMessagesSearched() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        setupSearchResponses("1 OK SEARCH Completed")
        folder.open(OpenMode.READ_WRITE)

        folder.areMoreMessagesAvailable(600, null)

        assertCommandIssued("SEARCH 100:599 NOT DELETED")
        assertCommandIssued("SEARCH 1:99 NOT DELETED")
    }

    @Test
    fun fetch_withEmptyMessageListArgument_shouldDoNothing() {
        val folder = createFolder("Folder")
        val fetchProfile = createFetchProfile()

        folder.fetch(emptyList(), fetchProfile, null, MAX_DOWNLOAD_SIZE)

        assertThat(testConnectionManager.numberOfGetConnectionCalls).isEqualTo(0)
    }

    @Test
    fun `fetch() on closed folder should throw`() {
        val folder = createFolder("Folder")
        val messages = createImapMessages("1")
        val fetchProfile = createFetchProfile()

        assertFailure {
            folder.fetch(
                messages = messages,
                fetchProfile = fetchProfile,
                listener = null,
                maxDownloadSize = MAX_DOWNLOAD_SIZE,
            )
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' is not open.")

        verifyNoMoreInteractions(imapConnection)
    }

    @Test
    fun fetch_withFlagsFetchProfile_shouldIssueRespectiveCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        whenever(imapConnection.readResponse(anyOrNull())).thenReturn(createImapResponse("x OK"))
        val messages = createImapMessages("1")
        val fetchProfile = createFetchProfile(FetchProfile.Item.FLAGS)

        folder.fetch(messages, fetchProfile, null, MAX_DOWNLOAD_SIZE)

        verify(imapConnection).sendCommand("UID FETCH 1 (UID FLAGS)", false)
    }

    @Test
    fun fetch_withEnvelopeFetchProfile_shouldIssueRespectiveCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        whenever(imapConnection.readResponse(anyOrNull())).thenReturn(createImapResponse("x OK"))
        val messages = createImapMessages("1")
        val fetchProfile = createFetchProfile(FetchProfile.Item.ENVELOPE)

        folder.fetch(messages, fetchProfile, null, MAX_DOWNLOAD_SIZE)

        verify(imapConnection).sendCommand(
            "UID FETCH 1 (UID INTERNALDATE RFC822.SIZE BODY.PEEK[HEADER.FIELDS " +
                "(date subject from content-type to cc bcc reply-to message-id references in-reply-to " +
                "list-post list-unsubscribe sender X-K9mail-Identity Chat-Version)])",
            false,
        )
    }

    @Test
    fun fetch_withStructureFetchProfile_shouldIssueRespectiveCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        whenever(imapConnection.readResponse(anyOrNull())).thenReturn(createImapResponse("x OK"))
        val messages = createImapMessages("1")
        val fetchProfile = createFetchProfile(FetchProfile.Item.STRUCTURE)

        folder.fetch(messages, fetchProfile, null, MAX_DOWNLOAD_SIZE)

        verify(imapConnection).sendCommand("UID FETCH 1 (UID BODYSTRUCTURE)", false)
    }

    @Test
    fun fetch_withStructureFetchProfile_shouldSetContentType() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        val bodyStructure = "(\"TEXT\" \"PLAIN\" (\"CHARSET\" \"US-ASCII\") NIL NIL \"7BIT\" 2279 48)"
        whenever(imapConnection.readResponse(anyOrNull()))
            .thenReturn(createImapResponse("* 1 FETCH (BODYSTRUCTURE $bodyStructure UID 1)"))
            .thenReturn(createImapResponse("x OK"))
        val messages = createImapMessages("1")
        val fetchProfile = createFetchProfile(FetchProfile.Item.STRUCTURE)

        folder.fetch(messages, fetchProfile, null, MAX_DOWNLOAD_SIZE)

        verify(messages[0]).setHeader(MimeHeader.HEADER_CONTENT_TYPE, "text/plain;\r\n CHARSET=US-ASCII")
    }

    @Test
    fun fetch_withStructureFetchProfile_shouldNotBreakOnUnicodeAddresses() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        val bodyStructure = // from RFC 3501 via Arnt and Abhijit
            "* 1 FETCH (BODYSTRUCTURE ((\"text\" \"plain\" NIL " +
                "NIL \"Part number 1\" \"7BIT\" 9 1 NIL NIL NIL NIL" +
                ")(\"application\" \"octet-stream\" NIL NIL \"Part " +
                "number 2\" \"BASE64\" 14 \"qWXKy9s0ny8E1/5/uzNhpg=" +
                "=\" (\"attachment\" (\"filename\" \"foo.bar\" \"si" +
                "ze\" \"8\")) NIL NIL)(\"message\" \"rfc822\" NIL N" +
                "IL \"Part number 3\" \"7BIT\" 540 (\"Thu, 20 May 2" +
                "004 14:28:50 +0200\" \"embedded rfc822 message\" (" +
                "(\"Arnt Gulbrandsen\" NIL \"arnt\" \"ø.example\"))" +
                " NIL NIL ((\"Abhijit Menon-Sen\" NIL \"ams\" \"ø.e" +
                "xample\")) NIL NIL NIL NIL) ((\"text\" \"plain\" N" +
                "IL NIL \"Part number 3.1\" \"7BIT\" 9 1 NIL (\"inl" +
                "ine\" NIL) (\"en\" \"no\" \"de\") NIL)(\"applicati" +
                "on\" \"octet-stream\" NIL NIL \"Part number 3.2\" " +
                "\"BASE64\" 14 NIL NIL NIL NIL) \"mixed\" (\"bounda" +
                "ry\" \"Y\") NIL NIL NIL) 24 NIL NIL NIL NIL)((\"im" +
                "age\" \"gif\" NIL NIL \"Part number 4.1\" \"BASE64" +
                "\" 0 NIL NIL NIL NIL)(\"message\" \"rfc822\" NIL N" +
                "IL \"Part number 4.2\" \"7BIT\" 658 (\"Thu, 20 May" +
                " 2004 14:28:50 +0200\" \"second embedded rfc822 me" +
                "ssage\" ((\"Abhijit Menon-Sen\" NIL \"ams\" \"ø.ex" +
                "ample\")) NIL NIL ((\"Arnt Gulbrandsen\" NIL \"arn" +
                "t\" \"ø.example\")) NIL NIL NIL NIL) ((\"text\" \"" +
                "plain\" NIL NIL \"Part number 4.2.1\" \"7BIT\" 9 1" +
                " NIL NIL NIL NIL)((\"text\" \"plain\" NIL NIL \"Pa" +
                "rt number 4.2.2.1\" \"7BIT\" 9 1 NIL NIL \"en\" NI" +
                "L)(\"text\" \"richtext\" NIL NIL \"Part number 4.2" +
                ".2.2\" \"7BIT\" 9 1 NIL NIL NIL NIL) \"alternative" +
                "\" (\"boundary\" \"B\") NIL NIL NIL) \"mixed\" (\"" +
                "boundary\" \"A\") NIL NIL NIL) 34 NIL NIL NIL NIL)" +
                " \"mixed\" (\"boundary\" \"Z\") NIL NIL NIL) \"mix" +
                "ed\" (\"boundary\" \"X\") NIL NIL NIL) UID 1)"
        whenever(imapConnection.readResponse(anyOrNull()))
            .thenReturn(createImapResponse(bodyStructure))
            .thenReturn(createImapResponse("x OK"))
        val messages = createImapMessages("1")
        val fetchProfile = createFetchProfile(FetchProfile.Item.STRUCTURE)

        folder.fetch(messages, fetchProfile, null, MAX_DOWNLOAD_SIZE)
        // We don't really care what happens; the tests in Address and
        // MIME4J take care of that. At this point we just care that
        // it doesn't break parsing, cause an exception or anything
        // like that.
    }

    @Test
    fun `fetch() with simple content type parameter`() {
        testHeaderFromBodyStructure(
            bodyStructure = """("text" "plain" ("name" "token") NIL NIL "7bit" 42 23)""",
            headerName = MimeHeader.HEADER_CONTENT_TYPE,
            expectedHeaderValue = "text/plain;\r\n name=token",
        )
    }

    @Test
    fun `fetch() with content type parameter that needs to be a quoted string`() {
        testHeaderFromBodyStructure(
            bodyStructure = """("text" "plain" ("name" "one two three") NIL NIL "7bit" 42 23)""",
            headerName = MimeHeader.HEADER_CONTENT_TYPE,
            expectedHeaderValue = "text/plain;\r\n name=\"one two three\"",
        )
    }

    @Test
    fun `fetch() with content type parameter that needs to be a quoted string with escaped characters`() {
        testHeaderFromBodyStructure(
            bodyStructure = """("text" "plain" ("name" "one \"two\" three") NIL NIL "7bit" 42 23)""",
            headerName = MimeHeader.HEADER_CONTENT_TYPE,
            expectedHeaderValue = "text/plain;\r\n name=\"one \\\"two\\\" three\"",
        )
    }

    @Test
    fun `fetch() with RFC 2231 encoded content type parameter`() {
        testHeaderFromBodyStructure(
            bodyStructure = """("text" "plain" ("name*" "utf-8''filen%C3%A4me.ext") NIL NIL "7bit" 42 23)""",
            headerName = MimeHeader.HEADER_CONTENT_TYPE,
            expectedHeaderValue = "text/plain;\r\n name*=utf-8''filen%C3%A4me.ext",
        )
    }

    @Test
    fun `fetch() with UTF-8 encoded content type parameter`() {
        testHeaderFromBodyStructure(
            bodyStructure = """("text" "plain" ("name" "filenäme.ext") NIL NIL "7bit" 42 23)""",
            headerName = MimeHeader.HEADER_CONTENT_TYPE,
            expectedHeaderValue = "text/plain;\r\n name=\"filenäme.ext\"",
        )
    }

    @Test
    fun `fetch() with simple content disposition parameter`() {
        testHeaderFromBodyStructure(
            bodyStructure = """("application" "octet-stream" NIL NIL NIL "8bit" 23 NIL """ +
                """("attachment" ("filename" "token")) NIL NIL)""",
            headerName = MimeHeader.HEADER_CONTENT_DISPOSITION,
            expectedHeaderValue = "attachment;\r\n filename=token;\r\n size=23",
        )
    }

    @Test
    fun `fetch() with content disposition parameter that needs to be a quoted string`() {
        testHeaderFromBodyStructure(
            bodyStructure = """("application" "octet-stream" NIL NIL NIL "8bit" 23 NIL """ +
                """("attachment" ("filename" "one two three")) NIL NIL)""",
            headerName = MimeHeader.HEADER_CONTENT_DISPOSITION,
            expectedHeaderValue = "attachment;\r\n filename=\"one two three\";\r\n size=23",
        )
    }

    @Test
    fun `fetch() with content disposition parameter that needs to be a quoted string with escaped characters`() {
        testHeaderFromBodyStructure(
            bodyStructure = """("application" "octet-stream" NIL NIL NIL "8bit" 23 NIL """ +
                """("attachment" ("filename" "one \"two\" three")) NIL NIL)""",
            headerName = MimeHeader.HEADER_CONTENT_DISPOSITION,
            expectedHeaderValue = "attachment;\r\n filename=\"one \\\"two\\\" three\";\r\n size=23",
        )
    }

    @Test
    fun `fetch() with RFC 2231 encoded content disposition parameter`() {
        testHeaderFromBodyStructure(
            bodyStructure = """("application" "octet-stream" NIL NIL NIL "8bit" 23 NIL """ +
                """("attachment" ("filename*" "utf-8''filen%C3%A4me.ext")) NIL NIL)""",
            headerName = MimeHeader.HEADER_CONTENT_DISPOSITION,
            expectedHeaderValue = "attachment;\r\n filename*=utf-8''filen%C3%A4me.ext;\r\n size=23",
        )
    }

    @Test
    fun `fetch() with UTF-8 encoded content disposition parameter`() {
        testHeaderFromBodyStructure(
            bodyStructure = """("application" "octet-stream" NIL NIL NIL "8bit" 23 NIL """ +
                """("attachment" ("filename" "filenäme.ext")) NIL NIL)""",
            headerName = MimeHeader.HEADER_CONTENT_DISPOSITION,
            expectedHeaderValue = "attachment;\r\n filename=\"filenäme.ext\";\r\n size=23",
        )
    }

    @Test
    fun fetch_withBodySaneFetchProfile_shouldIssueRespectiveCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        whenever(imapConnection.readResponse(anyOrNull())).thenReturn(createImapResponse("x OK"))
        val messages = createImapMessages("1")
        val fetchProfile = createFetchProfile(FetchProfile.Item.BODY_SANE)

        folder.fetch(messages, fetchProfile, null, 4096)

        verify(imapConnection).sendCommand("UID FETCH 1 (UID BODY.PEEK[]<0.4096>)", false)
    }

    @Test
    fun fetch_withBodySaneFetchProfileAndNoMaximumDownloadSize_shouldIssueRespectiveCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        whenever(imapConnection.readResponse(anyOrNull())).thenReturn(createImapResponse("x OK"))
        val messages = createImapMessages("1")
        val fetchProfile = createFetchProfile(FetchProfile.Item.BODY_SANE)

        folder.fetch(messages, fetchProfile, null, 0)

        verify(imapConnection).sendCommand("UID FETCH 1 (UID BODY.PEEK[])", false)
    }

    @Test
    fun fetch_withBodyFetchProfileAndNoMaximumDownloadSize_shouldIssueRespectiveCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        whenever(imapConnection.readResponse(anyOrNull())).thenReturn(createImapResponse("x OK"))
        val messages = createImapMessages("1")
        val fetchProfile = createFetchProfile(FetchProfile.Item.BODY)

        folder.fetch(messages, fetchProfile, null, MAX_DOWNLOAD_SIZE)

        verify(imapConnection).sendCommand("UID FETCH 1 (UID BODY.PEEK[])", false)
    }

    @Test
    fun fetch_withFlagsFetchProfile_shouldSetFlags() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        val messages = createImapMessages("1")
        val fetchProfile = createFetchProfile(FetchProfile.Item.FLAGS)
        whenever(imapConnection.readResponse(anyOrNull()))
            .thenReturn(createImapResponse("* 1 FETCH (FLAGS (\\Seen) UID 1)"))
            .thenReturn(createImapResponse("x OK"))

        folder.fetch(messages, fetchProfile, null, MAX_DOWNLOAD_SIZE)

        verify(messages[0]).setFlag(Flag.SEEN, true)
    }

    @Test
    fun `fetchPart() on closed folder should throw`() {
        val folder = createFolder("Folder")
        val message = createImapMessage("1")
        val part = createPart("TEXT")

        assertFailure {
            folder.fetchPart(message = message, part = part, bodyFactory = mock(), maxDownloadSize = 4096)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' is not open.")

        verifyNoMoreInteractions(imapConnection)
    }

    @Test
    fun fetchPart_withTextSection_shouldIssueRespectiveCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        val message = createImapMessage("1")
        val part = createPart("TEXT")
        whenever(imapConnection.readResponse(anyOrNull())).thenReturn(createImapResponse("x OK"))

        folder.fetchPart(message, part, mock(), 4096)

        verify(imapConnection).sendCommand("UID FETCH 1 (UID BODY.PEEK[TEXT]<0.4096>)", false)
    }

    @Test
    fun fetchPart_withNonTextSection_shouldIssueRespectiveCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        val message = createImapMessage("1")
        val part = createPart("1.1")
        whenever(imapConnection.readResponse(anyOrNull())).thenReturn(createImapResponse("x OK"))

        folder.fetchPart(message, part, mock(), MAX_DOWNLOAD_SIZE)

        verify(imapConnection).sendCommand("UID FETCH 1 (UID BODY.PEEK[1.1])", false)
    }

    @Test
    fun fetchPart_withTextSection_shouldProcessImapResponses() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        val message = createImapMessage("1")
        val part = createPlainTextPart("1.1")
        setupSingleFetchResponseToCallback()

        folder.fetchPart(message, part, DefaultBodyFactory(), MAX_DOWNLOAD_SIZE)

        val bodyArgumentCaptor = argumentCaptor<Body>()
        verify(part).body = bodyArgumentCaptor.capture()
        val body = bodyArgumentCaptor.firstValue
        val buffer = Buffer()
        body.writeTo(buffer.outputStream())
        assertThat(buffer.readUtf8()).isEqualTo("text")
    }

    @Test
    fun `appendMessages() on closed folder should throw`() {
        val folder = createFolder("Folder")
        val messages = listOf(createImapMessage("1"))

        assertFailure {
            folder.appendMessages(messages)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' is not open.")

        verifyNoMoreInteractions(imapConnection)
    }

    @Test
    fun `appendMessages() on folder opened as read-only should throw`() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        val messages = listOf(createImapMessage("1"))

        assertFailure {
            folder.appendMessages(messages)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' needs to be opened for read-write access.")
    }

    @Test
    fun appendMessages_shouldIssueRespectiveCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        folder.open(OpenMode.READ_WRITE)
        val messages = listOf(createImapMessage("1"))
        whenever(imapConnection.readResponse()).thenReturn(createImapResponse("x OK [APPENDUID 1 23]"))

        folder.appendMessages(messages)

        verify(imapConnection).sendCommand("APPEND \"Folder\" () {0}", false)
    }

    @Test
    fun appendMessages_withNegativeResponse_shouldThrow() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        folder.open(OpenMode.READ_WRITE)
        val messages = listOf(createImapMessage("1"))
        whenever(imapConnection.readResponse()).thenReturn(createImapResponse("x NO Can't append to this folder"))

        assertFailure {
            folder.appendMessages(messages)
        }.isInstanceOf<NegativeImapResponseException>().all {
            hasMessage("APPEND failed")
            transform { it.lastResponse[0] }.isEqualTo("NO")
        }
    }

    @Test
    fun appendMessages_withBadResponse_shouldThrow() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        folder.open(OpenMode.READ_WRITE)
        val messages = listOf(createImapMessage("1"))
        whenever(imapConnection.readResponse()).thenReturn(createImapResponse("x BAD [TOOBIG] Message too large."))

        assertFailure {
            folder.appendMessages(messages)
        }.isInstanceOf<NegativeImapResponseException>().all {
            hasMessage("APPEND failed")
            transform { it.lastResponse[0] }.isEqualTo("BAD")
        }
    }

    @Test
    fun getUidFromMessageId_withMessageIdHeader_shouldIssueUidSearchCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        folder.open(OpenMode.READ_WRITE)
        setupUidSearchResponses("1 OK SEARCH Completed")

        folder.getUidFromMessageId("<00000000.0000000@example.org>")

        assertCommandIssued("UID SEARCH HEADER MESSAGE-ID \"<00000000.0000000@example.org>\"")
    }

    @Test
    fun getUidFromMessageId() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        folder.open(OpenMode.READ_WRITE)
        setupUidSearchResponses("* SEARCH 23")

        val uid = folder.getUidFromMessageId("<00000000.0000000@example.org>")

        assertThat(uid).isEqualTo("23")
    }

    @Test
    fun `deleteMessages() on closed folder should throw`() {
        val folder = createFolder("Folder")
        val messages = listOf(createImapMessage("1"))

        assertFailure {
            folder.deleteMessages(messages)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' is not open.")

        verifyNoMoreInteractions(imapConnection)
    }

    @Test
    fun `deleteMessages() on folder opened as read-only should throw`() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        val messages = listOf(createImapMessage("1"))

        assertFailure {
            folder.deleteMessages(messages)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' needs to be opened for read-write access.")
    }

    @Test
    fun `deleteMessages() with expungeImmediately = true should set deleted flag and expunge message`() {
        imapStoreConfig.expungeImmediately = true
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        imapConnection.stub {
            on { isUidPlusCapable } doReturn true
        }
        folder.open(OpenMode.READ_WRITE)
        val messages = listOf(createImapMessage("1"))

        folder.deleteMessages(messages)

        assertCommandWithIdsIssued("UID STORE 1 +FLAGS.SILENT (\\Deleted)")
        assertCommandWithIdsIssued("UID EXPUNGE 1")
    }

    @Test
    fun `deleteMessages() with expungeImmediately = false should set deleted flag but not expunge message`() {
        imapStoreConfig.expungeImmediately = false
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        imapConnection.stub {
            on { isUidPlusCapable } doReturn true
        }
        folder.open(OpenMode.READ_WRITE)
        val messages = listOf(createImapMessage("1"))

        folder.deleteMessages(messages)

        assertCommandWithIdsIssued("UID STORE 1 +FLAGS.SILENT (\\Deleted)")
        verify(imapConnection, never()).executeCommandWithIdSet("UID EXPUNGE", "", setOf(1L))
        verify(imapConnection, never()).executeSimpleCommand("EXPUNGE")
    }

    @Test
    fun `deleteAllMessages() on closed folder should throw`() {
        val folder = createFolder("Folder")

        assertFailure {
            folder.deleteAllMessages()
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' is not open.")

        verifyNoMoreInteractions(imapConnection)
    }

    @Test
    fun `deleteAllMessages() on folder opened as read-only should throw`() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)

        assertFailure {
            folder.deleteAllMessages()
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' needs to be opened for read-write access.")
    }

    @Test
    fun `deleteAllMessages() with expungeImmediately = true should set deleted flag and expunge`() {
        imapStoreConfig.expungeImmediately = true
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        folder.open(OpenMode.READ_WRITE)

        folder.deleteAllMessages()

        assertCommandIssued("UID STORE 1:* +FLAGS.SILENT (\\Deleted)")
        assertCommandIssued("EXPUNGE")
    }

    @Test
    fun `deleteAllMessages() with expungeImmediately = false should set deleted flag but not expunge`() {
        imapStoreConfig.expungeImmediately = false
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        folder.open(OpenMode.READ_WRITE)

        folder.deleteAllMessages()

        assertCommandIssued("UID STORE 1:* +FLAGS.SILENT (\\Deleted)")
        verify(imapConnection, never()).executeSimpleCommand("EXPUNGE")
    }

    @Test
    fun `expunge() on closed folder should throw`() {
        val folder = createFolder("Folder")

        assertFailure {
            folder.expunge()
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' is not open.")

        verifyNoMoreInteractions(imapConnection)
    }

    @Test
    fun `expunge() on folder opened as read-only should throw`() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)

        assertFailure {
            folder.expunge()
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' needs to be opened for read-write access.")
    }

    @Test
    fun expunge_shouldIssueExpungeCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        folder.open(OpenMode.READ_WRITE)

        folder.expunge()

        verify(imapConnection).executeSimpleCommand("EXPUNGE")
    }

    @Test
    fun `expungeUids() on closed folder should throw`() {
        val folder = createFolder("Folder")

        assertFailure {
            folder.expungeUids(listOf("1"))
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' is not open.")

        verifyNoMoreInteractions(imapConnection)
    }

    @Test
    fun `expungeUids() on folder opened as read-only should throw`() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)

        assertFailure {
            folder.expungeUids(listOf("1"))
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' needs to be opened for read-write access.")
    }

    @Test
    fun expungeUids_withUidPlus_shouldIssueUidExpungeCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        folder.open(OpenMode.READ_WRITE)
        whenever(imapConnection.isUidPlusCapable).thenReturn(true)

        folder.expungeUids(listOf("1"))

        assertCommandWithIdsIssued("UID EXPUNGE 1")
    }

    @Test
    fun expungeUids_withoutUidPlus_shouldIssueExpungeCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        folder.open(OpenMode.READ_WRITE)
        whenever(imapConnection.isUidPlusCapable).thenReturn(false)

        folder.expungeUids(listOf("1"))

        verify(imapConnection).executeSimpleCommand("EXPUNGE")
    }

    @Test
    fun `setFlagsForAllMessages() on closed folder should throw`() {
        val folder = createFolder("Folder")

        assertFailure {
            folder.setFlagsForAllMessages(setOf(Flag.SEEN), true)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' is not open.")

        verifyNoMoreInteractions(imapConnection)
    }

    @Test
    fun `setFlagsForAllMessages() on folder opened as read-only should throw`() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)

        assertFailure {
            folder.setFlagsForAllMessages(setOf(Flag.SEEN), true)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' needs to be opened for read-write access.")
    }

    @Test
    fun setFlagsForAllMessages_shouldIssueUidStoreCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        folder.open(OpenMode.READ_WRITE)

        folder.setFlagsForAllMessages(setOf(Flag.SEEN), true)

        assertCommandIssued("UID STORE 1:* +FLAGS.SILENT (\\Seen)")
    }

    @Test
    fun `setFlags() on closed folder should throw`() {
        val folder = createFolder("Folder")
        val messages = listOf(createImapMessage("1"))

        assertFailure {
            folder.setFlags(messages, setOf(Flag.SEEN), true)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' is not open.")

        verifyNoMoreInteractions(imapConnection)
    }

    @Test
    fun `setFlags() on folder opened as read-only should throw`() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        val messages = listOf(createImapMessage("1"))

        assertFailure {
            folder.setFlags(messages, setOf(Flag.SEEN), true)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' needs to be opened for read-write access.")
    }

    @Test
    fun `setFlags() should issue UID STORE command`() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_WRITE)
        folder.open(OpenMode.READ_WRITE)
        val messages = listOf(createImapMessage("1"))

        folder.setFlags(messages, setOf(Flag.SEEN), true)

        assertCommandWithIdsIssued("UID STORE 1 +FLAGS.SILENT (\\Seen)")
    }

    @Test
    fun `search() on closed folder should throw`() {
        val folder = createFolder("Folder")

        assertFailure {
            folder.search("query", setOf(Flag.SEEN), emptySet(), true)
        }.isInstanceOf<IllegalStateException>()
            .hasMessage("Folder 'Folder' is not open.")

        verifyNoMoreInteractions(imapConnection)
    }

    @Test
    fun search_withFullTextSearchEnabled_shouldIssueRespectiveCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        setupUidSearchResponses("1 OK SEARCH completed")

        folder.search("query", setOf(Flag.SEEN), emptySet(), true)

        assertCommandIssued("UID SEARCH TEXT \"query\" SEEN")
    }

    @Test
    fun search_withFullTextSearchDisabled_shouldIssueRespectiveCommand() {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        setupUidSearchResponses("1 OK SEARCH completed")

        folder.search("query", emptySet(), emptySet(), false)

        assertCommandIssued("""UID SEARCH OR OR OR OR SUBJECT "query" FROM "query" TO "query" CC "query" BCC "query"""")
    }

    @Test
    fun getMessageByUid_returnsNewImapMessageWithUid() {
        val folder = createFolder("Folder")

        val message = folder.getMessage("uid")

        assertThat(message.uid).isEqualTo("uid")
    }

    @Suppress("SameParameterValue")
    private fun createPlainTextPart(serverExtra: String): Part {
        val part = createPart(serverExtra)
        whenever(part.getHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING)).thenReturn(arrayOf(MimeUtil.ENC_7BIT))
        whenever(part.getHeader(MimeHeader.HEADER_CONTENT_TYPE)).thenReturn(arrayOf("text/plain"))
        return part
    }

    private fun setupSingleFetchResponseToCallback() {
        whenever(imapConnection.readResponse(anyOrNull()))
            .thenAnswer { invocation ->
                val callback = invocation.arguments[0] as ImapResponseCallback
                buildImapFetchResponse(callback)
            }
            .thenAnswer { invocation ->
                val callback = invocation.arguments[0] as ImapResponseCallback
                ImapResponse.newTaggedResponse(callback, "TAG")
            }
    }

    private fun buildImapFetchResponse(callback: ImapResponseCallback): ImapResponse {
        val response = ImapResponse.newContinuationRequest(callback)
        response.add("1")
        response.add("FETCH")
        val fetchList = ImapList()
        fetchList.add("UID")
        fetchList.add("1")
        fetchList.add("BODY")
        fetchList.add("1.1")
        fetchList.add("text")
        response.add(fetchList)
        return response
    }

    private fun createFolder(folderName: String): RealImapFolder {
        return RealImapFolder(internalImapStore, testConnectionManager, folderName, FolderNameCodec())
    }

    private fun createImapMessage(uid: String): ImapMessage {
        return mock {
            on { this.uid } doReturn uid
        }
    }

    private fun createImapMessages(vararg uids: String) = uids.map { createImapMessage(it) }

    private fun createPart(serverExtra: String): Part {
        return mock {
            on { this.serverExtra } doReturn serverExtra
        }
    }

    private fun createFetchProfile(vararg items: FetchProfile.Item) = items.toCollection(FetchProfile())

    private fun createMessageRetrievalListener() = mock<MessageRetrievalListener<ImapMessage>>()

    private fun prepareImapFolderForOpen(openMode: OpenMode) {
        val imapResponses = listOf(
            createImapResponse("* FLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft NonJunk \$MDNSent)"),
            createImapResponse(
                "* OK [PERMANENTFLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft NonJunk \$MDNSent \\*)] " +
                    "Flags permitted.",
            ),
            createImapResponse("* 23 EXISTS"),
            createImapResponse("* 0 RECENT"),
            createImapResponse("* OK [UIDVALIDITY 1125022061] UIDs valid"),
            createImapResponse("* OK [UIDNEXT 57576] Predicted next UID"),
            if (openMode == OpenMode.READ_WRITE) {
                createImapResponse("2 OK [READ-WRITE] Select completed.")
            } else {
                createImapResponse("2 OK [READ-ONLY] Examine completed.")
            },
        )

        if (openMode == OpenMode.READ_WRITE) {
            whenever(imapConnection.executeSimpleCommand("SELECT \"Folder\"")).thenReturn(imapResponses)
        } else {
            whenever(imapConnection.executeSimpleCommand("EXAMINE \"Folder\"")).thenReturn(imapResponses)
        }
    }

    private fun assertCommandWithIdsIssued(expectedCommand: String) {
        val commandPrefixCaptor = argumentCaptor<String>()
        val commandSuffixCaptor = argumentCaptor<String>()
        val commandUidsCaptor = argumentCaptor<Set<Long>>()
        verify(imapConnection, atLeastOnce()).executeCommandWithIdSet(
            commandPrefixCaptor.capture(),
            commandSuffixCaptor.capture(),
            commandUidsCaptor.capture(),
        )

        val commandPrefixes = commandPrefixCaptor.allValues
        val commandSuffixes = commandSuffixCaptor.allValues
        val commandUids = commandUidsCaptor.allValues

        for (i in commandPrefixes.indices) {
            val command = commandPrefixes[i] + " " + ImapUtility.join(",", commandUids[i]) +
                if (commandSuffixes[i].isEmpty()) "" else " " + commandSuffixes[i]

            if (command == expectedCommand) {
                return
            }
        }

        fail("Expected IMAP command not issued: $expectedCommand")
    }

    private fun assertCommandIssued(expectedCommand: String) {
        verify(imapConnection, atLeastOnce()).executeSimpleCommand(expectedCommand)
    }

    private fun setupUidSearchResponses(vararg responses: String) {
        val imapResponses = responses.map { createImapResponse(it) }
        whenever(imapConnection.executeSimpleCommand(startsWith("UID SEARCH"))).thenReturn(imapResponses)
        whenever(imapConnection.executeCommandWithIdSet(startsWith("UID SEARCH"), anyString(), anySet()))
            .thenReturn(imapResponses)
    }

    private fun setupSearchResponses(vararg responses: String) {
        val imapResponses = responses.map { createImapResponse(it) }
        whenever(imapConnection.executeSimpleCommand(startsWith("SEARCH"))).thenReturn(imapResponses)
    }

    @Suppress("SameParameterValue")
    private fun setupCopyResponse(response: String) {
        val imapResponses = listOf(createImapResponse(response))
        whenever(imapConnection.executeCommandWithIdSet(eq(Commands.UID_COPY), anyString(), anySet()))
            .thenReturn(imapResponses)
    }

    @Suppress("SameParameterValue")
    private fun setupMoveResponses(vararg responses: String) {
        val imapResponses = createImapResponseList(*responses)
        whenever(imapConnection.executeCommandWithIdSet(eq(Commands.UID_MOVE), anyString(), anySet()))
            .thenReturn(imapResponses)
    }

    private fun testHeaderFromBodyStructure(bodyStructure: String, headerName: String, expectedHeaderValue: String) {
        val folder = createFolder("Folder")
        prepareImapFolderForOpen(OpenMode.READ_ONLY)
        folder.open(OpenMode.READ_ONLY)
        whenever(imapConnection.readResponse(anyOrNull()))
            .thenReturn(createImapResponse("* 1 FETCH (BODYSTRUCTURE $bodyStructure UID 1)"))
            .thenReturn(createImapResponse("x OK"))
        val imapMessage = ImapMessage("1")
        val messages = listOf(imapMessage)
        val fetchProfile = createFetchProfile(FetchProfile.Item.STRUCTURE)

        folder.fetch(messages, fetchProfile, null, MAX_DOWNLOAD_SIZE)

        assertThat(imapMessage.getHeader(headerName)).containsExactly(expectedHeaderValue)
    }

    companion object {
        private const val MAX_DOWNLOAD_SIZE = -1
    }
}

internal class TestConnectionManager(private val connection: ImapConnection) : ImapConnectionManager {
    var numberOfGetConnectionCalls = 0
        private set

    override fun getConnection(): ImapConnection {
        numberOfGetConnectionCalls++
        return connection
    }

    override fun releaseConnection(connection: ImapConnection?) = Unit
}
