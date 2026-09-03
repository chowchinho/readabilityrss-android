package co.chinho.readabilityreader.ui.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupDockingTest {

    private val headerH = 40

    // A composed header at a known position.
    private fun live(id: Long, index: Int, top: Int) =
        HeaderSlot(groupId = id, itemIndex = index, topPx = top, bottomPx = top + headerH)

    // A header LazyColumn has recycled: no position exists, only its index.
    private fun recycled(id: Long, index: Int) =
        HeaderSlot(groupId = id, itemIndex = index, topPx = null, bottomPx = null)

    private fun docks(
        slots: List<HeaderSlot>,
        firstVisible: Int,
        top: Int = 0,
        bottom: Int = 500,
        max: Int = 3,
    ) = computeGroupDocks(slots, firstVisible, top, bottom, headerH, max)

    @Test
    fun `empty group list docks nothing`() {
        val d = docks(emptyList(), firstVisible = 0)
        assertEquals(0, d.aboveCount)
        assertEquals(0, d.belowCount)
    }

    @Test
    fun `all categories live docks nothing`() {
        val d = docks(listOf(live(1, 0, 10), live(2, 5, 200)), firstVisible = 0)
        assertEquals(0, d.aboveCount)
        assertEquals(0, d.belowCount)
        assertNull(d.crossingAbove)
        assertNull(d.crossingBelow)
    }

    @Test
    fun `header fully above the viewport docks above`() {
        val d = docks(listOf(live(1, 0, -60), live(2, 5, 100)), firstVisible = 0)
        assertEquals(listOf(1L), d.aboveRows)
        assertEquals(0, d.belowCount)
    }

    @Test
    fun `header fully below the viewport docks below`() {
        val d = docks(listOf(live(1, 0, 100), live(2, 5, 520)), firstVisible = 0)
        assertEquals(listOf(2L), d.belowRows)
        assertEquals(0, d.aboveCount)
    }

    // The case the browser prototype could not exercise: LazyColumn does not compose off-screen
    // items, so a departed header has no measured position at all.
    @Test
    fun `recycled headers are classified by index not position`() {
        val slots = listOf(
            recycled(1, 0), recycled(2, 6), recycled(3, 11),
            live(4, 20, 10),
            recycled(5, 40), recycled(6, 47),
        )
        val d = docks(slots, firstVisible = 20)
        assertEquals(listOf(1L, 2L, 3L), d.aboveRows)
        assertEquals(listOf(5L, 6L), d.belowRows)
    }

    @Test
    fun `above is capped at max and takes the nearest`() {
        val slots = (1..6).map { recycled(it.toLong(), it) } + live(99, 20, 10)
        val d = docks(slots, firstVisible = 20, max = 3)
        assertEquals(6, d.aboveCount)
        // nearest three are the last three, plus one more for the row sliding out
        assertEquals(listOf(3L, 4L, 5L, 6L), d.aboveRows)
    }

    @Test
    fun `below is capped at max and takes the nearest`() {
        val slots = listOf(live(99, 0, 10)) + (1..6).map { recycled(it.toLong(), it + 10) }
        val d = docks(slots, firstVisible = 0, max = 3)
        assertEquals(6, d.belowCount)
        assertEquals(listOf(1L, 2L, 3L, 4L), d.belowRows)
    }

    @Test
    fun `rolling window advances as scroll advances`() {
        val slots = (1..6).map { recycled(it.toLong(), it) } + live(99, 20, 10)
        assertEquals(listOf(2L, 3L), docks(slots, firstVisible = 4, max = 2).aboveRows.takeLast(2))
        assertEquals(listOf(5L, 6L), docks(slots, firstVisible = 20, max = 2).aboveRows.takeLast(2))
    }

    @Test
    fun `both docks can be full at once`() {
        val slots = (1..4).map { recycled(it.toLong(), it) } +
            live(50, 20, 10) +
            (5..8).map { recycled(it.toLong(), it + 30) }
        val d = docks(slots, firstVisible = 20, max = 3)
        assertEquals(4, d.aboveCount)
        assertEquals(4, d.belowCount)
    }

    @Test
    fun `a header straddling the top edge is the crossing header and is not docked`() {
        val d = docks(listOf(live(1, 0, -20), live(2, 5, 100)), firstVisible = 0)
        assertEquals(1L, d.crossingAbove)
        assertEquals(0, d.aboveCount)
    }

    @Test
    fun `a header straddling the bottom edge is the crossing header and is not docked`() {
        val d = docks(listOf(live(1, 0, 100), live(2, 5, 480)), firstVisible = 0)
        assertEquals(2L, d.crossingBelow)
        assertEquals(0, d.belowCount)
    }

    @Test
    fun `crossing fraction runs from zero to one across the top edge`() {
        assertEquals(0f, crossingFraction(top = 0, edge = 0, headerHeightPx = headerH, side = DockSide.Above), 0.001f)
        assertEquals(0.5f, crossingFraction(top = -20, edge = 0, headerHeightPx = headerH, side = DockSide.Above), 0.001f)
        assertEquals(1f, crossingFraction(top = -40, edge = 0, headerHeightPx = headerH, side = DockSide.Above), 0.001f)
        // clamped
        assertEquals(1f, crossingFraction(top = -400, edge = 0, headerHeightPx = headerH, side = DockSide.Above), 0.001f)
    }

    @Test
    fun `crossing fraction runs from zero to one across the bottom edge`() {
        assertEquals(0f, crossingFraction(top = 460, edge = 500, headerHeightPx = headerH, side = DockSide.Below), 0.001f)
        assertEquals(0.5f, crossingFraction(top = 480, edge = 500, headerHeightPx = headerH, side = DockSide.Below), 0.001f)
        assertEquals(1f, crossingFraction(top = 500, edge = 500, headerHeightPx = headerH, side = DockSide.Below), 0.001f)
    }

    @Test
    fun `a manually collapsed category docks like any other`() {
        // A collapsed group contributes only its header, so indices are contiguous.
        val slots = listOf(recycled(1, 0), recycled(2, 1), live(3, 2, 10))
        val d = docks(slots, firstVisible = 2)
        assertEquals(listOf(1L, 2L), d.aboveRows)
    }

    @Test
    fun `max of zero disables docking entirely`() {
        val slots = (1..5).map { recycled(it.toLong(), it) } + live(99, 20, 10)
        val d = docks(slots, firstVisible = 20, max = 0)
        assertEquals(0, d.aboveCount)
        assertEquals(0, d.belowCount)
        assertEquals(emptyList<Long>(), d.aboveRows)
    }

    // The docked count is a user setting, so every cap in its range has to carry the extra row
    // the dock renders past its own capacity, not just the former hardcoded three.
    @Test
    fun `each cap in the settings range keeps one spare row`() {
        val slots = (1..10).map { recycled(it.toLong(), it) } + live(99, 20, 10)
        for (max in 1..6) {
            val d = docks(slots, firstVisible = 20, max = max)
            assertEquals(10, d.aboveCount)
            assertEquals(max + 1, d.aboveRows.size)
            assertEquals((10L - max)..10L, d.aboveRows.first()..d.aboveRows.last())
        }
    }
}
