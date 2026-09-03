package co.chinho.readabilityreader.ui.components

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideshowModelTest {

    private fun img(n: Int) = SlideshowImage("https://e.test/$n.jpg")

    @Test
    fun `cover leads and document order is preserved`() {
        val cover = SlideshowImage("https://e.test/cover.jpg", focalX = 20, focalY = 80)

        val slides = buildSlides(cover, listOf(img(1), img(2)))

        assertEquals(
            listOf("https://e.test/cover.jpg", "https://e.test/1.jpg", "https://e.test/2.jpg"),
            slides.map { it.src },
        )
        assertEquals(20, slides.first().focalX)
    }

    @Test
    fun `the cover is not repeated when it also appears in the body`() {
        val cover = SlideshowImage("https://e.test/1.jpg")

        val slides = buildSlides(cover, listOf(img(1), img(2)))

        assertEquals(listOf("https://e.test/1.jpg", "https://e.test/2.jpg"), slides.map { it.src })
    }

    @Test
    fun `a twenty image article is capped at five slides`() {
        val slides = buildSlides(img(0), (1..20).map(::img))

        assertEquals(5, slides.size)
        assertEquals("https://e.test/4.jpg", slides.last().src)
    }

    @Test
    fun `no cover yields no slides`() {
        assertEquals(emptyList<SlideshowImage>(), buildSlides(null, listOf(img(1))))
    }

    @Test
    fun `a cover with no extras stays a single slide`() {
        assertEquals(1, buildSlides(img(0), emptyList()).size)
    }

    @Test
    fun `stagger stays inside the declared window`() {
        val offsets = (1L..3_000L).map(::staggerMs)

        assertTrue(offsets.all { it in 0 until MAX_STAGGER_MS })
    }

    @Test
    fun `extreme article ids never produce a negative delay`() {
        assertTrue(staggerMs(Long.MIN_VALUE) >= 0L)
        assertTrue(staggerMs(Long.MAX_VALUE) >= 0L)
        assertTrue(staggerMs(0L) >= 0L)
    }

    @Test
    fun `stagger covers the whole window rather than clustering`() {
        val buckets = (1L..3_000L).map { (staggerMs(it) / (MAX_STAGGER_MS / 10)).toInt() }.toSet()

        assertEquals(10, buckets.size)
    }

    @Test
    fun `consecutive article ids are de-phased on average`() {
        // The previous hash was `(id * 2654435761L ushr 20) % 1500`, which left neighbouring
        // cards about 4ms apart and made a page of cards transition in lockstep. For a uniform
        // spread over 0..1499 the expected mean gap is ~500ms.
        val meanGap = (1L..1_000L).map(::staggerMs)
            .zipWithNext { a, b -> abs(a - b) }
            .average()

        assertTrue("mean gap was $meanGap", meanGap > 300.0)
    }
}
