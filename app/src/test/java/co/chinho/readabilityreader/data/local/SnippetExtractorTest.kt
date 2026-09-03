package co.chinho.readabilityreader.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetExtractorTest {

    @Test
    fun testTranslationNoticeIsStrippedWhenFirstElement() {
        val html = "<p>Translated by DeepL</p><p>Actual article content starts here.</p>"
        val snippet = SnippetExtractor.extract(html)
        assertEquals("Actual article content starts here.", snippet)
    }

    @Test
    fun testTranslationNoticeWithGlobeIconAndClassIsStripped() {
        val html = "<p class=\"translation-notice\">🌐 Translated by Google Translate</p><div>Real story text.</div>"
        val snippet = SnippetExtractor.extract(html)
        assertEquals("Real story text.", snippet)
    }

    @Test
    fun testTagsBecomeSpaces() {
        val html = "<p>Hello</p><p>World</p>"
        val snippet = SnippetExtractor.extract(html)
        assertEquals("Hello World", snippet)
    }

    @Test
    fun testTrailingPartialTagIsDropped() {
        val html = "<p>Hello World</p><div class=\"unclosed"
        val snippet = SnippetExtractor.extract(html)
        assertEquals("Hello World", snippet)
    }

    @Test
    fun testWhitespaceCollapsesAndTrims() {
        val html = "   <p>   Hello  \n\t  World  \n </p>   "
        val snippet = SnippetExtractor.extract(html)
        assertEquals("Hello World", snippet)
    }

    @Test
    fun testResultIsCappedAt300Characters() {
        val repeatedWord = "word "
        val longText = "<p>" + repeatedWord.repeat(100) + "</p>"
        val snippet = SnippetExtractor.extract(longText)
        org.junit.Assert.assertNotNull(snippet)
        assertEquals(SnippetExtractor.MAX_VISIBLE_CHARS, snippet!!.length)
        assertTrue(snippet.length <= 300)
    }

    @Test
    fun testEmptyOrWhitespaceOnlyReturnsNull() {
        assertNull(SnippetExtractor.extract(null))
        assertNull(SnippetExtractor.extract(""))
        assertNull(SnippetExtractor.extract("   \n\t   "))
        assertNull(SnippetExtractor.extract("<p></p><div>   </div>"))
        assertNull(SnippetExtractor.extract("<p>Translated by DeepL</p>"))
    }

    @Test
    fun testFixtureWithHeavyMarkupBefore800CharsProducesSnippet() {
        // Simulates an article where translation notice and heavy JSON data attributes
        // consumed over 1,200 characters before any readable text appeared.
        val largeDataAttribute = "a".repeat(1200)
        val html = "<p>Translated by DeepL</p><div class=\"gallery\" data-images=\"$largeDataAttribute\"></div><p>Real article text that would have been missed by an 800-char prefix.</p>"
        val snippet = SnippetExtractor.extract(html)
        assertEquals("Real article text that would have been missed by an 800-char prefix.", snippet)
    }
}
