package co.chinho.readabilityreader.data.repository

import android.util.Log
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.dao.ArticleDao
import co.chinho.readabilityreader.data.local.dao.FeedDao
import co.chinho.readabilityreader.data.local.dao.GroupDao
import co.chinho.readabilityreader.data.local.dao.ReadStateQueueDao
import co.chinho.readabilityreader.data.remote.FeverApiService
import co.chinho.readabilityreader.data.remote.FeverConnection
import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import co.chinho.readabilityreader.data.remote.dto.FeverItemDto
import co.chinho.readabilityreader.data.remote.dto.FeverResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FetchPagedItemsTest {

    private val fixedNowMillis: Long = 1_700_000_000_000L
    private val keepDays = 5
    private val cutoffSeconds: Long =
        (fixedNowMillis - keepDays * 24L * 60L * 60L * 1000L) / 1000L

    private lateinit var service: StubFeverApiService
    private lateinit var repo: ArticleRepositoryImpl

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        service = StubFeverApiService()
        val clock = object : SyncClock {
            override fun nowMillis(): Long = fixedNowMillis
        }
        repo = ArticleRepositoryImpl(
            database = mockk<ReadabilityDatabase>(relaxed = true),
            articleDao = mockk<ArticleDao>(relaxed = true),
            feedDao = mockk<FeedDao>(relaxed = true),
            groupDao = mockk<GroupDao>(relaxed = true),
            readStateQueueDao = mockk<ReadStateQueueDao>(relaxed = true),
            articleImageDao = mockk(relaxed = true),
            feverConnectionProvider = mockk<FeverConnectionProvider>(relaxed = true),
            connectivityMonitor = mockk<ConnectivityMonitor>(relaxed = true),
            syncClock = clock,
            articleImageCache = mockk<ArticleImageCache>(relaxed = true),
            hostReachabilityTracker = mockk<HostReachabilityTracker>(relaxed = true),
            labelWeightDao = mockk(relaxed = true),
            voteQueueDao = mockk(relaxed = true),
            eventQueueDao = mockk(relaxed = true),
            userPreferencesRepository = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun connection() = FeverConnection(
        serverUrl = "https://example.test/",
        apiKey = "key",
        service = service,
        rankingService = mockk(relaxed = true),
        focalService = mockk(relaxed = true),
    )

    private fun item(id: Long, secondsOld: Long): FeverItemDto = FeverItemDto(
        id = id,
        feedId = 1,
        title = "t$id",
        url = "https://example.test/$id",
        createdOnTime = (fixedNowMillis / 1000L) - secondsOld,
    )

    private val inWindow: Long get() = 60L
    private val outsideWindow: Long get() = (keepDays.toLong() + 1) * 24L * 60L * 60L

    @Test
    fun `initial sync first page mixed newest-old continues paging until all older`() = runTest {
        val firstPage = FeverResponse(
            auth = 1,
            totalItems = 50,
            items = listOf(
                item(id = 100, secondsOld = inWindow),
                item(id = 99, secondsOld = inWindow),
                item(id = 98, secondsOld = outsideWindow),
            ),
        )
        service.responses = ArrayDeque(
            listOf(
                FeverResponse(
                    auth = 1,
                    totalItems = 50,
                    items = listOf(
                        item(id = 97, secondsOld = inWindow),
                        item(id = 96, secondsOld = outsideWindow),
                    ),
                ),
                FeverResponse(
                    auth = 1,
                    totalItems = 50,
                    items = listOf(
                        item(id = 95, secondsOld = outsideWindow),
                        item(id = 94, secondsOld = outsideWindow),
                    ),
                ),
            )
        )

        val result = repo.fetchPagedItems(
            connection = connection(),
            firstPage = firstPage,
            startingAfterId = null,
            oldestLocalId = null,
            localArticleCount = 0,
            keepDays = keepDays,
        ) { _, _ -> }

        assertEquals(listOf<Long>(100, 99, 97), result.map { it.id })
        assertTrue(service.responses.isEmpty())
    }

    @Test
    fun `initial sync first page all-old stops immediately`() = runTest {
        val firstPage = FeverResponse(
            auth = 1,
            totalItems = 41,
            items = listOf(
                item(id = 5, secondsOld = outsideWindow),
                item(id = 4, secondsOld = outsideWindow),
            ),
        )
        service.responses = ArrayDeque()

        val result = repo.fetchPagedItems(
            connection = connection(),
            firstPage = firstPage,
            startingAfterId = null,
            oldestLocalId = null,
            localArticleCount = 0,
            keepDays = keepDays,
        ) { _, _ -> }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `incremental sync stops when all newer items exhausted`() = runTest {
        val firstPage = FeverResponse(
            auth = 1,
            totalItems = 100,
            items = listOf(
                item(id = 50, secondsOld = inWindow),
                item(id = 49, secondsOld = inWindow),
            ),
        )
        service.responses = ArrayDeque(
            listOf(
                FeverResponse(auth = 1, totalItems = 100, items = emptyList())
            )
        )

        val result = repo.fetchPagedItems(
            connection = connection(),
            firstPage = firstPage,
            startingAfterId = 40L,
            oldestLocalId = null,
            localArticleCount = 100,
            keepDays = keepDays,
        ) { _, _ -> }

        assertEquals(listOf<Long>(50, 49), result.map { it.id })
    }

    private class StubFeverApiService : FeverApiService {
        var responses: ArrayDeque<FeverResponse> = ArrayDeque()

        override suspend fun query(
            apiKey: String,
            groups: String?,
            feeds: String?,
            items: String?,
            sinceId: Long?,
            maxId: Long?,
            unreadItemIds: String?,
            savedItemIds: String?,
            favicons: String?,
            mark: String?,
            actionState: String?,
            id: Long?,
            before: Long?,
        ): FeverResponse {
            return responses.removeFirstOrNull()
                ?: FeverResponse(auth = 1, items = emptyList())
        }
    }
}
