package co.chinho.readabilityreader.data.repository

import co.chinho.readabilityreader.util.DebugSessionRecorder
import okhttp3.Interceptor
import okhttp3.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostReachabilityInterceptor @Inject constructor(
    private val tracker: HostReachabilityTracker,
    private val debugSessionRecorder: DebugSessionRecorder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        if (tracker.isFailing(host)) {
            debugSessionRecorder.logEvent("[host-fast-fail] host=$host url=${request.url}")
            throw UnknownHostException("host marked unreachable: $host")
        }

        return try {
            val response = chain.proceed(request)
            if (response.isSuccessful) {
                tracker.markReachable(host)
            }
            response
        } catch (e: Exception) {
            if (isNetworkFailure(e)) {
                tracker.markFailing(host)
                debugSessionRecorder.logEvent("[host-mark-failing] host=$host cause=${e::class.simpleName}")
            }
            throw e
        }
    }

    private fun isNetworkFailure(throwable: Throwable): Boolean {
        var t: Throwable? = throwable
        while (t != null) {
            if (t is UnknownHostException || t is ConnectException || t is SocketTimeoutException) {
                return true
            }
            t = t.cause
        }
        return false
    }
}
