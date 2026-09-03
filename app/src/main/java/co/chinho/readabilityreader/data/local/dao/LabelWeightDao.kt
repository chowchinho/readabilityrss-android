package co.chinho.readabilityreader.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import co.chinho.readabilityreader.data.local.entity.LabelWeightEntity

@Dao
interface LabelWeightDao {
    @Query("SELECT * FROM label_weights")
    fun observeAllWeights(): kotlinx.coroutines.flow.Flow<List<LabelWeightEntity>>

    @Query("DELETE FROM label_weights")
    suspend fun clearAll()

    @Upsert
    suspend fun upsertWeights(weights: List<LabelWeightEntity>)

    @Transaction
    suspend fun replaceAllWeights(weights: List<LabelWeightEntity>) {
        clearAll()
        if (weights.isNotEmpty()) {
            upsertWeights(weights)
        }
    }
}
