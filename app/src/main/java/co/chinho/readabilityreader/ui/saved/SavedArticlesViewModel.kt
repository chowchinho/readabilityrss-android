package co.chinho.readabilityreader.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.repository.FeedRepository
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.domain.usecase.GetSavedArticlesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface SavedArticlesUiState {
    data object Loading : SavedArticlesUiState
    data class Success(val articles: List<Article>) : SavedArticlesUiState
    data class Error(val message: String) : SavedArticlesUiState
}

@HiltViewModel
class SavedArticlesViewModel @Inject constructor(
    private val getSavedArticlesUseCase: GetSavedArticlesUseCase,
    feedRepository: FeedRepository,
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<SavedArticlesUiState> = combine(
        getSavedArticlesUseCase(),
        feedRepository.getFeeds(),
    ) { articles, feeds ->
        val feedById = feeds.associateBy { it.id }
        articles.map { article ->
            article.copy(feedFaviconUrl = feedById[article.feedId]?.faviconUrl)
        }
    }
        .map { SavedArticlesUiState.Success(it) as SavedArticlesUiState }
        .catch { emit(SavedArticlesUiState.Error(it.message ?: "Unknown error")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SavedArticlesUiState.Loading
        )
}
