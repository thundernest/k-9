package app.k9mail.feature.navigation.drawer.domain.usecase

import app.k9mail.legacy.account.Account
import app.k9mail.legacy.ui.folder.DisplayFolder
import app.k9mail.legacy.ui.folder.DisplayFolderRepository
import kotlinx.coroutines.flow.Flow

internal class FakeDisplayFolderRepository(
    private val foldersFlow: Flow<List<DisplayFolder>>,
) : DisplayFolderRepository {
    override fun getDisplayFoldersFlow(
        account: Account,
        includeHiddenFolders: Boolean,
    ): Flow<List<DisplayFolder>> {
        TODO("Not yet implemented")
    }

    override fun getDisplayFoldersFlow(accountUuid: String): Flow<List<DisplayFolder>> {
        return foldersFlow
    }
}
