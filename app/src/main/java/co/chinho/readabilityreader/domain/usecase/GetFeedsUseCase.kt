package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.model.Feed
import co.chinho.readabilityreader.domain.repository.FeedRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetFeedsUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    operator fun invoke(): Flow<List<Feed>> = feedRepository.getFeeds()
}
