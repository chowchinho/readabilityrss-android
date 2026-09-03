package co.chinho.readabilityreader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Left and right system-gesture insets in pixels.
 *
 * Deliberately not `WindowInsets.systemGestures`. This app does not call
 * `setDecorFitsSystemWindows(window, false)`, so the decor view consumes the insets before
 * Compose's inset provider sees them and `WindowInsets.systemGestures` reports zero — measured on
 * device, not assumed. The root insets are unaffected by that consumption.
 *
 * Returns zeroes under three-button navigation, which correctly disables edge exclusion rather
 * than stealing a strip of a row that has no back gesture on it.
 */
@Composable
fun rememberSystemGestureInsetsPx(): Pair<Float, Float> {
    val view = LocalView.current
    // Configuration is a key because folding the device swaps windows and changes the insets.
    val configuration = LocalConfiguration.current
    return remember(view, configuration) {
        val insets = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.systemGestures())
        (insets?.left?.toFloat() ?: 0f) to (insets?.right?.toFloat() ?: 0f)
    }
}

/**
 * A drag beginning inside the system's gesture insets belongs to the OS back gesture, not to
 * voting.
 */
fun isGestureFromSystemEdge(
    startX: Float,
    rowWidth: Float,
    leftInsetPx: Float,
    rightInsetPx: Float,
): Boolean {
    if (leftInsetPx > 0f && startX < leftInsetPx) return true
    if (rightInsetPx > 0f && rowWidth > 0f && startX > rowWidth - rightInsetPx) return true
    return false
}
