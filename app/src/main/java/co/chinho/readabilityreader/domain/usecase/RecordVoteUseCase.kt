package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.ArticleRepository
import javax.inject.Inject

class RecordVoteUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
) {
    suspend operator fun invoke(
        articleId: Long,
        vote: String?,
        markRead: Boolean = false,
    ) {
        articleRepository.recordVote(articleId = articleId, vote = vote, markRead = markRead)
    }
}
