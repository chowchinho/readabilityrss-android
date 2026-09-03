package co.chinho.readabilityreader.ui.reader

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.ui.components.EInkPageButtonDirection
import co.chinho.readabilityreader.ui.components.getFocalAlignment
import co.chinho.readabilityreader.ui.components.rememberEInkPageButtonModifier
import co.chinho.readabilityreader.util.ExternalLinkOpener
import co.chinho.readabilityreader.ui.tablet.LocalTabletPaneWidth
import co.chinho.readabilityreader.ui.theme.LocalEInkMode
import co.chinho.readabilityreader.ui.theme.SlateDarkReaderBackground
import co.chinho.readabilityreader.ui.theme.SlateLightReaderBackground
import co.chinho.readabilityreader.ui.theme.toComposeFontFamily
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import co.chinho.readabilityreader.domain.model.LabelWeight
import co.chinho.readabilityreader.ui.theme.SyncCompletedGreen

@Composable
fun ArticleReaderScreen(
    onBackClick: () -> Unit,
    showBackButton: Boolean = true,
    onArticleChanged: (Long) -> Unit = {},
    viewModel: ArticleReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readerFontSizeSp by viewModel.articleReaderFontSizeSp.collectAsStateWithLifecycle()
    val readerFontFamily by viewModel.articleReaderFontFamily.collectAsStateWithLifecycle()
    val autoMarkReadDelaySec by viewModel.autoMarkReadDelaySec.collectAsStateWithLifecycle()
    val externalLinkHandler by viewModel.externalLinkHandler.collectAsStateWithLifecycle()
    val prefetchCount by viewModel.prefetchCount.collectAsStateWithLifecycle()
    val cacheStatus by viewModel.adjacentCacheStatus.collectAsStateWithLifecycle()
    val readerBaseUrl by viewModel.readerBaseUrl.collectAsStateWithLifecycle()
    val labelWeights by viewModel.labelWeights.collectAsStateWithLifecycle()
    val articleOrder by viewModel.articleOrder.collectAsStateWithLifecycle()
    val rankingAiEnabled by viewModel.rankingAiEnabled.collectAsStateWithLifecycle()
    val isEInk = LocalEInkMode.current
    val toastContext = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.jottyEvents.collect { event ->
            val message = when (event) {
                is ArticleReaderViewModel.JottyEvent.Sent -> "Sent to Jotty"
                is ArticleReaderViewModel.JottyEvent.Queued -> "Queued for Jotty (${event.reason})"
            }
            android.widget.Toast.makeText(toastContext, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val isInTabletPane = LocalTabletPaneWidth.current != null
    val baseBackground = MaterialTheme.colorScheme.background
    val readerBackground = if (isInTabletPane && !isEInk) {
        if (baseBackground.red > 0.5f) SlateLightReaderBackground else SlateDarkReaderBackground
    } else {
        baseBackground
    }

    Box(modifier = Modifier.fillMaxSize().background(readerBackground)) {
        when (val state = uiState) {
            is ArticleReaderUiState.Loading -> {
                if (isEInk) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading...")
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                }
            }
            is ArticleReaderUiState.Error -> {
                Text(state.message, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
            }
            is ArticleReaderUiState.Success -> {
                val pagerState = rememberPagerState(initialPage = state.initialIndex) {
                    state.articles.size
                }
                val coroutineScope = rememberCoroutineScope()
                val articleScrollStates = remember { mutableStateMapOf<Long, androidx.compose.foundation.ScrollState>() }
                var readerViewportHeightPx by remember { mutableIntStateOf(0) }
                val pageButtonModifier = rememberEInkPageButtonModifier(enabled = isEInk) { direction ->
                    if (readerViewportHeightPx <= 0) return@rememberEInkPageButtonModifier
                    val currentArticle = state.articles.getOrNull(pagerState.currentPage) ?: return@rememberEInkPageButtonModifier
                    val scrollState = articleScrollStates[currentArticle.id] ?: return@rememberEInkPageButtonModifier
                    val distancePx = readerViewportHeightPx * 0.85f
                    coroutineScope.launch {
                        scrollState.animateScrollTo(
                            (scrollState.value + if (direction == EInkPageButtonDirection.Down) distancePx else -distancePx)
                                .toInt()
                                .coerceIn(0, scrollState.maxValue)
                        )
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        viewModel.onReaderClosed()
                    }
                }

                LaunchedEffect(pagerState.currentPage, autoMarkReadDelaySec) {
                    val currentArticle = state.articles.getOrNull(pagerState.currentPage)
                        ?: return@LaunchedEffect
                    onArticleChanged(currentArticle.id)
                    viewModel.onPageSettled(currentArticle.id)
                    if (!currentArticle.isRead && autoMarkReadDelaySec > 0) {
                        delay(autoMarkReadDelaySec * 1000L)
                        viewModel.toggleReadState(currentArticle.id, true)
                    }
                }

                LaunchedEffect(pagerState.currentPage, state.articles, prefetchCount) {
                    viewModel.prefetchAdjacentArticles(
                        currentIndex = pagerState.currentPage,
                        articles = state.articles,
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(pageButtonModifier)
                        .onSizeChanged { readerViewportHeightPx = it.height },
                    pageSpacing = 16.dp,
                    key = { page -> state.articles[page].id },
                ) { page ->
                    val articleSummary = state.articles[page]
                    val fullArticle by viewModel.getArticleFlow(articleSummary.id).collectAsStateWithLifecycle(initialValue = null)
                    val scrollState = articleScrollStates.getOrPut(articleSummary.id) { androidx.compose.foundation.ScrollState(0) }
                    ArticleContent(
                        article = fullArticle ?: articleSummary,
                        isLoaded = fullArticle != null,
                        readerFontSizeSp = readerFontSizeSp,
                        readerFontFamily = readerFontFamily,
                        externalLinkHandler = externalLinkHandler,
                        readerBaseUrl = readerBaseUrl,
                        scrollState = scrollState,
                        onToggleVote = { articleId, currentVote, direction ->
                            viewModel.toggleVote(articleId, currentVote, direction)
                        },
                        labelWeights = labelWeights,
                        isPersonalisedSort = articleOrder == "personalised" && rankingAiEnabled,
                    )
                }

                val currentArticleSummary = state.articles.getOrNull(pagerState.currentPage)
                    ?: state.articles.first()
                val currentArticle by viewModel.getArticleFlow(currentArticleSummary.id).collectAsStateWithLifecycle(initialValue = currentArticleSummary)
                val currentActiveArticle = currentArticle ?: currentArticleSummary

                ReaderTopBar(
                    article = currentActiveArticle,
                    pagerState = pagerState,
                    articles = state.articles,
                    cacheStatus = cacheStatus,
                    prefetchCount = prefetchCount,
                    externalLinkHandler = externalLinkHandler,
                    onToggleRead = { viewModel.toggleReadState(currentActiveArticle.id, !currentActiveArticle.isRead) },
                    onToggleSaved = { viewModel.toggleSaved(currentActiveArticle.id, !currentActiveArticle.isSaved) },
                    onBackClick = onBackClick,
                    showBackButton = showBackButton,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                val storedPillX by viewModel.votePillFractionX.collectAsStateWithLifecycle()
                val storedPillY by viewModel.votePillFractionY.collectAsStateWithLifecycle()
                var containerSize by remember { mutableStateOf(IntSize.Zero) }
                var pillSize by remember { mutableStateOf(IntSize.Zero) }
                // Held only for the duration of a drag; the resting place is the fraction, which
                // survives a fold or rotation where raw pixels would put the pill off-screen.
                var dragPx by remember { mutableStateOf<Offset?>(null) }
                var localPillFraction by remember { mutableStateOf<Pair<Float, Float>?>(null) }
                val pillMarginPx = with(LocalDensity.current) { 24.dp.roundToPx() }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSize = it }
                ) {
                    val fractionX = localPillFraction?.first ?: storedPillX
                    val fractionY = localPillFraction?.second ?: storedPillY
                    val resting = votePillOffset(
                        fractionX = fractionX,
                        fractionY = fractionY,
                        containerWidth = containerSize.width,
                        containerHeight = containerSize.height,
                        pillWidth = pillSize.width,
                        pillHeight = pillSize.height,
                        marginPx = pillMarginPx,
                    )
                    val shown = dragPx ?: Offset(resting.x.toFloat(), resting.y.toFloat())
                    // The gesture lambdas outlive the composition that created them, so they must
                    // read the resting place through state. Capturing it directly meant a second
                    // drag started from wherever the pill first sat, snapping it back there.
                    val restingState = rememberUpdatedState(
                        Offset(resting.x.toFloat(), resting.y.toFloat())
                    )

                    FloatingVoteButtons(
                        article = currentActiveArticle,
                        onToggleVote = { articleId, currentVote, direction ->
                            viewModel.toggleVote(articleId, currentVote, direction)
                        },
                        modifier = Modifier
                            .offset { IntOffset(shown.x.roundToInt(), shown.y.roundToInt()) }
                            .onSizeChanged { pillSize = it }
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragPx = restingState.value },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        val from = dragPx ?: restingState.value
                                        val maxX = (containerSize.width - pillSize.width - pillMarginPx)
                                            .coerceAtLeast(pillMarginPx)
                                        val maxY = (containerSize.height - pillSize.height - pillMarginPx)
                                            .coerceAtLeast(pillMarginPx)
                                        dragPx = Offset(
                                            (from.x + delta.x).coerceIn(pillMarginPx.toFloat(), maxX.toFloat()),
                                            (from.y + delta.y).coerceIn(pillMarginPx.toFloat(), maxY.toFloat()),
                                        )
                                    },
                                    onDragEnd = {
                                        dragPx?.let { dropped ->
                                            val fraction = votePillFraction(
                                                offsetX = dropped.x,
                                                offsetY = dropped.y,
                                                containerWidth = containerSize.width,
                                                containerHeight = containerSize.height,
                                                pillWidth = pillSize.width,
                                                pillHeight = pillSize.height,
                                                marginPx = pillMarginPx,
                                            )
                                            // Applied locally first so the pill does not snap back
                                            // for the frames before DataStore reports the write.
                                            localPillFraction = fraction
                                            viewModel.saveVotePillFraction(fraction.first, fraction.second)
                                        }
                                        dragPx = null
                                    },
                                    onDragCancel = { dragPx = null },
                                )
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingVoteButtons(
    article: Article,
    onToggleVote: (Long, String?, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEInk = LocalEInkMode.current
    val isUpvoted = article.vote == "show_more"
    val isDownvoted = article.vote == "show_less"

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (isEInk) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh ?: MaterialTheme.colorScheme.surface
        },
        border = if (isEInk) BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface) else null,
        tonalElevation = 4.dp,
        shadowElevation = if (isEInk) 0.dp else 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onToggleVote(article.id, article.vote, "show_more") },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (isUpvoted) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = "Upvote",
                    tint = if (isEInk) {
                        MaterialTheme.colorScheme.onSurface
                    } else if (isUpvoted) {
                        SyncCompletedGreen
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = { onToggleVote(article.id, article.vote, "show_less") },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (isDownvoted) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                    contentDescription = "Downvote",
                    tint = if (isEInk) {
                        MaterialTheme.colorScheme.onSurface
                    } else if (isDownvoted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ArticleContent(
    article: Article,
    isLoaded: Boolean = true,
    readerFontSizeSp: Int,
    readerFontFamily: String,
    externalLinkHandler: String,
    readerBaseUrl: String?,
    scrollState: androidx.compose.foundation.ScrollState,
    onToggleVote: (Long, String?, String) -> Unit = { _, _, _ -> },
    labelWeights: List<LabelWeight> = emptyList(),
    isPersonalisedSort: Boolean = false,
) {
    val isEInk = LocalEInkMode.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = 60.dp, bottom = 48.dp)
    ) {
        val horizontalContentPadding = if (isEInk || maxWidth >= 600.dp) 32.dp else 16.dp
        val contentWidth = minOf(maxWidth, 1000.dp)

        Column(
            modifier = Modifier
                .widthIn(max = 1000.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val heroUrl = article.thumbnailUrl
            if (!heroUrl.isNullOrBlank()) {
                HeroImage(
                    url = heroUrl,
                    focalX = article.focalX,
                    focalY = article.focalY,
                    availableWidth = contentWidth,
                    isEInk = isEInk,
                )
            }

            var showBreakdown by remember(article.id) { mutableStateOf(false) }

            Column(
                modifier = Modifier.padding(
                    start = horizontalContentPadding,
                    end = horizontalContentPadding,
                    top = 16.dp,
                    bottom = 16.dp
                )
            ) {
                SelectionContainer {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = readerFontFamily.toComposeFontFamily()
                    )
                }
                if (article.feedTitle != null || isPersonalisedSort || article.score != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (!article.feedFaviconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = article.feedFaviconUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    colorFilter = if (LocalEInkMode.current) {
                                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                                    } else null
                                )
                            } else if (article.feedTitle != null) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (article.feedTitle != null) {
                                Text(
                                    text = article.feedTitle,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = readerFontFamily.toComposeFontFamily(),
                                )
                            }
                        }

                        if (isPersonalisedSort || article.score != null) {
                            IconButton(
                                onClick = { showBreakdown = !showBreakdown },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = if (showBreakdown) Icons.Default.Info else Icons.Outlined.Info,
                                    contentDescription = "Score breakdown",
                                    tint = if (showBreakdown) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                if (showBreakdown) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ScoreBreakdownPanel(
                        article = article,
                        labelWeights = labelWeights,
                    )
                }
            }

            val hasContent = !article.content.isNullOrBlank()
            if (hasContent) {
                val currentOnToggleVote by rememberUpdatedState(onToggleVote)
                val selectionActions = remember(article.id, article.vote) {
                    listOf(
                        // Hollow until the vote is cast, filled once it is — the same read as
                        // the floating pill's outlined/filled thumbs, in a glyph the selection
                        // toolbar will render (it ignores setIcon on all but its leading slot).
                        SelectionAction(
                            title = if (article.vote == "show_more") "▲" else "△",
                        ) { currentOnToggleVote(article.id, article.vote, "show_more") },
                        SelectionAction(
                            title = if (article.vote == "show_less") "▼" else "▽",
                        ) { currentOnToggleVote(article.id, article.vote, "show_less") },
                    )
                }
                HtmlContent(
                    html = article.content.orEmpty(),
                    articleUrl = article.url,
                    readerBaseUrl = readerBaseUrl,
                    fontSize = readerFontSizeSp.sp,
                    fontFamily = readerFontFamily,
                    externalLinkHandler = externalLinkHandler,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = horizontalContentPadding, end = horizontalContentPadding),
                    selectionActions = selectionActions,
                )
            } else if (isLoaded) {
                Text(
                    text = "No content cached.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = readerFontSizeSp.sp,
                    fontFamily = readerFontFamily.toComposeFontFamily(),
                    modifier = Modifier.padding(horizontal = horizontalContentPadding)
                )
            }

            HorizontalDivider(
                modifier = Modifier
                    .width(40.dp)
                    .padding(top = 80.dp)
                    .align(Alignment.CenterHorizontally),
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
            Text(
                text = "Feed ID: #${article.feedId} | Article ID: #${article.id}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 24.dp)
                    .align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun HeroImage(
    url: String,
    focalX: Int,
    focalY: Int,
    availableWidth: Dp,
    isEInk: Boolean,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imageLoader = context.imageLoader
    val maxHeightDp = LocalConfiguration.current.screenHeightDp * HERO_MAX_VIEWPORT_FRACTION

    var aspectRatio by remember(url) { mutableStateOf(probeCachedAspect(imageLoader, url)) }
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return

    val layout = computeHeroLayout(availableWidth.value, aspectRatio, maxHeightDp)
    if (layout.heightDp <= 0f) return

    val request = remember(url, availableWidth) {
        buildHeroRequest(context, url, with(density) { availableWidth.roundToPx() })
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .height(layout.heightDp.dp),
        contentScale = ContentScale.Crop,
        alignment = if (layout.isCropped) {
            getFocalAlignment(focalX, focalY)
        } else {
            Alignment.Center
        },
        onState = { state ->
            when (state) {
                is AsyncImagePainter.State.Success -> {
                    val size = state.painter.intrinsicSize
                    if (size.width > 0f && size.height > 0f) {
                        aspectRatio = size.width / size.height
                    }
                }
                is AsyncImagePainter.State.Error -> failed = true
                else -> Unit
            }
        },
        colorFilter = if (isEInk) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        } else null,
    )
}

@Composable
private fun ReaderTopBar(
    article: Article,
    pagerState: PagerState,
    articles: List<Article>,
    cacheStatus: Map<Int, CacheStatus>,
    prefetchCount: Int,
    externalLinkHandler: String,
    onToggleRead: () -> Unit,
    onToggleSaved: () -> Unit,
    onBackClick: () -> Unit,
    showBackButton: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isEInk = LocalEInkMode.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBackButton && isEInk) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(onClick = onToggleRead) {
                    Icon(
                        imageVector = if (article.isRead) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = "Toggle Read",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onToggleSaved) {
                    Icon(
                        imageVector = if (article.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Toggle Saved",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Pager Indicator & Center Content
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                PagerIndicator(
                    pagerState = pagerState,
                    articleCount = articles.size,
                    cacheStatus = cacheStatus,
                    prefetchCount = prefetchCount,
                    isEInk = isEInk,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    ExternalLinkOpener.open(context, article.url, externalLinkHandler)
                }) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open in Browser", tint = MaterialTheme.colorScheme.onSurface)
                }

                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, article.url)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Article"))
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private val SlotDotPitch: Dp = 14.dp
private val CurrentDotSize: Dp = 10.dp
private val NeighborDotSize: Dp = 6.dp
private const val CrossfadeMillis = 200
private const val SettleMillis = 200

@Composable
private fun PagerIndicator(
    pagerState: PagerState,
    articleCount: Int,
    cacheStatus: Map<Int, CacheStatus>,
    prefetchCount: Int,
    isEInk: Boolean,
) {
    val windowSize = 1 + 2 * prefetchCount
    if (windowSize <= 0 || articleCount == 0) return

    val centerSlot = prefetchCount
    val density = LocalDensity.current
    val slotPitchPx = with(density) { SlotDotPitch.toPx() }

    // anchorPage lags currentPage during the post-settle slide so the dot row
    // can animate from its old position to the new one before snapping.
    var anchorPage by remember(pagerState) { mutableIntStateOf(pagerState.currentPage) }
    val rowShift = remember(pagerState) { Animatable(0f) }

    LaunchedEffect(pagerState.currentPage, isEInk) {
        val newPage = pagerState.currentPage
        if (newPage == anchorPage) return@LaunchedEffect
        if (isEInk) {
            anchorPage = newPage
            rowShift.snapTo(0f)
            return@LaunchedEffect
        }
        val delta = (newPage - anchorPage).toFloat()
        rowShift.snapTo(delta)
        rowShift.animateTo(targetValue = 0f, animationSpec = tween(SettleMillis))
        anchorPage = newPage
    }

    val swipeFraction by remember(pagerState, isEInk) {
        derivedStateOf {
            if (isEInk) 0f else pagerState.currentPageOffsetFraction.coerceIn(-0.5f, 0.5f)
        }
    }

    Box(
        modifier = Modifier
            .width(SlotDotPitch * windowSize)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        // The Row renders windowSize + 2 slots; the extras at slot -1 / slot
        // windowSize hold the departing and arriving dots during a slide and
        // are clipped at rest. unbounded width is needed so the Row keeps its
        // natural size instead of shrinking to the clipped container.
        Row(
            modifier = Modifier
                .wrapContentWidth(align = Alignment.CenterHorizontally, unbounded = true)
                .offset { IntOffset(x = (-rowShift.value * slotPitchPx).toInt(), y = 0) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (slotIndex in -1..windowSize) {
                val absoluteIndex = anchorPage + (slotIndex - centerSlot)
                val inRange = absoluteIndex in 0 until articleCount
                val slotAlpha = when (slotIndex) {
                    -1 -> (-rowShift.value).coerceIn(0f, 1f)
                    0 -> 1f - rowShift.value.coerceIn(0f, 1f)
                    windowSize - 1 -> 1f - (-rowShift.value).coerceIn(0f, 1f)
                    windowSize -> rowShift.value.coerceIn(0f, 1f)
                    else -> 1f
                }
                Box(
                    modifier = Modifier
                        .size(SlotDotPitch)
                        .alpha(slotAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    if (inRange) {
                        NeighborDot(
                            status = cacheStatus[absoluteIndex] ?: CacheStatus.NOT_CACHED,
                            isEInk = isEInk,
                        )
                    }
                }
            }
        }

        val highlightOffsetSlots = swipeFraction + rowShift.value
        Box(
            modifier = Modifier.offset {
                IntOffset(x = (highlightOffsetSlots * slotPitchPx).toInt(), y = 0)
            },
            contentAlignment = Alignment.Center,
        ) {
            CurrentDot()
        }
    }
}

@Composable
private fun CurrentDot() {
    Box(
        modifier = Modifier
            .size(CurrentDotSize)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface)
    )
}

@Composable
private fun NeighborDot(status: CacheStatus, isEInk: Boolean) {
    if (isEInk) {
        NeighborDotForStatus(status)
    } else {
        Crossfade(
            targetState = status,
            animationSpec = tween(CrossfadeMillis),
            label = "neighborDotCrossfade",
        ) { animated ->
            NeighborDotForStatus(animated)
        }
    }
}

@Composable
private fun NeighborDotForStatus(status: CacheStatus) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    when (status) {
        CacheStatus.CACHED -> Box(
            modifier = Modifier
                .size(NeighborDotSize)
                .clip(CircleShape)
                .background(onSurface.copy(alpha = 0.6f))
        )
        CacheStatus.LOADING -> Box(
            modifier = Modifier
                .size(NeighborDotSize)
                .border(1.dp, onSurface.copy(alpha = 0.6f), CircleShape)
        )
        CacheStatus.NOT_CACHED -> Box(
            modifier = Modifier
                .size(NeighborDotSize)
                .clip(CircleShape)
                .background(onSurface.copy(alpha = 0.3f))
        )
    }
}
