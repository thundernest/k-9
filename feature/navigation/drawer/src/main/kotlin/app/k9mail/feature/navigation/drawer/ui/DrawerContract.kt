package app.k9mail.feature.navigation.drawer.ui

import androidx.compose.runtime.Stable
import app.k9mail.core.ui.compose.common.mvi.UnidirectionalViewModel
import app.k9mail.feature.navigation.drawer.NavigationDrawerExternalContract.DrawerConfig
import app.k9mail.feature.navigation.drawer.domain.entity.DisplayAccount
import app.k9mail.feature.navigation.drawer.domain.entity.DisplayFolder
import app.k9mail.feature.navigation.drawer.ui.folder.FolderListScrollSnapshot
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal interface DrawerContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    @Stable
    data class State(
        val config: DrawerConfig = DrawerConfig(
            showUnifiedFolders = false,
            showStarredCount = false,
            showAccountSelector = true,
        ),
        val accounts: ImmutableList<DisplayAccount> = persistentListOf(),
        val selectedAccountId: String? = null,
        val folders: ImmutableList<DisplayFolder> = persistentListOf(),
        val selectedFolderId: String? = null,
        val folderListScrollSnapshot: FolderListScrollSnapshot = FolderListScrollSnapshot(
            scrollPosition = 0,
            scrollOffset = 0
        ),
        val isLoading: Boolean = false,
    )

    sealed interface Event {
        data class SelectAccount(val accountId: String?) : Event
        data class SelectFolder(val folderId: String?) : Event
        data class OnAccountClick(val account: DisplayAccount) : Event
        data class OnAccountViewClick(val account: DisplayAccount) : Event
        data class OnUpdateFolderListScrollPosition(val position: Int, val offset: Int) : Event
        data class OnFolderClick(val folder: DisplayFolder) : Event
        data object OnAccountSelectorClick : Event
        data object OnManageFoldersClick : Event
        data object OnSettingsClick : Event
        data object OnSyncAccount : Event
        data object OnSyncAllAccounts : Event
    }

    sealed interface Effect {
        data class OpenAccount(val accountId: String) : Effect
        data class OpenFolder(val folderId: Long) : Effect
        data object OpenUnifiedFolder : Effect
        data object OpenManageFolders : Effect
        data object OpenSettings : Effect
        data object CloseDrawer : Effect
    }
}
