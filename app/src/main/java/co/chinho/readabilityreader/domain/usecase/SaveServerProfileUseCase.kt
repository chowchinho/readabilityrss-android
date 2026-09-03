package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.model.ServerProfile
import co.chinho.readabilityreader.domain.repository.CredentialRepository
import co.chinho.readabilityreader.domain.repository.ServerProfileRepository
import javax.inject.Inject

class SaveServerProfileUseCase @Inject constructor(
    private val serverProfileRepository: ServerProfileRepository,
    private val credentialRepository: CredentialRepository,
) {
    suspend operator fun invoke(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        profileId: Long? = null,
    ): Result<Unit> {
        val isNewProfile = profileId == null || profileId == 0L
        var createdProfileId: Long? = null
        return try {
            val profile = ServerProfile(
                id = profileId ?: 0L,
                name = name,
                serverUrl = serverUrl,
                username = username,
                isActive = true,
            )

            val savedId = serverProfileRepository.saveProfile(profile)
            val targetId = if (isNewProfile) savedId else (profileId ?: savedId)
            if (isNewProfile) {
                createdProfileId = targetId
            }

            credentialRepository.savePassword(targetId, password)
            serverProfileRepository.setActiveProfile(targetId)

            Result.success(Unit)
        } catch (e: Exception) {
            if (isNewProfile && createdProfileId != null) {
                runCatching { serverProfileRepository.deleteProfile(createdProfileId) }
            }
            Result.failure(e)
        }
    }
}
