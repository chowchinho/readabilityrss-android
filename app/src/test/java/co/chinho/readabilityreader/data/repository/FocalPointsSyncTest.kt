package co.chinho.readabilityreader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import co.chinho.readabilityreader.data.local.entity.ArticleImageEntity
import co.chinho.readabilityreader.data.remote.FeverApiService
import co.chinho.readabilityreader.data.remote.FeverConnection
import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import co.chinho.readabilityreader.data.remote.FocalApiService
import co.chinho.readabilityreader.data.remote.RankingApiService
import co.chinho.readabilityreader.data.remote.dto.FeverItemDto
import co.chinho.readabilityreader.data.remote.dto.FeverResponse
import co.chinho.readabilityreader.data.remote.dto.FocalPointsRequest
import co.chinho.readabilityreader.data.remote.dto.FocalPointsResponse
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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
class FocalPointsSyncTest {

    private lateinit var db: ReadabilityDatabase
    private lateinit var userPrefsRepo: UserPreferencesRepository
    private lateinit var repo: ArticleRepositoryImpl
    private lateinit var feverService: FeverApiService
    private lateinit var rankingService: RankingApiService
    private lateinit var focalService: FocalApiService

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
        every { userPrefsRepo.serverFocalResetDone } returns flowOf(true)
        feverService = mockk(relaxed = true)
        rankingService = mockk(relaxed = true)
        focalService = mockk(relaxed = true)

        val feverConnection = FeverConnection(
            serverUrl = "https://reader.example.com/fever/",
            apiKey = "testkey",
            service = feverService,
            rankingService = rankingService,
            focalService = focalService,
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

    private fun insertArticle(id: Long) = runBlocking {
        db.articleDao().upsertArticles(
            listOf(
                ArticleEntity(
                    id = id,
                    feedId = 1L,
                    title = "A$id",
                    url = "https://example.com/$id",
                    content = null,
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
    fun testPagingAcross2500HashesProducesThreeRequests() = runBlocking {
        insertArticle(1L)
        val imageRows = (1..2500).map { i ->
            val hex = "%032x".format(i)
            ArticleImageEntity(
                articleId = 1L,
                imageUrl = "https://reader.example.com/api/reader/cached-image/$hex.jpg",
                focalComputed = false,
            )
        }
        db.articleImageDao().insertAll(imageRows)

        val capturedRequests = mutableListOf<FocalPointsRequest>()
        coEvery { focalService.getFocalPoints(capture(capturedRequests)) } answers {
            val req = firstArg<FocalPointsRequest>()
            // Return empty response for each page
            FocalPointsResponse(focal = emptyMap())
        }

        repo.syncFocalPoints()

        assertEquals(3, capturedRequests.size)
        assertEquals(1000, capturedRequests[0].hashes.size)
        assertEquals(1000, capturedRequests[1].hashes.size)
        assertEquals(500, capturedRequests[2].hashes.size)
        assertEquals("testkey", capturedRequests[0].apiKey)
    }

    @Test
    fun testFocalPointsSyncWithDeliberatelyMismatchedFixtures() = runBlocking {
        insertArticle(1L)

        val hashA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val hashB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val hashC = "cccccccccccccccccccccccccccccccc"
        val hashUnrequested = "dddddddddddddddddddddddddddddddd"

        val urlA = "https://reader.example.com/api/reader/cached-image/$hashA.jpg"
        val urlB = "https://reader.example.com/api/reader/cached-image/$hashB.jpg"
        val urlC = "https://reader.example.com/api/reader/cached-image/$hashC.jpg"
        val urlNonCached = "https://example.com/raw/image.jpg"

        db.articleImageDao().insertAll(
            listOf(
                ArticleImageEntity(1L, urlA, focalX = 50, focalY = 50, focalComputed = false),
                ArticleImageEntity(1L, urlB, focalX = 50, focalY = 50, focalComputed = false),
                ArticleImageEntity(1L, urlC, focalX = 50, focalY = 50, focalComputed = false),
                ArticleImageEntity(1L, urlNonCached, focalX = 50, focalY = 50, focalComputed = false),
            )
        )

        // Fake server response:
        // - hashA is present with coords [25, 75]
        // - hashB is missing from server response (server doesn't know it)
        // - hashC is present with coords [80, 20]
        // - hashUnrequested is present in server response but was never requested by phone
        val serverFocalMap = mapOf(
            hashA to listOf(25, 75),
            hashC to listOf(80, 20),
            hashUnrequested to listOf(10, 90),
        )

        val reqSlot = slot<FocalPointsRequest>()
        coEvery { focalService.getFocalPoints(capture(reqSlot)) } returns FocalPointsResponse(focal = serverFocalMap)

        repo.syncFocalPoints()

        // Verify request hashes only contained A, B, C (non-cached URL excluded)
        val requestedHashes = reqSlot.captured.hashes
        assertEquals(3, requestedHashes.size)
        assertTrue(requestedHashes.contains(hashA))
        assertTrue(requestedHashes.contains(hashB))
        assertTrue(requestedHashes.contains(hashC))
        assertFalse(requestedHashes.contains(hashUnrequested))

        // Verify database state
        val images = db.articleImageDao().getOrderedImagesForArticle(1L)
        val imgA = images.first { it.imageUrl == urlA }
        val imgB = images.first { it.imageUrl == urlB }
        val imgC = images.first { it.imageUrl == urlC }
        val imgNonCached = images.first { it.imageUrl == urlNonCached }

        // hashA updated
        assertEquals(25, imgA.focalX)
        assertEquals(75, imgA.focalY)

        // hashB untouched (focalComputed = 0, coords remain 50, 50)
        assertEquals(50, imgB.focalX)
        assertEquals(50, imgB.focalY)

        // hashC updated
        assertEquals(80, imgC.focalX)
        assertEquals(20, imgC.focalY)

        // Non-cached URL untouched
        assertEquals(50, imgNonCached.focalX)
        assertEquals(50, imgNonCached.focalY)

        // Uncomputed query should now only return hashB and non-cached URL
        val remainingUncomputed = db.articleImageDao().getUrlsNeedingFocal(limit = 1000, offset = 0)
        assertEquals(2, remainingUncomputed.size)
        assertTrue(remainingUncomputed.contains(urlB))
        assertTrue(remainingUncomputed.contains(urlNonCached))
    }

    @Test
    fun testBestEffortFocalSyncDoesNotFailSyncWhenEndpointThrows() = runBlocking {
        val feverItem = FeverItemDto(
            id = 100L,
            feedId = 1L,
            title = "FEVER Item",
            url = "https://example.com/100",
            html = "<img src=\"https://reader.example.com/api/reader/cached-image/11111111111111111111111111111111.jpg\">",
            createdOnTime = fixedNowMillis / 1000L,
        )
        coEvery { feverService.query(apiKey = "testkey", feeds = "") } returns FeverResponse(auth = 1)
        coEvery { feverService.query(apiKey = "testkey", groups = "1", feeds = "1", unreadItemIds = "1", savedItemIds = "1") } returns FeverResponse(auth = 1)
        coEvery { feverService.query(apiKey = "testkey", items = "1") } returns FeverResponse(auth = 1, totalItems = 1, items = listOf(feverItem))

        // Focal service throws
        coEvery { focalService.getFocalPoints(any()) } throws RuntimeException("500 Internal Server Error")

        // Sync must succeed best-effort and return 1 item synced
        val synced = repo.syncFromServer(keepDays = 7)
        assertEquals(1, synced)
    }

    // The unpaged read threw `Couldn't read row 6364 from CursorWindow` on a real 20,259-row
    // device set. Anything larger than one page must still come back whole.
    @Test
    fun testUrlsNeedingFocalAreReadAcrossMultiplePages() = runBlocking {
        // Two full read pages plus a partial one. Kept literal because the companion
        // holding FOCAL_URL_READ_PAGE_SIZE is private; 2000 is that page size.
        val total = 2000 * 2 + 137
        val rows = (0 until total).map { i ->
            ArticleImageEntity(
                articleId = 1L,
                imageUrl = "https://reader.example.com/api/reader/cached-image/%032x.jpg".format(i),
                position = i,
            )
        }
        insertArticle(1L)
        db.articleImageDao().insertAll(rows)

        val reqSlot = mutableListOf<FocalPointsRequest>()
        coEvery { focalService.getFocalPoints(capture(reqSlot)) } returns FocalPointsResponse(focal = emptyMap())

        repo.syncFocalPoints()

        val requested = reqSlot.flatMap { it.hashes }.toSet()
        assertEquals(total, requested.size)
    }

    // ML Kit's focal points survive in rows already flagged computed, and the sync only touches
    // focalComputed = 0, so the one-shot reset is the only thing that hands them to the server.
    @Test
    fun testStaleOnDeviceFocalPointsAreResetExactlyOnce() = runBlocking {
        every { userPrefsRepo.serverFocalResetDone } returns flowOf(false)

        val url = "https://reader.example.com/api/reader/cached-image/22222222222222222222222222222222.jpg"
        insertArticle(1L)
        db.articleImageDao().insertAll(listOf(ArticleImageEntity(articleId = 1L, imageUrl = url, position = 0)))
        db.articleImageDao().setFocalForUrl(url, 11, 99)
        assertEquals(1, db.articleImageDao().countFocalComputed())

        coEvery { focalService.getFocalPoints(any()) } returns FocalPointsResponse(
            focal = mapOf("22222222222222222222222222222222" to listOf(70, 30))
        )

        repo.syncFocalPoints()

        val img = db.articleImageDao().getOrderedImagesForArticle(1L).first { it.imageUrl == url }
        assertEquals(70, img.focalX)
        assertEquals(30, img.focalY)
        coVerify(exactly = 1) { userPrefsRepo.setServerFocalResetDone(true) }
    }
}
