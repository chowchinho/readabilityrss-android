package co.chinho.readabilityreader.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import co.chinho.readabilityreader.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY title COLLATE NOCASE")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Upsert
    suspend fun upsertGroups(groups: List<GroupEntity>)

    @Query("DELETE FROM groups")
    suspend fun clearAll()
}
