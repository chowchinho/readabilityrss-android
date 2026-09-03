package co.chinho.readabilityreader.ui.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupExpansionTest {

    @Test
    fun unknownGroupDefaultsToExpanded() {
        assertTrue(isGroupExpanded(emptyList(), groupId = 1L))
        assertTrue(isGroupExpanded(listOf(2L, 3L), groupId = 1L))
    }

    @Test
    fun collapsedGroupIsNotExpanded() {
        assertFalse(isGroupExpanded(listOf(1L), groupId = 1L))
    }

    @Test
    fun togglingCollapsesThenExpands() {
        val afterFirst = toggleGroupCollapsed(emptyList(), groupId = 1L)
        assertEquals(listOf(1L), afterFirst)
        assertFalse(isGroupExpanded(afterFirst, groupId = 1L))

        val afterSecond = toggleGroupCollapsed(afterFirst, groupId = 1L)
        assertEquals(emptyList<Long>(), afterSecond)
        assertTrue(isGroupExpanded(afterSecond, groupId = 1L))
    }

    @Test
    fun togglingOneGroupLeavesOthersAlone() {
        val collapsed = toggleGroupCollapsed(listOf(2L), groupId = 1L)
        assertEquals(listOf(2L, 1L), collapsed)
        assertFalse(isGroupExpanded(collapsed, groupId = 2L))
    }

    @Test
    fun togglingIsIdempotentPerPair() {
        var state = listOf<Long>()
        repeat(4) { state = toggleGroupCollapsed(state, groupId = 9L) }
        assertEquals(emptyList<Long>(), state)
    }
}
