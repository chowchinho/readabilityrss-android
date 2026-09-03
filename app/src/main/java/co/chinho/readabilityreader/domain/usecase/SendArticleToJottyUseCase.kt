package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.repository.JottyRepository
import javax.inject.Inject

class SendArticleToJottyUseCase @Inject constructor(
    private val jottyRepository: JottyRepository,
) {
    suspend operator fun invoke(article: Article): JottyRepository.SendResult {
        return jottyRepository.sendArticle(article)
    }
}
