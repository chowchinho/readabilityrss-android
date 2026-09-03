package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.ArticleRepository
import javax.inject.Inject

class ToggleSavedUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
) {
    suspend operator fun invoke(articleId: Long, isSaved: Boolean) {
        articleRepository.markSaved(articleId = articleId, isSaved = isSaved)
    }
}
