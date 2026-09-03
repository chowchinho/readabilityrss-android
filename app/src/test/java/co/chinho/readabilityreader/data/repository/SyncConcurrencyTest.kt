package co.chinho.readabilityreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.remote.FeverApiService
import co.chinho.readabilityreader.data.remote.FeverConnection
import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import co.chinho.readabilityreader.data.remote.FocalApiService
import co.chinho.readabilityreader.data.remote.RankingApiService
import co.chinho.readabilityreader.data.remote.dto.FeverResponse
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncConcurrencyTest {

    private lateinit var db: ReadabilityDatabase
    private lateinit var repo: ArticleRepositoryImpl
    private lateinit var feverService: FeverApiService
    private lateinit var rankingService: RankingApiService
    private lateinit var focalService: FocalApiService
    private lateinit var userPrefsRepo: UserPreferencesRepository

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

        feverService = mockk(relaxed = true)
        rankingService = mockk(relaxed = true)
        focalService = mockk(relaxed = true)
        userPrefsRepo = mockk(relaxed = true)

        every { userPrefsRepo.serverFocalResetDone } returns flowOf(true)

        val connection = FeverConnection(
            serverUrl = "https://reader.example.com/fever/",
            apiKey = "testapikey",
            service = feverService,
            rankingService = rankingService,
            focalService = focalService,
        )

        val connectionProvider = mockk<FeverConnectionProvider>()
        coEvery { connectionProvider.getPotentialConnections() } returns listOf(connection)

        repo = ArticleRepositoryImpl(
            database = db,
            articleDao = db.articleDao(),
            feedDao = db.feedDao(),
            groupDao = db.groupDao(),
            readStateQueueDao = db.readStateQueueDao(),
            articleImageDao = db.articleImageDao(),
            feverConnectionProvider = connectionProvider,
            connectivityMonitor = mockk(relaxed = true),
            syncClock = clock,
            articleImageCache = mockk(relaxed = true),
            hostReachabilityTracker = mockk(relaxed = true),
            labelWeightDao = mockk(relaxed = true),
            voteQueueDao = mockk(relaxed = true),
            eventQueueDao = mockk(relaxed = true),
            userPreferencesRepository = userPrefsRepo,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `concurrent syncFromServer calls - second call returns 0 immediately without second FEVER round trip`() = runBlocking {
        val firstSyncEntered = CompletableDeferred<Unit>()
        val allowFirstSyncToComplete = CompletableDeferred<Unit>()

        coEvery { feverService.query(apiKey = "testapikey", feeds = "") } coAnswers {
            firstSyncEntered.complete(Unit)
            allowFirstSyncToComplete.await()
            FeverResponse(auth = 1)
        }

        coEvery { feverService.query(apiKey = "testapikey", items = "1") } returns FeverResponse(
            auth = 1,
            totalItems = 0,
            items = emptyList(),
        )
        coEvery {
            feverService.query(
                apiKey = "testapikey",
                groups = "1",
                feeds = "1",
                unreadItemIds = "1",
                savedItemIds = "1",
            )
        } returns FeverResponse(auth = 1)

        val firstJob = async {
            repo.syncFromServer(keepDays = 7)
        }

        // Wait until the first sync is running and holds the Mutex lock
        firstSyncEntered.await()

        // Attempt a concurrent second sync call
        val secondResult = repo.syncFromServer(keepDays = 7)

        // The second call must return 0 immediately without blocking
        assertEquals(0, secondResult)

        // Allow first sync to finish
        allowFirstSyncToComplete.complete(Unit)
        val firstResult = firstJob.await()
        assertEquals(0, firstResult)

        // FEVER initial probe must have been called exactly once, proving no second round trip occurred
        coVerify(exactly = 1) { feverService.query(apiKey = "testapikey", feeds = "") }
    }

    @Test
    fun `guard releases - subsequent call after first sync completes syncs normally`() = runBlocking {
        coEvery { feverService.query(apiKey = "testapikey", feeds = "") } returns FeverResponse(auth = 1)
        coEvery { feverService.query(apiKey = "testapikey", items = "1") } returns FeverResponse(
            auth = 1,
            totalItems = 0,
            items = emptyList(),
        )
        coEvery {
            feverService.query(
                apiKey = "testapikey",
                groups = "1",
                feeds = "1",
                unreadItemIds = "1",
                savedItemIds = "1",
            )
        } returns FeverResponse(auth = 1)

        // First sync
        val firstResult = repo.syncFromServer(keepDays = 7)
        assertEquals(0, firstResult)
        coVerify(exactly = 1) { feverService.query(apiKey = "testapikey", feeds = "") }

        // Second sync (subsequent)
        val secondResult = repo.syncFromServer(keepDays = 7)
        assertEquals(0, secondResult)

        // Mutex was unlocked properly, so FEVER probe was called a second time
        coVerify(exactly = 2) { feverService.query(apiKey = "testapikey", feeds = "") }
    }

    @Test
    fun `guard releases even when first sync throws an exception`() = runBlocking {
        var firstCall = true
        coEvery { feverService.query(apiKey = "testapikey", feeds = "") } answers {
            if (firstCall) {
                firstCall = false
                throw java.io.IOException("Network error")
            } else {
                FeverResponse(auth = 1)
            }
        }
        coEvery { feverService.query(apiKey = "testapikey", items = "1") } returns FeverResponse(
            auth = 1,
            totalItems = 0,
            items = emptyList(),
        )
        coEvery {
            feverService.query(
                apiKey = "testapikey",
                groups = "1",
                feeds = "1",
                unreadItemIds = "1",
                savedItemIds = "1",
            )
        } returns FeverResponse(auth = 1)

        // First sync throws
        try {
            repo.syncFromServer(keepDays = 7)
        } catch (_: Exception) {
        }

        // Second sync should succeed because finally { syncMutex.unlock() } released the lock
        val secondResult = repo.syncFromServer(keepDays = 7)
        assertEquals(0, secondResult)

        coVerify(exactly = 2) { feverService.query(apiKey = "testapikey", feeds = "") }
    }
}
