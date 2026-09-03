package co.chinho.readabilityreader.ui.articles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.ui.tablet.LocalTabletPaneWidth
import co.chinho.readabilityreader.ui.components.ArticleRow
import co.chinho.readabilityreader.ui.components.ArticleRowFullImage
import co.chinho.readabilityreader.ui.components.launchLazyListPageScroll
import co.chinho.readabilityreader.ui.components.rememberEInkPageButtonModifier
import co.chinho.readabilityreader.ui.theme.AppTheme
import co.chinho.readabilityreader.ui.theme.LocalEInkMode
import co.chinho.readabilityreader.ui.components.SlideshowImage
import coil.compose.AsyncImage

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

import co.chinho.readabilityreader.ui.components.SwipeableArticleRow
import co.chinho.readabilityreader.ui.components.rememberTwoTapConfirm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    onArticleClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    selectedArticleId: Long? = null,
    onFeedPaneToggleClick: (() -> Unit)? = null,
    viewModel: ArticleListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val currentFeed by viewModel.currentFeed.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val showFeedMetadata by viewModel.showFeedMetadata.collectAsStateWithLifecycle()
    val fontSizeSp by viewModel.fontSizeSp.collectAsStateWithLifecycle()
    val fontFamily by viewModel.fontFamily.collectAsStateWithLifecycle()
    val isCategoryScope = viewModel.isCategoryScope
    val isEInk = LocalEInkMode.current

    ArticleListContent(
        uiState = uiState,
        title = title,
        topBarFaviconUrl = if (showFeedMetadata) null else currentFeed?.faviconUrl,
        unreadCount = unreadCount,
        viewMode = viewMode,
        showFeedMetadata = showFeedMetadata,
        fontSizeSp = fontSizeSp,
        fontFamily = fontFamily,
        isEInk = isEInk,
        onArticleClick = onArticleClick,
        onBackClick = onBackClick,
        onVote = { articleId, currentVote, direction -> viewModel.voteArticle(articleId, currentVote, direction) },
        onToggleViewMode = viewModel::toggleViewMode,
        onMarkRead = if (isCategoryScope) viewModel::markGroupRead else null,
        onFetchExtraImages = viewModel::fetchArticleExtraImages,
        onVisibleRangeChanged = viewModel::onVisibleRangeChanged,
        selectedArticleId = selectedArticleId,
        onFeedPaneToggleClick = onFeedPaneToggleClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun ArticleListContent(
    uiState: ArticleListUiState,
    title: String,
    topBarFaviconUrl: String?,
    unreadCount: Int,
    viewMode: String,
    showFeedMetadata: Boolean,
    isEInk: Boolean,
    onArticleClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    onVote: (Long, String?, String) -> Unit = { _, _, _ -> },
    onToggleViewMode: () -> Unit,
    onMarkRead: (() -> Unit)? = null,
    fontSizeSp: Int = 16,
    fontFamily: String = "system",
    onFetchExtraImages: (suspend (Long) -> List<SlideshowImage>)? = null,
    onVisibleRangeChanged: (Int, Int) -> Unit = { _, _ -> },
    selectedArticleId: Long? = null,
    onFeedPaneToggleClick: (() -> Unit)? = null,
) {
    val isInTabletPane = LocalTabletPaneWidth.current != null
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var scrollAreaHeightPx by remember { mutableStateOf(0) }
    val pageButtonModifier = rememberEInkPageButtonModifier(enabled = isEInk) { direction ->
        coroutineScope.launchLazyListPageScroll(
            state = listState,
            direction = direction,
            viewportHeightPx = scrollAreaHeightPx,
            pageFraction = 0.6f,
        )
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            (visible.firstOrNull()?.index ?: 0) to (visible.lastOrNull()?.index ?: 0)
        }
            .distinctUntilChanged()
            .debounce(PREFETCH_DEBOUNCE_MS)
            .collect { (first, last) -> onVisibleRangeChanged(first, last) }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(start = 8.dp, end = if (isInTabletPane) 0.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onFeedPaneToggleClick != null) {
                            IconButton(onClick = onFeedPaneToggleClick) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Expand feed sources",
                                )
                            }
                            if (!isInTabletPane) {
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                        if (isEInk) {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        } else if (!isInTabletPane) {
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        if (!showFeedMetadata) {
                            if (!topBarFaviconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = topBarFaviconUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Crop,
                                    colorFilter = if (isEInk) {
                                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                                    } else null
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    ViewModeToggleButton(
                        viewMode = viewMode,
                        isEInk = isEInk,
                        onToggleViewMode = onToggleViewMode,
                    )
                    if (isInTabletPane) {
                        if (unreadCount > 0) {
                            UnreadCountBadge(
                                unreadCount = unreadCount,
                                isEInk = isEInk,
                                onMarkRead = onMarkRead,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .padding(end = 8.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            if (unreadCount > 0) {
                                UnreadCountBadge(
                                    unreadCount = unreadCount,
                                    isEInk = isEInk,
                                    onMarkRead = onMarkRead,
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .then(pageButtonModifier)
                .onSizeChanged { scrollAreaHeightPx = it.height },
            color = MaterialTheme.colorScheme.background
        ) {
            when (val state = uiState) {
                is ArticleListUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (isEInk) {
                            Text("Loading...", style = MaterialTheme.typography.bodyLarge)
                        } else {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                is ArticleListUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    }
                }

                is ArticleListUiState.Success -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.articles, key = { it.id }) { article ->
                            SwipeableArticleRow(
                                article = article,
                                onVote = onVote,
                            ) {
                                if (viewMode == "full_image") {
                                    ArticleRowFullImage(
                                        article = article,
                                        onClick = { onArticleClick(article.id) },
                                        showFeedMetadata = showFeedMetadata,
                                        fontSizeSp = fontSizeSp,
                                        fontFamily = fontFamily,
                                        selected = selectedArticleId == article.id,
                                        onFetchExtraImages = onFetchExtraImages,
                                    )
                                } else {
                                    ArticleRow(
                                        article = article,
                                        onClick = { onArticleClick(article.id) },
                                        showFeedMetadata = showFeedMetadata,
                                        fontSizeSp = fontSizeSp,
                                        fontFamily = fontFamily,
                                        selected = selectedArticleId == article.id,
                                        onFetchExtraImages = onFetchExtraImages,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeToggleButton(
    viewMode: String,
    isEInk: Boolean,
    onToggleViewMode: () -> Unit,
) {
    val isFullImage = viewMode == "full_image"
    val imageVector = if (isFullImage) {
        Icons.AutoMirrored.Filled.ViewList
    } else {
        Icons.Filled.Image
    }
    val contentDescription = if (isFullImage) {
        "Switch to standard view"
    } else {
        "Switch to full image view"
    }
    val tint = MaterialTheme.colorScheme.onSurface

    if (isEInk) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleViewMode,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
    } else {
        IconButton(onClick = onToggleViewMode) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}

@Composable
private fun UnreadCountBadge(
    unreadCount: Int,
    isEInk: Boolean,
    onMarkRead: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val confirmState = if (onMarkRead != null) {
        rememberTwoTapConfirm(onConfirm = onMarkRead)
    } else null
    val showReadAll = confirmState?.armed == true

    Surface(
        shape = MaterialTheme.shapes.small,
        color = when {
            isEInk -> Color.Black
            showReadAll -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = when {
            isEInk -> Color.White
            showReadAll -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        tonalElevation = 0.dp,
        modifier = modifier.then(
            if (confirmState != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    confirmState.tap()
                }
            } else Modifier
        ),
    ) {
        Text(
            text = if (showReadAll) "Read all" else unreadCount.toString(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private val previewArticles = listOf(
    Article(
        id = 101L,
        feedId = 1L,
        title = "Kotlin 2.0 and Compose: Practical migration notes",
        url = "https://example.com/kotlin-compose-migration",
        content = "Migration notes for upgrading Kotlin and Compose in a production app.",
        publishedAt = 1_717_171_717_000,
        isRead = false,
        isSaved = false,
        thumbnailUrl = "https://reader.example.com/api/reader/cached-image/063e451a9a16c246ac611af714a799a5.jpg",
        isCached = true,
        feedTitle = "Android Weekly",
        feedFaviconUrl = "https://reader.example.com/api/reader/favicon/1"
    ),
    Article(
        id = 102L,
        feedId = 1L,
        title = "Designing high-contrast interfaces for e-ink readers",
        url = "https://example.com/e-ink-contrast",
        content = "A deep dive into typography and contrast for low refresh displays.",
        publishedAt = 1_717_181_818_000,
        isRead = true,
        isSaved = true,
        thumbnailUrl = null,
        isCached = false,
        feedTitle = "UX Research",
        feedFaviconUrl = "https://reader.example.com/api/reader/favicon/1"
    ),
    Article(
        id = 103L,
        feedId = 2L,
        title = "Offline-first Android: resilient sync patterns",
        url = "https://example.com/offline-first-sync",
        content = "Techniques for robust sync and conflict handling.",
        publishedAt = 1_717_191_919_000,
        isRead = false,
        isSaved = false,
        thumbnailUrl = null,
        isCached = true,
        feedTitle = "Mobile Architecture",
        feedFaviconUrl = "https://reader.example.com/api/reader/favicon/2"
    ),
)

@Preview(showBackground = true, name = "Article List - Standard")
@Composable
private fun ArticleListPreviewStandard() {
    AppTheme(isEInkMode = false, isDarkTheme = false) {
        ArticleListContent(
            uiState = ArticleListUiState.Success(previewArticles),
            title = "All Sources",
            topBarFaviconUrl = null,
            unreadCount = 12,
            viewMode = "standard",
            showFeedMetadata = true,
            isEInk = false,
            onArticleClick = {},
            onBackClick = {},
            onToggleViewMode = {},
        )
    }
}

@Preview(showBackground = true, name = "Article List - Full Image")
@Composable
private fun ArticleListPreviewFullImage() {
    AppTheme(isEInkMode = false, isDarkTheme = false) {
        ArticleListContent(
            uiState = ArticleListUiState.Success(previewArticles),
            title = "Tech Feed",
            topBarFaviconUrl = "https://reader.example.com/api/reader/favicon/1",
            unreadCount = 4,
            viewMode = "full_image",
            showFeedMetadata = false,
            isEInk = false,
            onArticleClick = {},
            onBackClick = {},
            onToggleViewMode = {},
        )
    }
}

@Preview(showBackground = true, name = "Article List - Loading (E-Ink)")
@Composable
private fun ArticleListPreviewLoadingEInk() {
    AppTheme(isEInkMode = true, isDarkTheme = false) {
        ArticleListContent(
            uiState = ArticleListUiState.Loading,
            title = "All Sources",
            topBarFaviconUrl = null,
            unreadCount = 0,
            viewMode = "standard",
            showFeedMetadata = true,
            isEInk = true,
            onArticleClick = {},
            onBackClick = {},
            onToggleViewMode = {},
        )
    }
}
