package app.k9mail.feature.navigation.drawer.data

import app.k9mail.feature.navigation.drawer.domain.entity.DisplayUnifiedFolder
import app.k9mail.feature.navigation.drawer.domain.entity.DisplayUnifiedFolderType
import app.k9mail.legacy.message.controller.MessageCounts
import app.k9mail.legacy.search.api.SearchAttribute
import app.k9mail.legacy.search.api.SearchField
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

internal class UnifiedFolderRepositoryTest {

    @Test
    fun `should return DisplayUnifiedFolder for unified inbox`() = runTest {
        val messageCountsProvider = FakeMessageCountsProvider(
            messageCounts = MessageCounts(
                unread = 2,
                starred = 2,
            ),
        )
        val testSubject = UnifiedFolderRepository(
            messageCountsProvider = messageCountsProvider,
        )
        val folderType = DisplayUnifiedFolderType.INBOX

        val result = testSubject.getDisplayUnifiedFolderFlow(folderType).first()

        assertThat(result).isEqualTo(
            DisplayUnifiedFolder(
                id = "unified_inbox",
                unifiedType = folderType,
                unreadMessageCount = 2,
                starredMessageCount = 2,
            ),
        )

        val search = messageCountsProvider.recordedSearch
        assertThat(search.id).isEqualTo("unified_inbox")
        val condition = search.conditions.condition
        assertThat(condition.value).isEqualTo("1")
        assertThat(condition.attribute).isEqualTo(SearchAttribute.EQUALS)
        assertThat(condition.field).isEqualTo(SearchField.INTEGRATE)
    }
}
