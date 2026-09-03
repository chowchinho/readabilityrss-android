package co.chinho.readabilityreader.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    indices = [
        Index("feedId"),
        Index("isSaved"),
    ],
)
data class ArticleEntity(
    @PrimaryKey val id: Long,
    val feedId: Long,
    val title: String,
    val url: String,
    val content: String?,
    val publishedAt: Long,
    val isRead: Boolean,
    val isSaved: Boolean,
    val thumbnailUrl: String?,
    val cachedAt: Long,
    val contentCachedAt: Long?,
    val imagesCachedAt: Long?,
    val score: Double? = null,
    val primaryTopic: String? = null,
    val secondaryTopics: String? = null,
    val region: String? = null,
    val articleType: String? = null,
    val vote: String? = null,
    val freshness: Double? = null,
    val pairTerms: String? = null,
    val snippetText: String? = null,
)
