package co.chinho.readabilityreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import co.chinho.readabilityreader.data.local.entity.JottyQueueEntity

@Dao
interface JottyQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: JottyQueueEntity)

    @Query("SELECT * FROM jotty_queue WHERE attempts < :maxAttempts ORDER BY lastAttemptAt ASC")
    suspend fun getPending(maxAttempts: Int): List<JottyQueueEntity>

    @Query("DELETE FROM jotty_queue WHERE articleId = :articleId")
    suspend fun deleteById(articleId: Long)

    @Query("UPDATE jotty_queue SET attempts = :attempts, lastAttemptAt = :lastAttemptAt, lastErrorMessage = :error WHERE articleId = :articleId")
    suspend fun updateAttempt(articleId: Long, attempts: Int, lastAttemptAt: Long, error: String?)

    @Query("SELECT attempts FROM jotty_queue WHERE articleId = :articleId")
    suspend fun getAttempts(articleId: Long): Int?

    @Query("SELECT COUNT(*) FROM jotty_queue")
    suspend fun count(): Int

    @Query("DELETE FROM jotty_queue")
    suspend fun clear()
}
