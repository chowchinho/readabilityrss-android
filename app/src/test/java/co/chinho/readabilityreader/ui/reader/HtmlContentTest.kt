package co.chinho.readabilityreader.ui.reader

import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlContentTest {

    @Test
    fun `sanitized images are separated from following paragraph text`() {
        val result = sanitizeHtml(
            html = """<p><img src="/hero.jpg"></p><p>Article text follows the image.</p>""",
            articleUrl = "https://example.com/articles/1",
            readerBaseUrl = "https://reader.example.com",
        )

        assertTrue(result.contains("""<br><img src="https://example.com/hero.jpg"><br><p>Article text"""))
    }
}
