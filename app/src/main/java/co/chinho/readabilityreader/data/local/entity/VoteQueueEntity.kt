package co.chinho.readabilityreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vote_queue")
data class VoteQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleId: Long,
    val vote: String?,
    val markRead: Boolean,
    val queuedAt: Long,
)
