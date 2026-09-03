package co.chinho.readabilityreader.di

import android.content.Context
import co.chinho.readabilityreader.data.imageloading.DebugEventListener
import co.chinho.readabilityreader.data.repository.OfflineAwareImageInterceptor
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppModuleTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val userPreferencesRepository = mockk<UserPreferencesRepository>()
    private val offlineAwareImageInterceptor = mockk<OfflineAwareImageInterceptor>(relaxed = true)
    private val debugEventListener = mockk<DebugEventListener>(relaxed = true)
    private val okHttpClient = OkHttpClient()

    @Test
    fun `provideImageLoader returns loader when preference emits normally`() {
        val context = mockContext()
        every { userPreferencesRepository.maxCacheSizeMb } returns flowOf(250)

        val imageLoader = AppModule.provideImageLoader(
            context = context,
            imageOkHttpClient = okHttpClient,
            userPreferencesRepository = userPreferencesRepository,
            offlineAwareImageInterceptor = offlineAwareImageInterceptor,
            debugEventListener = debugEventListener,
        )

        assertNotNull(imageLoader)
    }

    @Test(timeout = 5000)
    fun `provideImageLoader falls back to default cache size without hanging when preference flow never emits`() {
        val context = mockContext()
        val neverEmittingFlow = flow<Int> {
            suspendCancellableCoroutine<Unit> { }
        }
        every { userPreferencesRepository.maxCacheSizeMb } returns neverEmittingFlow

        val imageLoader = AppModule.provideImageLoader(
            context = context,
            imageOkHttpClient = okHttpClient,
            userPreferencesRepository = userPreferencesRepository,
            offlineAwareImageInterceptor = offlineAwareImageInterceptor,
            debugEventListener = debugEventListener,
        )

        assertNotNull(imageLoader)
    }

    private fun mockContext(): Context {
        val cacheDir = tempFolder.newFolder("app_cache")
        val context = mockk<Context>(relaxed = true)
        every { context.cacheDir } returns cacheDir
        every { context.applicationContext } returns context
        return context
    }
}
