package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.model.LabelWeight
import co.chinho.readabilityreader.domain.repository.ArticleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLabelWeightsUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
) {
    operator fun invoke(): Flow<List<LabelWeight>> {
        return articleRepository.getLabelWeights()
    }
}
