package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.ArticleRepository
import javax.inject.Inject

class RewarmMissingThumbnailsUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
) {
    suspend operator fun invoke(maxToWarm: Int = DEFAULT_BATCH): Int {
        return articleRepository.rewarmMissingThumbnails(maxToWarm)
    }

    companion object {
        const val DEFAULT_BATCH = 300
    }
}
