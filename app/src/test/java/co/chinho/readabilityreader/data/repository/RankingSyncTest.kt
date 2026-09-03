package co.chinho.readabilityreader.data.repository

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
import co.chinho.readabilityreader.data.remote.dto.FeverItemDto
import co.chinho.readabilityreader.data.remote.dto.FeverResponse
import co.chinho.readabilityreader.data.remote.dto.RankingScoresResponse
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RankingSyncTest {

    private lateinit var db: ReadabilityDatabase
    private lateinit var userPrefsRepo: UserPreferencesRepository
    private lateinit var repo: ArticleRepositoryImpl
    private lateinit var feverService: FeverApiService
    private lateinit var rankingService: RankingApiService

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
    fun testBestEffortRankingPassDoesNotFailSyncWhenRankingThrows() = runBlocking {
        // Setup FEVER mock response
        val feverItem = FeverItemDto(
            id = 100L,
            feedId = 1L,
            title = "FEVER Item",
            url = "https://example.com/100",
            html = "Content",
            createdOnTime = fixedNowMillis / 1000L,
        )
        coEvery { feverService.query(apiKey = "testkey", feeds = "") } returns FeverResponse(auth = 1)
        coEvery { feverService.query(apiKey = "testkey", groups = "1", feeds = "1", unreadItemIds = "1", savedItemIds = "1") } returns FeverResponse(auth = 1)
        coEvery { feverService.query(apiKey = "testkey", items = "1") } returns FeverResponse(auth = 1, totalItems = 1, items = listOf(feverItem))

        // Ranking service throws Exception
        coEvery { rankingService.getScores(apiKey = "testkey", sinceId = any()) } throws RuntimeException("Ranking server offline")

        // Call syncFromServer — must succeed best-effort and return 1 item synced
        val synced = repo.syncFromServer(keepDays = 7)
        assertEquals(1, synced)
    }

    @Test
    fun testWholesaleScoreReplacement() = runBlocking {
        // Pre-insert Article 1 (score 5.0) and Article 2 (score 3.0)
        db.articleDao().upsertArticles(
            listOf(
                ArticleEntity(
                    id = 1L, feedId = 1L, title = "A1", url = "https://a1", content = "c1",
                    publishedAt = 1000L, isRead = false, isSaved = false, thumbnailUrl = null,
                    cachedAt = fixedNowMillis, contentCachedAt = fixedNowMillis, imagesCachedAt = null,
                    score = 5.0
                ),
                ArticleEntity(
                    id = 2L, feedId = 1L, title = "A2", url = "https://a2", content = "c2",
                    publishedAt = 2000L, isRead = false, isSaved = false, thumbnailUrl = null,
                    cachedAt = fixedNowMillis, contentCachedAt = fixedNowMillis, imagesCachedAt = null,
                    score = 3.0
                )
            )
        )

        // Setup FEVER mock
        coEvery { feverService.query(apiKey = "testkey", feeds = "") } returns FeverResponse(auth = 1)
        coEvery { feverService.query(apiKey = "testkey", groups = "1", feeds = "1", unreadItemIds = "1", savedItemIds = "1") } returns FeverResponse(auth = 1)
        coEvery { feverService.query(apiKey = "testkey", items = "1") } returns FeverResponse(auth = 1, totalItems = 0, items = emptyList())

        // Ranking mock returns scores for Article 1 (score 8.0) only
        val item1Score = JsonArray().apply {
            add(JsonPrimitive(1L))
            add(JsonPrimitive(8.0))
        }
        val rankingResponse = RankingScoresResponse(
            aiEnabled = true,
            scores = listOf(item1Score)
        )
        // Lowest held article is id 1, and the server bound is exclusive, so the request must be
        // `id > 0` for article 1 to come back at all.
        coEvery { rankingService.getScores(apiKey = "testkey", sinceId = 0L) } returns rankingResponse

        repo.syncFromServer(keepDays = 7)

        // Article 1 updated to score 8.0
        // Article 2 score reset to null (wholesale score replacement)
        val articles = db.articleDao().observeArticles(feedId = null, showRead = true, stickyIds = emptyList()).first()
        val article1 = articles.first { it.article.id == 1L }
        val article2 = articles.first { it.article.id == 2L }

        assertEquals(8.0, article1.article.score!!, 0.001)
        assertNull(article2.article.score)
    }
}
