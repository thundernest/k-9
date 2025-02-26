package app.k9mail.feature.navigation.drawer.data

import app.k9mail.legacy.account.Account
import app.k9mail.legacy.message.controller.MessageCounts
import app.k9mail.legacy.message.controller.MessageCountsProvider
import app.k9mail.legacy.search.LocalSearch
import app.k9mail.legacy.search.SearchAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class FakeMessageCountsProvider(
    private val messageCounts: MessageCounts,
) : MessageCountsProvider {
    var recordedSearch: LocalSearch = LocalSearch()

    override fun getMessageCounts(account: Account): MessageCounts {
        TODO("Not yet implemented")
    }

    override fun getMessageCounts(searchAccount: SearchAccount): MessageCounts {
        TODO("Not yet implemented")
    }

    override fun getMessageCounts(search: LocalSearch): MessageCounts {
        TODO("Not yet implemented")
    }

    override fun getMessageCountsFlow(search: LocalSearch): Flow<MessageCounts> {
        recordedSearch = search
        return flowOf(messageCounts)
    }

    override fun getUnreadMessageCount(account: Account, folderId: Long): Int {
        TODO("Not yet implemented")
    }
}
