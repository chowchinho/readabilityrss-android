package co.chinho.readabilityreader.ui.feeds

/** How many categories may sit docked at each end of the live list. */
const val MaxDockedPerEnd = 3

enum class DockSide { Above, Below }

/**
 * A category header's position, or its absence. [topPx] and [bottomPx] are null when LazyColumn has
 * recycled the header, which is the normal state for anything more than a screen away.
 */
data class HeaderSlot(
    val groupId: Long,
    val itemIndex: Int,
    val topPx: Int?,
    val bottomPx: Int?,
)

/**
 * [aboveRows] and [belowRows] carry one more than the cap so the row being pushed out of the far
 * edge is available to render; [aboveCount] and [belowCount] are the true totals and are what the
 * dock height is derived from.
 */
data class GroupDockRows(
    val aboveCount: Int = 0,
    val belowCount: Int = 0,
    val aboveRows: List<Long> = emptyList(),
    val belowRows: List<Long> = emptyList(),
    val crossingAbove: Long? = null,
    val crossingBelow: Long? = null,
)

fun computeGroupDocks(
    slots: List<HeaderSlot>,
    firstVisibleItemIndex: Int,
    viewportTopPx: Int,
    viewportBottomPx: Int,
    liveHeaderHeightPx: Int,
    maxPerDock: Int = MaxDockedPerEnd,
): GroupDockRows {
    if (maxPerDock <= 0 || slots.isEmpty() || liveHeaderHeightPx <= 0) return GroupDockRows()

    val above = mutableListOf<Long>()
    val below = mutableListOf<Long>()
    var crossingAbove: Long? = null
    var crossingBelow: Long? = null

    for (slot in slots) {
        val top = slot.topPx
        val bottom = slot.bottomPx
        if (top == null || bottom == null) {
            if (slot.itemIndex < firstVisibleItemIndex) above += slot.groupId else below += slot.groupId
            continue
        }
        when {
            bottom <= viewportTopPx -> above += slot.groupId
            top >= viewportBottomPx -> below += slot.groupId
            else -> {
                if (top < viewportTopPx && crossingAbove == null) crossingAbove = slot.groupId
                if (bottom > viewportBottomPx) crossingBelow = slot.groupId
            }
        }
    }

    return GroupDockRows(
        aboveCount = above.size,
        belowCount = below.size,
        aboveRows = above.takeLast(maxPerDock + 1),
        belowRows = below.take(maxPerDock + 1),
        crossingAbove = crossingAbove,
        crossingBelow = crossingBelow,
    )
}

/**
 * How far the crossing header has travelled past the edge, as a fraction of a live header. Drives
 * the dock's growth and the crossing row's compression; must be read in the layout or draw phase,
 * never as recomposition-triggering state.
 */
fun crossingFraction(top: Int, edge: Int, headerHeightPx: Int, side: DockSide): Float {
    if (headerHeightPx <= 0) return 0f
    val travelled = when (side) {
        DockSide.Above -> (edge - top).toFloat()
        DockSide.Below -> (top + headerHeightPx - edge).toFloat()
    }
    return (travelled / headerHeightPx).coerceIn(0f, 1f)
}
