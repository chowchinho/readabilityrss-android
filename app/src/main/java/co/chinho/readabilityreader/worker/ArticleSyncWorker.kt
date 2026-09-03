package co.chinho.readabilityreader.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.domain.usecase.BackfillSnippetsUseCase
import co.chinho.readabilityreader.domain.usecase.EvictCacheUseCase
import co.chinho.readabilityreader.domain.usecase.FlushJottyQueueUseCase
import co.chinho.readabilityreader.domain.usecase.RewarmMissingThumbnailsUseCase
import co.chinho.readabilityreader.domain.usecase.SyncUseCase
import co.chinho.readabilityreader.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class ArticleSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncUseCase: SyncUseCase,
    private val evictCacheUseCase: EvictCacheUseCase,
    private val rewarmMissingThumbnailsUseCase: RewarmMissingThumbnailsUseCase,
    private val backfillSnippetsUseCase: BackfillSnippetsUseCase,
    private val flushJottyQueueUseCase: FlushJottyQueueUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
) : CoroutineWorker(context, params) {

    private var lastForegroundUpdateMs = 0L
    private var lastForegroundPercent = -1

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            FOREGROUND_NOTIFICATION_ID,
            buildNotification(
                title = "Syncing articles",
                text = "Preparing article sync",
                percent = 0,
                indeterminate = true,
                ongoing = true,
                autoCancel = false,
            ),
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }

    override suspend fun doWork(): Result {
        return try {
            val keepDays = userPreferencesRepository.cacheDurationDays.first()
            val wifiOnlySync = userPreferencesRepository.wifiOnlySync.first()
            val chargingOnlySync = userPreferencesRepository.chargingOnlySync.first()

            updateForeground(
                stage = STAGE_SYNC_START,
                title = "Syncing articles",
                text = "Preparing article sync",
                percent = 0,
                indeterminate = true,
            )
            // Runs before the network sync: it needs no connection, and a sync that throws
            // must not leave existing rows without a snippet, which is now the only text the
            // article list has.
            runCatching { backfillSnippetsUseCase() }
                .onSuccess { count ->
                    if (count > 0) Log.i(TAG, "Snippet backfill: updated $count articles")
                }
                .onFailure { Log.w(TAG, "Snippet backfill pass failed", it) }

            val syncedCount = syncUseCase(keepDays) { current, total ->
                val percent = if (total > 0) (current * 100 / total) else 0
                throttledUpdateForeground(
                    stage = STAGE_SYNC_DOWNLOAD,
                    title = "Syncing articles",
                    text = "Scanning server articles ($current/$total)",
                    percent = percent,
                    current = current,
                    total = total,
                )
            }

            updateForeground(
                stage = STAGE_SYNC_PROCESS,
                title = "Syncing articles",
                text = "Saving articles to database",
                percent = 80,
                current = syncedCount,
                total = syncedCount,
            )

            val imageCacheConstraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnlySync) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresCharging(chargingOnlySync)
                .build()

            val imageCacheRequest = OneTimeWorkRequestBuilder<ImageCacheWorker>()
                .setConstraints(imageCacheConstraints)
                .addTag(SyncScheduler.IMAGE_CACHE_WORK_TAG)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                SyncScheduler.IMAGE_CACHE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                imageCacheRequest,
            )

            updateForeground(
                stage = STAGE_SYNC_REWARM,
                title = "Syncing articles",
                text = "Filling missing thumbnails",
                percent = 85,
            )
            runCatching { rewarmMissingThumbnailsUseCase() }
                .onFailure { Log.w(TAG, "Thumbnail rewarm pass failed", it) }

            setProgress(workDataOf(KEY_STAGE to STAGE_SYNC_EVICT, KEY_PERCENT to 90))
            updateForeground(
                stage = STAGE_SYNC_EVICT,
                title = "Syncing articles",
                text = "Cleaning cached data",
                percent = 90,
            )
            evictCacheUseCase(keepDays)

            runCatching { flushJottyQueueUseCase() }
                .onSuccess { summary ->
                    if (summary.sent > 0 || summary.stillPending > 0 || summary.failedHard > 0) {
                        Log.i(TAG, "Jotty flush: sent=${summary.sent} pending=${summary.stillPending} failedHard=${summary.failedHard}")
                    }
                    if (summary.failedHard > 0) {
                        Log.w(TAG, "Jotty flush: ${summary.failedHard} entries exhausted MAX_ATTEMPTS and will not retry")
                    }
                }
                .onFailure { Log.w(TAG, "Jotty queue flush failed", it) }

            setProgress(workDataOf(KEY_STAGE to STAGE_SYNC_COMPLETE, KEY_PERCENT to 100))
            updateForeground(
                stage = STAGE_SYNC_COMPLETE,
                title = "Syncing articles",
                text = "Finishing sync",
                percent = 100,
            )
            postCompletionNotification(syncedCount)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Log.w(TAG, "Article sync failed", t)
            Result.retry()
        }
    }

    private suspend fun throttledUpdateForeground(
        stage: String,
        title: String,
        text: String,
        percent: Int,
        current: Int? = null,
        total: Int? = null,
    ) {
        val now = System.currentTimeMillis()
        if (percent == lastForegroundPercent && now - lastForegroundUpdateMs < FOREGROUND_UPDATE_THROTTLE_MS) {
            return
        }
        lastForegroundUpdateMs = now
        lastForegroundPercent = percent
        updateForeground(
            stage = stage,
            title = title,
            text = text,
            percent = percent,
            current = current,
            total = total,
        )
    }

    private suspend fun updateForeground(
        stage: String,
        title: String,
        text: String,
        percent: Int,
        indeterminate: Boolean = false,
        current: Int? = null,
        total: Int? = null,
    ) {
        val dataBuilder = androidx.work.Data.Builder()
            .putString(KEY_STAGE, stage)
            .putInt(KEY_PERCENT, percent)
            .putBoolean("is_indeterminate", indeterminate)
        
        current?.let { dataBuilder.putInt(KEY_SYNCED_COUNT, it) }
        total?.let { dataBuilder.putInt(KEY_TOTAL_COUNT, it) }

        setProgress(dataBuilder.build())
        runCatching {
            setForeground(
                ForegroundInfo(
                    FOREGROUND_NOTIFICATION_ID,
                    buildNotification(
                        title = title,
                        text = text,
                        percent = percent,
                        indeterminate = indeterminate,
                        ongoing = true,
                        autoCancel = false,
                    ),
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        0
                    }
                )
            )
        }
    }

    private fun postCompletionNotification(syncedCount: Int) {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return

        val text = if (syncedCount > 0) {
            "Sync complete. $syncedCount new articles downloaded."
        } else {
            "Sync complete. No new articles."
        }

        val notification = buildNotification(
            title = "Sync complete",
            text = text,
            percent = 100,
            indeterminate = false,
            ongoing = false,
            autoCancel = true,
        )

        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(COMPLETION_NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        title: String,
        text: String,
        percent: Int,
        indeterminate: Boolean,
        ongoing: Boolean,
        autoCancel: Boolean,
    ) = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_sync_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setOngoing(ongoing)
        .setAutoCancel(autoCancel)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setContentIntent(buildLaunchIntent())
        .setProgress(if (indeterminate) 0 else 100, if (indeterminate) 0 else percent, indeterminate)
        .build()

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

    companion object {
        private const val TAG = "ArticleSyncWorker"
        const val NOTIFICATION_CHANNEL_ID = "sync_notifications"
        const val FOREGROUND_NOTIFICATION_ID = 2001
        const val COMPLETION_NOTIFICATION_ID = 2002
        const val KEY_STAGE = "stage"
        const val KEY_PERCENT = "percent"
        const val STAGE_SYNC_START = "sync_start"
        const val STAGE_SYNC_DOWNLOAD = "sync_download"
        const val STAGE_SYNC_PROCESS = "sync_process"
        const val STAGE_SYNC_REWARM = "sync_rewarm"
        const val STAGE_SYNC_EVICT = "sync_evict"
        const val STAGE_SYNC_COMPLETE = "sync_complete"
        const val KEY_SYNCED_COUNT = "synced_count"
        const val KEY_TOTAL_COUNT = "total_count"
        private const val FOREGROUND_UPDATE_THROTTLE_MS = 500L
    }
}
