package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.model.ArticleImage
import co.chinho.readabilityreader.domain.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetArticleImagesUseCase @Inject constructor(
    private val articleRepository: ArticleRepository
) {
    suspend operator fun invoke(articleId: Long): List<ArticleImage> = withContext(Dispatchers.IO) {
        articleRepository.getArticleImages(articleId)
    }
}
