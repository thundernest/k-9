package app.k9mail.feature.onboarding.welcome.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.Surface
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextDisplayMedium
import app.k9mail.core.ui.compose.designsystem.template.LazyColumnWithHeaderFooter
import app.k9mail.core.ui.compose.designsystem.template.ResponsiveContent
import app.k9mail.core.ui.compose.theme2.MainTheme
import app.k9mail.feature.onboarding.welcome.R
import kotlinx.coroutines.launch

private const val CIRCLE_COLOR = 0xFFEEEEEE
private const val CIRCLE_SIZE_DP = 200
private const val LOGO_SIZE_DP = 125

@Composable
internal fun WelcomeContent(
    onStartClick: () -> Unit,
    onImportClick: () -> Unit,
    appName: String,
    showImportButton: Boolean,
    modifier: Modifier = Modifier,
) {
    val itemHeights = remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    val lazyListState = rememberLazyListState()
    val totalItemsCount = 5

    Surface(modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            ResponsiveContent {
                LazyColumnWithHeaderFooter(
                    state = lazyListState,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    header = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultItemModifier()
                                .padding(top = MainTheme.spacings.double)
                                .onGloballyPositioned { coordinates ->
                                    itemHeights.value = itemHeights.value.toMutableMap().apply {
                                        put(0, coordinates.size.height)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            WelcomeLogo()
                        }
                    },
                    footer = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MainTheme.spacings.quadruple)
                                .onGloballyPositioned { coordinates ->
                                    itemHeights.value = itemHeights.value.toMutableMap().apply {
                                        put(totalItemsCount - 1, coordinates.size.height)
                                    }
                                },
                        ) {
                            WelcomeFooter(
                                showImportButton = showImportButton,
                                onStartClick = onStartClick,
                                onImportClick = onImportClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = MainTheme.spacings.quadruple),
                            )
                        }
                    },
                    content = {
                        item {
                            Box(
                                modifier = Modifier
                                    .defaultItemModifier()
                                    .onGloballyPositioned { coordinates ->
                                        itemHeights.value = itemHeights.value.toMutableMap().apply {
                                            put(1, coordinates.size.height)
                                        }
                                    },
                            ) {
                                WelcomeTitle(
                                    title = appName,
                                    modifier = Modifier.defaultItemModifier(),
                                )
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .defaultItemModifier()
                                    .onGloballyPositioned { coordinates ->
                                        itemHeights.value = itemHeights.value.toMutableMap().apply {
                                            put(2, coordinates.size.height)
                                        }
                                    },
                            ) {
                                WelcomeMessage(
                                    modifier = Modifier.defaultItemModifier(),
                                )
                            }
                        }
                        item {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .onGloballyPositioned { coords ->
                                        itemHeights.value = itemHeights.value.toMutableMap().apply {
                                            put(3, coords.size.height)
                                        }
                                    },
                            )
                        }
                    },
                )
            }
            VerticalScrollIndicator(
                listState = lazyListState,
                totalItemsCount = totalItemsCount,
                itemHeights = itemHeights.value,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = MainTheme.spacings.quarter),

            )
        }
    }
}

@Composable
fun VerticalScrollIndicator(
    listState: LazyListState,
    totalItemsCount: Int,
    itemHeights: Map<Int, Int>,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd,
    ) {
        val density = LocalDensity.current
        val viewportHeightPx = with(density) { maxHeight.toPx() }

        val averageHeight: Float = if (itemHeights.isNotEmpty()) {
            itemHeights.values.sum().toFloat() / itemHeights.size
        } else {
            viewportHeightPx / 2f
        }

        val totalContentHeightPx = (0 until totalItemsCount).sumOf { idx ->
            val measuredHeight = itemHeights[idx]
            (measuredHeight ?: averageHeight).toDouble()
        }.toFloat()

        val isScrollable = totalContentHeightPx > viewportHeightPx

        val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
        val firstVisibleItemOffset by remember { derivedStateOf { listState.firstVisibleItemScrollOffset.toFloat() } }

        val offsetBeforeFirst = (0 until firstVisibleIndex).sumOf { index ->
            (itemHeights[index] ?: averageHeight).toDouble()
        }.toFloat()

        val currentScrollOffsetPx = offsetBeforeFirst + firstVisibleItemOffset

        val scrollableRange = (totalContentHeightPx - viewportHeightPx).coerceAtLeast(1f)
        val scrolledFraction = (currentScrollOffsetPx / scrollableRange).coerceIn(0f, 1f)

        val visibleFraction = (viewportHeightPx / totalContentHeightPx).coerceIn(0.1f, 1f)

        val rawScrollbarHeightPx = viewportHeightPx * visibleFraction
        val scrollbarHeightPx = rawScrollbarHeightPx
            .coerceAtMost(viewportHeightPx * 0.2f)
            .coerceAtLeast(20f)
        val scrollbarHeight = with(density) { scrollbarHeightPx.toDp() }
        val scrollbarWidth = 4.dp

        val scrollbarOffsetY = with(density) {
            (scrolledFraction * (viewportHeightPx - scrollbarHeightPx)).toDp()
        }

        val targetAlpha = if (isScrollable) 1f else 0f
        val animatedAlpha by animateFloatAsState(targetValue = targetAlpha, label = "")

        val draggableState = rememberDraggableState { delta ->
            val scrollDelta = delta * (scrollableRange / (viewportHeightPx - scrollbarHeightPx))
            coroutineScope.launch {
                listState.scrollBy(scrollDelta)
            }
        }

        Box(
            modifier = Modifier
                .padding(end = MainTheme.spacings.default)
                .width(scrollbarWidth)
                .height(scrollbarHeight)
                .offset(y = scrollbarOffsetY)
                .alpha(animatedAlpha)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = draggableState,
                )
                .background(
                    color = MainTheme.colors.onSurfaceVariant,
                    shape = MainTheme.shapes.extraSmall,
                ),
        )
    }
}

@Composable
private fun WelcomeLogo(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(CIRCLE_COLOR))
                .size(CIRCLE_SIZE_DP.dp),
        ) {
            Image(
                painter = painterResource(id = MainTheme.images.logo),
                contentDescription = null,
                modifier = Modifier
                    .size(LOGO_SIZE_DP.dp)
                    .align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun WelcomeTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = MainTheme.spacings.quadruple),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextDisplayMedium(
            text = title,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WelcomeMessage(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = MainTheme.spacings.quadruple)
            .then(modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextBodyLarge(
            text = stringResource(id = R.string.onboarding_welcome_text),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WelcomeFooter(
    showImportButton: Boolean,
    onStartClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(bottom = MainTheme.spacings.double),
        verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.quarter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ButtonFilled(
            text = stringResource(id = R.string.onboarding_welcome_start_button),
            onClick = onStartClick,
        )
        if (showImportButton) {
            ButtonText(
                text = stringResource(id = R.string.onboarding_welcome_import_button),
                onClick = onImportClick,
            )
        }

        TextBodySmall(
            text = stringResource(R.string.onboarding_welcome_developed_by),
            modifier = Modifier
                .padding(top = MainTheme.spacings.quadruple)
                .padding(horizontal = MainTheme.spacings.double),
        )
    }
}

private fun Modifier.defaultItemModifier() = composed {
    fillMaxWidth()
        .padding(MainTheme.spacings.default)
}
