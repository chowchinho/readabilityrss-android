package co.chinho.readabilityreader.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.entity.ReadStateQueueEntity
import co.chinho.readabilityreader.data.remote.FeverApiService
import co.chinho.readabilityreader.data.remote.FeverConnection
import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import co.chinho.readabilityreader.data.remote.dto.FeverResponse
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadStateFlushConcurrencyTest {

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
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

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
    fun testTwelveQueuedActionsAllSucceedMaxInFlightAtMostFiveAndAllDeleted() = runBlocking {
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)

        coEvery {
            feverService.query(
                apiKey = any(),
                mark = "item",
                actionState = any(),
                id = any(),
            )
        } coAnswers {
            val current = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, current) }
            delay(20)
            inFlight.decrementAndGet()
            FeverResponse(auth = 1)
        }

        // Insert 12 queued actions
        for (i in 1L..12L) {
            db.readStateQueueDao().insert(
                ReadStateQueueEntity(
                    id = i,
                    articleId = i * 10,
                    action = "read",
                    queuedAt = fixedNowMillis,
                )
            )
        }

        assertEquals(12, db.readStateQueueDao().getQueuedActions().size)

        repo.flushReadStateQueue()

        // Verify concurrency bounds: never exceeded 5, but ran concurrently (> 1)
        assertTrue("Max in-flight was ${maxInFlight.get()}, expected <= 5", maxInFlight.get() <= 5)
        assertTrue("Max in-flight was ${maxInFlight.get()}, expected > 1", maxInFlight.get() > 1)

        // All 12 deleted
        assertTrue(db.readStateQueueDao().getQueuedActions().isEmpty())
    }

    @Test
    fun testOneActionThrowsRemainingElevenAreDeletedAndFailingOneStaysQueued() = runBlocking {
        coEvery {
            feverService.query(
                apiKey = any(),
                mark = "item",
                actionState = any(),
                id = any(),
            )
        } coAnswers {
            val articleId = it.invocation.args.filterIsInstance<Long>().firstOrNull()
            if (articleId == 50L) {
                throw IOException("Connection reset on article 50")
            }
            FeverResponse(auth = 1)
        }

        for (i in 1L..12L) {
            db.readStateQueueDao().insert(
                ReadStateQueueEntity(
                    id = i,
                    articleId = i * 10,
                    action = "read",
                    queuedAt = fixedNowMillis,
                )
            )
        }

        repo.flushReadStateQueue()

        val remaining = db.readStateQueueDao().getQueuedActions()
        assertEquals(1, remaining.size)
        assertEquals(5L, remaining[0].id)
        assertEquals(50L, remaining[0].articleId)
    }

    @Test
    fun testOneActionReturnsAuthZeroStaysQueuedAndOthersDeleted() = runBlocking {
        coEvery {
            feverService.query(
                apiKey = any(),
                mark = "item",
                actionState = any(),
                id = any(),
            )
        } coAnswers {
            val articleId = it.invocation.args.filterIsInstance<Long>().firstOrNull()
            if (articleId == 70L) {
                FeverResponse(auth = 0)
            } else {
                FeverResponse(auth = 1)
            }
        }

        for (i in 1L..12L) {
            db.readStateQueueDao().insert(
                ReadStateQueueEntity(
                    id = i,
                    articleId = i * 10,
                    action = "read",
                    queuedAt = fixedNowMillis,
                )
            )
        }

        repo.flushReadStateQueue()

        val remaining = db.readStateQueueDao().getQueuedActions()
        assertEquals(1, remaining.size)
        assertEquals(7L, remaining[0].id)
        assertEquals(70L, remaining[0].articleId)
    }
}
