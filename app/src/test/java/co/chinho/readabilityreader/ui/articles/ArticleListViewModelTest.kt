package co.chinho.readabilityreader.ui.articles

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.model.Feed
import co.chinho.readabilityreader.domain.model.Group
import co.chinho.readabilityreader.domain.repository.FeedRepository
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.domain.usecase.GetArticleImagesUseCase
import co.chinho.readabilityreader.domain.usecase.GetArticlesUseCase
import co.chinho.readabilityreader.domain.usecase.MarkGroupReadUseCase
import co.chinho.readabilityreader.domain.usecase.RecordVoteUseCase
import co.chinho.readabilityreader.domain.usecase.ToggleReadUseCase
import co.chinho.readabilityreader.domain.usecase.UpdateFeedViewModeUseCase
import co.chinho.readabilityreader.testutil.MainDispatcherRule
import co.chinho.readabilityreader.ui.navigation.Screen
import coil.ImageLoader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getArticlesUseCase = mockk<GetArticlesUseCase>()
    private val getArticleImagesUseCase = mockk<GetArticleImagesUseCase>(relaxed = true)
    private val toggleReadUseCase = mockk<ToggleReadUseCase>()
    private val recordVoteUseCase = mockk<RecordVoteUseCase>(relaxed = true)
    private val markGroupReadUseCase = mockk<MarkGroupReadUseCase>(relaxed = true)
    private val updateFeedViewModeUseCase = mockk<UpdateFeedViewModeUseCase>()
    private val feedRepository = mockk<FeedRepository>()
    private val userPreferencesRepository = mockk<UserPreferencesRepository>()
    private val imageLoader = mockk<ImageLoader>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @Test
    fun `toggleViewMode flips standard to full_image`() = runTest {
        val viewModel = createViewModel(feedIdArg = FEED_ID, listViewMode = "standard")
        collectViewMode(viewModel)
        advanceUntilIdle()

        viewModel.toggleViewMode()
        advanceUntilIdle()

        coVerify(exactly = 1) { updateFeedViewModeUseCase(FEED_ID, "full_image") }
    }

    @Test
    fun `toggleViewMode flips full_image to standard`() = runTest {
        val viewModel = createViewModel(feedIdArg = FEED_ID, listViewMode = "full_image")
        collectViewMode(viewModel)
        advanceUntilIdle()

        viewModel.toggleViewMode()
        advanceUntilIdle()

        coVerify(exactly = 1) { updateFeedViewModeUseCase(FEED_ID, "standard") }
    }

    @Test
    fun `All Sources reads its view mode from preferences`() = runTest {
        val viewModel = createViewModel(allSourcesFullImage = true)
        collectViewMode(viewModel)
        advanceUntilIdle()

        assertEquals("full_image", viewModel.viewMode.value)
    }

    @Test
    fun `toggleViewMode on All Sources writes the preference, not the feed`() = runTest {
        val viewModel = createViewModel(allSourcesFullImage = false)
        collectViewMode(viewModel)
        advanceUntilIdle()

        viewModel.toggleViewMode()
        advanceUntilIdle()

        coVerify(exactly = 1) { userPreferencesRepository.setAllSourcesFullImage(true) }
        coVerify(exactly = 0) { updateFeedViewModeUseCase(any(), any()) }
    }

    @Test
    fun `a category is full image only when its own id is stored`() = runTest {
        val viewModel = createViewModel(groupIdArg = GROUP_ID, fullImageCategoryIds = setOf(GROUP_ID))
        collectViewMode(viewModel)
        advanceUntilIdle()

        assertEquals("full_image", viewModel.viewMode.value)

        val other = createViewModel(groupIdArg = GROUP_ID, fullImageCategoryIds = setOf(GROUP_ID + 1))
        collectViewMode(other)
        advanceUntilIdle()

        assertEquals("standard", other.viewMode.value)
    }

    @Test
    fun `toggleViewMode on a category writes only that category`() = runTest {
        val viewModel = createViewModel(groupIdArg = GROUP_ID, fullImageCategoryIds = emptySet())
        collectViewMode(viewModel)
        advanceUntilIdle()

        viewModel.toggleViewMode()
        advanceUntilIdle()

        coVerify(exactly = 1) { userPreferencesRepository.setCategoryFullImage(GROUP_ID, true) }
        coVerify(exactly = 0) { updateFeedViewModeUseCase(any(), any()) }
    }

    @Test
    fun `markGroupRead is a no-op for AllSources scope`() = runTest {
        val viewModel = createViewModel()
        viewModel.markGroupRead()
        advanceUntilIdle()

        coVerify(exactly = 0) { markGroupReadUseCase(any()) }
    }

    @Test
    fun `markGroupRead is a no-op for SingleFeed scope`() = runTest {
        val viewModel = createViewModel(feedIdArg = FEED_ID)
        viewModel.markGroupRead()
        advanceUntilIdle()

        coVerify(exactly = 0) { markGroupReadUseCase(any()) }
    }

    @Test
    fun `markGroupRead calls use case with correct id for Category scope`() = runTest {
        val viewModel = createViewModel(groupIdArg = GROUP_ID)
        viewModel.markGroupRead()
        advanceUntilIdle()

        coVerify(exactly = 1) { markGroupReadUseCase(GROUP_ID) }
    }

    private fun createViewModel(
        feedIdArg: Long = 0L,
        groupIdArg: Long = 0L,
        listViewMode: String = "standard",
        allSourcesFullImage: Boolean = false,
        fullImageCategoryIds: Set<Long> = emptySet(),
        showReadArticles: Boolean = true,
        articlesProvider: ((feedId: Long?, showRead: Boolean, groupId: Long?, stickyIds: List<Long>) -> kotlinx.coroutines.flow.Flow<List<Article>>)? = null,
    ): ArticleListViewModel {
        val feed = feed(listViewMode = listViewMode)
        if (articlesProvider != null) {
            every {
                getArticlesUseCase(feedId = any(), showRead = any(), groupId = any(), stickyIds = any())
            } answers {
                val feedId = args[0] as? Long
                val showRead = args[1] as Boolean
                val groupId = args[2] as? Long
                @Suppress("UNCHECKED_CAST")
                val stickyIds = args[3] as List<Long>
                articlesProvider(feedId, showRead, groupId, stickyIds)
            }
        } else {
            every {
                getArticlesUseCase(feedId = any(), showRead = any(), groupId = any(), stickyIds = any())
            } returns flowOf(emptyList<Article>())
        }
        every { feedRepository.getFeed(feed.id) } returns flowOf(feed)
        every { feedRepository.getFeeds() } returns flowOf(listOf(feed))
        every { feedRepository.getGroups() } returns flowOf(
            listOf(Group(id = GROUP_ID, title = "Tech", feeds = listOf(feed)))
        )
        coEvery { toggleReadUseCase(any(), any()) } returns Unit
        coEvery { updateFeedViewModeUseCase(any(), any()) } returns Unit
        every { userPreferencesRepository.articleListFontSizeSp } returns flowOf(16)
        every { userPreferencesRepository.articleListFontFamily } returns flowOf("system")
        every { userPreferencesRepository.showReadArticles } returns flowOf(showReadArticles)
        every { userPreferencesRepository.allSourcesFullImage } returns flowOf(allSourcesFullImage)
        every { userPreferencesRepository.fullImageCategoryIds } returns flowOf(fullImageCategoryIds)
        coEvery { userPreferencesRepository.setAllSourcesFullImage(any()) } returns Unit
        coEvery { userPreferencesRepository.setCategoryFullImage(any(), any()) } returns Unit

        return ArticleListViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    Screen.ArticleList.NAV_ARG_FEED_ID to feedIdArg,
                    Screen.ArticleList.NAV_ARG_GROUP_ID to groupIdArg,
                )
            ),
            getArticlesUseCase = getArticlesUseCase,
            getArticleImagesUseCase = getArticleImagesUseCase,
            toggleReadUseCase = toggleReadUseCase,
            recordVoteUseCase = recordVoteUseCase,
            markGroupReadUseCase = markGroupReadUseCase,
            updateFeedViewModeUseCase = updateFeedViewModeUseCase,
            feedRepository = feedRepository,
            userPreferencesRepository = userPreferencesRepository,
            imageLoader = imageLoader,
            context = context,
        )
    }

    @Test
    fun `stickyIds feedback loop converges in at most one extra round-trip when showRead is false`() = runTest {
        val unreadArticles = listOf(
            Article(id = 1L, feedId = FEED_ID, title = "A1", url = "https://e.test/1", content = "C1", publishedAt = 1000L, isRead = false, isSaved = false, thumbnailUrl = null, isCached = true),
            Article(id = 2L, feedId = FEED_ID, title = "A2", url = "https://e.test/2", content = "C2", publishedAt = 2000L, isRead = false, isSaved = false, thumbnailUrl = null, isCached = true),
            Article(id = 3L, feedId = FEED_ID, title = "A3", url = "https://e.test/3", content = "C3", publishedAt = 3000L, isRead = false, isSaved = false, thumbnailUrl = null, isCached = true),
        )
        val queriedStickyIds = mutableListOf<List<Long>>()

        val viewModel = createViewModel(
            feedIdArg = FEED_ID,
            showReadArticles = false,
            articlesProvider = { _, _, _, stickyIds ->
                queriedStickyIds.add(stickyIds)
                flowOf(unreadArticles)
            },
        )
        val emissions = mutableListOf<ArticleListUiState>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { emissions.add(it) }
        }
        advanceUntilIdle()

        val successEmissions = emissions.filterIsInstance<ArticleListUiState.Success>()
        org.junit.Assert.assertTrue("Emitted success state", successEmissions.isNotEmpty())
        assertEquals(3, successEmissions.last().articles.size)
        org.junit.Assert.assertTrue("Loop converged promptly (<= 2 query round-trips)", queriedStickyIds.size <= 2)
        assertEquals(emptyList<Long>(), queriedStickyIds.first())
        if (queriedStickyIds.size > 1) {
            assertEquals(listOf(1L, 2L, 3L), queriedStickyIds.last())
        }
        job.cancel()
    }

    private fun TestScope.collectViewMode(viewModel: ArticleListViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.viewMode.collect {}
        }
    }

    private fun feed(listViewMode: String): Feed {
        return Feed(
            id = FEED_ID,
            groupId = GROUP_ID,
            title = "Tech Feed",
            url = "https://example.com/feed.xml",
            faviconUrl = null,
            unreadCount = 3,
            listViewMode = listViewMode,
        )
    }

    private companion object {
        const val FEED_ID = 5L
        const val GROUP_ID = 1L
    }
}
