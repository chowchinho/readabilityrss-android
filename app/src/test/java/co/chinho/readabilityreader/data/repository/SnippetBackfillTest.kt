package co.chinho.readabilityreader.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.dao.ArticleDao
import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import co.chinho.readabilityreader.data.remote.dto.FeverItemDto
import co.chinho.readabilityreader.data.remote.dto.FeverResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnippetBackfillTest {

    private lateinit var db: ReadabilityDatabase
    private lateinit var spiedArticleDao: ArticleDao
    private lateinit var repo: ArticleRepositoryImpl

    private val fixedNowMillis = 1_700_000_000_000L
    private val clock = object : SyncClock {
        override fun nowMillis(): Long = fixedNowMillis
    }

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReadabilityDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        spiedArticleDao = spyk(db.articleDao())

        repo = ArticleRepositoryImpl(
            database = db,
            articleDao = spiedArticleDao,
            feedDao = db.feedDao(),
            groupDao = db.groupDao(),
            readStateQueueDao = db.readStateQueueDao(),
            articleImageDao = db.articleImageDao(),
            feverConnectionProvider = mockk(relaxed = true),
            connectivityMonitor = mockk(relaxed = true),
            syncClock = clock,
            articleImageCache = mockk(relaxed = true),
            hostReachabilityTracker = mockk(relaxed = true),
            labelWeightDao = mockk(relaxed = true),
            voteQueueDao = mockk(relaxed = true),
            eventQueueDao = mockk(relaxed = true),
            userPreferencesRepository = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        db.close()
        unmockkStatic(Log::class)
    }

    private fun insertTestArticle(id: Long, content: String?, snippetText: String? = null) {
        runBlocking {
            db.articleDao().upsertArticles(
                listOf(
                    ArticleEntity(
                        id = id,
                        feedId = 1L,
                        title = "Article $id",
                        url = "https://example.com/$id",
                        content = content,
                        publishedAt = fixedNowMillis / 1000L,
                        isRead = false,
                        isSaved = false,
                        thumbnailUrl = null,
                        cachedAt = fixedNowMillis,
                        contentCachedAt = fixedNowMillis,
                        imagesCachedAt = null,
                        snippetText = snippetText,
                    )
                )
            )
        }
    }

    @Test
    fun testBackfillProcesses500NullRowsInBatchesPerId() = runBlocking {
        for (i in 1L..500L) {
            insertTestArticle(
                id = i,
                content = "<p>Article content number $i with plenty of visible text.</p>",
                snippetText = null,
            )
        }

        assertEquals(500, spiedArticleDao.getIdsNeedingSnippet(1000).size)

        val updatedCount = repo.backfillSnippets()
        assertEquals(500, updatedCount)

        // Verify per-id accessor was called for each row
        coVerify(exactly = 500) { spiedArticleDao.getArticleContent(any()) }

        // All rows should now have snippetText populated
        val remainingNeedingSnippet = spiedArticleDao.getIdsNeedingSnippet(1000)
        assertEquals(0, remainingNeedingSnippet.size)

        // Check a sample article
        val article = spiedArticleDao.observeArticle(42L).first()
        assertNotNull(article)
        assertEquals("Article content number 42 with plenty of visible text.", article!!.article.snippetText)
    }

    @Test
    fun testBackfillHandlesExceptionsGracefullyAndContinuesBatch() = runBlocking {
        insertTestArticle(1L, "<p>Valid article 1</p>", snippetText = null)
        insertTestArticle(2L, "<p>Throws on read</p>", snippetText = null)
        insertTestArticle(3L, "<p>Valid article 3</p>", snippetText = null)

        coEvery { spiedArticleDao.getArticleContent(2L) } throws IllegalStateException("Simulated cursor window error")

        val updatedCount = repo.backfillSnippets()
        assertEquals(2, updatedCount)

        // Articles 1 and 3 were updated
        val a1 = spiedArticleDao.observeArticle(1L).first()!!.article
        val a3 = spiedArticleDao.observeArticle(3L).first()!!.article
        assertEquals("Valid article 1", a1.snippetText)
        assertEquals("Valid article 3", a3.snippetText)

        // Article 2 remained NULL since reading threw
        val a2 = spiedArticleDao.observeArticle(2L).first()!!.article
        assertEquals(null, a2.snippetText)
    }

    @Test
    fun testGenuinelyEmptyArticleWrittenAsEmptyStringAndNotReturnedByGetIdsNeedingSnippet() = runBlocking {
        insertTestArticle(1L, "<p></p><div></div>", snippetText = null)
        insertTestArticle(2L, null, snippetText = null)

        val updatedCount = repo.backfillSnippets()
        assertEquals(2, updatedCount)

        val a1 = spiedArticleDao.observeArticle(1L).first()!!.article
        val a2 = spiedArticleDao.observeArticle(2L).first()!!.article
        assertEquals("", a1.snippetText)
        assertEquals("", a2.snippetText)

        // Neither is returned by getIdsNeedingSnippet
        val remaining = spiedArticleDao.getIdsNeedingSnippet(10)
        assertEquals(0, remaining.size)
    }

    private fun insertTestArticles(count: Int) {
        runBlocking {
            db.articleDao().upsertArticles(
                (1L..count.toLong()).map { id ->
                    ArticleEntity(
                        id = id,
                        feedId = 1L,
                        title = "Article $id",
                        url = "https://example.com/$id",
                        content = "<p>Body $id</p>",
                        publishedAt = fixedNowMillis / 1000L,
                        isRead = false,
                        isSaved = false,
                        thumbnailUrl = null,
                        cachedAt = fixedNowMillis,
                        contentCachedAt = fixedNowMillis,
                        imagesCachedAt = null,
                        snippetText = null,
                    )
                }
            )
        }
    }

    @Test
    fun testBackfillStopsAtTheBudget() = runBlocking {
        val overflow = 200
        insertTestArticles(SNIPPET_BACKFILL_BUDGET + overflow)

        val updatedCount = repo.backfillSnippets()
        assertEquals(SNIPPET_BACKFILL_BUDGET, updatedCount)

        val remaining = spiedArticleDao.getIdsNeedingSnippet(SNIPPET_BACKFILL_BUDGET * 2)
        assertEquals(overflow, remaining.size)
    }

    @Test
    fun testBackfillDrainsAFullDeviceSizedCacheInOnePass() = runBlocking {
        val deviceSizedCache = 3_566
        insertTestArticles(deviceSizedCache)

        assertEquals(deviceSizedCache, repo.backfillSnippets())
        assertEquals(0, spiedArticleDao.getIdsNeedingSnippet(deviceSizedCache).size)
    }

    @Test
    fun testSyncPopulatesSnippetTextForNewArticles() = runBlocking {
        val item = FeverItemDto(
            id = 100L,
            feedId = 1L,
            title = "Synced Title",
            url = "https://example.com/sync",
            html = "<p>🌐 Translated by DeepL</p><p>This is newly synced text.</p>",
            createdOnTime = fixedNowMillis / 1000L,
        )

        val response = FeverResponse(
            auth = 1,
            items = listOf(item),
        )

        repo.processSyncResponse(response, "https://reader.example.com") { _, _ -> }

        val article = spiedArticleDao.observeArticle(100L).first()!!.article
        assertEquals("This is newly synced text.", article.snippetText)
    }
}
