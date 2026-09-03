package co.chinho.readabilityreader.data.local.model

import androidx.room.Embedded
import co.chinho.readabilityreader.data.local.entity.ArticleEntity

data class ArticleWithFeedTitle(
    @Embedded val article: ArticleEntity,
    val feedTitle: String?,
    val coverFocalX: Int?,
    val coverFocalY: Int?,
)
