package co.chinho.readabilityreader.domain.usecase

import co.chinho.readabilityreader.domain.model.Group
import co.chinho.readabilityreader.domain.repository.FeedRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetGroupsUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
) {
    operator fun invoke(): Flow<List<Group>> = feedRepository.getGroups()
}
