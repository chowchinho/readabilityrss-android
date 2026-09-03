package co.chinho.readabilityreader.ui.reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import co.chinho.readabilityreader.data.remote.FeverApiService
import co.chinho.readabilityreader.data.remote.FeverConnection
import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import co.chinho.readabilityreader.data.remote.RankingApiService
import co.chinho.readabilityreader.data.remote.dto.RankingFeedbackRequest
import co.chinho.readabilityreader.data.remote.dto.RankingFeedbackResponse
import co.chinho.readabilityreader.data.repository.ArticleRepositoryImpl
import co.chinho.readabilityreader.data.repository.SyncClock
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VotingSemanticsTest {

    private lateinit var db: ReadabilityDatabase
    private lateinit var userPrefsRepo: UserPreferencesRepository
    private lateinit var repo: ArticleRepositoryImpl
    private lateinit var feverService: FeverApiService
    private lateinit var rankingService: RankingApiService
    private lateinit var connectivityMonitor: co.chinho.readabilityreader.data.repository.ConnectivityMonitor

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

        userPrefsRepo = mockk(relaxed = true)
        coEvery { userPrefsRepo.articleOrder } returns flowOf("personalised")
        coEvery { userPrefsRepo.rankingAiEnabled } returns flowOf(true)

        feverService = mockk(relaxed = true)
        rankingService = mockk(relaxed = true)

        val feverConnection = FeverConnection(
            serverUrl = "https://reader.example.com/fever/",
            apiKey = "testkey",
            service = feverService,
            rankingService = rankingService,
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
    }

    @Test
    fun testUpvoteSendsMarkReadFalseAndLeavesIsReadUnchanged() = runBlocking {
        db.articleDao().upsertArticles(
            listOf(
                ArticleEntity(
                    id = 10L, feedId = 1L, title = "Art 10", url = "https://art10", content = "c",
                    publishedAt = 1000L, isRead = false, isSaved = false, thumbnailUrl = null,
                    cachedAt = fixedNowMillis, contentCachedAt = fixedNowMillis, imagesCachedAt = null,
                )
            )
        )

        // Record upvote ("show_more") with markRead = false
        repo.recordVote(articleId = 10L, vote = "show_more", markRead = false)

        val article = db.articleDao().observeArticle(10L).first()
        assertNotNull(article)
        assertEquals("show_more", article!!.article.vote)
        assertFalse(article.article.isRead) // Must remain unread!

        // Online, so the vote goes straight out rather than waiting for the next sync.
        val sent = slot<RankingFeedbackRequest>()
        coVerify { rankingService.sendFeedback(capture(sent)) }
        assertEquals(10L, sent.captured.votes!!.single().articleId)
        assertEquals("show_more", sent.captured.votes!!.single().vote)
        assertFalse(sent.captured.votes!!.single().markRead)

        assertTrue(db.voteQueueDao().getAll().isEmpty())
    }

    @Test
    fun testDownvoteSendsMarkReadTrueAndMarksReadLocally() = runBlocking {
        db.articleDao().upsertArticles(
            listOf(
                ArticleEntity(
                    id = 20L, feedId = 1L, title = "Art 20", url = "https://art20", content = "c",
                    publishedAt = 1000L, isRead = false, isSaved = false, thumbnailUrl = null,
                    cachedAt = fixedNowMillis, contentCachedAt = fixedNowMillis, imagesCachedAt = null,
                )
            )
        )

        // Record downvote ("show_less") with markRead = true
        repo.recordVote(articleId = 20L, vote = "show_less", markRead = true)

        val article = db.articleDao().observeArticle(20L).first()
        assertNotNull(article)
        assertEquals("show_less", article!!.article.vote)
        assertTrue(article.article.isRead) // Downvote marks read!

        val sent = slot<RankingFeedbackRequest>()
        coVerify { rankingService.sendFeedback(capture(sent)) }
        assertEquals(20L, sent.captured.votes!!.single().articleId)
        assertEquals("show_less", sent.captured.votes!!.single().vote)
        assertTrue(sent.captured.votes!!.single().markRead)

        assertTrue(db.voteQueueDao().getAll().isEmpty())
    }

    @Test
    fun testVoteIsQueuedWhenOnlineButTheSendThrows() = runBlocking {
        db.articleDao().upsertArticles(
            listOf(
                ArticleEntity(
                    id = 50L, feedId = 1L, title = "Art 50", url = "https://art50", content = "c",
                    publishedAt = 1000L, isRead = false, isSaved = false, thumbnailUrl = null,
                    cachedAt = fixedNowMillis, contentCachedAt = fixedNowMillis, imagesCachedAt = null,
                )
            )
        )

        // "Online" only means a route existed a moment ago. On unstable mobile the POST still
        // throws, and the vote must fall through to the queue rather than being lost - this is
        // the read-state gap documented in CLAUDE.md, deliberately not repeated here.
        coEvery { connectivityMonitor.isOnline() } returns true
        coEvery { rankingService.sendFeedback(any<RankingFeedbackRequest>()) } throws
            java.io.IOException("connection reset")

        repo.recordVote(articleId = 50L, vote = "show_more", markRead = false)

        assertEquals("show_more", db.articleDao().observeArticle(50L).first()!!.article.vote)
        val queued = db.voteQueueDao().getAll()
        assertEquals(1, queued.size)
        assertEquals(50L, queued[0].articleId)
    }

    @Test
    fun testSameDirectionClearsVoteAndOppositeFlipsIt() = runBlocking {
        db.articleDao().upsertArticles(
            listOf(
                ArticleEntity(
                    id = 30L, feedId = 1L, title = "Art 30", url = "https://art30", content = "c",
                    publishedAt = 1000L, isRead = false, isSaved = false, thumbnailUrl = null,
                    cachedAt = fixedNowMillis, contentCachedAt = fixedNowMillis, imagesCachedAt = null,
                )
            )
        )

        // Step 1: Upvote
        repo.recordVote(articleId = 30L, vote = "show_more", markRead = false)
        assertEquals("show_more", db.articleDao().observeArticle(30L).first()!!.article.vote)

        // Step 2: Upvote again (same direction) -> clears vote
        repo.recordVote(articleId = 30L, vote = null, markRead = false)
        assertNull(db.articleDao().observeArticle(30L).first()!!.article.vote)

        // Step 3: Downvote (opposite direction) -> sets downvote
        repo.recordVote(articleId = 30L, vote = "show_less", markRead = true)
        assertEquals("show_less", db.articleDao().observeArticle(30L).first()!!.article.vote)
    }

    @Test
    fun testOfflineQueuedVoteFlushesOnNextSync() = runBlocking {
        db.articleDao().upsertArticles(
            listOf(
                ArticleEntity(
                    id = 40L, feedId = 1L, title = "Art 40", url = "https://art40", content = "c",
                    publishedAt = 1000L, isRead = false, isSaved = false, thumbnailUrl = null,
                    cachedAt = fixedNowMillis, contentCachedAt = fixedNowMillis, imagesCachedAt = null,
                )
            )
        )

        // Cast vote while genuinely offline - the Tube case. Nothing is sent; it must queue.
        coEvery { connectivityMonitor.isOnline() } returns false
        repo.recordVote(articleId = 40L, vote = "show_more", markRead = false)
        assertEquals(1, db.voteQueueDao().getAll().size)
        coVerify(exactly = 0) { rankingService.sendFeedback(any<RankingFeedbackRequest>()) }

        coEvery { connectivityMonitor.isOnline() } returns true

        // Mock feedback API accept
        val feedbackResponse = RankingFeedbackResponse(
            acceptedVotes = listOf(40L),
            acceptedEvents = emptyList(),
            status = "ok",
        )
        coEvery { rankingService.sendFeedback(any<RankingFeedbackRequest>()) } returns feedbackResponse

        // Flush ranking queues
        repo.flushRankingQueues()

        // Queue must be empty after flush
        assertTrue(db.voteQueueDao().getAll().isEmpty())
    }

    private fun assertNotNull(obj: Any?) {
        org.junit.Assert.assertNotNull(obj)
    }
}
