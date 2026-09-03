package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.repository.ArticleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetArticlesUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
) {
    operator fun invoke(
        feedId: Long?,
        showRead: Boolean,
        groupId: Long? = null,
        stickyIds: List<Long> = emptyList(),
    ): Flow<List<Article>> {
        return articleRepository.getArticles(
            feedId = feedId,
            showRead = showRead,
            groupId = groupId,
            stickyIds = stickyIds,
        )
    }
}
