package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.ArticleRepository
import javax.inject.Inject

class ClearAllCacheUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
) {
    suspend operator fun invoke() {
        articleRepository.clearAllCache()
    }
}
