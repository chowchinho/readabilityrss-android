package co.chinho.readabilityreader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_queue")
data class EventQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleId: Long,
    val eventType: String,
    val dwellSeconds: Int?,
    val queuedAt: Long,
)
