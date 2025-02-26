package app.k9mail.feature.navigation.drawer.domain.usecase

import app.k9mail.feature.navigation.drawer.domain.DomainContract.UnifiedFolderRepository
import app.k9mail.feature.navigation.drawer.domain.DomainContract.UseCase
import app.k9mail.feature.navigation.drawer.domain.entity.DisplayAccountFolder
import app.k9mail.feature.navigation.drawer.domain.entity.DisplayFolder
import app.k9mail.feature.navigation.drawer.domain.entity.DisplayUnifiedFolder
import app.k9mail.feature.navigation.drawer.domain.entity.DisplayUnifiedFolderType
import app.k9mail.legacy.ui.folder.DisplayFolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class GetDisplayFoldersForAccount(
    private val displayFolderRepository: DisplayFolderRepository,
    private val unifiedFolderRepository: UnifiedFolderRepository,
) : UseCase.GetDisplayFoldersForAccount {
    override fun invoke(accountId: String, includeUnifiedFolders: Boolean): Flow<List<DisplayFolder>> {
        val accountFoldersFlow: Flow<List<DisplayFolder>> =
            displayFolderRepository.getDisplayFoldersFlow(accountId).map { displayFolders ->
                displayFolders.map { displayFolder ->
                    DisplayAccountFolder(
                        accountId = accountId,
                        folder = displayFolder.folder,
                        isInTopGroup = displayFolder.isInTopGroup,
                        unreadMessageCount = displayFolder.unreadMessageCount,
                        starredMessageCount = displayFolder.starredMessageCount,
                    )
                }
            }

        val unifiedFoldersFlow: Flow<List<DisplayFolder>> = if (includeUnifiedFolders) {
            unifiedFolderRepository.getDisplayUnifiedFolderFlow(DisplayUnifiedFolderType.INBOX)
                .map { displayUnifiedFolder ->
                    listOf(displayUnifiedFolder)
                }
        } else {
            flowOf(emptyList<DisplayUnifiedFolder>())
        }

        return combine(
            accountFoldersFlow,
            unifiedFoldersFlow,
        ) { accountFolders, unifiedFolders ->
            unifiedFolders + accountFolders
        }
    }
}
