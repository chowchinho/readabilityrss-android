package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.ServerProfileRepository
import javax.inject.Inject

class IsServerConfiguredUseCase @Inject constructor(
    private val serverProfileRepository: ServerProfileRepository
) {
    suspend operator fun invoke(): Boolean = serverProfileRepository.isServerConfigured()
}
