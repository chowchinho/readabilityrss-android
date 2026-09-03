package co.chinho.readabilityreader.worker

import androidx.work.WorkInfo
import androidx.work.WorkManager
import co.chinho.readabilityreader.data.repository.ConnectivityMonitor
import co.chinho.readabilityreader.domain.repository.ArticleRepository
import co.chinho.readabilityreader.domain.repository.FeedRepository
import co.chinho.readabilityreader.ui.components.SyncStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class SyncStatusObserver @Inject constructor(
    private val workManager: WorkManager,
    private val articleRepository: ArticleRepository,
    private val feedRepository: FeedRepository,
    private val connectivityMonitor: ConnectivityMonitor,
) {
    val syncStatus: Flow<SyncStatus> = combine(
        workManager.getWorkInfosByTagFlow(SyncScheduler.SYNC_WORK_TAG),
        workManager.getWorkInfosByTagFlow(SyncScheduler.PERIODIC_SYNC_WORK_TAG),
        workManager.getWorkInfosByTagFlow(SyncScheduler.IMAGE_CACHE_WORK_TAG),
        articleRepository.getArticleCount(),
        connectivityMonitor.isOffline,
        feedRepository.getLastSyncedAt(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val oneTime = values[0] as List<WorkInfo>
        @Suppress("UNCHECKED_CAST")
        val periodic = values[1] as List<WorkInfo>
        @Suppress("UNCHECKED_CAST")
        val imageCache = values[2] as List<WorkInfo>
        val articleCount = values[3] as Int
        val offline = values[4] as Boolean
        val lastSyncedAt = values[5] as Long?

        val workInfos = oneTime + periodic
        val activeSync = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
            ?: oneTime.firstOrNull { it.state == WorkInfo.State.ENQUEUED }
        val syncedCount = activeSync?.progress?.getInt(ArticleSyncWorker.KEY_SYNCED_COUNT, 0) ?: 0
        val totalToSync = activeSync?.progress?.getInt(ArticleSyncWorker.KEY_TOTAL_COUNT, 0) ?: 0
        val stage = activeSync?.progress?.getString(ArticleSyncWorker.KEY_STAGE)
        val hasCompletedSync = workInfos.any { it.state == WorkInfo.State.SUCCEEDED }
        val activeImage = imageCache.firstOrNull { it.state == WorkInfo.State.RUNNING }

        SyncStatus(
            isSyncing = activeSync != null,
            syncedCount = syncedCount,
            totalCount = if (activeSync != null && totalToSync > 0) totalToSync else articleCount,
            stage = stage,
            isOffline = offline,
            hasCompletedSync = hasCompletedSync,
            lastSyncedAt = lastSyncedAt,
            checkedImages = activeImage?.progress?.getInt(ImageCacheWorker.KEY_CHECKED, 0) ?: 0,
            fetchedImages = activeImage?.progress?.getInt(ImageCacheWorker.KEY_FETCHED, 0) ?: 0,
            totalImages = activeImage?.progress?.getInt(ImageCacheWorker.KEY_DOWNLOAD_TOTAL, 0) ?: 0,
        )
    }
}
