package co.chinho.readabilityreader.data.repository

import co.chinho.readabilityreader.data.local.dao.FeedDao
import co.chinho.readabilityreader.data.local.dao.GroupDao
import co.chinho.readabilityreader.data.local.mapper.toDomain
import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import co.chinho.readabilityreader.domain.model.Feed
import co.chinho.readabilityreader.domain.model.Group
import co.chinho.readabilityreader.domain.repository.FeedRepository
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map

@Singleton
class FeedRepositoryImpl @Inject constructor(
    private val feedDao: FeedDao,
    private val groupDao: GroupDao,
    private val feverConnectionProvider: FeverConnectionProvider,
    private val userPreferencesRepository: UserPreferencesRepository,
) : FeedRepository {

    override fun getFeeds(): Flow<List<Feed>> {
        return flow {
            val readerBaseUrl = feverConnectionProvider.getActiveConnection()
                ?.serverUrl
                ?.toReaderBaseUrl()

            emitAll(
                combine(
                    feedDao.observeFeedSummaries(),
                    userPreferencesRepository.showReadArticles,
                    userPreferencesRepository.hideEmptyFeedSources,
                ) { summaries, showReadArticles, hideEmptyFeedSources ->
                    summaries
                        .filter { summary ->
                            !hideEmptyFeedSources || if (showReadArticles) {
                                summary.totalArticleCount > 0
                            } else {
                                summary.unreadCount > 0
                            }
                        }
                        .map { summary ->
                            summary.toDomain(
                                faviconUrl = resolveFeedFaviconUrl(
                                    readerBaseUrl = readerBaseUrl,
                                    feedUrl = summary.url,
                                    siteUrl = summary.siteUrl,
                                    faviconUrl = summary.faviconUrl,
                                    faviconProxyUrl = summary.faviconProxyUrl,
                                    feedId = summary.id,
                                )
                            )
                        }
                }
            )
        }
    }

    override fun getFeed(feedId: Long): Flow<Feed?> {
        return feedDao.observeFeedSummary(feedId)
            .map { summary ->
                val readerBaseUrl = feverConnectionProvider.getActiveConnection()
                    ?.serverUrl
                    ?.toReaderBaseUrl()

                summary?.toDomain(
                    faviconUrl = resolveFeedFaviconUrl(
                        readerBaseUrl = readerBaseUrl,
                        feedUrl = summary.url,
                        siteUrl = summary.siteUrl,
                        faviconUrl = summary.faviconUrl,
                        faviconProxyUrl = summary.faviconProxyUrl,
                        feedId = summary.id,
                    )
                )
            }
    }

    override fun getGroups(): Flow<List<Group>> {
        return combine(
            groupDao.observeGroups(),
            getFeeds(),
            userPreferencesRepository.feedCategoryOrder,
        ) { groupEntities, allFeeds, categoryOrder ->
            android.util.Log.d("FeedRepo", "Combining ${groupEntities.size} groups and ${allFeeds.size} feeds")
            val groups = groupEntities
                .map { group -> group.toDomain(allFeeds) }
                .filter { group -> group.feeds.isNotEmpty() }
                .toMutableList()
            
            // Find feeds that don't belong to any group we just mapped
            val groupedFeedIds = groups.flatMap { g -> g.feeds.map { it.id } }.toSet()
            val uncategorizedFeeds = allFeeds.filter { it.id !in groupedFeedIds }
            
            if (uncategorizedFeeds.isNotEmpty()) {
                groups.add(
                    Group(
                        id = -1, // Synthetic ID
                        title = "Uncategorized",
                        feeds = uncategorizedFeeds
                    )
                )
            }

            val fallbackOrder = groups.mapIndexed { index, group -> group.id to index }.toMap()

            groups.sortedWith(
                compareBy<Group> { categoryOrder.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                    .thenBy { fallbackOrder[it.id] ?: Int.MAX_VALUE }
            )
        }
    }

    override fun getLastSyncedAt(): Flow<Long?> = feedDao.observeLastSyncedAt()

    override suspend fun updateFeedViewMode(feedId: Long, viewMode: String) {
        feedDao.updateViewMode(feedId = feedId, viewMode = viewMode)
    }

    private fun String.toReaderBaseUrl(): String {
        val normalized = trim().removeSuffix("/")
        return normalized.removeSuffix("/fever")
    }

    private fun resolveFeedFaviconUrl(
        readerBaseUrl: String?,
        feedUrl: String,
        siteUrl: String?,
        faviconUrl: String?,
        faviconProxyUrl: String?,
        feedId: Long,
    ): String? = ThumbnailUrlResolver.resolveFaviconUrl(
        feedId = feedId,
        feedUrl = feedUrl,
        siteUrl = siteUrl,
        faviconUrl = faviconUrl,
        faviconProxyUrl = faviconProxyUrl,
        readerBaseUrl = readerBaseUrl,
    )
}
