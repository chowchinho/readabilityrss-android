package co.chinho.readabilityreader.ui.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import coil.ImageLoader
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.model.LabelWeight
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.domain.usecase.GetArticleContentUseCase
import co.chinho.readabilityreader.domain.usecase.GetArticleFlowUseCase
import co.chinho.readabilityreader.domain.usecase.GetArticlesUseCase
import co.chinho.readabilityreader.domain.usecase.GetLabelWeightsUseCase
import co.chinho.readabilityreader.domain.usecase.GetSavedArticlesUseCase
import co.chinho.readabilityreader.domain.usecase.RecordVoteUseCase
import co.chinho.readabilityreader.domain.usecase.SendArticleToJottyUseCase
import co.chinho.readabilityreader.domain.usecase.ToggleReadUseCase
import co.chinho.readabilityreader.domain.usecase.ToggleSavedUseCase
import co.chinho.readabilityreader.domain.usecase.TrackEventUseCase
import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import co.chinho.readabilityreader.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleReaderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getArticlesUseCase = mockk<GetArticlesUseCase>()
    private val getSavedArticlesUseCase = mockk<GetSavedArticlesUseCase>()
    private val getArticleFlowUseCase = mockk<GetArticleFlowUseCase>(relaxed = true)
    private val getArticleContentUseCase = mockk<GetArticleContentUseCase>(relaxed = true)
    private val toggleReadUseCase = mockk<ToggleReadUseCase>()
    private val toggleSavedUseCase = mockk<ToggleSavedUseCase>()
    private val sendArticleToJottyUseCase = mockk<SendArticleToJottyUseCase>(relaxed = true)
    private val trackEventUseCase = mockk<TrackEventUseCase>(relaxed = true)
    private val recordVoteUseCase = mockk<RecordVoteUseCase>(relaxed = true)
    private val getLabelWeightsUseCase = mockk<GetLabelWeightsUseCase>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val feverConnectionProvider = mockk<FeverConnectionProvider>(relaxed = true)
    private val imageLoader = mockk<ImageLoader>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setup() {
        every {
            getArticlesUseCase(feedId = any(), groupId = any(), showRead = any(), stickyIds = any())
        } returns flowOf(listOf(article(id = 1L, content = "<p>body</p>")))
        every { getSavedArticlesUseCase() } returns flowOf(listOf(article(id = 1L, content = "<p>body</p>")))
        every { getLabelWeightsUseCase() } returns flowOf(emptyList())
        coEvery { toggleReadUseCase(any(), any()) } returns Unit
        coEvery { toggleSavedUseCase(any(), any()) } returns Unit
        every { userPreferencesRepository.articleReaderFontSizeSp } returns flowOf(18)
        every { userPreferencesRepository.articleReaderFontFamily } returns flowOf("serif")
        every { userPreferencesRepository.autoMarkReadDelaySec } returns flowOf(2)
        every { userPreferencesRepository.externalLinkHandler } returns flowOf("system_browser")
        every { userPreferencesRepository.prefetchCount } returns flowOf(2)
        every { userPreferencesRepository.articleOrder } returns flowOf("chronological")
        every { userPreferencesRepository.votePillFractionX } returns flowOf(0.85f)
        every { userPreferencesRepository.votePillFractionY } returns flowOf(0.85f)
    }

    @Test
    fun `toggleReadState calls use case correctly`() = runTest {
        val viewModel = createViewModel()
        coEvery { toggleReadUseCase(1L, true) } returns Unit

        viewModel.toggleReadState(1L, true)
        advanceUntilIdle()

        coVerify(exactly = 1) { toggleReadUseCase(1L, true) }
    }

    @Test
    fun `toggleSaved calls use case correctly`() = runTest {
        val viewModel = createViewModel()
        coEvery { toggleSavedUseCase(2L, false) } returns Unit

        viewModel.toggleSaved(2L, false)
        advanceUntilIdle()

        coVerify(exactly = 1) { toggleSavedUseCase(2L, false) }
    }

    @Test
    fun `externalLinkHandler reflects repository preference`() = runTest {
        every { userPreferencesRepository.externalLinkHandler } returns flowOf("custom_tabs")

        val viewModel = createViewModel()
        val job = backgroundScope.launch { viewModel.externalLinkHandler.collect() }
        advanceUntilIdle()

        assertEquals("custom_tabs", viewModel.externalLinkHandler.value)
        job.cancel()
    }

    @Test
    fun `prefetchAdjacentArticles marks cached and uncached neighbors`() = runTest {
        coEvery { getArticleContentUseCase(1L) } returns "<p>current</p>"
        coEvery { getArticleContentUseCase(2L) } returns "<p>cached</p><img src=\"https://example.com/a.jpg\"/>"
        coEvery { getArticleContentUseCase(3L) } returns null

        val viewModel = createViewModel()
        val articles = listOf(
            article(id = 1L, content = "<p>current</p>"),
            article(id = 2L, content = "<p>cached</p><img src=\"https://example.com/a.jpg\"/>"),
            article(id = 3L, content = null),
        )

        viewModel.prefetchAdjacentArticles(currentIndex = 0, articles = articles)
        advanceUntilIdle()

        assertEquals(CacheStatus.CACHED, viewModel.adjacentCacheStatus.value[1])
        assertEquals(CacheStatus.NOT_CACHED, viewModel.adjacentCacheStatus.value[2])
    }

    private fun createViewModel(): ArticleReaderViewModel {
        return ArticleReaderViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "articleId" to 1L,
                    "feedId" to 0L,
                    "isSaved" to false,
                )
            ),
            getArticlesUseCase = getArticlesUseCase,
            getSavedArticlesUseCase = getSavedArticlesUseCase,
            getArticleFlowUseCase = getArticleFlowUseCase,
            getArticleContentUseCase = getArticleContentUseCase,
            toggleReadUseCase = toggleReadUseCase,
            toggleSavedUseCase = toggleSavedUseCase,
            sendArticleToJottyUseCase = sendArticleToJottyUseCase,
            trackEventUseCase = trackEventUseCase,
            recordVoteUseCase = recordVoteUseCase,
            getLabelWeightsUseCase = getLabelWeightsUseCase,
            userPreferencesRepository = userPreferencesRepository,
            feverConnectionProvider = feverConnectionProvider,
            imageLoader = imageLoader,
            context = context,
        )
    }

    private fun article(id: Long, content: String?): Article {
        return Article(
            id = id,
            feedId = 1L,
            title = "Article $id",
            url = "https://example.com/$id",
            content = content,
            publishedAt = 1_700_000_000L,
            isRead = false,
            isSaved = false,
            thumbnailUrl = null,
            isCached = content != null,
            feedTitle = "Feed",
        )
    }
}
