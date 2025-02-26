package app.k9mail.feature.navigation.drawer.domain.usecase

import app.k9mail.feature.navigation.drawer.domain.DomainContract.UnifiedFolderRepository
import app.k9mail.feature.navigation.drawer.domain.entity.DisplayUnifiedFolder
import app.k9mail.feature.navigation.drawer.domain.entity.DisplayUnifiedFolderType
import kotlinx.coroutines.flow.Flow

internal class FakeUnifiedFolderRepository(
    private val displayUnifiedFolderFlow: Flow<DisplayUnifiedFolder>,
) : UnifiedFolderRepository {
    override fun getDisplayUnifiedFolderFlow(unifiedFolderType: DisplayUnifiedFolderType): Flow<DisplayUnifiedFolder> {
        return displayUnifiedFolderFlow
    }
}
