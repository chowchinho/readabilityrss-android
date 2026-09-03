package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.repository.ArticleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetArticleFlowUseCase @Inject constructor(
    private val articleRepository: ArticleRepository
) {
    operator fun invoke(articleId: Long): Flow<Article?> {
        return articleRepository.getArticle(articleId)
    }
}
