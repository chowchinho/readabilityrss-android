package co.chinho.readabilityreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroImageLayoutTest {

    @Test
    fun landscapeHeroIsShownWholeAtItsNaturalHeight() {
        val layout = computeHeroLayout(widthDp = 400f, aspectRatio = 16f / 9f, maxHeightDp = 500f)

        assertEquals(225f, layout.heightDp, 0.5f)
        assertFalse(layout.isCropped)
    }

    @Test
    fun portraitHeroTallerThanTheCapIsCroppedToTheCap() {
        val layout = computeHeroLayout(widthDp = 400f, aspectRatio = 3f / 4f, maxHeightDp = 500f)

        assertEquals(500f, layout.heightDp, 0.01f)
        assertTrue(layout.isCropped)
    }

    @Test
    fun heroExactlyAtTheCapIsNotCropped() {
        val layout = computeHeroLayout(widthDp = 500f, aspectRatio = 1f, maxHeightDp = 500f)

        assertEquals(500f, layout.heightDp, 0.01f)
        assertFalse(layout.isCropped)
    }

    @Test
    fun unknownAspectReservesTheFallbackShape() {
        val layout = computeHeroLayout(widthDp = 400f, aspectRatio = null, maxHeightDp = 500f)

        assertEquals(400f / HERO_FALLBACK_ASPECT, layout.heightDp, 0.01f)
        assertFalse(layout.isCropped)
    }

    @Test
    fun nonsenseAspectFallsBackInsteadOfCollapsingOrExploding() {
        val expected = computeHeroLayout(400f, null, 500f).heightDp

        assertEquals(expected, computeHeroLayout(400f, 0f, 500f).heightDp, 0.01f)
        assertEquals(expected, computeHeroLayout(400f, -2f, 500f).heightDp, 0.01f)
        assertEquals(expected, computeHeroLayout(400f, Float.NaN, 500f).heightDp, 0.01f)
        assertEquals(expected, computeHeroLayout(400f, Float.POSITIVE_INFINITY, 500f).heightDp, 0.01f)
    }

    @Test
    fun zeroWidthReservesNothing() {
        assertEquals(0f, computeHeroLayout(0f, 1.5f, 500f).heightDp, 0.01f)
    }
}
