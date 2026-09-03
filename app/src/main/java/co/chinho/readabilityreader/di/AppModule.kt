package co.chinho.readabilityreader.di

import android.content.Context
import androidx.work.WorkManager
import co.chinho.readabilityreader.data.imageloading.DebugEventListener
import co.chinho.readabilityreader.data.repository.OfflineAwareImageInterceptor
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val IMAGE_LOADER_PREF_TIMEOUT_MS = 1000L
    // Sourced from UserPreferencesRepositoryImpl.DEFAULT_MAX_CACHE_SIZE_MB / Settings default (500 MB).
    private const val DEFAULT_MAX_CACHE_SIZE_MB = 500

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @ImageOkHttpClient imageOkHttpClient: OkHttpClient,
        userPreferencesRepository: UserPreferencesRepository,
        offlineAwareImageInterceptor: OfflineAwareImageInterceptor,
        debugEventListener: DebugEventListener,
    ): ImageLoader {
        // DataStore has no synchronous read and the disk cache size is fixed at build time, so this
        // one read is unavoidable here. Bounded so a slow or contended DataStore cannot stall the
        // first frame; the default matches the Settings default.
        val maxCacheSizeMb = runBlocking {
            withTimeoutOrNull(IMAGE_LOADER_PREF_TIMEOUT_MS) {
                userPreferencesRepository.maxCacheSizeMb.first()
            }
        } ?: DEFAULT_MAX_CACHE_SIZE_MB
        val diskCacheBuilder = DiskCache.Builder()
            .directory(context.cacheDir.resolve("image_cache"))
        val sizedDiskCache = if (maxCacheSizeMb <= 0) {
            diskCacheBuilder.maxSizePercent(0.20)
        } else {
            diskCacheBuilder.maxSizeBytes(maxCacheSizeMb.toLong() * 1024L * 1024L)
        }
        return ImageLoader.Builder(context)
            .okHttpClient(imageOkHttpClient)
            .respectCacheHeaders(false)
            .components { add(offlineAwareImageInterceptor) }
            .eventListener(debugEventListener)
            .fetcherDispatcher(Dispatchers.IO.limitedParallelism(IMAGE_FETCHER_PARALLELISM))
            .decoderDispatcher(Dispatchers.IO.limitedParallelism(IMAGE_DECODER_PARALLELISM))
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache { sizedDiskCache.build() }
            .build()
    }

    private const val IMAGE_FETCHER_PARALLELISM = 4
    private const val IMAGE_DECODER_PARALLELISM = 4

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
