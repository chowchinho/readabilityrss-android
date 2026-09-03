package co.chinho.readabilityreader.ui.reader

import kotlin.math.roundToInt

/**
 * The vote pill's resting place is stored as a fraction of the free space rather than as pixels,
 * so a position chosen on the folded Flip still resolves sensibly unfolded, rotated, or in a
 * tablet pane. 0 is the left/top edge, 1 the right/bottom.
 */
const val VOTE_PILL_DEFAULT_FRACTION_X = 1f
const val VOTE_PILL_DEFAULT_FRACTION_Y = 1f

data class VotePillOffset(val x: Int, val y: Int)

private fun freeSpace(container: Int, pill: Int, margin: Int): Int =
    (container - pill - margin * 2).coerceAtLeast(0)

fun votePillOffset(
    fractionX: Float,
    fractionY: Float,
    containerWidth: Int,
    containerHeight: Int,
    pillWidth: Int,
    pillHeight: Int,
    marginPx: Int,
): VotePillOffset {
    val spanX = freeSpace(containerWidth, pillWidth, marginPx)
    val spanY = freeSpace(containerHeight, pillHeight, marginPx)
    return VotePillOffset(
        x = marginPx + (fractionX.coerceIn(0f, 1f) * spanX).roundToInt(),
        y = marginPx + (fractionY.coerceIn(0f, 1f) * spanY).roundToInt(),
    )
}

/** Inverse of [votePillOffset], for turning a drag's resting pixels back into a stored fraction. */
fun votePillFraction(
    offsetX: Float,
    offsetY: Float,
    containerWidth: Int,
    containerHeight: Int,
    pillWidth: Int,
    pillHeight: Int,
    marginPx: Int,
): Pair<Float, Float> {
    val spanX = freeSpace(containerWidth, pillWidth, marginPx)
    val spanY = freeSpace(containerHeight, pillHeight, marginPx)
    // A container with no room to move keeps whatever fraction was stored rather than snapping.
    val x = if (spanX == 0) VOTE_PILL_DEFAULT_FRACTION_X else (offsetX - marginPx) / spanX
    val y = if (spanY == 0) VOTE_PILL_DEFAULT_FRACTION_Y else (offsetY - marginPx) / spanY
    return Pair(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}
