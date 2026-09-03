package co.chinho.readabilityreader.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.entity.ArticleEntity
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
import java.net.UnknownHostException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the single-article read/saved path against the crash of 2026-09-01: on an unstable
 * network isOnline() is true, HostReachabilityInterceptor throws immediately, and an unwrapped
 * throw here escaped viewModelScope.launch and killed the process.
 */
@RunWith(AndroidJUnit4::class)
class SingleItemActionSemanticsTest {

    private lateinit var db: ReadabilityDatabase
    private lateinit var repo: ArticleRepositoryImpl
    private lateinit var feverService: FeverApiService
    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var userPrefsRepo: UserPreferencesRepository

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
    fun testMarkReadIsQueuedWhenOnlineButTheSendThrows() = runBlocking {
        coEvery {
            feverService.query(apiKey = any(), mark = "item", actionState = "read", id = 1L)
        } throws UnknownHostException("host marked unreachable: reader.example.com")

        db.articleDao().upsertArticles(listOf(createArticle(id = 1L)))

        repo.markRead(articleId = 1L, isRead = true)

        assertTrue(db.articleDao().observeArticle(1L).first()!!.article.isRead)
        val queued = db.readStateQueueDao().getQueuedActions()
        assertEquals(listOf(1L), queued.map { it.articleId })
        assertEquals("read", queued.single().action)
        assertEquals(fixedNowMillis, queued.single().queuedAt)
    }

    @Test
    fun testMarkSavedIsQueuedWhenOnlineButTheSendThrows() = runBlocking {
        coEvery {
            feverService.query(apiKey = any(), mark = "item", actionState = "saved", id = 1L)
        } throws UnknownHostException("host marked unreachable: reader.example.com")

        db.articleDao().upsertArticles(listOf(createArticle(id = 1L)))

        repo.markSaved(articleId = 1L, isSaved = true)

        assertTrue(db.articleDao().observeArticle(1L).first()!!.article.isSaved)
        val queued = db.readStateQueueDao().getQueuedActions()
        assertEquals(listOf(1L), queued.map { it.articleId })
        assertEquals("saved", queued.single().action)
    }

    @Test
    fun testMarkReadWhileOfflineQueuesWithoutAnyNetworkCall() = runBlocking {
        coEvery { connectivityMonitor.isOnline() } returns false

        db.articleDao().upsertArticles(listOf(createArticle(id = 1L)))

        repo.markRead(articleId = 1L, isRead = true)

        coVerify(exactly = 0) {
            feverService.query(apiKey = any(), mark = any(), actionState = any(), id = any())
        }
        assertEquals(listOf(1L), db.readStateQueueDao().getQueuedActions().map { it.articleId })
    }

    @Test
    fun testMarkReadIsQueuedWhenTheServerRejectsAuth() = runBlocking {
        coEvery {
            feverService.query(apiKey = any(), mark = "item", actionState = "read", id = 1L)
        } returns FeverResponse(auth = 0)

        db.articleDao().upsertArticles(listOf(createArticle(id = 1L)))

        repo.markRead(articleId = 1L, isRead = true)

        assertEquals(listOf(1L), db.readStateQueueDao().getQueuedActions().map { it.articleId })
    }

    @Test
    fun testMarkReadOnSuccessfulSendQueuesNothing() = runBlocking {
        coEvery {
            feverService.query(apiKey = any(), mark = "item", actionState = "read", id = 1L)
        } returns FeverResponse(auth = 1)

        db.articleDao().upsertArticles(listOf(createArticle(id = 1L)))

        repo.markRead(articleId = 1L, isRead = true)

        assertTrue(db.articleDao().observeArticle(1L).first()!!.article.isRead)
        assertTrue(db.readStateQueueDao().getQueuedActions().isEmpty())
    }

    private fun createArticle(id: Long): ArticleEntity {
        return ArticleEntity(
            id = id,
            feedId = 10L,
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
    }
}
