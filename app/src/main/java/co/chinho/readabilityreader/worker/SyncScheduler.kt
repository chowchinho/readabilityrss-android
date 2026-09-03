package co.chinho.readabilityreader.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler(
    private val workManager: WorkManager,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(WorkManager.getInstance(context))

    fun schedulePeriodicSync(intervalHours: Int, wifiOnly: Boolean, chargingOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresCharging(chargingOnly)
            .build()

        val request = PeriodicWorkRequestBuilder<ArticleSyncWorker>(
            intervalHours.toLong(),
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .addTag(PERIODIC_SYNC_WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun triggerOneTimeSync() {
        val request = OneTimeWorkRequestBuilder<ArticleSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(SYNC_WORK_TAG)
            .build()

        // ExistingWorkPolicy rationale:
        // - KEEP silently drops a refresh issued while a sync runs — the user acts, nothing happens.
        // - REPLACE cancels a sync mid-flight; repeated refreshes could starve it of ever completing.
        // - APPEND_OR_REPLACE queues behind the running one: no overlap, no lost request, no cancellation,
        //   and it self-heals if the previous work was cancelled or failed.
        workManager.enqueueUniqueWork(
            ONE_TIME_SYNC_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
    }

    companion object {
        const val ONE_TIME_SYNC_WORK_NAME = "readability_one_time_sync"
        const val PERIODIC_SYNC_WORK_NAME = "readability_periodic_sync"
        const val SYNC_WORK_TAG = "sync_articles"
        const val PERIODIC_SYNC_WORK_TAG = "periodic_sync_articles"
        const val IMAGE_CACHE_WORK_TAG = "image_cache_work"
        const val IMAGE_CACHE_WORK_NAME = "image_cache_work_unique"
    }
}


