package co.chinho.readabilityreader.domain.model

data class Feed(
    val id: Long,
    val groupId: Long,
    val title: String,
    val url: String,
    val faviconUrl: String?,
    val unreadCount: Int,
    val listViewMode: String,
)
