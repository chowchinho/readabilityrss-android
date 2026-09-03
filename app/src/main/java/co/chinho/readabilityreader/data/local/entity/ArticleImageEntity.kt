package co.chinho.readabilityreader.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "article_images",
    primaryKeys = ["articleId", "image_url"],
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["image_url"])],
)
data class ArticleImageEntity(
    val articleId: Long,
    @ColumnInfo(name = "image_url") val imageUrl: String,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
    @ColumnInfo(defaultValue = "50") val focalX: Int = 50,
    @ColumnInfo(defaultValue = "50") val focalY: Int = 50,
    @ColumnInfo(defaultValue = "0") val focalComputed: Boolean = false,
)
