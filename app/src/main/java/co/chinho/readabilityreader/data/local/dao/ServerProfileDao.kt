package co.chinho.readabilityreader.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import co.chinho.readabilityreader.data.local.entity.ServerProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerProfileDao {
    @Query("SELECT * FROM server_profiles ORDER BY isActive DESC, name COLLATE NOCASE")
    fun observeProfiles(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): ServerProfileEntity?

    @Query("SELECT * FROM server_profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfile(profileId: Long): ServerProfileEntity?

    @Query("SELECT * FROM server_profiles ORDER BY isActive DESC, name COLLATE NOCASE LIMIT 1")
    suspend fun getFirstProfile(): ServerProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: ServerProfileEntity): Long

    @Query("UPDATE server_profiles SET isActive = CASE WHEN id = :profileId THEN 1 ELSE 0 END")
    suspend fun setActiveProfile(profileId: Long)

    @Query("DELETE FROM server_profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: Long)
}
