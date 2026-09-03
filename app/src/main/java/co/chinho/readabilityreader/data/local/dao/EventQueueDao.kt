package co.chinho.readabilityreader.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import co.chinho.readabilityreader.data.local.entity.EventQueueEntity

@Dao
interface EventQueueDao {
    @Query("SELECT * FROM event_queue ORDER BY queuedAt ASC")
    suspend fun getAll(): List<EventQueueEntity>

    @Upsert
    suspend fun insert(item: EventQueueEntity)

    @Query("DELETE FROM event_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM event_queue WHERE articleId = :articleId AND eventType = :eventType")
    suspend fun deleteByArticleIdAndType(articleId: Long, eventType: String)

    @Query("DELETE FROM event_queue")
    suspend fun clearAll()
}
