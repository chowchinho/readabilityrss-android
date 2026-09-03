package co.chinho.readabilityreader.ui.feeds

import co.chinho.readabilityreader.data.repository.ConnectivityMonitor
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.model.Group
import co.chinho.readabilityreader.domain.repository.ArticleRepository
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.domain.usecase.GetGroupsUseCase
import co.chinho.readabilityreader.domain.usecase.MarkFeedReadUseCase
import co.chinho.readabilityreader.domain.usecase.MarkGroupReadUseCase
import co.chinho.readabilityreader.domain.usecase.UpdateFeedViewModeUseCase
import co.chinho.readabilityreader.testutil.MainDispatcherRule
import co.chinho.readabilityreader.ui.components.SyncStatus
import co.chinho.readabilityreader.worker.SyncScheduler
import co.chinho.readabilityreader.worker.SyncStatusObserver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val groupsFlow = flowOf(emptyList<Group>())
    private val getGroupsUseCase = mockk<GetGroupsUseCase>()
    private val markFeedReadUseCase = mockk<MarkFeedReadUseCase>()
    private val markGroupReadUseCase = mockk<MarkGroupReadUseCase>()
    private val updateFeedViewModeUseCase = mockk<UpdateFeedViewModeUseCase>()
    private val connectivityMonitor = mockk<ConnectivityMonitor>()
    private val articleRepository = mockk<ArticleRepository>(relaxed = true)
    private val syncScheduler = mockk<SyncScheduler>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val syncStatusObserver = mockk<SyncStatusObserver>(relaxed = true)

    @Before
    fun setup() {
        every { getGroupsUseCase() } returns groupsFlow
        every { connectivityMonitor.isOffline } returns MutableStateFlow(false)
        every { articleRepository.getSavedArticles() } returns flowOf(emptyList<Article>())
        every { articleRepository.getArticleCount() } returns flowOf(0)
        every { userPreferencesRepository.hideSavedWhenEmpty } returns flowOf(false)
        every { userPreferencesRepository.showSavedArticlesSection } returns flowOf(true)
        every { userPreferencesRepository.dockedCategoryCount } returns flowOf(MaxDockedPerEnd)
        every { syncStatusObserver.syncStatus } returns MutableStateFlow(SyncStatus())
        coEvery { markFeedReadUseCase(any()) } returns Unit
        coEvery { markGroupReadUseCase(any()) } returns Unit
        coEvery { updateFeedViewModeUseCase(any(), any()) } returns Unit
    }

    @Test
    fun `markFeedRead calls use case with correct feed id`() = runTest {
        val viewModel = createViewModel()
        coEvery { markFeedReadUseCase(9L) } returns Unit

        viewModel.markFeedRead(9L)
        advanceUntilIdle()

        coVerify(exactly = 1) { markFeedReadUseCase(9L) }
    }

    @Test
    fun `markGroupRead calls use case with correct group id`() = runTest {
        val viewModel = createViewModel()
        coEvery { markGroupReadUseCase(3L) } returns Unit

        viewModel.markGroupRead(3L)
        advanceUntilIdle()

        coVerify(exactly = 1) { markGroupReadUseCase(3L) }
    }

    @Test
    fun `toggleViewMode switches standard to full image`() = runTest {
        val viewModel = createViewModel()
        coEvery { updateFeedViewModeUseCase(5L, "full_image") } returns Unit

        viewModel.toggleViewMode(5L, "standard")
        advanceUntilIdle()

        coVerify(exactly = 1) { updateFeedViewModeUseCase(5L, "full_image") }
    }

    @Test
    fun `toggleViewMode switches full image to standard`() = runTest {
        val viewModel = createViewModel()
        coEvery { updateFeedViewModeUseCase(5L, "standard") } returns Unit

        viewModel.toggleViewMode(5L, "full_image")
        advanceUntilIdle()

        coVerify(exactly = 1) { updateFeedViewModeUseCase(5L, "standard") }
    }

    @Test
    fun `isOffline reflects connectivity monitor flow`() = runTest {
        val offlineFlow = MutableStateFlow(false)
        every { connectivityMonitor.isOffline } returns offlineFlow

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.isOffline.collect() }
        advanceUntilIdle()
        assertFalse(viewModel.isOffline.value)

        offlineFlow.value = true
        advanceUntilIdle()

        assertTrue(viewModel.isOffline.value)
        job.cancel()
    }

    private fun createViewModel(): FeedListViewModel {
        return FeedListViewModel(
            getGroupsUseCase = getGroupsUseCase,
            markFeedReadUseCase = markFeedReadUseCase,
            markGroupReadUseCase = markGroupReadUseCase,
            updateFeedViewModeUseCase = updateFeedViewModeUseCase,
            articleRepository = articleRepository,
            syncScheduler = syncScheduler,
            userPreferencesRepository = userPreferencesRepository,
            connectivityMonitor = connectivityMonitor,
            syncStatusObserver = syncStatusObserver,
        )
    }
}
