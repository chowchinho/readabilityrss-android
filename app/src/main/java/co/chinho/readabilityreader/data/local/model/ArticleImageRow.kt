package co.chinho.readabilityreader.data.local.model

import androidx.room.ColumnInfo

data class ArticleImageRow(
    @ColumnInfo(name = "image_url") val imageUrl: String,
    val focalX: Int,
    val focalY: Int,
)
