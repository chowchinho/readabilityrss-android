package co.chinho.readabilityreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import co.chinho.readabilityreader.data.local.entity.ReadStateQueueEntity

@Dao
interface ReadStateQueueDao {
    @Insert
    suspend fun insert(action: ReadStateQueueEntity): Long

    @Insert
    suspend fun insertAll(actions: List<ReadStateQueueEntity>)

    @Query("SELECT * FROM read_state_queue ORDER BY queuedAt ASC, id ASC")
    suspend fun getQueuedActions(): List<ReadStateQueueEntity>

    @Query("DELETE FROM read_state_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM read_state_queue")
    suspend fun clearAll()
}
