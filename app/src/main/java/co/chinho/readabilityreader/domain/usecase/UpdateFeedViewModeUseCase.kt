package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.repository.FeedRepository
import javax.inject.Inject

class UpdateFeedViewModeUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    suspend operator fun invoke(feedId: Long, viewMode: String) {
        feedRepository.updateFeedViewMode(feedId = feedId, viewMode = viewMode)
    }
}
