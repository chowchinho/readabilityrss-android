package co.chinho.readabilityreader.domain.repository

import co.chinho.readabilityreader.domain.model.ServerProfile
import kotlinx.coroutines.flow.Flow

interface ServerProfileRepository {
    fun observeProfiles(): Flow<List<ServerProfile>>
    suspend fun getActiveProfile(): ServerProfile?
    suspend fun getProfile(profileId: Long): ServerProfile?
    suspend fun getFirstProfile(): ServerProfile?
    suspend fun saveProfile(profile: ServerProfile): Long
    suspend fun setActiveProfile(profileId: Long)
    suspend fun deleteProfile(profileId: Long)
    suspend fun isServerConfigured(): Boolean
}
