package co.chinho.readabilityreader.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleImageJoinRowsTest {

    @Test
    fun `body images keep document order`() {
        val content = """
            <p><img src="https://e.test/one.jpg"></p>
            <p><img src="https://e.test/two.jpg"></p>
            <p><img src="https://e.test/three.jpg"></p>
        """.trimIndent()

        val rows = buildArticleImageRows(articleId = 7L, content = content, thumbnailUrl = null)

        assertEquals(listOf(0, 1, 2), rows.map { it.position })
        assertEquals("https://e.test/one.jpg", rows[0].imageUrl)
        assertEquals("https://e.test/three.jpg", rows[2].imageUrl)
    }

    @Test
    fun `thumbnail not present in body is appended last`() {
        val content = """<p><img src="https://e.test/one.jpg"></p>"""

        val rows = buildArticleImageRows(
            articleId = 7L,
            content = content,
            thumbnailUrl = "https://e.test/cover.jpg",
        )

        assertEquals(2, rows.size)
        assertEquals("https://e.test/cover.jpg", rows.last().imageUrl)
        assertEquals(1, rows.last().position)
    }

    @Test
    fun `thumbnail already in body keeps its body position and is not duplicated`() {
        val content = """
            <p><img src="https://e.test/one.jpg"></p>
            <p><img src="https://e.test/cover.jpg"></p>
        """.trimIndent()

        val rows = buildArticleImageRows(
            articleId = 7L,
            content = content,
            thumbnailUrl = "https://e.test/cover.jpg",
        )

        assertEquals(2, rows.size)
        assertEquals(1, rows.first { it.imageUrl == "https://e.test/cover.jpg" }.position)
    }

    @Test
    fun `null content with no thumbnail yields no rows`() {
        assertEquals(emptyList<Any>(), buildArticleImageRows(7L, null, null))
    }

    @Test
    fun `all rows default to uncomputed centre focal`() {
        val rows = buildArticleImageRows(7L, """<img src="https://e.test/a.jpg">""", null)

        assertEquals(50, rows[0].focalX)
        assertEquals(50, rows[0].focalY)
        assertEquals(false, rows[0].focalComputed)
    }
}
