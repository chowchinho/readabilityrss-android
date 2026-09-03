package co.chinho.readabilityreader.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import co.chinho.readabilityreader.data.local.entity.ArticleImageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleImageDaoTest {
    private lateinit var db: ReadabilityDatabase
    private lateinit var articleDao: ArticleDao
    private lateinit var articleImageDao: ArticleImageDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReadabilityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        articleDao = db.articleDao()
        articleImageDao = db.articleImageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun insertArticle(
        id: Long,
        isSaved: Boolean,
        publishedAt: Long,
        thumbnailUrl: String? = null,
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
                    thumbnailUrl = thumbnailUrl,
                    cachedAt = System.currentTimeMillis(),
                    contentCachedAt = System.currentTimeMillis(),
                    imagesCachedAt = null
                )
            )
        )
    }

    @Test
    fun testInsertAllAndIgnoreDuplicates() = runBlocking {
        insertArticle(1L, false, 1000L)
        val rows = listOf(
            ArticleImageEntity(1L, "https://example.com/img1.jpg"),
            ArticleImageEntity(1L, "https://example.com/img1.jpg"), // duplicate
            ArticleImageEntity(1L, "https://example.com/img2.jpg")
        )
        articleImageDao.insertAll(rows)

        // Since getUrlsOwnedOnlyBy with the article id will return all owned by that id
        val urls = articleImageDao.getUrlsOwnedOnlyBy(listOf(1L))
        assertEquals(2, urls.size)
        assertTrue(urls.contains("https://example.com/img1.jpg"))
        assertTrue(urls.contains("https://example.com/img2.jpg"))
    }

    @Test
    fun testCascadeOnDelete() = runBlocking {
        insertArticle(1L, false, 1000L)
        insertArticle(2L, false, 1000L)

        articleImageDao.insertAll(
            listOf(
                ArticleImageEntity(1L, "https://example.com/img1.jpg"),
                ArticleImageEntity(2L, "https://example.com/img2.jpg")
            )
        )

        // Delete article 1
        articleDao.deleteArticlesByIds(listOf(1L))

        val urlsFor1 = articleImageDao.getUrlsOwnedOnlyBy(listOf(1L))
        assertTrue(urlsFor1.isEmpty())

        val urlsFor2 = articleImageDao.getUrlsOwnedOnlyBy(listOf(2L))
        assertEquals(1, urlsFor2.size)
        assertEquals("https://example.com/img2.jpg", urlsFor2.first())
    }

    @Test
    fun testGetUrlsOwnedOnlyBy() = runBlocking {
        // Canonical example: 4 articles.
        // Photo A shared by #1, #2, #4.
        // Photo B owned by #1.
        // Photo C owned by #2.
        // Photo D and E owned by #2 (so #2 has A, C, D, E).
        // Photo F owned by #3.
        insertArticle(1L, false, 1000L)
        insertArticle(2L, false, 1000L)
        insertArticle(3L, false, 1000L)
        insertArticle(4L, false, 1000L)

        articleImageDao.insertAll(
            listOf(
                ArticleImageEntity(1L, "photo-A"),
                ArticleImageEntity(1L, "photo-B"),

                ArticleImageEntity(2L, "photo-A"),
                ArticleImageEntity(2L, "photo-C"),
                ArticleImageEntity(2L, "photo-D"),
                ArticleImageEntity(2L, "photo-E"),

                ArticleImageEntity(3L, "photo-F"),

                ArticleImageEntity(4L, "photo-A")
            )
        )

        // getUrlsOwnedOnlyBy(listOf(1L, 2L))
        // Outside articles: 3, 4. Outside articles reference: photo-A (from 4), photo-F (from 3).
        // So photo-A is excluded.
        // Surviving articles 1 and 2 reference: photo-A, photo-B, photo-C, photo-D, photo-E.
        // Excluded: photo-A.
        // Result should be: photo-B, photo-C, photo-D, photo-E.
        val urls = articleImageDao.getUrlsOwnedOnlyBy(listOf(1L, 2L))
        assertEquals(4, urls.size)
        assertTrue(urls.contains("photo-B"))
        assertTrue(urls.contains("photo-C"))
        assertTrue(urls.contains("photo-D"))
        assertTrue(urls.contains("photo-E"))
    }

    @Test
    fun testGetWarmableUrls() = runBlocking {
        // Cutoff: 5 days. We use milliseconds.
        val now = 1_700_000_000_000L
        val cutoffMillis = now - 5L * 24 * 60 * 60 * 1000
        val cutoffSeconds = cutoffMillis / 1000L

        // Article 1: saved, published long ago (outside cutoff)
        insertArticle(1L, isSaved = true, publishedAt = (cutoffSeconds - 1000) * 1000L)
        // Article 2: unsaved, published within cutoff (in millis)
        insertArticle(2L, isSaved = false, publishedAt = (cutoffSeconds + 100) * 1000L)
        // Article 3: unsaved, published outside cutoff (in millis)
        insertArticle(3L, isSaved = false, publishedAt = (cutoffSeconds - 100) * 1000L)

        articleImageDao.insertAll(
            listOf(
                ArticleImageEntity(1L, "url-saved-old"),
                ArticleImageEntity(2L, "url-unsaved-new"),
                ArticleImageEntity(3L, "url-unsaved-old")
            )
        )

        val warmable = articleImageDao.getWarmableUrls(cutoffMillis, cutoffSeconds, limit = 1000, offset = 0)
        assertEquals(2, warmable.size)
        assertTrue(warmable.contains("url-saved-old"))
        assertTrue(warmable.contains("url-unsaved-new"))
    }

    @Test
    fun testClearAll() = runBlocking {
        insertArticle(1L, false, 1000L)
        articleImageDao.insertAll(listOf(ArticleImageEntity(1L, "url1")))

        articleImageDao.clearAll()
        val urls = articleImageDao.getUrlsOwnedOnlyBy(listOf(1L))
        assertTrue(urls.isEmpty())
    }

    @Test
    fun testWarmableUrlsPutCoversFirstNewestFirst() = runBlocking {
        val now = System.currentTimeMillis()
        val cutoffMillis = now - 5L * 24L * 60L * 60L * 1000L
        val cutoffSeconds = cutoffMillis / 1000L

        insertArticle(1L, false, now - 60_000L, thumbnailUrl = "cover-older")
        insertArticle(2L, false, now, thumbnailUrl = "cover-newest")

        articleImageDao.insertAll(
            listOf(
                ArticleImageEntity(1L, "cover-older"),
                ArticleImageEntity(1L, "body-a"),
                ArticleImageEntity(2L, "cover-newest"),
                ArticleImageEntity(2L, "body-b"),
            )
        )

        val warmable = articleImageDao.getWarmableUrls(cutoffMillis, cutoffSeconds, limit = 1000, offset = 0)

        assertEquals(4, warmable.size)
        assertEquals(listOf("cover-newest", "cover-older"), warmable.take(2))
    }

    @Test
    fun testWarmableUrlsOrderSecondsAndMillisByRealDate() = runBlocking {
        val now = System.currentTimeMillis()
        val cutoffMillis = now - 5L * 24L * 60L * 60L * 1000L
        val cutoffSeconds = cutoffMillis / 1000L

        // Stored in seconds, published now — a far smaller raw number than the millis row below.
        insertArticle(1L, false, now / 1000L, thumbnailUrl = "cover-seconds-now")
        // Stored in millis, published two days ago.
        insertArticle(2L, false, now - 2L * 24L * 60L * 60L * 1000L, thumbnailUrl = "cover-millis-old")

        articleImageDao.insertAll(
            listOf(
                ArticleImageEntity(1L, "cover-seconds-now"),
                ArticleImageEntity(2L, "cover-millis-old"),
            )
        )

        val warmable = articleImageDao.getWarmableUrls(cutoffMillis, cutoffSeconds, limit = 1000, offset = 0)

        assertEquals(listOf("cover-seconds-now", "cover-millis-old"), warmable)
    }

    @Test
    fun testWarmableUrlsTreatsAnyArticlesCoverAsACover() = runBlocking {
        val now = System.currentTimeMillis()
        val cutoffMillis = now - 5L * 24L * 60L * 60L * 1000L
        val cutoffSeconds = cutoffMillis / 1000L

        // Shared image: a body image for the newer article, the cover of the older one.
        insertArticle(1L, false, now, thumbnailUrl = "plain-cover")
        insertArticle(2L, false, now - 60_000L, thumbnailUrl = "shared")

        articleImageDao.insertAll(
            listOf(
                ArticleImageEntity(1L, "plain-cover"),
                ArticleImageEntity(1L, "shared"),
                ArticleImageEntity(2L, "shared"),
            )
        )

        val warmable = articleImageDao.getWarmableUrls(cutoffMillis, cutoffSeconds, limit = 1000, offset = 0)

        assertEquals(2, warmable.size)
        assertTrue(warmable.contains("shared"))
    }

    @Test
    fun testGetUrlsNeedingFocalReturnsOnlyUncomputedDistinctUrls() = runBlocking {
        insertArticle(1L, false, 1000L)
        insertArticle(2L, false, 2000L)

        articleImageDao.insertAll(
            listOf(
                ArticleImageEntity(1L, "url-uncomputed-1", focalComputed = false),
                ArticleImageEntity(1L, "url-computed", focalComputed = true, focalX = 30, focalY = 40),
                ArticleImageEntity(2L, "url-uncomputed-1", focalComputed = false), // same url, distinct check
                ArticleImageEntity(2L, "url-uncomputed-2", focalComputed = false),
            )
        )

        val uncomputed = articleImageDao.getUrlsNeedingFocal(limit = 1000, offset = 0)
        assertEquals(2, uncomputed.size)
        assertTrue(uncomputed.contains("url-uncomputed-1"))
        assertTrue(uncomputed.contains("url-uncomputed-2"))
    }

    @Test
    fun testSetFocalPointsBatchUpdatesRowsCorrectly() = runBlocking {
        insertArticle(1L, false, 1000L)
        insertArticle(2L, false, 2000L)

        articleImageDao.insertAll(
            listOf(
                ArticleImageEntity(1L, "url-a", focalX = 50, focalY = 50, focalComputed = false),
                ArticleImageEntity(1L, "url-b", focalX = 50, focalY = 50, focalComputed = false),
                ArticleImageEntity(2L, "url-a", focalX = 50, focalY = 50, focalComputed = false),
                ArticleImageEntity(2L, "url-c", focalX = 50, focalY = 50, focalComputed = false),
            )
        )

        val updates = listOf(
            co.chinho.readabilityreader.data.local.model.FocalPointUpdate("url-a", focalX = 25, focalY = 75),
            co.chinho.readabilityreader.data.local.model.FocalPointUpdate("url-b", focalX = 80, focalY = 20),
        )

        articleImageDao.setFocalPoints(updates)

        val images1 = articleImageDao.getOrderedImagesForArticle(1L)
        val image1A = images1.first { it.imageUrl == "url-a" }
        val image1B = images1.first { it.imageUrl == "url-b" }
        assertEquals(25, image1A.focalX)
        assertEquals(75, image1A.focalY)
        assertEquals(80, image1B.focalX)
        assertEquals(20, image1B.focalY)

        val images2 = articleImageDao.getOrderedImagesForArticle(2L)
        val image2A = images2.first { it.imageUrl == "url-a" }
        val image2C = images2.first { it.imageUrl == "url-c" }
        assertEquals(25, image2A.focalX)
        assertEquals(75, image2A.focalY)
        assertEquals(50, image2C.focalX) // Unchanged
        assertEquals(50, image2C.focalY) // Unchanged

        val remainingUncomputed = articleImageDao.getUrlsNeedingFocal(limit = 1000, offset = 0)
        assertEquals(listOf("url-c"), remainingUncomputed)
    }

    @Test
    fun testWarmableUrlsPaginationAndTiebreaker() = runBlocking {
        val now = System.currentTimeMillis()
        val cutoffMillis = now - 5L * 24L * 60L * 60L * 1000L
        val cutoffSeconds = cutoffMillis / 1000L

        insertArticle(1L, false, now)
        articleImageDao.insertAll(
            listOf(
                ArticleImageEntity(1L, "img-c"),
                ArticleImageEntity(1L, "img-a"),
                ArticleImageEntity(1L, "img-b"),
            )
        )

        val page1 = articleImageDao.getWarmableUrls(cutoffMillis, cutoffSeconds, limit = 2, offset = 0)
        val page2 = articleImageDao.getWarmableUrls(cutoffMillis, cutoffSeconds, limit = 2, offset = 2)

        assertEquals(listOf("img-a", "img-b"), page1)
        assertEquals(listOf("img-c"), page2)
    }
}
