package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.JottyRepository
import javax.inject.Inject

class TestJottyConnectionUseCase @Inject constructor(
    private val jottyRepository: JottyRepository,
) {
    suspend operator fun invoke(serverUrl: String, apiKey: String): JottyRepository.TestResult {
        return jottyRepository.testConnection(serverUrl, apiKey)
    }
}
