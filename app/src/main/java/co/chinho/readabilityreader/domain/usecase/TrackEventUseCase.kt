package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.ArticleRepository
import javax.inject.Inject

class TrackEventUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
) {
    suspend operator fun invoke(
        articleId: Long,
        eventType: String,
        dwellSeconds: Int? = null,
    ) {
        articleRepository.recordEvent(articleId = articleId, eventType = eventType, dwellSeconds = dwellSeconds)
    }
}
