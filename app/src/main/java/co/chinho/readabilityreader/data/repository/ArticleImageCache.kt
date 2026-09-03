package co.chinho.readabilityreader.data.repository

import android.content.Context
import android.util.Log
import coil.annotation.ExperimentalCoilApi
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.ErrorResult
import coil.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
@OptIn(ExperimentalCoilApi::class)
class ArticleImageCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
) {
    suspend fun cacheUrls(urls: Collection<String>): Boolean = coroutineScope {
        val cleaned = urls.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct()
        if (cleaned.isEmpty()) return@coroutineScope true
        cleaned.map { url -> async { cacheUrl(url) } }.awaitAll().all { it }
    }

    fun removeUrls(urls: Collection<String>) {
        val diskCache = imageLoader.diskCache ?: return
        urls.asSequence()
            .map { it.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .forEach { diskCache.remove(it) }
    }

    fun clearAll() {
        runCatching { imageLoader.memoryCache?.clear() }
        runCatching { imageLoader.diskCache?.clear() }
    }

    fun isCached(url: String): Boolean {
        val diskCache = imageLoader.diskCache ?: return false
        val snapshot = runCatching { diskCache.openSnapshot(url) }.getOrNull() ?: return false
        snapshot.close()
        return true
    }

    // Reads Coil's disk-cache state directly. Each cached image is one DiskLruCache entry,
    // whose metadata file is named "<hashedKey>.0" — counting those gives the image count.
    fun diskCacheUsage(): DiskCacheUsage {
        val diskCache = imageLoader.diskCache ?: return DiskCacheUsage(0L, 0L, 0)
        val usedBytes = runCatching { diskCache.size }.getOrDefault(0L)
        val maxBytes = runCatching { diskCache.maxSize }.getOrDefault(0L)
        val imageCount = runCatching {
            java.io.File(diskCache.directory.toString())
                .listFiles()
                ?.count { it.isFile && it.name.endsWith(".0") }
                ?: 0
        }.getOrDefault(0)
        return DiskCacheUsage(usedBytes = usedBytes, maxBytes = maxBytes, imageCount = imageCount)
    }

    data class DiskCacheUsage(
        val usedBytes: Long,
        val maxBytes: Long,
        val imageCount: Int,
    )

    suspend fun cacheUrl(imageUrl: String): Boolean {
        val snapshot = imageLoader.diskCache?.openSnapshot(imageUrl)
        if (snapshot != null) {
            snapshot.close()
            return true
        }

        val result = withTimeoutOrNull(IMAGE_FETCH_TIMEOUT_MS) {
            imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(imageUrl)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
            )
        }
        if (result is ErrorResult) {
            Log.w("ArticleImageCache", "image cache failed url=$imageUrl", result.throwable)
        } else if (result == null) {
            Log.w("ArticleImageCache", "image cache timed out url=$imageUrl")
        }
        return result is SuccessResult
    }

    companion object {
        private const val IMAGE_FETCH_TIMEOUT_MS = 15_000L
        private val IMAGE_REGEX =
            Regex("<img[^>]+src\\s*=\\s*[\"']?([^\"'\\s>]+)", RegexOption.IGNORE_CASE)

        fun extractImageUrls(html: String): List<String> {
            return IMAGE_REGEX.findAll(html)
                .mapNotNull { match ->
                    match.groupValues.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
                }
                .toList()
        }
    }
}
