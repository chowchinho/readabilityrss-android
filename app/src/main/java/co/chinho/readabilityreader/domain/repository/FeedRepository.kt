package co.chinho.readabilityreader.domain.repository

import co.chinho.readabilityreader.domain.model.Feed
import co.chinho.readabilityreader.domain.model.Group
import kotlinx.coroutines.flow.Flow

interface FeedRepository {
    fun getFeeds(): Flow<List<Feed>>

    fun getGroups(): Flow<List<Group>>

    fun getFeed(feedId: Long): Flow<Feed?>

    fun getLastSyncedAt(): Flow<Long?>

    suspend fun updateFeedViewMode(feedId: Long, viewMode: String)
}
