package co.chinho.readabilityreader.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

data class SyncStatus(
    val isSyncing: Boolean = false,
    val syncedCount: Int = 0,
    val totalCount: Int = 0,
    val stage: String? = null,
    val isOffline: Boolean = false,
    val hasCompletedSync: Boolean = false,
    val lastSyncedAt: Long? = null,
    val checkedImages: Int = 0,
    val fetchedImages: Int = 0,
    val totalImages: Int = 0,
)

@Composable
private fun rememberSyncRotation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "SyncRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )
    return rotation
}

@Composable
fun SyncStatusBottomBar(
    syncStatus: SyncStatus,
    isEInk: Boolean,
    modifier: Modifier = Modifier,
    onSettingsClick: (() -> Unit)? = null
) {
    // Only spin while there is something to spin for. An infinite transition requests a frame
    // callback every frame for as long as it exists, so creating one unconditionally keeps the UI
    // thread awake permanently and leaves no idle headroom for scrolling.
    val spinning = !isEInk && !syncStatus.isOffline &&
        (syncStatus.isSyncing || imageStatusText(syncStatus) != null)
    val rotation = if (spinning) rememberSyncRotation() else 0f

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, iconColor, text, iconModifier) = when {
                syncStatus.isOffline -> {
                    Quad(Icons.Default.CloudOff, MaterialTheme.colorScheme.outline, "Offline - ${syncStatus.totalCount} Articles Cached", Modifier)
                }
                syncStatus.isSyncing || imageStatusText(syncStatus) != null -> {
                    val mod = if (isEInk) Modifier else Modifier.rotate(rotation)
                    val text = syncStatus.toDisplayText()
                    Quad(Icons.Default.Sync, MaterialTheme.colorScheme.primary, text, mod)
                }
                else -> {
                    val ago = syncStatus.lastSyncedAt?.let { formatTimeAgo(nowMs - it) }
                    val text = if (ago != null) {
                        "Sync Completed - ${syncStatus.totalCount} Articles Cached ($ago)"
                    } else {
                        "Sync Completed - ${syncStatus.totalCount} Articles Cached"
                    }
                    Quad(Icons.Default.CheckCircle, Color(0xFF4CAF50), text, Modifier)
                }
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isEInk) MaterialTheme.colorScheme.onSurfaceVariant else iconColor,
                    modifier = iconModifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (onSettingsClick != null) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun formatTimeAgo(elapsedMs: Long): String {
    val minutes = (elapsedMs.coerceAtLeast(0L) / 60_000L)
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        minutes < 1440L -> "${minutes / 60L}h ago"
        else -> "${minutes / 1440L}d ago"
    }
}

private fun Int.grouped(): String = "%,d".format(this)

internal fun imageStatusText(status: SyncStatus): String? {
    // Most of this phase is a disk probe that skips an already-cached URL, so the running count
    // is "checked", not "downloaded". Only the tail that was genuinely absent gets fetched, and
    // that is the number worth calling a download.
    if (status.totalImages > 0) {
        val checked = "Checking images (${status.checkedImages.grouped()}/${status.totalImages.grouped()})"
        return if (status.fetchedImages > 0) {
            "$checked - ${status.fetchedImages.grouped()} downloaded"
        } else {
            checked
        }
    }
    return null
}

private const val WEIGHT_ARTICLES = 0.30f
private const val WEIGHT_DOWNLOAD = 0.70f

/**
 * Progress across every sync phase, not just the article fetch.
 */
internal fun syncProgressFraction(status: SyncStatus): Float {
    if (status.isOffline) return 0f

    val phases = listOf(
        Triple(status.syncedCount, status.totalCount, WEIGHT_ARTICLES),
        Triple(status.checkedImages, status.totalImages, WEIGHT_DOWNLOAD),
    ).filter { (_, total, _) -> total > 0 }

    if (phases.isEmpty()) return if (status.hasCompletedSync && !status.isSyncing) 1f else 0f

    val totalWeight = phases.fold(0f) { acc, (_, _, weight) -> acc + weight }
    return phases.fold(0f) { acc, (done, total, weight) ->
        acc + (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) * (weight / totalWeight)
    }.coerceIn(0f, 1f)
}

/** True while any phase still has outstanding work, including the image phase. */
internal fun SyncStatus.isBusy(): Boolean =
    isSyncing || (totalImages > 0 && checkedImages < totalImages)

private fun SyncStatus.toDisplayText(): String {
    return when (stage) {
        "sync_start" -> "Preparing sync..."
        "sync_download" -> {
            if (totalCount > 0) {
                "Scanning server articles ${syncedCount}/${totalCount}..."
            } else {
                "Scanning server articles..."
            }
        }
        "sync_process" -> {
            if (totalCount > 0) {
                "Saving articles ${syncedCount}/${totalCount}..."
            } else {
                "Saving articles..."
            }
        }
        "sync_evict" -> "Cleaning cached data..."
        "sync_complete" -> imageStatusText(this) ?: "Finishing sync..."
        else -> {
            imageStatusText(this) ?: if (totalCount > 0) {
                "Synchronising ${syncedCount}/${totalCount} articles..."
            } else {
                "Synchronising..."
            }
        }
    }
}

