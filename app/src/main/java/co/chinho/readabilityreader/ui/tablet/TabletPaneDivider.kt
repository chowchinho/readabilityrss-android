package co.chinho.readabilityreader.ui.tablet

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun TabletPaneDivider(
    onDrag: (Float) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragFinished by rememberUpdatedState(onDragFinished)

    Box(
        modifier = modifier
            .width(16.dp)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { currentOnDragFinished() },
                    onDragCancel = { currentOnDragFinished() },
                ) { change, dragAmount ->
                    change.consume()
                    currentOnDrag(dragAmount.x)
                }
            },
    )
}
