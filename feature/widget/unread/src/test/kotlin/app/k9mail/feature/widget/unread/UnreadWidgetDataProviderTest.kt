package app.k9mail.feature.widget.unread

import android.content.Context
import app.k9mail.core.mail.folder.api.Folder
import app.k9mail.core.mail.folder.api.FolderType
import app.k9mail.legacy.account.Account
import app.k9mail.legacy.mailstore.FolderRepository
import app.k9mail.legacy.message.controller.MessageCounts
import app.k9mail.legacy.message.controller.MessageCountsProvider
import app.k9mail.legacy.search.LocalSearch
import app.k9mail.legacy.search.SearchAccount
import app.k9mail.legacy.ui.folder.FolderNameFormatter
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fsck.k9.CoreResourceProvider
import com.fsck.k9.Preferences
import com.fsck.k9.ui.messagelist.DefaultFolderProvider
import kotlinx.coroutines.flow.Flow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.test.AutoCloseKoinTest
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UnreadWidgetDataProviderTest : AutoCloseKoinTest() {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val account = createAccount()
    private val preferences = createPreferences()
    private val messageCountsProvider = createMessageCountsProvider()
    private val defaultFolderStrategy = createDefaultFolderStrategy()
    private val folderRepository = createFolderRepository()
    private val folderNameFormatter = createFolderNameFormatter()
    private val coreResourceProvider = createCoreResourceProvider()
    private val provider = UnreadWidgetDataProvider(
        context,
        preferences,
        messageCountsProvider,
        defaultFolderStrategy,
        folderRepository,
        folderNameFormatter,
        coreResourceProvider,
    )

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    factory<CoreResourceProvider> { FakeCoreResourceProvider() }
                },
            )
        }
    }

    @Test
    fun unifiedInbox() {
        val configuration = UnreadWidgetConfiguration(
            appWidgetId = 1,
            accountUuid = SearchAccount.UNIFIED_INBOX,
            folderId = null,
        )

        val widgetData = provider.loadUnreadWidgetData(configuration)

        with(widgetData!!) {
            assertThat(title).isEqualTo("Unified Inbox")
            assertThat(unreadCount).isEqualTo(SEARCH_ACCOUNT_UNREAD_COUNT)
        }
    }

    @Test
    fun regularAccount() {
        val configuration = UnreadWidgetConfiguration(
            appWidgetId = 3,
            accountUuid = ACCOUNT_UUID,
            folderId = null,
        )

        val widgetData = provider.loadUnreadWidgetData(configuration)

        with(widgetData!!) {
            assertThat(title).isEqualTo(ACCOUNT_NAME)
            assertThat(unreadCount).isEqualTo(ACCOUNT_UNREAD_COUNT)
        }
    }

    @Test
    fun folder() {
        val configuration = UnreadWidgetConfiguration(
            appWidgetId = 4,
            accountUuid = ACCOUNT_UUID,
            folderId = FOLDER_ID,
        )

        val widgetData = provider.loadUnreadWidgetData(configuration)

        with(widgetData!!) {
            assertThat(title).isEqualTo("$ACCOUNT_NAME - $LOCALIZED_FOLDER_NAME")
            assertThat(unreadCount).isEqualTo(FOLDER_UNREAD_COUNT)
        }
    }

    @Test
    fun nonExistentAccount_shouldReturnNull() {
        val configuration = UnreadWidgetConfiguration(
            appWidgetId = 3,
            accountUuid = "invalid",
            folderId = null,
        )

        val widgetData = provider.loadUnreadWidgetData(configuration)

        assertThat(widgetData).isNull()
    }

    private fun createAccount(): Account = mock {
        on { uuid } doReturn ACCOUNT_UUID
        on { displayName } doReturn ACCOUNT_NAME
    }

    private fun createPreferences(): Preferences = mock {
        on { getAccount(ACCOUNT_UUID) } doReturn account
    }

    private fun createMessageCountsProvider() = object : MessageCountsProvider {
        override fun getMessageCounts(account: Account): MessageCounts {
            return MessageCounts(unread = ACCOUNT_UNREAD_COUNT, starred = 0)
        }

        override fun getMessageCounts(searchAccount: SearchAccount): MessageCounts {
            return MessageCounts(unread = SEARCH_ACCOUNT_UNREAD_COUNT, starred = 0)
        }

        override fun getMessageCounts(search: LocalSearch): MessageCounts {
            throw UnsupportedOperationException()
        }

        override fun getMessageCountsFlow(search: LocalSearch): Flow<MessageCounts> {
            throw UnsupportedOperationException()
        }

        override fun getUnreadMessageCount(account: Account, folderId: Long): Int {
            return FOLDER_UNREAD_COUNT
        }
    }

    private fun createDefaultFolderStrategy(): DefaultFolderProvider = mock {
        on { getDefaultFolder(account) } doReturn FOLDER_ID
    }

    private fun createFolderRepository(): FolderRepository {
        return mock {
            on { getFolder(account, FOLDER_ID) } doReturn FOLDER
        }
    }

    private fun createFolderNameFormatter(): FolderNameFormatter = mock {
        on { displayName(FOLDER) } doReturn LOCALIZED_FOLDER_NAME
    }

    private fun createCoreResourceProvider(): CoreResourceProvider = mock {
        on { searchUnifiedInboxTitle() } doReturn UNIFIED_INBOX_NAME
        on { searchUnifiedInboxDetail() } doReturn UNIFIED_INBOX_DETAIL
    }

    companion object {
        const val ACCOUNT_UUID = "00000000-0000-0000-0000-000000000000"
        const val ACCOUNT_NAME = "Test account"
        const val FOLDER_ID = 23L
        const val SEARCH_ACCOUNT_UNREAD_COUNT = 1
        const val ACCOUNT_UNREAD_COUNT = 2
        const val FOLDER_UNREAD_COUNT = 3
        const val LOCALIZED_FOLDER_NAME = "Posteingang"
        const val UNIFIED_INBOX_NAME = "Unified Inbox"
        const val UNIFIED_INBOX_DETAIL = "All Messages"
        val FOLDER = Folder(
            id = FOLDER_ID,
            name = "INBOX",
            type = FolderType.INBOX,
            isLocalOnly = false,
        )
    }
}
