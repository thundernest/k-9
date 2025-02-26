package app.k9mail.legacy.message.controller

import app.k9mail.legacy.account.Account
import app.k9mail.legacy.search.LocalSearch
import app.k9mail.legacy.search.SearchAccount
import kotlinx.coroutines.flow.Flow

interface MessageCountsProvider {
    fun getMessageCounts(account: Account): MessageCounts
    fun getMessageCounts(searchAccount: SearchAccount): MessageCounts
    fun getMessageCounts(search: LocalSearch): MessageCounts
    fun getMessageCountsFlow(search: LocalSearch): Flow<MessageCounts>
    fun getUnreadMessageCount(account: Account, folderId: Long): Int
}

data class MessageCounts(val unread: Int, val starred: Int)
