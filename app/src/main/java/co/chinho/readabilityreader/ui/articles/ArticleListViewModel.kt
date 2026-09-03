package co.chinho.readabilityreader.ui.articles

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.chinho.readabilityreader.domain.model.Feed
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.repository.FeedRepository
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.domain.usecase.GetArticleImagesUseCase
import co.chinho.readabilityreader.domain.usecase.GetArticlesUseCase
import co.chinho.readabilityreader.domain.usecase.ToggleReadUseCase
import co.chinho.readabilityreader.domain.usecase.UpdateFeedViewModeUseCase
import co.chinho.readabilityreader.ui.components.ArticleThumbnailRequest
import co.chinho.readabilityreader.ui.components.SlideshowImage
import co.chinho.readabilityreader.ui.navigation.Screen
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

import co.chinho.readabilityreader.domain.usecase.MarkGroupReadUseCase
import co.chinho.readabilityreader.domain.usecase.RecordVoteUseCase

sealed interface ArticleListUiState {
    data object Loading : ArticleListUiState
    data class Success(val articles: List<Article>) : ArticleListUiState
    data class Error(val message: String) : ArticleListUiState
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ArticleListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getArticlesUseCase: GetArticlesUseCase,
    private val getArticleImagesUseCase: GetArticleImagesUseCase,
    private val toggleReadUseCase: ToggleReadUseCase,
    private val recordVoteUseCase: RecordVoteUseCase,
    private val markGroupReadUseCase: MarkGroupReadUseCase,
    private val updateFeedViewModeUseCase: UpdateFeedViewModeUseCase,
    private val feedRepository: FeedRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val imageLoader: ImageLoader,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val scope: ArticleScope = when {
        savedStateHandle.get<Long>(Screen.ArticleList.NAV_ARG_FEED_ID)?.takeIf { it > 0 } != null ->
            ArticleScope.SingleFeed(savedStateHandle.get<Long>(Screen.ArticleList.NAV_ARG_FEED_ID)!!)
        savedStateHandle.get<Long>(Screen.ArticleList.NAV_ARG_GROUP_ID)?.takeIf { it > 0 } != null ->
            ArticleScope.Category(savedStateHandle.get<Long>(Screen.ArticleList.NAV_ARG_GROUP_ID)!!)
        else ->
            ArticleScope.AllSources
    }

    val isCategoryScope: Boolean = scope is ArticleScope.Category

    private val targetFeedId: Long? = (scope as? ArticleScope.SingleFeed)?.feedId
    private val targetGroupId: Long? = (scope as? ArticleScope.Category)?.groupId

    val title: StateFlow<String> = when (scope) {
        is ArticleScope.SingleFeed -> feedRepository.getFeed(scope.feedId)
            .map { it?.title ?: "Articles" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Articles")
        is ArticleScope.AllSources -> MutableStateFlow("All Sources")
        is ArticleScope.Category -> feedRepository.getGroups()
            .map { groups -> groups.find { it.id == scope.groupId }?.title ?: "Category" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Category")
    }

    val currentFeed: StateFlow<Feed?> = if (targetFeedId == null) {
        MutableStateFlow(null)
    } else {
        feedRepository.getFeed(targetFeedId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    val unreadCount: StateFlow<Int> = when (scope) {
        is ArticleScope.SingleFeed -> feedRepository.getFeed(scope.feedId)
            .map { it?.unreadCount ?: 0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        is ArticleScope.AllSources -> feedRepository.getFeeds()
            .map { feeds -> feeds.sumOf { it.unreadCount } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        is ArticleScope.Category -> feedRepository.getGroups()
            .map { groups ->
                groups.find { it.id == scope.groupId }
                    ?.feeds
                    ?.sumOf { it.unreadCount }
                    ?: 0
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    }

    // A feed carries its own listViewMode column; the aggregated scopes have no row to hang
    // it on, so All Sources and each category keep theirs in user preferences instead.
    val viewMode: StateFlow<String> = when (scope) {
        is ArticleScope.SingleFeed -> feedRepository.getFeed(scope.feedId)
            .map { it?.listViewMode ?: STANDARD_VIEW_MODE }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), STANDARD_VIEW_MODE)
        is ArticleScope.AllSources -> userPreferencesRepository.allSourcesFullImage
            .map { if (it) FULL_IMAGE_VIEW_MODE else STANDARD_VIEW_MODE }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), STANDARD_VIEW_MODE)
        is ArticleScope.Category -> userPreferencesRepository.fullImageCategoryIds
            .map { if (scope.groupId in it) FULL_IMAGE_VIEW_MODE else STANDARD_VIEW_MODE }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), STANDARD_VIEW_MODE)
    }

    val showFeedMetadata: StateFlow<Boolean> = MutableStateFlow(targetFeedId == null)

    val fontSizeSp: StateFlow<Int> = userPreferencesRepository.articleListFontSizeSp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16)

    val fontFamily: StateFlow<String> = userPreferencesRepository.articleListFontFamily
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    // IDs of articles that have appeared unread during this viewing session. When
    // "Show read articles" is off, these remain visible (in read styling) after being
    // read, and only drop out when the screen is re-entered and the ViewModel is recreated.
    private val sessionStickyIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<ArticleListUiState> = combine(
        userPreferencesRepository.showReadArticles,
        sessionStickyIds,
    ) { showRead, stickyIds ->
        // Collapse to the values the query actually binds. With "Show read articles" on -- the
        // default -- sticky ids are irrelevant, so every newly-seen unread article would
        // otherwise change this key and make flatMapLatest cancel and re-subscribe the Room
        // query. Re-subscribing per sync chunk is more expensive than the emission churn this
        // whole change set exists to remove.
        showRead to (if (showRead) emptyList() else stickyIds.toList())
    }
        .distinctUntilChanged()
        .flatMapLatest { (showRead, effectiveStickyIds) ->
            val articlesFlow = getArticlesUseCase(
                feedId = targetFeedId,
                showRead = showRead,
                groupId = targetGroupId,
                stickyIds = effectiveStickyIds,
            )
            // Only the favicon is consumed here, but getFeeds() re-emits on every unread-count
            // write, which during a sync is constant. Narrowing to the favicon map and
            // de-duplicating keeps feed churn from multiplying article-list emissions.
            val faviconsFlow = feedRepository.getFeeds()
                .map { feeds -> feeds.associate { it.id to it.faviconUrl } }
                .distinctUntilChanged()

            combine(articlesFlow, faviconsFlow) { articles, faviconByFeedId ->
                val currentSticky = sessionStickyIds.value
                val newUnread = mutableListOf<Long>()
                val withFavicons = articles.map { article ->
                    if (!article.isRead && article.id !in currentSticky) {
                        newUnread.add(article.id)
                    }
                    article.copy(feedFaviconUrl = faviconByFeedId[article.feedId])
                }
                if (newUnread.isNotEmpty()) {
                    sessionStickyIds.update { it + newUnread }
                }
                withFavicons
            }
        }
        .map { ArticleListUiState.Success(it) as ArticleListUiState }
        .catch { emit(ArticleListUiState.Error(it.message ?: "Unknown error")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ArticleListUiState.Loading
        )

    private var prefetchJob: Job? = null

    // Bounded LRU: a long scroll session would otherwise grow an unbounded key set.
    private val prewarmedUrls = object : LinkedHashMap<String, Unit>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Unit>): Boolean =
            size > PREWARM_MEMORY
    }

    fun onVisibleRangeChanged(firstVisible: Int, lastVisible: Int) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch { prefetchCovers(firstVisible, lastVisible) }
    }

    private suspend fun prefetchCovers(firstVisible: Int, lastVisible: Int) {
        val articles = (uiState.value as? ArticleListUiState.Success)?.articles ?: return
        val window = prefetchWindow(firstVisible, lastVisible, articles.size)
        if (window.isEmpty()) return

        // Read the live value rather than the WhileSubscribed StateFlow: a prefetch can run
        // before the UI has subscribed, and the wrong mode produces the wrong request size.
        val fullImageMode = runCatching {
            when (scope) {
                is ArticleScope.SingleFeed ->
                    feedRepository.getFeed(scope.feedId).first()?.listViewMode == FULL_IMAGE_VIEW_MODE
                is ArticleScope.AllSources -> userPreferencesRepository.allSourcesFullImage.first()
                is ArticleScope.Category ->
                    scope.groupId in userPreferencesRepository.fullImageCategoryIds.first()
            }
        }.getOrDefault(false)

        val spec = ArticleThumbnailRequest.specFor(context, fullImageMode)
        val variant = "${spec.widthPx}x${spec.heightPx}"

        for (index in window) {
            val url = articles[index].thumbnailUrl?.takeIf(String::isNotBlank) ?: continue
            if (prewarmedUrls.put("$variant|$url", Unit) != null) continue
            imageLoader.enqueue(ArticleThumbnailRequest.build(context, url, spec))
        }
    }

    fun toggleReadState(article: Article) {
        viewModelScope.launch {
            toggleReadUseCase(article.id, !article.isRead)
        }
    }

    fun voteArticle(articleId: Long, currentVote: String?, newDirection: String) {
        val nextVote = if (currentVote == newDirection) null else newDirection
        val markRead = nextVote == "show_less"
        viewModelScope.launch {
            recordVoteUseCase(articleId = articleId, vote = nextVote, markRead = markRead)
        }
    }

    fun toggleViewMode() {
        val fullImage = viewMode.value != FULL_IMAGE_VIEW_MODE
        viewModelScope.launch {
            runCatching {
                when (scope) {
                    is ArticleScope.SingleFeed -> updateFeedViewModeUseCase(
                        scope.feedId,
                        if (fullImage) FULL_IMAGE_VIEW_MODE else STANDARD_VIEW_MODE,
                    )
                    is ArticleScope.AllSources ->
                        userPreferencesRepository.setAllSourcesFullImage(fullImage)
                    is ArticleScope.Category ->
                        userPreferencesRepository.setCategoryFullImage(scope.groupId, fullImage)
                }
            }
        }
    }

    fun markGroupRead() {
        val groupId = (scope as? ArticleScope.Category)?.groupId ?: return
        viewModelScope.launch {
            runCatching { markGroupReadUseCase(groupId) }
        }
    }

    suspend fun fetchArticleExtraImages(articleId: Long): List<SlideshowImage> {
        return runCatching {
            getArticleImagesUseCase(articleId).map {
                SlideshowImage(src = it.url, focalX = it.focalX, focalY = it.focalY)
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREWARM_MEMORY = 512
        const val STANDARD_VIEW_MODE = "standard"
        const val FULL_IMAGE_VIEW_MODE = "full_image"
    }
}
