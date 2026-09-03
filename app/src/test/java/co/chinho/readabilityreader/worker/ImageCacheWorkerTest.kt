package co.chinho.readabilityreader.worker

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerParameters
import androidx.work.impl.utils.futures.SettableFuture
import co.chinho.readabilityreader.data.local.dao.ArticleImageDao
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoilApi::class)
class ImageCacheWorkerTest {

    private lateinit var context: Context
    private lateinit var params: WorkerParameters
    private lateinit var articleImageDao: ArticleImageDao
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var coilImageLoader: ImageLoader
    private lateinit var diskCache: DiskCache

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0

        context = ApplicationProvider.getApplicationContext()
        params = mockk(relaxed = true)
        articleImageDao = mockk()
        userPreferencesRepository = mockk()
        coilImageLoader = mockk(relaxed = true)
        diskCache = mockk(relaxed = true)

        val progressUpdater = mockk<ProgressUpdater> {
            every { updateProgress(any(), any(), any()) } answers {
                SettableFuture.create<Void?>().apply { set(null) }
            }
        }
        every { params.progressUpdater } returns progressUpdater

        every { userPreferencesRepository.cacheDurationDays } returns flowOf(5)
        every { coilImageLoader.diskCache } returns diskCache
        every { diskCache.openSnapshot(any()) } answers {
            mockk(relaxed = true)
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun testPagingAccumulatesAllUrlsAcrossMultiplePages() = runBlocking {
        val totalUrls = 4500
        val allUrls = (0 until totalUrls).map { "https://example.com/image_$it.jpg" }

        coEvery {
            articleImageDao.getWarmableUrls(any(), any(), limit = 2000, offset = any())
        } answers {
            val limit = arg<Int>(2)
            val offset = arg<Int>(3)
            allUrls.drop(offset).take(limit)
        }

        val checkedUrls = mutableListOf<String>()
        every { diskCache.openSnapshot(any()) } answers {
            checkedUrls.add(firstArg())
            mockk(relaxed = true)
        }

        val worker = ImageCacheWorker(
            context = context,
            params = params,
            articleImageDao = articleImageDao,
            userPreferencesRepository = userPreferencesRepository,
            coilImageLoader = coilImageLoader,
        )

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(4500, checkedUrls.size)
        assertEquals(allUrls, checkedUrls)
        assertEquals(allUrls.toSet().size, checkedUrls.size)

        coVerify(exactly = 1) { articleImageDao.getWarmableUrls(any(), any(), limit = 2000, offset = 0) }
        coVerify(exactly = 1) { articleImageDao.getWarmableUrls(any(), any(), limit = 2000, offset = 2000) }
        coVerify(exactly = 1) { articleImageDao.getWarmableUrls(any(), any(), limit = 2000, offset = 4000) }
        coVerify(exactly = 3) { articleImageDao.getWarmableUrls(any(), any(), limit = 2000, offset = any()) }
    }

    @Test
    fun testEmptyTableTerminatesLoopImmediately() = runBlocking {
        coEvery {
            articleImageDao.getWarmableUrls(any(), any(), limit = 2000, offset = 0)
        } returns emptyList()

        val worker = ImageCacheWorker(
            context = context,
            params = params,
            articleImageDao = articleImageDao,
            userPreferencesRepository = userPreferencesRepository,
            coilImageLoader = coilImageLoader,
        )

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) {
            articleImageDao.getWarmableUrls(any(), any(), limit = 2000, offset = 0)
        }
        coVerify(exactly = 1) {
            articleImageDao.getWarmableUrls(any(), any(), any(), any())
        }
    }
}
