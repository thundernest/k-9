package app.k9mail.feature.navigation.drawer.data

import app.k9mail.feature.navigation.drawer.domain.DomainContract
import app.k9mail.feature.navigation.drawer.domain.entity.DisplayUnifiedFolder
import app.k9mail.feature.navigation.drawer.domain.entity.DisplayUnifiedFolderType
import app.k9mail.legacy.message.controller.MessageCountsProvider
import app.k9mail.legacy.search.LocalSearch
import app.k9mail.legacy.search.api.SearchAttribute
import app.k9mail.legacy.search.api.SearchField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class UnifiedFolderRepository(
    private val messageCountsProvider: MessageCountsProvider,
) : DomainContract.UnifiedFolderRepository {

    override fun getDisplayUnifiedFolderFlow(unifiedFolderType: DisplayUnifiedFolderType): Flow<DisplayUnifiedFolder> {
        return messageCountsProvider.getMessageCountsFlow(createUnifiedFolderSearch(unifiedFolderType)).map {
            DisplayUnifiedFolder(
                id = UNIFIED_INBOX_ID,
                unifiedType = DisplayUnifiedFolderType.INBOX,
                unreadMessageCount = it.unread,
                starredMessageCount = it.starred,
            )
        }
    }

    private fun createUnifiedFolderSearch(unifiedFolderType: DisplayUnifiedFolderType): LocalSearch {
        return when (unifiedFolderType) {
            DisplayUnifiedFolderType.INBOX -> return createUnifiedInboxSearch()
        }
    }

    private fun createUnifiedInboxSearch(): LocalSearch {
        return LocalSearch().apply {
            id = UNIFIED_INBOX_ID
            and(SearchField.INTEGRATE, "1", SearchAttribute.EQUALS)
        }
    }

    companion object {
        const val UNIFIED_INBOX_ID = "unified_inbox"
    }
}
