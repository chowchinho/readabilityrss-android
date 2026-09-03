package co.chinho.readabilityreader.ui.articles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverPrefetchTest {

    @Test
    fun `mid list window extends seven either side`() {
        assertEquals(13..34, prefetchWindow(firstVisible = 20, lastVisible = 27, itemCount = 100))
    }

    @Test
    fun `window clamps at the start of the list`() {
        assertEquals(0..10, prefetchWindow(firstVisible = 0, lastVisible = 3, itemCount = 100))
    }

    @Test
    fun `window clamps at the end of the list`() {
        assertEquals(85..99, prefetchWindow(firstVisible = 92, lastVisible = 99, itemCount = 100))
    }

    @Test
    fun `a short list is covered entirely`() {
        assertEquals(0..2, prefetchWindow(firstVisible = 0, lastVisible = 2, itemCount = 3))
    }

    @Test
    fun `an empty list yields no window`() {
        assertTrue(prefetchWindow(firstVisible = 0, lastVisible = 0, itemCount = 0).isEmpty())
    }

    @Test
    fun `nothing measured yet still warms the head of the list`() {
        assertEquals(0..7, prefetchWindow(firstVisible = 0, lastVisible = 0, itemCount = 50))
    }
}
