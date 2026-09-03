package co.chinho.readabilityreader.ui.articles

const val PREFETCH_RADIUS = 7
const val PREFETCH_DEBOUNCE_MS = 150L

fun prefetchWindow(
    firstVisible: Int,
    lastVisible: Int,
    itemCount: Int,
    radius: Int = PREFETCH_RADIUS,
): IntRange {
    if (itemCount <= 0) return IntRange.EMPTY
    val start = (firstVisible - radius).coerceIn(0, itemCount - 1)
    val end = (lastVisible + radius).coerceIn(0, itemCount - 1)
    return if (start > end) IntRange.EMPTY else start..end
}
