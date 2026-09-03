package co.chinho.readabilityreader.data.local.model

data class FeedSummaryEntity(
    val id: Long,
    val groupId: Long,
    val title: String,
    val url: String,
    val siteUrl: String?,
    val faviconId: Long?,
    val faviconUrl: String?,
    val faviconProxyUrl: String?,
    val lastSyncedAt: Long?,
    val listViewMode: String,
    val totalArticleCount: Int,
    val unreadCount: Int,
)
