package com.fsck.k9.controller

import app.cash.turbine.test
import app.k9mail.legacy.account.Account
import app.k9mail.legacy.account.AccountManager
import app.k9mail.legacy.mailstore.ListenableMessageStore
import app.k9mail.legacy.mailstore.MessageStoreManager
import app.k9mail.legacy.message.controller.MessageCounts
import app.k9mail.legacy.message.controller.MessagingControllerRegistry
import app.k9mail.legacy.message.controller.MessagingListener
import app.k9mail.legacy.message.controller.SimpleMessagingListener
import app.k9mail.legacy.search.ConditionsTreeNode
import app.k9mail.legacy.search.LocalSearch
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

private const val ACCOUNT_UUID = "irrelevant"
private const val UNREAD_COUNT = 2
private const val STARRED_COUNT = 3

class DefaultMessageCountsProviderTest {

    private val account = Account(ACCOUNT_UUID)
    private val accountManager = mock<AccountManager> {
        on { getAccounts() } doReturn listOf(account)
    }
    private val messageStore = mock<ListenableMessageStore> {
        on { getUnreadMessageCount(anyOrNull<ConditionsTreeNode>()) } doReturn UNREAD_COUNT
        on { getStarredMessageCount(anyOrNull()) } doReturn STARRED_COUNT
    }
    private val messageStoreManager = mock<MessageStoreManager> {
        on { getMessageStore(account) } doReturn messageStore
    }

    private val messagingControllerRegistry = mock<MessagingControllerRegistry> {}

    private val messageCountsProvider = DefaultMessageCountsProvider(
        accountManager = accountManager,
        messageStoreManager = messageStoreManager,
        messagingControllerRegistry = messagingControllerRegistry,
    )

    @Test
    fun `getMessageCounts() without any special folders`() {
        account.inboxFolderId = null
        account.trashFolderId = null
        account.draftsFolderId = null
        account.spamFolderId = null
        account.outboxFolderId = null
        account.sentFolderId = null

        val messageCounts = messageCountsProvider.getMessageCounts(account)

        assertThat(messageCounts.unread).isEqualTo(UNREAD_COUNT)
        assertThat(messageCounts.starred).isEqualTo(STARRED_COUNT)
    }

    @Test
    fun `getMessageCountsFlow should emit for every change`() = runTest {
        var currentListener: SimpleMessagingListener? = null
        val registry = object : MessagingControllerRegistry {
            override fun addListener(listener: MessagingListener) {
                currentListener = listener as SimpleMessagingListener
            }

            override fun removeListener(listener: MessagingListener) {
                currentListener = null
            }
        }
        var currentCount = 0
        val messageStore = mock<ListenableMessageStore> {
            on { getUnreadMessageCount(anyOrNull<ConditionsTreeNode>()) } doAnswer { currentCount }
            on { getStarredMessageCount(anyOrNull()) } doAnswer { currentCount }
        }
        val messageStoreManager = mock<MessageStoreManager> {
            on { getMessageStore(account) } doReturn messageStore
        }
        val testSubject = DefaultMessageCountsProvider(
            accountManager = accountManager,
            messageStoreManager = messageStoreManager,
            messagingControllerRegistry = registry,
        )
        val search = LocalSearch().apply {
            addAccountUuid(account.uuid)
        }

        testSubject.getMessageCountsFlow(search).test {
            assertThat(awaitItem()).isEqualTo(MessageCounts(0, 0))
            currentCount = 1
            currentListener?.folderStatusChanged(account, 0)
            assertThat(awaitItem()).isEqualTo(MessageCounts(1, 1))
            currentCount = 2
            currentListener?.folderStatusChanged(account, 0)
            assertThat(awaitItem()).isEqualTo(MessageCounts(2, 2))
        }
    }
}
