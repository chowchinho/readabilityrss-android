package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.ArticleRepository
import javax.inject.Inject

class ToggleReadUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
) {
    suspend operator fun invoke(articleId: Long, isRead: Boolean) {
        articleRepository.markRead(articleId = articleId, isRead = isRead)
    }
}
