package co.chinho.readabilityreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey val id: Long,
    val groupId: Long,
    val title: String,
    val url: String,
    val siteUrl: String?,
    val faviconId: Long?,
    val faviconUrl: String?,
    val faviconProxyUrl: String?,
    val lastSyncedAt: Long?,
    val listViewMode: String = STANDARD_VIEW_MODE,
) {
    companion object {
        const val STANDARD_VIEW_MODE = "standard"
        const val FULL_IMAGE_VIEW_MODE = "full_image"
    }
}
