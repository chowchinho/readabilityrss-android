package co.chinho.readabilityreader.data.local.mapper

import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import co.chinho.readabilityreader.data.local.model.ArticleWithFeedTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleMapperFocalTest {

    private fun entity() = ArticleEntity(
        id = 1L,
        feedId = 10L,
        title = "T",
        url = "https://e.test/a",
        content = "<p>x</p>",
        publishedAt = 0L,
        isRead = false,
        isSaved = false,
        thumbnailUrl = "https://e.test/cover.jpg",
        cachedAt = 0L,
        contentCachedAt = null,
        imagesCachedAt = null,
    )

    @Test
    fun `stored cover focal is carried into the domain model`() {
        val row = ArticleWithFeedTitle(
            article = entity(),
            feedTitle = "Feed",
            coverFocalX = 30,
            coverFocalY = 70,
        )

        val article = row.toDomain()

        assertEquals(30, article.focalX)
        assertEquals(70, article.focalY)
    }

    @Test
    fun `an unanalysed cover falls back to centre`() {
        val row = ArticleWithFeedTitle(
            article = entity(),
            feedTitle = "Feed",
            coverFocalX = null,
            coverFocalY = null,
        )

        val article = row.toDomain()

        assertEquals(50, article.focalX)
        assertEquals(50, article.focalY)
    }
}
