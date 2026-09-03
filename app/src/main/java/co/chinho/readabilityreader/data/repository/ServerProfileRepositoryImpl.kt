package co.chinho.readabilityreader.data.repository

import co.chinho.readabilityreader.data.local.dao.ServerProfileDao
import co.chinho.readabilityreader.data.local.mapper.toDomain
import co.chinho.readabilityreader.data.local.mapper.toEntity
import co.chinho.readabilityreader.domain.model.ServerProfile
import co.chinho.readabilityreader.domain.repository.ServerProfileRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ServerProfileRepositoryImpl @Inject constructor(
    private val serverProfileDao: ServerProfileDao
) : ServerProfileRepository {

    override fun observeProfiles(): Flow<List<ServerProfile>> {
        return serverProfileDao.observeProfiles().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getActiveProfile(): ServerProfile? {
        return serverProfileDao.getActiveProfile()?.toDomain()
    }

    override suspend fun getProfile(profileId: Long): ServerProfile? {
        return serverProfileDao.getProfile(profileId)?.toDomain()
    }

    override suspend fun getFirstProfile(): ServerProfile? {
        return serverProfileDao.getFirstProfile()?.toDomain()
    }

    override suspend fun saveProfile(profile: ServerProfile): Long {
        return serverProfileDao.upsertProfile(profile.toEntity())
    }

    override suspend fun setActiveProfile(profileId: Long) {
        serverProfileDao.setActiveProfile(profileId)
    }

    override suspend fun deleteProfile(profileId: Long) {
        serverProfileDao.deleteProfile(profileId)
    }

    override suspend fun isServerConfigured(): Boolean {
        return serverProfileDao.getActiveProfile() != null
    }
}
