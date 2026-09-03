package co.chinho.readabilityreader.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import co.chinho.readabilityreader.data.local.entity.FeedEntity
import co.chinho.readabilityreader.data.local.entity.GroupEntity
import co.chinho.readabilityreader.data.remote.FeverApiService
import co.chinho.readabilityreader.data.remote.FeverConnection
import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import co.chinho.readabilityreader.data.remote.dto.FeverResponse
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BulkMarkReadSemanticsTest {

    private lateinit var db: ReadabilityDatabase
    private lateinit var userPrefsRepo: UserPreferencesRepository
    private lateinit var repo: ArticleRepositoryImpl
    private lateinit var feverService: FeverApiService
    private lateinit var connectivityMonitor: ConnectivityMonitor

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

        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReadabilityDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        userPrefsRepo = mockk(relaxed = true)
        coEvery { userPrefsRepo.articleOrder } returns flowOf("personalised")
        coEvery { userPrefsRepo.rankingAiEnabled } returns flowOf(true)

        feverService = mockk(relaxed = true)

        val feverConnection = FeverConnection(
            serverUrl = "https://reader.example.com/fever/",
            apiKey = "testkey",
            service = feverService,
            rankingService = mockk(relaxed = true),
            focalService = mockk(relaxed = true),
        )

        val connectionProvider = mockk<FeverConnectionProvider>()
        coEvery { connectionProvider.getPotentialConnections() } returns listOf(feverConnection)
        coEvery { connectionProvider.getActiveConnection() } returns feverConnection

        connectivityMonitor = mockk(relaxed = true)
        coEvery { connectivityMonitor.isOnline() } returns true

        repo = ArticleRepositoryImpl(
            database = db,
            articleDao = db.articleDao(),
            feedDao = db.feedDao(),
            groupDao = db.groupDao(),
            readStateQueueDao = db.readStateQueueDao(),
            articleImageDao = db.articleImageDao(),
            feverConnectionProvider = connectionProvider,
            connectivityMonitor = connectivityMonitor,
            syncClock = clock,
            articleImageCache = mockk(relaxed = true),
            hostReachabilityTracker = mockk(relaxed = true),
            labelWeightDao = db.labelWeightDao(),
            voteQueueDao = db.voteQueueDao(),
            eventQueueDao = db.eventQueueDao(),
            userPreferencesRepository = userPrefsRepo,
        )
    }

    @After
    fun tearDown() {
        db.close()
        unmockkStatic(Log::class)
    }

    @Test
    fun testMarkFeedReadWhileOfflineUpdatesDbAndQueuesUnreadArticlesWithoutNetworkCall() = runBlocking {
        coEvery { connectivityMonitor.isOnline() } returns false

        db.articleDao().upsertArticles(
            listOf(
                createArticle(id = 1L, feedId = 10L, isRead = false),
                createArticle(id = 2L, feedId = 10L, isRead = false),
                createArticle(id = 3L, feedId = 10L, isRead = true), // Already read
                createArticle(id = 4L, feedId = 20L, isRead = false), // Different feed
            )
        )

        repo.markFeedRead(feedId = 10L)

        // DB updated
        assertTrue(db.articleDao().observeArticle(1L).first()!!.article.isRead)
        assertTrue(db.articleDao().observeArticle(2L).first()!!.article.isRead)
        assertTrue(db.articleDao().observeArticle(3L).first()!!.article.isRead)
        assertFalse(db.articleDao().observeArticle(4L).first()!!.article.isRead)

        // No network call attempted
        coVerify(exactly = 0) { feverService.query(apiKey = any(), mark = any(), actionState = any(), id = any(), before = any()) }

        // Queued exactly the previously-unread articles in feed 10 (1L and 2L, not 3L or 4L)
        val queued = db.readStateQueueDao().getQueuedActions()
        assertEquals(listOf(1L, 2L), queued.map { it.articleId })
        assertTrue(queued.all { it.action == "read" && it.queuedAt == fixedNowMillis })
    }

    @Test
    fun testMarkFeedReadWhenOnlineButSendThrowsUpdatesDbAndQueuesRows() = runBlocking {
        coEvery { connectivityMonitor.isOnline() } returns true
        coEvery {
            feverService.query(
                apiKey = any(),
                mark = "feed",
                actionState = "read",
                id = 10L,
                before = any(),
            )
        } throws IOException("Socket timeout")

        db.articleDao().upsertArticles(
            listOf(
                createArticle(id = 1L, feedId = 10L, isRead = false),
                createArticle(id = 2L, feedId = 10L, isRead = false),
                createArticle(id = 3L, feedId = 10L, isRead = true),
            )
        )

        repo.markFeedRead(feedId = 10L)

        // DB updated
        assertTrue(db.articleDao().observeArticle(1L).first()!!.article.isRead)
        assertTrue(db.articleDao().observeArticle(2L).first()!!.article.isRead)

        // Queued
        val queued = db.readStateQueueDao().getQueuedActions()
        assertEquals(listOf(1L, 2L), queued.map { it.articleId })
    }

    @Test
    fun testMarkFeedReadOnSuccessfulSendUpdatesDbAndQueuesNothing() = runBlocking {
        coEvery { connectivityMonitor.isOnline() } returns true
        coEvery {
            feverService.query(
                apiKey = any(),
                mark = "feed",
                actionState = "read",
                id = 10L,
                before = any(),
            )
        } returns FeverResponse(auth = 1)

        db.articleDao().upsertArticles(
            listOf(
                createArticle(id = 1L, feedId = 10L, isRead = false),
                createArticle(id = 2L, feedId = 10L, isRead = false),
            )
        )

        repo.markFeedRead(feedId = 10L)

        // DB updated
        assertTrue(db.articleDao().observeArticle(1L).first()!!.article.isRead)
        assertTrue(db.articleDao().observeArticle(2L).first()!!.article.isRead)

        // Nothing queued
        val queued = db.readStateQueueDao().getQueuedActions()
        assertTrue(queued.isEmpty())
    }

    @Test
    fun testMarkGroupReadWhileOfflineUpdatesDbAndQueuesUnreadEligibleArticles() = runBlocking {
        coEvery { connectivityMonitor.isOnline() } returns false

        db.groupDao().upsertGroups(listOf(GroupEntity(id = 100L, title = "Tech")))
        db.feedDao().upsertFeeds(
            listOf(
                FeedEntity(id = 10L, groupId = 100L, title = "F1", url = "u1", siteUrl = null, faviconId = null, faviconUrl = null, faviconProxyUrl = null, lastSyncedAt = null, listViewMode = "standard"),
                FeedEntity(id = 20L, groupId = 100L, title = "F2", url = "u2", siteUrl = null, faviconId = null, faviconUrl = null, faviconProxyUrl = null, lastSyncedAt = null, listViewMode = "standard"),
                FeedEntity(id = 30L, groupId = 200L, title = "F3", url = "u3", siteUrl = null, faviconId = null, faviconUrl = null, faviconProxyUrl = null, lastSyncedAt = null, listViewMode = "standard"),
            )
        )

        val cutoffSeconds = 1000L
        val cutoffMillis = cutoffSeconds * 1000L

        db.articleDao().upsertArticles(
            listOf(
                createArticle(id = 1L, feedId = 10L, publishedAt = cutoffMillis - 100, isRead = false),
                createArticle(id = 2L, feedId = 20L, publishedAt = cutoffMillis, isRead = false),
                createArticle(id = 3L, feedId = 10L, publishedAt = cutoffMillis + 100, isRead = false), // Newer than cutoff
                createArticle(id = 4L, feedId = 10L, publishedAt = cutoffMillis - 100, isRead = true), // Already read
                createArticle(id = 5L, feedId = 30L, publishedAt = cutoffMillis - 100, isRead = false), // Other group
            )
        )

        repo.markGroupRead(groupId = 100L, beforeTimestamp = cutoffSeconds)

        // DB check
        assertTrue(db.articleDao().observeArticle(1L).first()!!.article.isRead)
        assertTrue(db.articleDao().observeArticle(2L).first()!!.article.isRead)
        assertFalse(db.articleDao().observeArticle(3L).first()!!.article.isRead) // Not marked!
        assertTrue(db.articleDao().observeArticle(4L).first()!!.article.isRead)
        assertFalse(db.articleDao().observeArticle(5L).first()!!.article.isRead) // Not marked!

        // Queue check: only 1L and 2L queued
        val queued = db.readStateQueueDao().getQueuedActions()
        assertEquals(listOf(1L, 2L), queued.map { it.articleId })
    }

    @Test
    fun testMarkGroupReadWhenOnlineButSendThrowsUpdatesDbAndQueuesRows() = runBlocking {
        coEvery { connectivityMonitor.isOnline() } returns true
        coEvery {
            feverService.query(
                apiKey = any(),
                mark = "group",
                actionState = "read",
                id = 100L,
                before = any(),
            )
        } throws IOException("Connection reset")

        db.groupDao().upsertGroups(listOf(GroupEntity(id = 100L, title = "Tech")))
        db.feedDao().upsertFeeds(
            listOf(
                FeedEntity(id = 10L, groupId = 100L, title = "F1", url = "u1", siteUrl = null, faviconId = null, faviconUrl = null, faviconProxyUrl = null, lastSyncedAt = null, listViewMode = "standard")
            )
        )

        val cutoffSeconds = 1000L
        val cutoffMillis = cutoffSeconds * 1000L

        db.articleDao().upsertArticles(
            listOf(
                createArticle(id = 1L, feedId = 10L, publishedAt = cutoffMillis, isRead = false),
                createArticle(id = 2L, feedId = 10L, publishedAt = cutoffMillis + 500, isRead = false), // Newer
            )
        )

        repo.markGroupRead(groupId = 100L, beforeTimestamp = cutoffSeconds)

        assertTrue(db.articleDao().observeArticle(1L).first()!!.article.isRead)
        assertFalse(db.articleDao().observeArticle(2L).first()!!.article.isRead)

        val queued = db.readStateQueueDao().getQueuedActions()
        assertEquals(listOf(1L), queued.map { it.articleId })
    }

    @Test
    fun testMarkGroupReadOnSuccessfulSendUpdatesDbAndQueuesNothing() = runBlocking {
        coEvery { connectivityMonitor.isOnline() } returns true
        coEvery {
            feverService.query(
                apiKey = any(),
                mark = "group",
                actionState = "read",
                id = 100L,
                before = any(),
            )
        } returns FeverResponse(auth = 1)

        db.groupDao().upsertGroups(listOf(GroupEntity(id = 100L, title = "Tech")))
        db.feedDao().upsertFeeds(
            listOf(
                FeedEntity(id = 10L, groupId = 100L, title = "F1", url = "u1", siteUrl = null, faviconId = null, faviconUrl = null, faviconProxyUrl = null, lastSyncedAt = null, listViewMode = "standard")
            )
        )

        val cutoffSeconds = 1000L
        val cutoffMillis = cutoffSeconds * 1000L

        db.articleDao().upsertArticles(
            listOf(
                createArticle(id = 1L, feedId = 10L, publishedAt = cutoffMillis, isRead = false),
            )
        )

        repo.markGroupRead(groupId = 100L, beforeTimestamp = cutoffSeconds)

        assertTrue(db.articleDao().observeArticle(1L).first()!!.article.isRead)
        val queued = db.readStateQueueDao().getQueuedActions()
        assertTrue(queued.isEmpty())
    }

    private fun createArticle(
        id: Long,
        feedId: Long,
        isRead: Boolean,
        publishedAt: Long = 1000L,
    ): ArticleEntity {
        return ArticleEntity(
            id = id,
            feedId = feedId,
            title = "Article $id",
            url = "https://example.com/$id",
            content = "Content $id",
            publishedAt = publishedAt,
            isRead = isRead,
            isSaved = false,
            thumbnailUrl = null,
            cachedAt = fixedNowMillis,
            contentCachedAt = fixedNowMillis,
            imagesCachedAt = null,
        )
    }
}
