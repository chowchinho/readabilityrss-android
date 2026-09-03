package co.chinho.readabilityreader.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class EInkPageButtonDirection {
    Up,
    Down,
}

@Composable
fun rememberEInkPageButtonModifier(
    enabled: Boolean,
    onPageButton: (EInkPageButtonDirection) -> Unit,
): Modifier {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(enabled) {
        if (enabled) {
            focusRequester.requestFocus()
        }
    }

    return Modifier
        .focusRequester(focusRequester)
        .focusable(enabled = enabled)
        .onPreviewKeyEvent { event ->
            if (!enabled || event.type != KeyEventType.KeyDown) {
                return@onPreviewKeyEvent false
            }

            when (event.key) {
                Key.PageUp -> {
                    onPageButton(EInkPageButtonDirection.Up)
                    true
                }

                Key.PageDown -> {
                    onPageButton(EInkPageButtonDirection.Down)
                    true
                }

                else -> false
            }
        }
}

fun CoroutineScope.launchLazyListPageScroll(
    state: LazyListState,
    direction: EInkPageButtonDirection,
    viewportHeightPx: Int,
    pageFraction: Float,
) {
    if (viewportHeightPx <= 0) return

    val visibleItems = state.layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return

    val averageItemSize = visibleItems
        .map(LazyListItemInfo::size)
        .average()
        .takeIf { it > 0.0 }
        ?: return

    val targetRows = ((viewportHeightPx * pageFraction) / averageItemSize)
        .toInt()
        .coerceAtLeast(1)

    val lastVisibleIndex = visibleItems.last().index
    val targetIndex = when (direction) {
        EInkPageButtonDirection.Down -> (lastVisibleIndex + targetRows)
            .coerceAtMost(state.layoutInfo.totalItemsCount - 1)

        EInkPageButtonDirection.Up -> (state.firstVisibleItemIndex - targetRows)
            .coerceAtLeast(0)
    }

    launch {
        state.animateScrollToItem(targetIndex)
    }
}
