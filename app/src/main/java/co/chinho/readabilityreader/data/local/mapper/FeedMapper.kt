package co.chinho.readabilityreader.data.local.mapper

import co.chinho.readabilityreader.data.local.entity.GroupEntity
import co.chinho.readabilityreader.data.local.model.FeedSummaryEntity
import co.chinho.readabilityreader.domain.model.Feed
import co.chinho.readabilityreader.domain.model.Group

fun FeedSummaryEntity.toDomain(
    faviconUrl: String? = null,
): Feed = Feed(
    id = id,
    groupId = groupId,
    title = title,
    url = url,
    faviconUrl = faviconUrl,
    unreadCount = unreadCount,
    listViewMode = listViewMode,
)

fun GroupEntity.toDomain(
    feeds: List<Feed>,
): Group = Group(
    id = id,
    title = title,
    feeds = feeds.filter { it.groupId == id },
)
