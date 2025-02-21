package app.k9mail.feature.navigation.drawer.ui.folder

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.k9mail.core.ui.compose.designsystem.atom.DividerHorizontal
import app.k9mail.core.ui.compose.theme2.MainTheme
import app.k9mail.feature.navigation.drawer.domain.entity.DisplayFolder
import app.k9mail.feature.navigation.drawer.domain.entity.DisplayUnifiedFolder
import app.k9mail.legacy.ui.folder.FolderNameFormatter
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun FolderList(
    folders: ImmutableList<DisplayFolder>,
    selectedFolder: DisplayFolder?,
    folderListScrollSnapshot: FolderListScrollSnapshot,
    onFolderClick: (DisplayFolder) -> Unit,
    onUpdateFolderListScrollPosition: (Int, Int) -> Unit,
    showStarredCount: Boolean,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    val folderNameFormatter = remember { FolderNameFormatter(resources) }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth(),
        contentPadding = PaddingValues(vertical = MainTheme.spacings.default),
    ) {
        items(
            items = folders,
            key = { it.id },
        ) { folder ->
            FolderListItem(
                displayFolder = folder,
                selected = folder == selectedFolder,
                showStarredCount = showStarredCount,
                onClick = onFolderClick,
                folderNameFormatter = folderNameFormatter,
            )
            if (folder is DisplayUnifiedFolder) {
                DividerHorizontal(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = MainTheme.spacings.default,
                            horizontal = MainTheme.spacings.triple,
                        ),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        listState.scrollToItem(
            index = folderListScrollSnapshot.scrollPosition,
            scrollOffset = folderListScrollSnapshot.scrollOffset
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            onUpdateFolderListScrollPosition(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }
}
