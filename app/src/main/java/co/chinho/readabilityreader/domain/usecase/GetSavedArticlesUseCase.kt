package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.repository.ArticleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetSavedArticlesUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
) {
    operator fun invoke(): Flow<List<Article>> = articleRepository.getSavedArticles()
}
