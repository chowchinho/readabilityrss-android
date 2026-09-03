package co.chinho.readabilityreader.util

import android.content.Context
import android.util.Log
import co.chinho.readabilityreader.data.repository.ConnectivityMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class DebugSessionRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectivityMonitor: ConnectivityMonitor,
) {

    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var activeSession: ActiveSession? = null

    fun start(durationMs: Long = DEFAULT_DURATION_MS) {
        synchronized(this) {
            if (activeSession != null) return
            val file = File(sessionDir(), "debug-${fileStamp()}.log")
            val session = ActiveSession(file = file, startedAt = System.currentTimeMillis(), endsAt = System.currentTimeMillis() + durationMs)
            activeSession = session
            _state.value = State(
                isActive = true,
                currentSession = session.file,
                lastSession = _state.value.lastSession,
                endsAtMs = session.endsAt,
            )
            try {
                file.parentFile?.mkdirs()
                file.appendText(header())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open session file", e)
            }

            session.tickJob = recorderScope.launch {
                while (isActive && System.currentTimeMillis() < session.endsAt) {
                    delay(TICK_MS)
                    write("[tick] isOffline=${connectivityMonitor.isOffline.value}")
                }
                stop()
            }
            session.connectivityJob = recorderScope.launch {
                connectivityMonitor.isOffline.collectLatest { offline ->
                    write("[connectivity] isOffline=$offline")
                }
            }
        }
    }

    fun stop() {
        val ending: ActiveSession = synchronized(this) {
            val current = activeSession ?: return
            activeSession = null
            current
        }
        ending.tickJob?.cancel()
        ending.connectivityJob?.cancel()
        runCatching { ending.file.appendText("=== session end ${nowStamp()} ===\n") }
        _state.value = State(
            isActive = false,
            currentSession = null,
            lastSession = ending.file,
            endsAtMs = 0L,
        )
    }

    fun logImage(url: String, source: String, result: String, elapsedMs: Long) {
        if (activeSession == null) return
        val trimmedUrl = url.take(MAX_URL_LEN)
        write("[image] result=$result src=$source elapsedMs=$elapsedMs url=$trimmedUrl")
    }

    fun logCrash(threadName: String, throwable: Throwable) {
        if (activeSession == null) return
        write("[uncaught] thread=$threadName ${throwable::class.simpleName}: ${throwable.message}")
    }

    fun logEvent(line: String) {
        if (activeSession == null) return
        write(line)
    }

    fun shutdown() {
        stop()
        recorderScope.cancel()
    }

    private fun sessionDir(): File = File(context.filesDir, "debug-sessions")

    private fun write(line: String) {
        val target = activeSession?.file ?: return
        try {
            target.appendText("${nowStamp()} $line\n")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write session log", e)
        }
    }

    private fun header(): String = buildString {
        append("=== Debug session start ").append(nowStamp()).append(" ===\n")
        append("initial isOffline=").append(connectivityMonitor.isOffline.value).append("\n")
    }

    private fun nowStamp(): String = TS_FMT.format(Date())
    private fun fileStamp(): String = FILE_FMT.format(Date())

    data class State(
        val isActive: Boolean = false,
        val currentSession: File? = null,
        val lastSession: File? = null,
        val endsAtMs: Long = 0L,
    )

    private class ActiveSession(
        val file: File,
        val startedAt: Long,
        val endsAt: Long,
    ) {
        var tickJob: Job? = null
        var connectivityJob: Job? = null
    }

    private companion object {
        const val TAG = "DebugSession"
        const val DEFAULT_DURATION_MS: Long = 5 * 60 * 1000L
        const val TICK_MS: Long = 5_000L
        const val MAX_URL_LEN = 220
        val TS_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val FILE_FMT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
