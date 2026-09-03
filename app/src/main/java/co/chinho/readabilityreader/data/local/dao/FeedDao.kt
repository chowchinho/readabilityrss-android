package co.chinho.readabilityreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import co.chinho.readabilityreader.data.local.entity.FeedEntity
import co.chinho.readabilityreader.data.local.model.FeedSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query(
        """
        SELECT
            feeds.id AS id,
            feeds.groupId AS groupId,
            feeds.title AS title,
            feeds.url AS url,
            feeds.siteUrl AS siteUrl,
            feeds.faviconId AS faviconId,
            feeds.faviconUrl AS faviconUrl,
            feeds.faviconProxyUrl AS faviconProxyUrl,
            feeds.lastSyncedAt AS lastSyncedAt,
            feeds.listViewMode AS listViewMode,
            COUNT(articles.id) AS totalArticleCount,
            COUNT(CASE WHEN articles.isRead = 0 THEN 1 END) AS unreadCount
        FROM feeds
        LEFT JOIN articles ON articles.feedId = feeds.id
        GROUP BY
            feeds.id,
            feeds.groupId,
            feeds.title,
            feeds.url,
            feeds.siteUrl,
            feeds.faviconId,
            feeds.faviconUrl,
            feeds.faviconProxyUrl,
            feeds.lastSyncedAt,
            feeds.listViewMode
        ORDER BY feeds.title COLLATE NOCASE
        """
    )
    fun observeFeedSummaries(): Flow<List<FeedSummaryEntity>>

    @Query(
        """
        SELECT
            feeds.id AS id,
            feeds.groupId AS groupId,
            feeds.title AS title,
            feeds.url AS url,
            feeds.siteUrl AS siteUrl,
            feeds.faviconId AS faviconId,
            feeds.faviconUrl AS faviconUrl,
            feeds.faviconProxyUrl AS faviconProxyUrl,
            feeds.lastSyncedAt AS lastSyncedAt,
            feeds.listViewMode AS listViewMode,
            COUNT(articles.id) AS totalArticleCount,
            COUNT(CASE WHEN articles.isRead = 0 THEN 1 END) AS unreadCount
        FROM feeds
        LEFT JOIN articles ON articles.feedId = feeds.id
        WHERE feeds.id = :feedId
        GROUP BY
            feeds.id,
            feeds.groupId,
            feeds.title,
            feeds.url,
            feeds.siteUrl,
            feeds.faviconId,
            feeds.faviconUrl,
            feeds.faviconProxyUrl,
            feeds.lastSyncedAt,
            feeds.listViewMode
        """
    )
    fun observeFeedSummary(feedId: Long): Flow<FeedSummaryEntity?>

    /**
     * Insert-or-update feeds while preserving user-set listViewMode.
     * @Upsert would replace the entire row, resetting listViewMode to "standard" on every sync.
     */
    @Transaction
    suspend fun upsertFeeds(feeds: List<FeedEntity>) {
        insertFeedsIgnoreConflict(feeds)
        feeds.forEach { feed ->
            updateFeedSyncFields(
                id = feed.id,
                groupId = feed.groupId,
                title = feed.title,
                url = feed.url,
                siteUrl = feed.siteUrl,
                faviconId = feed.faviconId,
                faviconUrl = feed.faviconUrl,
                faviconProxyUrl = feed.faviconProxyUrl,
                lastSyncedAt = feed.lastSyncedAt,
            )
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFeedsIgnoreConflict(feeds: List<FeedEntity>)

    @Query(
        """
        UPDATE feeds
        SET groupId = :groupId,
            title = :title,
            url = :url,
            siteUrl = :siteUrl,
            faviconId = :faviconId,
            faviconUrl = :faviconUrl,
            faviconProxyUrl = :faviconProxyUrl,
            lastSyncedAt = :lastSyncedAt
        WHERE id = :id
        """
    )
    suspend fun updateFeedSyncFields(
        id: Long,
        groupId: Long,
        title: String,
        url: String,
        siteUrl: String?,
        faviconId: Long?,
        faviconUrl: String?,
        faviconProxyUrl: String?,
        lastSyncedAt: Long?,
    )

    @Query("UPDATE feeds SET listViewMode = :viewMode WHERE id = :feedId")
    suspend fun updateViewMode(feedId: Long, viewMode: String)

    @Query("SELECT MAX(lastSyncedAt) FROM feeds")
    fun observeLastSyncedAt(): Flow<Long?>

    @Query("DELETE FROM feeds")
    suspend fun clearAll()
}
