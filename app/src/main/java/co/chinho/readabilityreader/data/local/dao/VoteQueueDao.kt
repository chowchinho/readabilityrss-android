package co.chinho.readabilityreader.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import co.chinho.readabilityreader.data.local.entity.VoteQueueEntity

@Dao
interface VoteQueueDao {
    @Query("SELECT * FROM vote_queue ORDER BY queuedAt ASC")
    suspend fun getAll(): List<VoteQueueEntity>

    @Upsert
    suspend fun insert(item: VoteQueueEntity)

    @Query("DELETE FROM vote_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM vote_queue WHERE articleId = :articleId")
    suspend fun deleteByArticleId(articleId: Long)

    @Query("DELETE FROM vote_queue")
    suspend fun clearAll()
}
