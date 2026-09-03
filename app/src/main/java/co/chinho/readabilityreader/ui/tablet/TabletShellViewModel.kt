package co.chinho.readabilityreader.ui.tablet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.domain.usecase.GetArticlesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TabletShellState(
    val selectedFeedId: Long? = 0L,
    val selectedGroupId: Long? = null,
    val selectedArticleId: Long? = null,
    val feedPaneExpanded: Boolean = true,
    val articleListWidthFraction: Float = TabletDimens.ArticleListDefaultFraction,
    val isPortrait: Boolean = false,
    val settingsVisible: Boolean = false,
    val savedVisible: Boolean = false,
)

@HiltViewModel
class TabletShellViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val getArticlesUseCase: GetArticlesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TabletShellState())
    val state: StateFlow<TabletShellState> = _state.asStateFlow()

    fun setPortrait(isPortrait: Boolean) {
        viewModelScope.launch {
            var savedWidth = if (isPortrait) {
                userPreferencesRepository.tabletArticleListWidthPortrait.first()
            } else {
                userPreferencesRepository.tabletArticleListWidthLandscape.first()
            }
            // Reset stale width values from previous configurations back to the default
            if (savedWidth > TabletDimens.ArticleListDefaultFraction + 0.05f) {
                savedWidth = TabletDimens.ArticleListDefaultFraction
                if (isPortrait) {
                    userPreferencesRepository.setTabletArticleListWidthPortrait(savedWidth)
                } else {
                    userPreferencesRepository.setTabletArticleListWidthLandscape(savedWidth)
                }
            }
            val savedCollapsed = if (isPortrait) {
                userPreferencesRepository.tabletFeedPaneCollapsedPortrait.first()
            } else {
                userPreferencesRepository.tabletFeedPaneCollapsedLandscape.first()
            }
            _state.update {
                it.copy(
                    isPortrait = isPortrait,
                    articleListWidthFraction = savedWidth.coerceIn(
                        TabletDimens.ArticleListMinFraction,
                        TabletDimens.ArticleListMaxFraction,
                    ),
                    feedPaneExpanded = !savedCollapsed,
                )
            }
        }
    }

    fun selectFeed(feedId: Long?) {
        val normalizedFeedId = feedId ?: 0L
        _state.update {
            if (it.selectedFeedId == normalizedFeedId && it.selectedGroupId == null && it.selectedArticleId == null && !it.settingsVisible && !it.savedVisible) {
                it
            } else {
                it.copy(
                    selectedFeedId = normalizedFeedId,
                    selectedGroupId = null,
                    selectedArticleId = null,
                    settingsVisible = false,
                    savedVisible = false,
                )
            }
        }
    }

    fun selectGroup(groupId: Long) {
        _state.update {
            if (it.selectedGroupId == groupId && it.selectedArticleId == null && !it.settingsVisible && !it.savedVisible) {
                it
            } else {
                it.copy(
                    selectedFeedId = 0L,
                    selectedGroupId = groupId,
                    selectedArticleId = null,
                    settingsVisible = false,
                    savedVisible = false,
                )
            }
        }
    }

    suspend fun selectGroupAndGetFirstArticle(groupId: Long): Long? {
        val showRead = userPreferencesRepository.showReadArticles.first()
        val articles = getArticlesUseCase(feedId = null, showRead = showRead, groupId = groupId).first()
        val firstArticleId = articles.firstOrNull()?.id

        _state.update {
            it.copy(
                selectedFeedId = 0L,
                selectedGroupId = groupId,
                selectedArticleId = firstArticleId ?: it.selectedArticleId,
                settingsVisible = false,
                savedVisible = false,
                feedPaneExpanded = false,
            )
        }
        return firstArticleId
    }

    suspend fun selectFeedAndGetFirstArticle(feedId: Long?): Long? {
        val normalizedFeedId = feedId ?: 0L
        val targetFeedId = if (normalizedFeedId == 0L) null else normalizedFeedId
        val showRead = userPreferencesRepository.showReadArticles.first()
        val articles = getArticlesUseCase(targetFeedId, showRead).first()
        val firstArticleId = articles.firstOrNull()?.id

        _state.update {
            it.copy(
                selectedFeedId = normalizedFeedId,
                selectedGroupId = null,
                selectedArticleId = firstArticleId ?: it.selectedArticleId,
                settingsVisible = false,
                savedVisible = false,
                feedPaneExpanded = false,
            )
        }
        return firstArticleId
    }

    fun selectArticle(articleId: Long) {
        _state.update {
            if (it.selectedArticleId == articleId && !it.settingsVisible && !it.savedVisible && !it.feedPaneExpanded) {
                it
            } else {
                it.copy(
                    selectedArticleId = articleId,
                    settingsVisible = false,
                    savedVisible = false,
                    feedPaneExpanded = false,
                )
            }
        }
        viewModelScope.launch {
            val isPortrait = _state.value.isPortrait
            if (isPortrait) {
                userPreferencesRepository.setTabletFeedPaneCollapsedPortrait(true)
            } else {
                userPreferencesRepository.setTabletFeedPaneCollapsedLandscape(true)
            }
        }
    }

    fun showSettings() {
        _state.update { it.copy(settingsVisible = true, savedVisible = false) }
    }

    fun hideSettings() {
        _state.update { it.copy(settingsVisible = false) }
    }

    fun showSaved() {
        _state.update { it.copy(savedVisible = true, settingsVisible = false) }
    }

    fun hideSaved() {
        _state.update { it.copy(savedVisible = false) }
    }

    fun setFeedPaneExpanded(expanded: Boolean) {
        _state.update { it.copy(feedPaneExpanded = expanded) }
        viewModelScope.launch {
            val isPortrait = _state.value.isPortrait
            if (isPortrait) {
                userPreferencesRepository.setTabletFeedPaneCollapsedPortrait(!expanded)
            } else {
                userPreferencesRepository.setTabletFeedPaneCollapsedLandscape(!expanded)
            }
        }
    }

    fun setArticleListWidthFraction(fraction: Float, persist: Boolean) {
        val coerced = fraction.coerceIn(
            TabletDimens.ArticleListMinFraction,
            TabletDimens.ArticleListMaxFraction,
        )
        _state.update { it.copy(articleListWidthFraction = coerced) }
        if (persist) {
            persistArticleListWidth(coerced)
        }
    }

    fun persistCurrentArticleListWidth() {
        persistArticleListWidth(_state.value.articleListWidthFraction)
    }

    private fun persistArticleListWidth(fraction: Float) {
        viewModelScope.launch {
            val isPortrait = _state.value.isPortrait
            if (isPortrait) {
                userPreferencesRepository.setTabletArticleListWidthPortrait(fraction)
            } else {
                userPreferencesRepository.setTabletArticleListWidthLandscape(fraction)
            }
        }
    }
}
