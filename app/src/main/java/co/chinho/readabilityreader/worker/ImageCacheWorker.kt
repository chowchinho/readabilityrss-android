package co.chinho.readabilityreader.worker

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.chinho.readabilityreader.R
import co.chinho.readabilityreader.data.local.dao.ArticleImageDao
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.worker.ArticleSyncWorker.Companion.NOTIFICATION_CHANNEL_ID
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.request.CachePolicy
import coil.request.ImageRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltWorker
@OptIn(ExperimentalCoilApi::class)
class ImageCacheWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val articleImageDao: ArticleImageDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val coilImageLoader: ImageLoader,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val keepDays = userPreferencesRepository.cacheDurationDays.first()
            val nowMillis = System.currentTimeMillis()
            val cutoffMillis = nowMillis - keepDays * MILLIS_PER_DAY
            val cutoffSeconds = cutoffMillis / 1000L

            val urls = readWarmableUrls(cutoffMillis, cutoffSeconds)
            val downloadTotal = urls.size

            val progress = ProgressState(downloadTotal, ::setProgress)
            progress.publishInitial()

            runDownloadPhase(urls, progress)

            postCompletionNotification(progress.fetched)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Log.w(TAG, "Image cache run failed", t)
            Result.retry()
        }
    }

    private suspend fun readWarmableUrls(cutoffMillis: Long, cutoffSeconds: Long): List<String> {
        val all = mutableListOf<String>()
        var offset = 0
        while (true) {
            val page = articleImageDao.getWarmableUrls(
                cutoffMillis = cutoffMillis,
                cutoffSeconds = cutoffSeconds,
                limit = WARM_PAGE_SIZE,
                offset = offset,
            )
            if (page.isEmpty()) break
            all += page
            if (page.size < WARM_PAGE_SIZE) break
            offset += WARM_PAGE_SIZE
        }
        return all
    }

    private suspend fun runDownloadPhase(urls: List<String>, progress: ProgressState) {
        urls.forEach { imageUrl ->
            val snapshot = coilImageLoader.diskCache?.openSnapshot(imageUrl)
            if (snapshot == null) {
                val request = ImageRequest.Builder(applicationContext)
                    .data(imageUrl)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
                coilImageLoader.execute(request)
                progress.checked(fetched = true)
            } else {
                snapshot.close()
                progress.checked(fetched = false)
            }
        }
    }

    private fun postCompletionNotification(fetchedCount: Int) {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return
        if (fetchedCount == 0) return

        val text = "Image cache complete. $fetchedCount new images downloaded."
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync_notification)
            .setContentTitle("Image Sync Complete")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(buildLaunchIntent())
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(IMAGE_SYNC_COMPLETION_NOTIFICATION_ID, notification)
        }
    }

    private fun buildLaunchIntent(): PendingIntent? {
        val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(
            applicationContext.packageName
        ) ?: return null

        return PendingIntent.getActivity(
            applicationContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private class ProgressState(
        val downloadTotal: Int,
        private val setProgressFn: suspend (androidx.work.Data) -> Unit,
    ) {
        private val mutex = Mutex()

        /** URLs walked, cache hits included — this is the progress denominator. */
        var checked = 0
            private set

        /** URLs that were actually absent from disk and pulled over the network. */
        var fetched = 0
            private set

        suspend fun checked(fetched: Boolean) {
            mutex.withLock {
                checked += 1
                if (fetched) this.fetched += 1
                publish()
            }
        }

        suspend fun publishInitial() {
            mutex.withLock {
                publish()
            }
        }

        private suspend fun publish() {
            val percent = if (downloadTotal == 0) 100 else (checked * 100) / downloadTotal
            setProgressFn(
                workDataOf(
                    KEY_TOTAL to downloadTotal,
                    KEY_CACHED to checked,
                    KEY_PERCENT to percent,
                    KEY_DOWNLOAD_TOTAL to downloadTotal,
                    KEY_CHECKED to checked,
                    KEY_FETCHED to fetched,
                )
            )
        }
    }

    companion object {
        const val KEY_TOTAL = "total"
        const val KEY_CACHED = "cached"
        const val KEY_PERCENT = "percent"
        const val KEY_DOWNLOAD_TOTAL = "download_total"
        const val KEY_CHECKED = "checked"
        const val KEY_FETCHED = "fetched"
        private const val TAG = "ImageCacheWorker"
        private const val WARM_PAGE_SIZE = 2000
        private const val IMAGE_SYNC_COMPLETION_NOTIFICATION_ID = 2003
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
