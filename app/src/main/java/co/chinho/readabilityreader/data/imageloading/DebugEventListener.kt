package co.chinho.readabilityreader.data.imageloading

import co.chinho.readabilityreader.util.DebugSessionRecorder
import coil.EventListener
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugEventListener @Inject constructor(
    private val recorder: DebugSessionRecorder,
) : EventListener {

    private val startTimes = java.util.concurrent.ConcurrentHashMap<Int, Long>()

    override fun onStart(request: ImageRequest) {
        startTimes[System.identityHashCode(request)] = System.currentTimeMillis()
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        val elapsed = elapsedFor(request)
        recorder.logImage(
            url = request.data.toString(),
            source = result.dataSource.name,
            result = "success",
            elapsedMs = elapsed,
        )
    }

    override fun onError(request: ImageRequest, result: ErrorResult) {
        val elapsed = elapsedFor(request)
        recorder.logImage(
            url = request.data.toString(),
            source = "error",
            result = result.throwable::class.simpleName ?: "error",
            elapsedMs = elapsed,
        )
    }

    override fun onCancel(request: ImageRequest) {
        val elapsed = elapsedFor(request)
        recorder.logImage(
            url = request.data.toString(),
            source = "cancel",
            result = "cancel",
            elapsedMs = elapsed,
        )
    }

    private fun elapsedFor(request: ImageRequest): Long {
        val key = System.identityHashCode(request)
        val start = startTimes.remove(key) ?: return -1L
        return System.currentTimeMillis() - start
    }
}
