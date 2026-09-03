package co.chinho.readabilityreader.ui.feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.chinho.readabilityreader.data.repository.ConnectivityMonitor
import co.chinho.readabilityreader.domain.model.Group
import co.chinho.readabilityreader.domain.repository.ArticleRepository
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.domain.usecase.GetGroupsUseCase
import co.chinho.readabilityreader.domain.usecase.MarkFeedReadUseCase
import co.chinho.readabilityreader.domain.usecase.MarkGroupReadUseCase
import co.chinho.readabilityreader.domain.usecase.UpdateFeedViewModeUseCase
import co.chinho.readabilityreader.ui.components.SyncStatus
import co.chinho.readabilityreader.worker.SyncScheduler
import co.chinho.readabilityreader.worker.SyncStatusObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

sealed interface FeedListUiState {
    data object Loading : FeedListUiState
    data class Success(val groups: List<Group>, val allUnreadCount: Int) : FeedListUiState
    data class Error(val message: String) : FeedListUiState
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class FeedListViewModel @Inject constructor(
    private val getGroupsUseCase: GetGroupsUseCase,
    private val markFeedReadUseCase: MarkFeedReadUseCase,
    private val markGroupReadUseCase: MarkGroupReadUseCase,
    private val updateFeedViewModeUseCase: UpdateFeedViewModeUseCase,
    private val articleRepository: ArticleRepository,
    private val syncScheduler: SyncScheduler,
    userPreferencesRepository: UserPreferencesRepository,
    connectivityMonitor: ConnectivityMonitor,
    syncStatusObserver: SyncStatusObserver,
) : ViewModel() {

    val savedCount: StateFlow<Int> = articleRepository.getSavedArticles()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val hideSavedWhenEmpty: StateFlow<Boolean> = userPreferencesRepository.hideSavedWhenEmpty
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val showSavedArticlesSection: StateFlow<Boolean> = userPreferencesRepository.showSavedArticlesSection
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val dockedCategoryCount: StateFlow<Int> = userPreferencesRepository.dockedCategoryCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MaxDockedPerEnd
        )

    private val _error = MutableStateFlow<String?>(null)

    val isOffline: StateFlow<Boolean> = connectivityMonitor.isOffline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val syncStatus: StateFlow<SyncStatus> = syncStatusObserver.syncStatus.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncStatus()
    )

    val uiState: StateFlow<FeedListUiState> = combine(
        getGroupsUseCase(),
        _error
    ) { groups, error ->
        if (error != null && groups.isEmpty()) {
            FeedListUiState.Error(error)
        } else {
            val totalUnread = groups.flatMap { it.feeds }.sumOf { it.unreadCount }
            FeedListUiState.Success(groups, totalUnread)
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FeedListUiState.Loading
        )

    fun refresh() {
        _error.value = null
        syncScheduler.triggerOneTimeSync()
    }

    fun markFeedRead(feedId: Long) {
        viewModelScope.launch {
            runCatching { markFeedReadUseCase(feedId) }
                .onFailure { _error.value = it.message ?: "Failed to mark feed read" }
        }
    }

    fun markGroupRead(groupId: Long) {
        viewModelScope.launch {
            runCatching { markGroupReadUseCase(groupId) }
                .onFailure { _error.value = it.message ?: "Failed to mark group read" }
        }
    }

    fun toggleViewMode(feedId: Long, currentMode: String) {
        viewModelScope.launch {
            val newMode = if (currentMode == "full_image") "standard" else "full_image"
            runCatching { updateFeedViewModeUseCase(feedId, newMode) }
                .onFailure { _error.value = it.message ?: "Failed to update view mode" }
        }
    }
}
