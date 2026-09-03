package co.chinho.readabilityreader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleThumbnailRequestTest {

    @Test
    fun `spec carries only pixel geometry`() {
        val spec = ArticleThumbnailRequest.Spec(widthPx = 288, heightPx = 288)

        assertEquals(288, spec.widthPx)
        assertEquals(288, spec.heightPx)
    }

    @Test
    fun `centre focal produces no bias`() {
        val alignment = getFocalAlignment(50, 50)

        assertEquals(androidx.compose.ui.BiasAlignment(0f, 0f), alignment)
    }

    @Test
    fun `focal maps into the minus one to one bias range`() {
        assertEquals(androidx.compose.ui.BiasAlignment(-1f, -1f), getFocalAlignment(0, 0))
        assertEquals(androidx.compose.ui.BiasAlignment(1f, 1f), getFocalAlignment(100, 100))
    }

    @Test
    fun `off centre focal biases towards the subject`() {
        val alignment = getFocalAlignment(25, 75) as androidx.compose.ui.BiasAlignment

        assertTrue(alignment.horizontalBias < 0f)
        assertTrue(alignment.verticalBias > 0f)
    }
}
