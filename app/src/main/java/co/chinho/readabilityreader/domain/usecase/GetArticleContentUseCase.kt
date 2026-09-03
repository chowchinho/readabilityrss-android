package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.ArticleRepository
import javax.inject.Inject

class GetArticleContentUseCase @Inject constructor(
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(articleId: Long): String? {
        return articleRepository.getArticleContent(articleId)
    }
}
