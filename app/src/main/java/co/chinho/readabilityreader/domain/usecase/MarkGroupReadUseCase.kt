package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.ArticleRepository
import javax.inject.Inject

class MarkGroupReadUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
) {
    suspend operator fun invoke(groupId: Long) {
        articleRepository.markGroupRead(groupId, System.currentTimeMillis() / 1000L)
    }
}
