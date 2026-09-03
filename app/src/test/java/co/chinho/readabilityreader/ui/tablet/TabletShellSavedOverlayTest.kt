package co.chinho.readabilityreader.ui.tablet

import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.domain.usecase.GetArticlesUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TabletShellSavedOverlayTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var getArticlesUseCase: GetArticlesUseCase
    private lateinit var viewModel: TabletShellViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userPreferencesRepository = mockk(relaxed = true)
        getArticlesUseCase = mockk(relaxed = true)

        every { userPreferencesRepository.tabletArticleListWidthPortrait } returns flowOf(0.40f)
        every { userPreferencesRepository.tabletArticleListWidthLandscape } returns flowOf(0.40f)
        every { userPreferencesRepository.tabletFeedPaneCollapsedPortrait } returns flowOf(false)
        every { userPreferencesRepository.tabletFeedPaneCollapsedLandscape } returns flowOf(false)
        every { userPreferencesRepository.showReadArticles } returns flowOf(true)

        viewModel = TabletShellViewModel(
            userPreferencesRepository = userPreferencesRepository,
            getArticlesUseCase = getArticlesUseCase,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testShowSavedSetsSavedVisibleAndHideSavedClearsIt() {
        assertFalse(viewModel.state.value.savedVisible)

        viewModel.showSaved()
        assertTrue(viewModel.state.value.savedVisible)

        viewModel.hideSaved()
        assertFalse(viewModel.state.value.savedVisible)
    }

    @Test
    fun testShowSavedClearsSettingsVisibleAndViceVersa() {
        viewModel.showSettings()
        assertTrue(viewModel.state.value.settingsVisible)
        assertFalse(viewModel.state.value.savedVisible)

        viewModel.showSaved()
        assertTrue(viewModel.state.value.savedVisible)
        assertFalse(viewModel.state.value.settingsVisible)

        viewModel.showSettings()
        assertTrue(viewModel.state.value.settingsVisible)
        assertFalse(viewModel.state.value.savedVisible)
    }

    @Test
    fun testSelectArticleUpdatesSelectedArticleIdAndClearsOverlays() {
        viewModel.showSaved()
        assertTrue(viewModel.state.value.savedVisible)

        viewModel.selectArticle(42L)
        assertEquals(42L, viewModel.state.value.selectedArticleId)
        assertFalse(viewModel.state.value.savedVisible)
        assertFalse(viewModel.state.value.settingsVisible)
    }
}
