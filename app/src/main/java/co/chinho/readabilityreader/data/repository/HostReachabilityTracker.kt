package co.chinho.readabilityreader.data.repository

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostReachabilityTracker @Inject constructor() {

    private val failureExpiryMs = ConcurrentHashMap<String, Long>()

    fun markFailing(host: String) {
        if (host.isEmpty()) return
        failureExpiryMs[host] = System.currentTimeMillis() + UNREACHABLE_TTL_MS
    }

    fun markReachable(host: String) {
        if (host.isEmpty()) return
        failureExpiryMs.remove(host)
    }

    fun isFailing(host: String): Boolean {
        if (host.isEmpty()) return false
        val expiry = failureExpiryMs[host] ?: return false
        if (System.currentTimeMillis() < expiry) return true
        failureExpiryMs.remove(host)
        return false
    }

    fun clearAll() {
        failureExpiryMs.clear()
    }

    companion object {
        const val UNREACHABLE_TTL_MS = 30_000L
    }
}
