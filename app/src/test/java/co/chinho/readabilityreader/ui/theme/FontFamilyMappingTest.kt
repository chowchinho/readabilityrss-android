package co.chinho.readabilityreader.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class FontFamilyMappingTest {

    @Test
    fun serifMapsInAllCasings() {
        assertEquals(FontFamily.Serif, "serif".toComposeFontFamily())
        assertEquals(FontFamily.Serif, "SERIF".toComposeFontFamily())
        assertEquals(FontFamily.Serif, "Serif".toComposeFontFamily())
        assertEquals(FontFamily.Serif, "sErIf".toComposeFontFamily())
    }

    @Test
    fun sansSerifMapsInAllCasings() {
        assertEquals(FontFamily.SansSerif, "sans-serif".toComposeFontFamily())
        assertEquals(FontFamily.SansSerif, "SANS-SERIF".toComposeFontFamily())
        assertEquals(FontFamily.SansSerif, "Sans-Serif".toComposeFontFamily())
        assertEquals(FontFamily.SansSerif, "Sans-serif".toComposeFontFamily())
    }

    @Test
    fun monospaceMapsInAllCasings() {
        assertEquals(FontFamily.Monospace, "monospace".toComposeFontFamily())
        assertEquals(FontFamily.Monospace, "MONOSPACE".toComposeFontFamily())
        assertEquals(FontFamily.Monospace, "Monospace".toComposeFontFamily())
        assertEquals(FontFamily.Monospace, "MonoSpace".toComposeFontFamily())
    }

    @Test
    fun unknownOrFallbackNamesMapToDefault() {
        assertEquals(FontFamily.Default, "system".toComposeFontFamily())
        assertEquals(FontFamily.Default, "SYSTEM".toComposeFontFamily())
        assertEquals(FontFamily.Default, "".toComposeFontFamily())
        assertEquals(FontFamily.Default, "unknown".toComposeFontFamily())
        assertEquals(FontFamily.Default, "comic-sans".toComposeFontFamily())
    }
}
