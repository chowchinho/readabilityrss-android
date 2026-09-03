package co.chinho.readabilityreader.data.repository

import android.util.Log
import co.chinho.readabilityreader.domain.repository.ArticleRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Singleton
class ReadStateFlushObserver @Inject constructor(
    private val connectivityMonitor: ConnectivityMonitor,
    private val articleRepository: ArticleRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            connectivityMonitor.isOffline
                .filter { offline -> !offline }
                .collect {
                    try {
                        articleRepository.flushReadStateQueue()
                    } catch (e: Exception) {
                        Log.w("ReadStateFlush", "flush on reconnect failed: ${e.message}")
                    }
                }
        }
    }
}
