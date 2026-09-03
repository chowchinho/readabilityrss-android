package co.chinho.readabilityreader.ui.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import co.chinho.readabilityreader.data.repository.ArticleImageCache
import co.chinho.readabilityreader.domain.usecase.GetArticlesUseCase
import co.chinho.readabilityreader.domain.usecase.GetSavedArticlesUseCase
import co.chinho.readabilityreader.domain.usecase.GetArticleFlowUseCase
import co.chinho.readabilityreader.domain.usecase.GetArticleContentUseCase
import co.chinho.readabilityreader.domain.usecase.SendArticleToJottyUseCase
import co.chinho.readabilityreader.domain.usecase.ToggleReadUseCase
import co.chinho.readabilityreader.domain.usecase.ToggleSavedUseCase
import co.chinho.readabilityreader.domain.repository.JottyRepository
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import co.chinho.readabilityreader.domain.usecase.TrackEventUseCase
import co.chinho.readabilityreader.domain.usecase.RecordVoteUseCase
import co.chinho.readabilityreader.domain.usecase.GetLabelWeightsUseCase
import co.chinho.readabilityreader.domain.model.LabelWeight
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

enum class CacheStatus {
    CACHED,
    LOADING,
    NOT_CACHED,
}

sealed interface ArticleReaderUiState {
    data object Loading : ArticleReaderUiState
    data class Success(
        val articles: List<Article>,
        val initialIndex: Int
    ) : ArticleReaderUiState
    data class Error(val message: String) : ArticleReaderUiState
}

@HiltViewModel
class ArticleReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getArticlesUseCase: GetArticlesUseCase,
    private val getSavedArticlesUseCase: GetSavedArticlesUseCase,
    private val getArticleFlowUseCase: GetArticleFlowUseCase,
    private val getArticleContentUseCase: GetArticleContentUseCase,
    private val toggleReadUseCase: ToggleReadUseCase,
    private val toggleSavedUseCase: ToggleSavedUseCase,
    private val sendArticleToJottyUseCase: SendArticleToJottyUseCase,
    private val trackEventUseCase: TrackEventUseCase,
    private val recordVoteUseCase: RecordVoteUseCase,
    private val getLabelWeightsUseCase: GetLabelWeightsUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    feverConnectionProvider: FeverConnectionProvider,
    private val imageLoader: ImageLoader,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val initialArticleId: Long = savedStateHandle.get<Long>(Screen.ArticleReader.NAV_ARG_ARTICLE_ID) ?: 0L
    private val feedId: Long = savedStateHandle.get<Long>(Screen.ArticleReader.NAV_ARG_FEED_ID) ?: 0L
    private val isSaved: Boolean = savedStateHandle.get<Boolean>(Screen.ArticleReader.NAV_ARG_IS_SAVED) ?: false
    private val _readerBaseUrl = MutableStateFlow<String?>(null)
    val readerBaseUrl: StateFlow<String?> = _readerBaseUrl.asStateFlow()

    init {
        viewModelScope.launch {
            _readerBaseUrl.value = feverConnectionProvider.getActiveConnection()
                ?.serverUrl
                ?.toReaderBaseUrl()
        }
    }

    // Snapshotted once, deliberately. The pager tracks position by integer index, so a live
    // list lets a sync insert articles above the one being read and silently swap the page
    // out from under the reader. Per-article state stays live through getArticleFlow(id).
    private val _uiState = MutableStateFlow<ArticleReaderUiState>(ArticleReaderUiState.Loading)
    val uiState: StateFlow<ArticleReaderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val source = if (isSaved) {
                getSavedArticlesUseCase()
            } else {
                getArticlesUseCase(feedId = if (feedId == 0L) null else feedId, showRead = true)
            }
            _uiState.value = try {
                // The reader is only reachable from a populated list, but a cold DB would
                // otherwise snapshot empty and never recover, so wait briefly for content.
                val list = withTimeoutOrNull(FIRST_LOAD_TIMEOUT_MS) {
                    source.first { it.isNotEmpty() }
                }.orEmpty()
                if (list.isNotEmpty()) {
                    val initialIndex = list.indexOfFirst { it.id == initialArticleId }
                        .coerceAtLeast(0)
                    ArticleReaderUiState.Success(list, initialIndex)
                } else {
                    ArticleReaderUiState.Error("No articles found")
                }
            } catch (e: Exception) {
                ArticleReaderUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    val articleReaderFontSizeSp: StateFlow<Int> = userPreferencesRepository.articleReaderFontSizeSp
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 18
        )

    val articleReaderFontFamily: StateFlow<String> = userPreferencesRepository.articleReaderFontFamily
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "serif"
        )

    val autoMarkReadDelaySec: StateFlow<Int> = userPreferencesRepository.autoMarkReadDelaySec
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 2
        )

    val externalLinkHandler: StateFlow<String> = userPreferencesRepository.externalLinkHandler
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "system_browser"
        )

    val prefetchCount: StateFlow<Int> = userPreferencesRepository.prefetchCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 2
        )

    val votePillFractionX: StateFlow<Float> = userPreferencesRepository.votePillFractionX
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VOTE_PILL_DEFAULT_FRACTION_X
        )

    val votePillFractionY: StateFlow<Float> = userPreferencesRepository.votePillFractionY
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VOTE_PILL_DEFAULT_FRACTION_Y
        )

    fun saveVotePillFraction(x: Float, y: Float) {
        viewModelScope.launch { userPreferencesRepository.setVotePillFraction(x, y) }
    }

    val labelWeights: StateFlow<List<LabelWeight>> = getLabelWeightsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val articleOrder: StateFlow<String> = userPreferencesRepository.articleOrder
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "latest"
        )

    val rankingAiEnabled: StateFlow<Boolean> = userPreferencesRepository.rankingAiEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _adjacentCacheStatus = MutableStateFlow<Map<Int, CacheStatus>>(emptyMap())
    val adjacentCacheStatus: StateFlow<Map<Int, CacheStatus>> = _adjacentCacheStatus.asStateFlow()

    private val articleFlowsLock = Any()
    private val articleFlows = object : LinkedHashMap<Long, Flow<Article?>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Flow<Article?>>): Boolean {
            return size > ARTICLE_FLOW_CACHE_LIMIT
        }
    }

    fun getArticleFlow(articleId: Long): Flow<Article?> {
        return synchronized(articleFlowsLock) {
            articleFlows.getOrPut(articleId) { getArticleFlowUseCase(articleId) }
        }
    }

    fun toggleReadState(articleId: Long, isRead: Boolean) {
        viewModelScope.launch {
            toggleReadUseCase(articleId, isRead)
        }
    }

    fun toggleVote(articleId: Long, currentVote: String?, newDirection: String) {
        val nextVote = if (currentVote == newDirection) null else newDirection
        val markRead = nextVote == "show_less"
        viewModelScope.launch {
            recordVoteUseCase(articleId = articleId, vote = nextVote, markRead = markRead)
        }
    }

    private val _jottyEvents = MutableSharedFlow<JottyEvent>(extraBufferCapacity = 4)
    val jottyEvents = _jottyEvents.asSharedFlow()

    fun toggleSaved(articleId: Long, isSaved: Boolean) {
        viewModelScope.launch {
            toggleSavedUseCase(articleId, isSaved)
            if (isSaved) {
                runCatching {
                    val article = getArticleFlow(articleId).first()
                    if (article != null) {
                        when (val result = sendArticleToJottyUseCase(article)) {
                            is JottyRepository.SendResult.Sent ->
                                _jottyEvents.tryEmit(JottyEvent.Sent)
                            is JottyRepository.SendResult.Queued ->
                                _jottyEvents.tryEmit(JottyEvent.Queued(result.reason))
                            is JottyRepository.SendResult.Disabled -> Unit
                        }
                    }
                }.onFailure {
                    _jottyEvents.tryEmit(JottyEvent.Queued(it.message ?: "Unknown error"))
                }
            }
        }
    }

    sealed interface JottyEvent {
        data object Sent : JottyEvent
        data class Queued(val reason: String) : JottyEvent
    }

    fun prefetchAdjacentArticles(currentIndex: Int, articles: List<Article>) {
        viewModelScope.launch {
            val radius = prefetchCount.value
            if (articles.isEmpty() || currentIndex !in articles.indices) {
                _adjacentCacheStatus.value = emptyMap()
                return@launch
            }

            val neighborIndices = ((currentIndex - radius)..(currentIndex + radius))
                .filter { index -> index in articles.indices && index != currentIndex }

            val loadingState = buildMap<Int, CacheStatus> {
                this[currentIndex] = CacheStatus.CACHED
                neighborIndices.forEach { index ->
                    this[index] = CacheStatus.LOADING
                }
            }
            _adjacentCacheStatus.value = loadingState

            if (radius <= 0) return@launch

            val finalStatus = buildMap<Int, CacheStatus> {
                this[currentIndex] = CacheStatus.CACHED
                neighborIndices.forEach { index ->
                    val article = articles[index]
                    prefetchHeroImage(article.thumbnailUrl)
                    val content = getArticleContentUseCase(article.id)
                    if (content != null) {
                        prefetchArticleImages(content)
                        this[index] = CacheStatus.CACHED
                    } else {
                        this[index] = CacheStatus.NOT_CACHED
                    }
                }
            }
            _adjacentCacheStatus.value = finalStatus
        }
    }

    /** Warms the neighbour's hero into memory so a swipe paints it on the first frame. */
    private fun prefetchHeroImage(url: String?) {
        if (url.isNullOrBlank()) return
        imageLoader.enqueue(
            buildHeroRequest(context, url, context.resources.displayMetrics.widthPixels)
        )
    }

    private fun prefetchArticleImages(html: String) {
        ArticleImageCache.extractImageUrls(html)
            .take(prefetchCount.value * 2)
            .forEach { imageUrl ->
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
                imageLoader.enqueue(request)
            }
    }

    private fun String.toReaderBaseUrl(): String {
        val normalized = trim().removeSuffix("/")
        return normalized.removeSuffix("/fever")
    }

    private var currentPageTrackingJob: Job? = null
    private var currentSettledArticleId: Long? = null
    private var currentSettledEnterTime: Long = 0L
    private var openEmittedForCurrent: Boolean = false

    fun onPageSettled(articleId: Long) {
        if (currentSettledArticleId == articleId) return

        currentSettledArticleId?.let { prevId ->
            val dwellMs = System.currentTimeMillis() - currentSettledEnterTime
            val dwellSec = (dwellMs / 1000).toInt()
            if (!openEmittedForCurrent && dwellSec in 1..4) {
                viewModelScope.launch {
                    trackEventUseCase(prevId, "skip", dwellSec)
                }
            }
        }

        currentPageTrackingJob?.cancel()
        currentSettledArticleId = articleId
        currentSettledEnterTime = System.currentTimeMillis()
        openEmittedForCurrent = false

        currentPageTrackingJob = viewModelScope.launch {
            delay(5000L)
            if (currentSettledArticleId == articleId) {
                trackEventUseCase(articleId, "open", 5)
                openEmittedForCurrent = true
            }
        }
    }

    fun onReaderClosed() {
        currentSettledArticleId?.let { articleId ->
            val dwellMs = System.currentTimeMillis() - currentSettledEnterTime
            val dwellSec = (dwellMs / 1000).toInt()
            if (!openEmittedForCurrent && dwellSec in 1..4) {
                viewModelScope.launch {
                    trackEventUseCase(articleId, "skip", dwellSec)
                }
            }
        }
        currentPageTrackingJob?.cancel()
        currentSettledArticleId = null
    }

    private companion object {
        const val ARTICLE_FLOW_CACHE_LIMIT = 20
        const val FIRST_LOAD_TIMEOUT_MS = 5000L
    }
}
