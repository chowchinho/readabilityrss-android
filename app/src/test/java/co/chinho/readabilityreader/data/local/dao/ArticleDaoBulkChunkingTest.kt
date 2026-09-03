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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleDaoBulkChunkingTest {

    private lateinit var db: ReadabilityDatabase
    private lateinit var dao: ArticleDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReadabilityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.articleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testTwoThousandFiveHundredIdsChunkedIntoBatchesOfNineHundredMatchesExpectedDbState() = runBlocking {
        val totalArticles = 3000
        val unreadCount = 2500
        val articles = (1..totalArticles).map { i ->
            createArticle(id = i.toLong(), isRead = true, isSaved = false)
        }
        dao.upsertArticles(articles)

        val unreadIds = (1L..unreadCount.toLong()).toList()
        val chunks = unreadIds.chunked(900)
        assertEquals(3, chunks.size) // 900 + 900 + 700 = 2500

        dao.markAllAsRead()
        for (chunk in chunks) {
            dao.markAllInAsUnread(chunk)
        }

        // Articles 1..2500 are unread (isRead = false)
        for (i in 1..unreadCount) {
            val article = dao.observeArticle(i.toLong()).first()
            assertFalse("Article $i should be unread", article!!.article.isRead)
        }

        // Articles 2501..3000 are read (isRead = true)
        for (i in (unreadCount + 1)..totalArticles) {
            val article = dao.observeArticle(i.toLong()).first()
            assertTrue("Article $i should be read", article!!.article.isRead)
        }
    }

    @Test
    fun testEmptyIdSetDoesNotIssueInQueryAndLeavesAllMarkedReadOrUnsaved() = runBlocking {
        val articles = (1..10).map { i ->
            createArticle(id = i.toLong(), isRead = false, isSaved = true)
        }
        dao.upsertArticles(articles)

        // Empty unread IDs -> all marked read
        dao.markAllAsRead()
        for (i in 1..10) {
            assertTrue(dao.observeArticle(i.toLong()).first()!!.article.isRead)
        }

        // Empty saved IDs -> all marked unsaved
        dao.markAllAsUnsaved()
        for (i in 1..10) {
            assertFalse(dao.observeArticle(i.toLong()).first()!!.article.isSaved)
        }
    }

    @Test
    fun testNotInReplacementProducesIdenticalResultToOriginalSingleStatement() = runBlocking {
        val articles = listOf(
            createArticle(id = 1L, isRead = false, isSaved = false),
            createArticle(id = 2L, isRead = true, isSaved = true),
            createArticle(id = 3L, isRead = false, isSaved = false),
            createArticle(id = 4L, isRead = true, isSaved = false),
            createArticle(id = 5L, isRead = false, isSaved = true),
        )
        dao.upsertArticles(articles)

        // Target unread: {2, 3} -> should become unread, rest {1, 4, 5} read
        val unreadIds = listOf(2L, 3L)
        dao.markAllAsRead()
        for (chunk in unreadIds.chunked(900)) {
            dao.markAllInAsUnread(chunk)
        }

        // Target saved: {4, 5} -> should become saved, rest {1, 2, 3} unsaved
        val savedIds = listOf(4L, 5L)
        dao.markAllAsUnsaved()
        for (chunk in savedIds.chunked(900)) {
            dao.markAllInAsSaved(chunk)
        }

        val a1 = dao.observeArticle(1L).first()!!.article
        assertTrue(a1.isRead)
        assertFalse(a1.isSaved)

        val a2 = dao.observeArticle(2L).first()!!.article
        assertFalse(a2.isRead)
        assertFalse(a2.isSaved)

        val a3 = dao.observeArticle(3L).first()!!.article
        assertFalse(a3.isRead)
        assertFalse(a3.isSaved)

        val a4 = dao.observeArticle(4L).first()!!.article
        assertTrue(a4.isRead)
        assertTrue(a4.isSaved)

        val a5 = dao.observeArticle(5L).first()!!.article
        assertTrue(a5.isRead)
        assertTrue(a5.isSaved)
    }

    private fun createArticle(
        id: Long,
        isRead: Boolean,
        isSaved: Boolean,
    ): ArticleEntity {
        return ArticleEntity(
            id = id,
            feedId = 1L,
            title = "Title $id",
            url = "https://example.com/$id",
            content = "Content $id",
            publishedAt = 1_000_000L + id,
            isRead = isRead,
            isSaved = isSaved,
            thumbnailUrl = null,
            cachedAt = 1_700_000_000_000L,
            contentCachedAt = null,
            imagesCachedAt = null,
        )
    }
}
