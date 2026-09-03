package co.chinho.readabilityreader.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeEdgeExclusionTest {

    // Typical gesture-navigation insets on a 1080px-wide device at 2.625x: ~24dp = 63px.
    private val left = 63f
    private val right = 63f
    private val width = 1080f

    @Test
    fun dragStartingInLeftEdgeIsExcluded() {
        assertTrue(isGestureFromSystemEdge(10f, width, left, right))
        assertTrue(isGestureFromSystemEdge(62f, width, left, right))
    }

    @Test
    fun dragStartingInRightEdgeIsExcluded() {
        assertTrue(isGestureFromSystemEdge(1070f, width, left, right))
        assertTrue(isGestureFromSystemEdge(1018f, width, left, right))
    }

    @Test
    fun dragStartingInTheBodyIsNotExcluded() {
        assertFalse(isGestureFromSystemEdge(63f, width, left, right))
        assertFalse(isGestureFromSystemEdge(540f, width, left, right))
        assertFalse(isGestureFromSystemEdge(1017f, width, left, right))
    }

    @Test
    fun threeButtonNavigationExcludesNothing() {
        assertFalse(isGestureFromSystemEdge(0f, width, 0f, 0f))
        assertFalse(isGestureFromSystemEdge(540f, width, 0f, 0f))
        assertFalse(isGestureFromSystemEdge(1080f, width, 0f, 0f))
    }

    @Test
    fun unmeasuredRowSkipsTheRightEdgeCheck() {
        assertFalse(isGestureFromSystemEdge(540f, 0f, left, right))
        assertTrue(isGestureFromSystemEdge(10f, 0f, left, right))
    }

    @Test
    fun onlyOneEdgeReservedStillGuardsThatEdge() {
        assertTrue(isGestureFromSystemEdge(10f, width, left, 0f))
        assertFalse(isGestureFromSystemEdge(1070f, width, left, 0f))
    }
}
