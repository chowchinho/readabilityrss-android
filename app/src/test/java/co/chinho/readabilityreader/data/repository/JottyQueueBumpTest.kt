package co.chinho.readabilityreader.data.repository

import co.chinho.readabilityreader.data.local.dao.JottyQueueDao
import co.chinho.readabilityreader.data.local.entity.JottyQueueEntity
import co.chinho.readabilityreader.data.remote.jotty.CreateNoteRequest
import co.chinho.readabilityreader.data.remote.jotty.CreateNoteResponse
import co.chinho.readabilityreader.data.remote.jotty.JottyApiService
import co.chinho.readabilityreader.data.remote.jotty.JottyNote
import co.chinho.readabilityreader.data.remote.jotty.JottyNoteFormatter
import co.chinho.readabilityreader.di.JottyServiceFactory
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.repository.CredentialRepository
import co.chinho.readabilityreader.domain.repository.JottyRepository
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class JottyQueueBumpTest {

    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var credentialRepository: CredentialRepository
    private lateinit var jottyServiceFactory: JottyServiceFactory
    private lateinit var jottyApiService: JottyApiService
    private lateinit var noteFormatter: JottyNoteFormatter
    private lateinit var jottyQueueDao: JottyQueueDao
    private lateinit var repository: JottyRepositoryImpl

    private val testArticle = Article(
        id = 123L,
        feedId = 1L,
        title = "Test Title",
        url = "https://example.com/test",
        content = "<p>Test Content</p>",
        publishedAt = 1000L,
        isRead = false,
        isSaved = true,
        thumbnailUrl = null,
        isCached = true,
    )

    @Before
    fun setup() {
        userPreferencesRepository = mockk()
        credentialRepository = mockk()
        jottyServiceFactory = mockk()
        jottyApiService = mockk()
        noteFormatter = mockk()
        jottyQueueDao = mockk(relaxed = true)

        every { userPreferencesRepository.jottyEnabled } returns flowOf(true)
        every { userPreferencesRepository.jottyDefaultCategory } returns flowOf("Articles")
        every { userPreferencesRepository.jottyServerUrl } returns flowOf("https://jotty.example.com")
        coEvery { credentialRepository.getJottyApiKey() } returns "test-api-key"
        every { jottyServiceFactory.create(any()) } returns jottyApiService
        every { noteFormatter.format(any(), any()) } returns JottyNote(
            title = "Test Title",
            content = "# Test Title\n\nTest Content",
            category = "Articles",
        )

        repository = JottyRepositoryImpl(
            userPreferencesRepository = userPreferencesRepository,
            credentialRepository = credentialRepository,
            jottyServiceFactory = jottyServiceFactory,
            noteFormatter = noteFormatter,
            jottyQueueDao = jottyQueueDao,
        )
    }

    @Test
    fun testFirstFailureInsertsRowWithAttempts1AndSnapshotIntact() = runBlocking {
        coEvery { jottyQueueDao.getAttempts(123L) } returns null
        val errorResponse: Response<CreateNoteResponse> = Response.error(
            500,
            "Internal Server Error".toResponseBody("text/plain".toMediaTypeOrNull()),
        )
        coEvery { jottyApiService.createNote(any(), any()) } returns errorResponse

        val result = repository.sendArticle(testArticle)

        assertEquals(JottyRepository.SendResult.Queued("HTTP 500"), result)

        val entitySlot = slot<JottyQueueEntity>()
        coVerify(exactly = 1) { jottyQueueDao.upsert(capture(entitySlot)) }

        val captured = entitySlot.captured
        assertEquals(123L, captured.articleId)
        assertEquals("Test Title", captured.title)
        assertEquals("# Test Title\n\nTest Content", captured.content)
        assertEquals("Articles", captured.category)
        assertEquals(1, captured.attempts)
        assertEquals("HTTP 500", captured.lastErrorMessage)
    }

    @Test
    fun testSecondFailureCallsUpdateAttemptAndDoesNotCallGetPending() = runBlocking {
        coEvery { jottyQueueDao.getAttempts(123L) } returns 1
        val errorResponse: Response<CreateNoteResponse> = Response.error(
            503,
            "Service Unavailable".toResponseBody("text/plain".toMediaTypeOrNull()),
        )
        coEvery { jottyApiService.createNote(any(), any()) } returns errorResponse

        val result = repository.sendArticle(testArticle)

        assertEquals(JottyRepository.SendResult.Queued("HTTP 503"), result)

        coVerify(exactly = 1) {
            jottyQueueDao.updateAttempt(
                articleId = 123L,
                attempts = 2,
                lastAttemptAt = any(),
                error = "HTTP 503",
            )
        }
        coVerify(exactly = 0) { jottyQueueDao.upsert(any()) }
        coVerify(exactly = 0) { jottyQueueDao.getPending(any()) }
    }

    @Test
    fun testFlushOver3FailingEntriesCallsGetAttempts3TimesAndGetPendingOnce() = runBlocking {
        val pendingEntries = listOf(
            JottyQueueEntity(1L, "Title 1", "Content 1", "Articles", attempts = 1, lastAttemptAt = 100L),
            JottyQueueEntity(2L, "Title 2", "Content 2", "Articles", attempts = 2, lastAttemptAt = 200L),
            JottyQueueEntity(3L, "Title 3", "Content 3", "Articles", attempts = 3, lastAttemptAt = 300L),
        )

        coEvery { jottyQueueDao.getPending(10) } returns pendingEntries
        coEvery { jottyQueueDao.getAttempts(1L) } returns 1
        coEvery { jottyQueueDao.getAttempts(2L) } returns 2
        coEvery { jottyQueueDao.getAttempts(3L) } returns 3

        val errorResponse: Response<CreateNoteResponse> = Response.error(
            500,
            "Server error".toResponseBody("text/plain".toMediaTypeOrNull()),
        )
        coEvery { jottyApiService.createNote(any(), any()) } returns errorResponse

        val summary = repository.flushQueue()

        assertEquals(0, summary.sent)
        assertEquals(3, summary.stillPending)
        assertEquals(0, summary.failedHard)

        coVerify(exactly = 1) { jottyQueueDao.getPending(10) }
        coVerify(exactly = 1) { jottyQueueDao.getPending(any()) }
        coVerify(exactly = 1) { jottyQueueDao.getAttempts(1L) }
        coVerify(exactly = 1) { jottyQueueDao.getAttempts(2L) }
        coVerify(exactly = 1) { jottyQueueDao.getAttempts(3L) }
        coVerify(exactly = 3) { jottyQueueDao.getAttempts(any()) }

        coVerify(exactly = 1) { jottyQueueDao.updateAttempt(1L, 2, any(), "HTTP 500") }
        coVerify(exactly = 1) { jottyQueueDao.updateAttempt(2L, 3, any(), "HTTP 500") }
        coVerify(exactly = 1) { jottyQueueDao.updateAttempt(3L, 4, any(), "HTTP 500") }
    }
}
