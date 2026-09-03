package co.chinho.readabilityreader.worker

import android.content.Context
import android.util.Log
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerParameters
import androidx.work.impl.utils.futures.SettableFuture
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.domain.usecase.BackfillSnippetsUseCase
import co.chinho.readabilityreader.domain.usecase.EvictCacheUseCase
import co.chinho.readabilityreader.domain.usecase.FlushJottyQueueUseCase
import co.chinho.readabilityreader.domain.usecase.RewarmMissingThumbnailsUseCase
import co.chinho.readabilityreader.domain.usecase.SyncUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArticleSyncWorkerTest {

    private lateinit var context: Context
    private lateinit var params: WorkerParameters
    private lateinit var syncUseCase: SyncUseCase
    private lateinit var evictCacheUseCase: EvictCacheUseCase
    private lateinit var rewarmMissingThumbnailsUseCase: RewarmMissingThumbnailsUseCase
    private lateinit var backfillSnippetsUseCase: BackfillSnippetsUseCase
    private lateinit var flushJottyQueueUseCase: FlushJottyQueueUseCase
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0

        context = mockk(relaxed = true)
        params = mockk(relaxed = true)
        syncUseCase = mockk()
        evictCacheUseCase = mockk(relaxed = true)
        rewarmMissingThumbnailsUseCase = mockk(relaxed = true)
        backfillSnippetsUseCase = mockk(relaxed = true)
        flushJottyQueueUseCase = mockk(relaxed = true)
        userPreferencesRepository = mockk()

        val completedFuture = SettableFuture.create<Void?>().apply { set(null) }
        val progressUpdater = mockk<ProgressUpdater> {
            every { updateProgress(any(), any(), any()) } returns completedFuture
        }
        val foregroundUpdater = mockk<ForegroundUpdater> {
            every { setForegroundAsync(any(), any(), any()) } returns completedFuture
        }
        every { params.progressUpdater } returns progressUpdater
        every { params.foregroundUpdater } returns foregroundUpdater

        every { userPreferencesRepository.cacheDurationDays } returns flowOf(5)
        every { userPreferencesRepository.wifiOnlySync } returns flowOf(true)
        every { userPreferencesRepository.chargingOnlySync } returns flowOf(false)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun testDoWorkRethrowsCancellationException() = runBlocking {
        coEvery { syncUseCase(any(), any()) } throws CancellationException("Sync job was cancelled")

        val worker = ArticleSyncWorker(
            context = context,
            params = params,
            syncUseCase = syncUseCase,
            evictCacheUseCase = evictCacheUseCase,
            rewarmMissingThumbnailsUseCase = rewarmMissingThumbnailsUseCase,
            backfillSnippetsUseCase = backfillSnippetsUseCase,
            flushJottyQueueUseCase = flushJottyQueueUseCase,
            userPreferencesRepository = userPreferencesRepository,
        )

        var thrown: Throwable? = null
        try {
            worker.doWork()
        } catch (t: Throwable) {
            thrown = t
        }

        assertTrue("Expected CancellationException, got $thrown", thrown is CancellationException)
    }

    @Test
    fun testDoWorkReturnsRetryOnIOException() = runBlocking {
        coEvery { syncUseCase(any(), any()) } throws IOException("Network connection dropped")

        val worker = ArticleSyncWorker(
            context = context,
            params = params,
            syncUseCase = syncUseCase,
            evictCacheUseCase = evictCacheUseCase,
            rewarmMissingThumbnailsUseCase = rewarmMissingThumbnailsUseCase,
            backfillSnippetsUseCase = backfillSnippetsUseCase,
            flushJottyQueueUseCase = flushJottyQueueUseCase,
            userPreferencesRepository = userPreferencesRepository,
        )

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
