package co.chinho.readabilityreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class VotePillPositionTest {

    private val container = Pair(1080, 2400)
    private val pill = Pair(300, 140)
    private val margin = 63

    private fun offsetFor(fx: Float, fy: Float) = votePillOffset(
        fractionX = fx,
        fractionY = fy,
        containerWidth = container.first,
        containerHeight = container.second,
        pillWidth = pill.first,
        pillHeight = pill.second,
        marginPx = margin,
    )

    private fun fractionFor(x: Float, y: Float) = votePillFraction(
        offsetX = x,
        offsetY = y,
        containerWidth = container.first,
        containerHeight = container.second,
        pillWidth = pill.first,
        pillHeight = pill.second,
        marginPx = margin,
    )

    @Test
    fun defaultFractionRestsBottomRightInsideTheMargin() {
        val o = offsetFor(VOTE_PILL_DEFAULT_FRACTION_X, VOTE_PILL_DEFAULT_FRACTION_Y)

        assertEquals(container.first - pill.first - margin, o.x)
        assertEquals(container.second - pill.second - margin, o.y)
    }

    @Test
    fun zeroFractionRestsTopLeftInsideTheMargin() {
        val o = offsetFor(0f, 0f)

        assertEquals(margin, o.x)
        assertEquals(margin, o.y)
    }

    @Test
    fun fractionRoundTripsThroughPixels() {
        for (fx in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            for (fy in listOf(0f, 0.33f, 1f)) {
                val o = offsetFor(fx, fy)
                val (backX, backY) = fractionFor(o.x.toFloat(), o.y.toFloat())
                assertEquals(fx, backX, 0.002f)
                assertEquals(fy, backY, 0.002f)
            }
        }
    }

    @Test
    fun outOfRangeFractionsAreClampedRatherThanPlacedOffScreen() {
        assertEquals(offsetFor(1f, 1f), offsetFor(4f, 9f))
        assertEquals(offsetFor(0f, 0f), offsetFor(-3f, -1f))
    }

    // Folding the Flip can leave a pane narrower than the pill plus its margins.
    @Test
    fun containerTooSmallToMoveInPinsToTheMarginWithoutNegativeOffsets() {
        val o = votePillOffset(1f, 1f, 200, 100, 300, 140, 63)

        assertEquals(63, o.x)
        assertEquals(63, o.y)
    }

    @Test
    fun containerTooSmallToMoveInKeepsTheStoredCornerRatherThanSnapping() {
        val (x, y) = votePillFraction(0f, 0f, 200, 100, 300, 140, 63)

        assertEquals(VOTE_PILL_DEFAULT_FRACTION_X, x, 0.001f)
        assertEquals(VOTE_PILL_DEFAULT_FRACTION_Y, y, 0.001f)
    }
}
