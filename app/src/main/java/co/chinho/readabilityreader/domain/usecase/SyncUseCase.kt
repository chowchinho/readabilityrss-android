package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.ArticleRepository
import javax.inject.Inject

class SyncUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
) {
    suspend operator fun invoke(
        keepDays: Int,
        onProgress: suspend (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Int {
        return articleRepository.syncFromServer(keepDays, onProgress)
    }
}
