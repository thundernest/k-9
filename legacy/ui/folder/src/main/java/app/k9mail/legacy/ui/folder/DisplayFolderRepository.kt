package app.k9mail.legacy.ui.folder

import app.k9mail.legacy.account.Account
import kotlinx.coroutines.flow.Flow

interface DisplayFolderRepository {

    fun getDisplayFoldersFlow(account: Account, includeHiddenFolders: Boolean): Flow<List<DisplayFolder>>

    fun getDisplayFoldersFlow(accountUuid: String): Flow<List<DisplayFolder>>
}
