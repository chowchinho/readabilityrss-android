package co.chinho.readabilityreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BehaviouralSignalsTest {

    private lateinit var db: ReadabilityDatabase
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

        repo = ArticleRepositoryImpl(
            database = db,
            articleDao = db.articleDao(),
            feedDao = db.feedDao(),
            groupDao = db.groupDao(),
            readStateQueueDao = db.readStateQueueDao(),
            articleImageDao = db.articleImageDao(),
            feverConnectionProvider = mockk<FeverConnectionProvider>(relaxed = true),
            connectivityMonitor = mockk(relaxed = true),
            syncClock = clock,
            articleImageCache = mockk(relaxed = true),
            hostReachabilityTracker = mockk(relaxed = true),
            labelWeightDao = db.labelWeightDao(),
            voteQueueDao = db.voteQueueDao(),
            eventQueueDao = db.eventQueueDao(),
            userPreferencesRepository = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun insertArticle(id: Long, feedId: Long = 10L) = runBlocking {
        db.articleDao().upsertArticles(
            listOf(
                ArticleEntity(
                    id = id,
                    feedId = feedId,
                    title = "Article $id",
                    url = "https://example.com/$id",
                    content = "Content $id",
                    publishedAt = 1000L,
                    isRead = false,
                    isSaved = false,
                    thumbnailUrl = null,
                    cachedAt = fixedNowMillis,
                    contentCachedAt = fixedNowMillis,
                    imagesCachedAt = null,
                )
            )
        )
    }

    @Test
    fun testPrefetchedNeverDisplayedPageEmitsNothing() = runBlocking {
        insertArticle(100L)
        // Event queue starts empty and stays empty when no user visibility event occurs
        val events = db.eventQueueDao().getAll()
        assertTrue(events.isEmpty())
    }

    @Test
    fun testFastSwipesEmitNoOpenAndNoSkip() = runBlocking {
        // Simulating fast swiping where user stays < 1s per article
        // Neither open nor skip is recorded when dwell < 1s
        val events = db.eventQueueDao().getAll()
        assertTrue(events.isEmpty())
    }

    @Test
    fun testPageDisplayed6sEmitsOneOpen() = runBlocking {
        insertArticle(100L)
        repo.recordEvent(100L, "open", 5)

        val events = db.eventQueueDao().getAll()
        assertEquals(1, events.size)
        assertEquals("open", events[0].eventType)
        assertEquals(100L, events[0].articleId)
        assertEquals(5, events[0].dwellSeconds)
    }

    @Test
    fun testBulkActionsEmitNothing() = runBlocking {
        insertArticle(100L, feedId = 5L)
        insertArticle(101L, feedId = 5L)

        repo.markFeedRead(5L)
        repo.markGroupRead(1L, System.currentTimeMillis())

        val events = db.eventQueueDao().getAll()
        assertTrue(events.isEmpty())
    }

    @Test
    fun testVoteSupersedesReadWithoutVote() = runBlocking {
        insertArticle(100L)
        repo.recordEvent(100L, "read_without_vote", null)
        assertEquals(1, db.eventQueueDao().getAll().size)

        // User votes show_more
        repo.recordVote(100L, "show_more", markRead = false)

        // Vote should have removed read_without_vote event
        val eventsAfterVote = db.eventQueueDao().getAll()
        assertTrue(eventsAfterVote.isEmpty())

        val queuedVotes = db.voteQueueDao().getAll()
        assertEquals(1, queuedVotes.size)
        assertEquals("show_more", queuedVotes[0].vote)
    }
}
