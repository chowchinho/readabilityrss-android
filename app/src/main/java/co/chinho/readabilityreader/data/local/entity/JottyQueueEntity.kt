package co.chinho.readabilityreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jotty_queue")
data class JottyQueueEntity(
    @PrimaryKey val articleId: Long,
    val title: String,
    val content: String,
    val category: String,
    val attempts: Int = 0,
    val lastAttemptAt: Long = 0L,
    val lastErrorMessage: String? = null,
)
