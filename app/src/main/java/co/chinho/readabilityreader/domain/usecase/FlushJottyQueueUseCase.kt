package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.JottyRepository
import javax.inject.Inject

class FlushJottyQueueUseCase @Inject constructor(
    private val jottyRepository: JottyRepository,
) {
    suspend operator fun invoke(): JottyRepository.FlushSummary {
        return jottyRepository.flushQueue()
    }
}
