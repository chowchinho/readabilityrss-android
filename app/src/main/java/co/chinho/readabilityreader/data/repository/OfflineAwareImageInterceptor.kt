package co.chinho.readabilityreader.data.repository

import android.util.Log
import coil.intercept.Interceptor
import coil.request.CachePolicy
import coil.request.ImageResult
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineAwareImageInterceptor @Inject constructor(
    private val connectivityMonitor: ConnectivityMonitor,
    private val hostReachabilityTracker: HostReachabilityTracker,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        val host = (data as? String)?.let(::hostOf)
        val androidOffline = !connectivityMonitor.isOnline()
        val hostFailing = host != null && hostReachabilityTracker.isFailing(host)

        val request = if (androidOffline || hostFailing) {
            Log.d(
                "ArticleImageCache",
                "image request forced offline androidOffline=$androidOffline hostFailing=$hostFailing data=$data",
            )
            chain.request.newBuilder()
                .networkCachePolicy(CachePolicy.DISABLED)
                .build()
        } else {
            chain.request
        }
        return chain.proceed(request)
    }

    private fun hostOf(url: String): String? {
        return runCatching { URI(url).host }.getOrNull()
    }
}
