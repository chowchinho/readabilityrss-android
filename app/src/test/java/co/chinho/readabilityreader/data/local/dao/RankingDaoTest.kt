package co.chinho.readabilityreader.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RankingDaoTest {

    private lateinit var db: ReadabilityDatabase
    private lateinit var articleDao: ArticleDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReadabilityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        articleDao = db.articleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun insertArticle(
        id: Long,
        publishedAt: Long,
        isSaved: Boolean = false,
        score: Double? = null,
    ) = runBlocking {
        articleDao.upsertArticles(
            listOf(
                ArticleEntity(
                    id = id,
                    feedId = 1L,
                    title = "Article $id",
                    url = "https://example.com/$id",
                    content = "Content $id",
                    publishedAt = publishedAt,
                    isRead = false,
                    isSaved = isSaved,
                    thumbnailUrl = null,
                    cachedAt = System.currentTimeMillis(),
                    contentCachedAt = System.currentTimeMillis(),
                    imagesCachedAt = null,
                    score = score,
                )
            )
        )
    }

    @Test
    fun testMinNonSavedArticleIdDoesNotIncludeSaved() = runBlocking {
        insertArticle(id = 50L, publishedAt = 1000L, isSaved = true)
        insertArticle(id = 100L, publishedAt = 2000L, isSaved = false)
        insertArticle(id = 200L, publishedAt = 3000L, isSaved = false)

        val minAll = articleDao.getMinArticleId()
        val minNonSaved = articleDao.getMinNonSavedArticleId()

        assertEquals(50L, minAll)
        assertEquals(100L, minNonSaved)
    }

    @Test
    fun testOrderingPersonalisedVsLatest() = runBlocking {
        // Article 1: score = 9.0, publishedAt = 100
        insertArticle(id = 1L, publishedAt = 100L, score = 9.0)
        // Article 2: score = 2.0, publishedAt = 300
        insertArticle(id = 2L, publishedAt = 300L, score = 2.0)
        // Article 3: score = null, publishedAt = 500
        insertArticle(id = 3L, publishedAt = 500L, score = null)
        // Article 4: score = null, publishedAt = 400
        insertArticle(id = 4L, publishedAt = 400L, score = null)

        // 1. Query with sortPersonalised = true
        val personalised = articleDao.observeArticles(feedId = null, showRead = true, sortPersonalised = true, stickyIds = emptyList()).first()
        val personalisedIds = personalised.map { it.article.id }
        // Expected: 1 (score 9.0), 2 (score 2.0), 3 (publishedAt 500), 4 (publishedAt 400)
        assertEquals(listOf(1L, 2L, 3L, 4L), personalisedIds)

        // 2. Query with sortPersonalised = false
        val latest = articleDao.observeArticles(feedId = null, showRead = true, sortPersonalised = false, stickyIds = emptyList()).first()
        val latestIds = latest.map { it.article.id }
        // Expected: 3 (500), 4 (400), 2 (300), 1 (100)
        assertEquals(listOf(3L, 4L, 2L, 1L), latestIds)
    }
}
