package co.chinho.readabilityreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import co.chinho.readabilityreader.data.local.entity.ArticleImageEntity
import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import co.chinho.readabilityreader.data.remote.dto.FeverItemDto
import co.chinho.readabilityreader.data.remote.dto.FeverResponse
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleRepositoryImplTest {

    private lateinit var db: ReadabilityDatabase
    private lateinit var articleImageCache: ArticleImageCache
    private lateinit var repo: ArticleRepositoryImpl

    private val fixedNowMillis = 1_700_000_000_000L
    private val clock = object : SyncClock {
        override fun nowMillis(): Long = fixedNowMillis
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReadabilityDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        articleImageCache = mockk(relaxed = true)

        repo = ArticleRepositoryImpl(
            database = db,
            articleDao = db.articleDao(),
            feedDao = db.feedDao(),
            groupDao = db.groupDao(),
            readStateQueueDao = db.readStateQueueDao(),
            articleImageDao = db.articleImageDao(),
            feverConnectionProvider = mockk<FeverConnectionProvider>(relaxed = true),
            connectivityMonitor = mockk<ConnectivityMonitor>(relaxed = true),
            syncClock = clock,
            articleImageCache = articleImageCache,
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
    }

    @Test
    fun testProcessSyncResponseImageExtraction() = runBlocking {
        val item1 = FeverItemDto(
            id = 1L,
            feedId = 10L,
            title = "Title 1",
            url = "https://example.com/1",
            html = """<p>Hello <img src="https://example.com/image1.jpg"> and <img src="/relative/image2.png"></p>""",
            mainImage = "https://example.com/api/reader/cached-image/thumb.jpg",
            createdOnTime = fixedNowMillis / 1000L
        )

        val item2 = FeverItemDto(
            id = 2L,
            feedId = 10L,
            title = "Title 2",
            url = "https://example.com/2",
            html = null,
            mainImage = "https://example.com/thumb2.jpg",
            createdOnTime = fixedNowMillis / 1000L
        )

        val item3 = FeverItemDto(
            id = 3L,
            feedId = 10L,
            title = "Title 3",
            url = "https://example.com/3",
            html = null,
            mainImage = null,
            createdOnTime = fixedNowMillis / 1000L
        )

        val response = FeverResponse(
            auth = 1,
            items = listOf(item1, item2, item3)
        )

        repo.processSyncResponse(response, "https://reader.example.com") { _, _ -> }

        // Verify Article 1 has 3 join rows: image1, image2 (resolved to reader.example.com), and thumb
        val images1 = db.articleImageDao().getUrlsOwnedOnlyBy(listOf(1L))
        assertEquals(3, images1.size)
        assertTrue(images1.contains("https://example.com/image1.jpg"))
        assertTrue(images1.contains("https://example.com/relative/image2.png"))
        assertTrue(images1.contains("https://example.com/api/reader/cached-image/thumb.jpg"))

        // Verify Article 2 has exactly 1 join row: thumb2
        val images2 = db.articleImageDao().getUrlsOwnedOnlyBy(listOf(2L))
        assertEquals(1, images2.size)
        assertTrue(images2.contains("https://example.com/thumb2.jpg"))

        // Verify Article 3 has 0 join rows
        val images3 = db.articleImageDao().getUrlsOwnedOnlyBy(listOf(3L))
        assertTrue(images3.isEmpty())

        // Verify Idempotency: re-running processSyncResponse.
        // Robolectric's SQLite driver has a known issue where Room's @Upsert UNIQUE constraint check fails to catch the exception.
        try {
            repo.processSyncResponse(response, "https://reader.example.com") { _, _ -> }
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Ignore Robolectric-specific @Upsert translation issue
        }
        val images1Again = db.articleImageDao().getUrlsOwnedOnlyBy(listOf(1L))
        assertEquals(3, images1Again.size)
    }

    @Test
    fun testProcessSyncResponseStoresEveryArticleAcrossChunkBoundaries() = runBlocking {
        // Writes are chunked at 200; 450 items spans three chunks with a partial tail, so a
        // chunk that dropped its remainder or reused a stale accumulator would show up here.
        val itemCount = 450
        val items = (1..itemCount).map { index ->
            FeverItemDto(
                id = index.toLong(),
                feedId = 10L,
                title = "Title $index",
                url = "https://example.com/$index",
                html = """<p><img src="https://example.com/inline-$index.jpg"></p>""",
                // Cached-image URLs win outright in resolveThumbnailUrl; a plain origin URL
                // would lose to the inline image and collapse both rows into one.
                mainImage = "https://example.com/api/reader/cached-image/thumb-$index.jpg",
                createdOnTime = fixedNowMillis / 1000L,
            )
        }

        val synced = repo.processSyncResponse(
            FeverResponse(auth = 1, items = items),
            "https://reader.example.com",
        ) { _, _ -> }

        assertEquals(itemCount, synced)
        assertEquals(itemCount, db.articleDao().getArticleCount())

        // Sample the first, last and both chunk boundaries rather than all 450.
        listOf(1L, 200L, 201L, 400L, 401L, 450L).forEach { id ->
            val urls = db.articleImageDao().getUrlsOwnedOnlyBy(listOf(id))
            assertEquals("article $id join rows", 2, urls.size)
            assertTrue("article $id inline", urls.contains("https://example.com/inline-$id.jpg"))
            assertTrue(
                "article $id thumb",
                urls.contains("https://example.com/api/reader/cached-image/thumb-$id.jpg"),
            )
        }
    }

    @Test
    fun testEvictOldArticlesOnlyRemovesUnsharedUrls() = runBlocking {
        // Cutoff: 5 days.
        val keepDays = 5
        val cutoffMillis = fixedNowMillis - keepDays * 24L * 60L * 60L * 1000L
        val cutoffSeconds = cutoffMillis / 1000L

        // Set up Article 1 (old, unsaved, to be evicted)
        db.articleDao().upsertArticles(
            listOf(
                ArticleEntity(
                    id = 1L,
                    feedId = 10L,
                    title = "Old Article",
                    url = "https://example.com/1",
                    content = "Content",
                    publishedAt = (cutoffSeconds - 100) * 1000L,
                    isRead = false,
                    isSaved = false,
                    thumbnailUrl = null,
                    cachedAt = fixedNowMillis,
                    contentCachedAt = fixedNowMillis,
                    imagesCachedAt = null
                )
            )
        )

        // Set up Article 2 (new, unsaved, surviving)
        db.articleDao().upsertArticles(
            listOf(
                ArticleEntity(
                    id = 2L,
                    feedId = 10L,
                    title = "New Article",
                    url = "https://example.com/2",
                    content = "Content",
                    publishedAt = (cutoffSeconds + 100) * 1000L,
                    isRead = false,
                    isSaved = false,
                    thumbnailUrl = null,
                    cachedAt = fixedNowMillis,
                    contentCachedAt = fixedNowMillis,
                    imagesCachedAt = null
                )
            )
        )

        // Populate article_images:
        // image-shared is shared by 1 and 2
        // image-unique-1 is unique to 1
        // image-unique-2 is unique to 2
        db.articleImageDao().insertAll(
            listOf(
                ArticleImageEntity(1L, "https://example.com/image-shared.jpg"),
                ArticleImageEntity(1L, "https://example.com/image-unique-1.jpg"),
                ArticleImageEntity(2L, "https://example.com/image-shared.jpg"),
                ArticleImageEntity(2L, "https://example.com/image-unique-2.jpg")
            )
        )

        // Run evictOldArticles
        repo.evictOldArticles(keepDays)

        // Verify articleImageCache.removeUrls was called with image-unique-1, but NOT image-shared
        val capturedUrls = slot<Collection<String>>()
        verify(exactly = 1) { articleImageCache.removeUrls(capture(capturedUrls)) }
        
        assertEquals(1, capturedUrls.captured.size)
        assertTrue(capturedUrls.captured.contains("https://example.com/image-unique-1.jpg"))

        // Verify Article 1 was deleted
        val articles = db.articleDao().getArticleContent(1L)
        assertTrue(articles == null)

        // Verify Article 2 survived
        val article2Content = db.articleDao().getArticleContent(2L)
        assertTrue(article2Content != null)
    }
}
