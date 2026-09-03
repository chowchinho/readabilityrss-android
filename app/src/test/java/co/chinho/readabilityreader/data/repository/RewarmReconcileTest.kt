package co.chinho.readabilityreader.data.repository

import android.util.Log
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.dao.ArticleDao
import co.chinho.readabilityreader.data.local.dao.FeedDao
import co.chinho.readabilityreader.data.local.dao.GroupDao
import co.chinho.readabilityreader.data.local.dao.ReadStateQueueDao
import co.chinho.readabilityreader.data.local.model.ArticleImageWarmRow
import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// Guards the recurring "offline images go blank" bug: warming must reconcile against Coil's
// disk (ArticleImageCache.isCached), NOT the imagesCachedAt flag. A thumbnail flagged cached
// but absent from disk MUST be re-fetched; one present on disk MUST be skipped.
@OptIn(ExperimentalCoroutinesApi::class)
class RewarmReconcileTest {

    private val flaggedButMissing = "https://rrssb.test/api/reader/cached-image/missing.jpg"
    private val flaggedAndOnDisk = "https://rrssb.test/api/reader/cached-image/present.jpg"

    private lateinit var articleDao: ArticleDao
    private lateinit var articleImageCache: ArticleImageCache
    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var hostReachabilityTracker: HostReachabilityTracker
    private lateinit var repo: ArticleRepositoryImpl

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        articleDao = mockk(relaxed = true)
        articleImageCache = mockk(relaxed = true)
        connectivityMonitor = mockk(relaxed = true)
        hostReachabilityTracker = mockk(relaxed = true)

        val clock = object : SyncClock {
            override fun nowMillis(): Long = 1_700_000_000_000L
        }
        repo = ArticleRepositoryImpl(
            database = mockk<ReadabilityDatabase>(relaxed = true),
            articleDao = articleDao,
            feedDao = mockk<FeedDao>(relaxed = true),
            groupDao = mockk<GroupDao>(relaxed = true),
            readStateQueueDao = mockk<ReadStateQueueDao>(relaxed = true),
            articleImageDao = mockk(relaxed = true),
            feverConnectionProvider = mockk<FeverConnectionProvider>(relaxed = true),
            connectivityMonitor = connectivityMonitor,
            syncClock = clock,
            articleImageCache = articleImageCache,
            hostReachabilityTracker = hostReachabilityTracker,
            labelWeightDao = mockk(relaxed = true),
            voteQueueDao = mockk(relaxed = true),
            eventQueueDao = mockk(relaxed = true),
            userPreferencesRepository = mockk(relaxed = true),
        )

        every { hostReachabilityTracker.isFailing(any()) } returns false
        coEvery { connectivityMonitor.isOnline() } returns true
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `re-warms a thumbnail flagged cached but missing from disk`() = runTest {
        // Both rows carry a non-null imagesCachedAt: the old flag would consider them "done".
        coEvery { articleDao.getThumbnailArticlesForReconcile(any()) } returns listOf(
            ArticleImageWarmRow(id = 1L, thumbnailUrl = flaggedButMissing, imagesCachedAt = 123L),
            ArticleImageWarmRow(id = 2L, thumbnailUrl = flaggedAndOnDisk, imagesCachedAt = 123L),
        )
        every { articleImageCache.isCached(flaggedButMissing) } returns false
        every { articleImageCache.isCached(flaggedAndOnDisk) } returns true
        coEvery { articleImageCache.cacheUrl(flaggedButMissing) } returns true

        val warmed = repo.rewarmMissingThumbnails(maxToWarm = 300)

        assertEquals(1, warmed)
        coVerify(exactly = 1) { articleImageCache.cacheUrl(flaggedButMissing) }
        coVerify(exactly = 0) { articleImageCache.cacheUrl(flaggedAndOnDisk) }
        coVerify(exactly = 1) { articleDao.markImagesCached(1L, any()) }
    }

    @Test
    fun `skips network entirely when all thumbnails are already on disk`() = runTest {
        coEvery { articleDao.getThumbnailArticlesForReconcile(any()) } returns listOf(
            ArticleImageWarmRow(id = 1L, thumbnailUrl = flaggedAndOnDisk, imagesCachedAt = 123L),
        )
        every { articleImageCache.isCached(flaggedAndOnDisk) } returns true

        val warmed = repo.rewarmMissingThumbnails(maxToWarm = 300)

        assertEquals(0, warmed)
        coVerify(exactly = 0) { articleImageCache.cacheUrl(any()) }
    }
}
