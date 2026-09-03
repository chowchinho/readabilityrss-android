package co.chinho.readabilityreader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.chinho.readabilityreader.ui.theme.LocalEInkMode

@Composable
fun SyncProgressTopBorder(
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier,
) {
    val isEInk = LocalEInkMode.current
    val borderHeight = 3.dp
    val targetProgress = syncProgressFraction(syncStatus)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 220),
        label = "syncProgressBorder",
    )
    val progress = if (isEInk) targetProgress else animatedProgress

    val trackColor = if (isEInk) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val fillColor = when {
        isEInk -> MaterialTheme.colorScheme.onSurface
        syncStatus.isBusy() -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(borderHeight)
            .background(trackColor)
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(fillColor)
            )
        }
    }
}
