package co.chinho.readabilityreader.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.ui.tablet.LocalTabletPaneWidth
import co.chinho.readabilityreader.ui.theme.AppTheme
import co.chinho.readabilityreader.ui.theme.LocalEInkMode
import co.chinho.readabilityreader.ui.theme.SyncCompletedGreen
import co.chinho.readabilityreader.ui.theme.toComposeFontFamily
import coil.compose.AsyncImage
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableArticleRow(
    article: Article,
    onVote: (Long, String?, String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isEInk = LocalEInkMode.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val (leftInsetPx, rightInsetPx) = rememberSystemGestureInsetsPx()

    // The OS cancels the pointer stream once it claims an edge drag for back navigation, but the
    // draggable still settles, so confirmValueChange fires and a back swipe reads as a vote.
    var startedAtSystemEdge by remember { mutableStateOf(false) }

    // rememberSwipeToDismissBoxState builds its state with rememberSaveable and NO keys, so the
    // confirmValueChange lambda below is captured once and never replaced. Reading `article`
    // directly would freeze it at whatever the row first composed with, so the second swipe
    // would re-send the vote it already holds instead of cancelling it -- and it would only
    // appear to work after the row was scrolled out of view and recreated.
    val currentArticle by rememberUpdatedState(article)
    val currentOnVote by rememberUpdatedState(onVote)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (!startedAtSystemEdge) {
                if (dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                    currentOnVote(currentArticle.id, currentArticle.vote, "show_more")
                } else if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                    currentOnVote(currentArticle.id, currentArticle.vote, "show_less")
                }
            }
            false
        },
        positionalThreshold = { totalDistance ->
            (totalDistance * 0.30f).coerceAtMost(with(density) { 96.dp.toPx() })
        }
    )

    val edgeAwareModifier = modifier.pointerInput(leftInsetPx, rightInsetPx) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press) continue
                val startX = event.changes.firstOrNull()?.position?.x ?: continue
                startedAtSystemEdge = isGestureFromSystemEdge(
                    startX = startX,
                    rowWidth = size.width.toFloat(),
                    leftInsetPx = leftInsetPx,
                    rightInsetPx = rightInsetPx,
                )
            }
        }
    }

    val isPastThreshold =
        !startedAtSystemEdge && dismissState.targetValue != SwipeToDismissBoxValue.Settled

    var lastThresholdState by remember { mutableStateOf(false) }
    LaunchedEffect(isPastThreshold) {
        if (isPastThreshold && !lastThresholdState) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        lastThresholdState = isPastThreshold
    }

    val offsetPx = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
    val progress = (abs(offsetPx) / with(density) { 96.dp.toPx() }).coerceIn(0f, 1f)

    SwipeToDismissBox(
        state = dismissState,
        // Dropped on press, before the drag clears touch slop, so an edge gesture never gets
        // anchors to move towards. The confirmValueChange guard stays as a backstop in case a
        // recomposition lands late.
        enableDismissFromStartToEnd = !startedAtSystemEdge,
        enableDismissFromEndToStart = !startedAtSystemEdge,
        modifier = edgeAwareModifier,
        backgroundContent = {
            if (isEInk) {
                if (isPastThreshold) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                            Alignment.CenterStart
                        } else {
                            Alignment.CenterEnd
                        }
                    ) {
                        val icon = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                            Icons.Default.ThumbUp
                        } else {
                            Icons.Default.ThumbDown
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                val direction = if (startedAtSystemEdge) {
                    SwipeToDismissBoxValue.Settled
                } else {
                    dismissState.dismissDirection
                }
                val isUpvote = direction == SwipeToDismissBoxValue.StartToEnd
                val isDownvote = direction == SwipeToDismissBoxValue.EndToStart

                if (isUpvote || isDownvote) {
                    val color = if (isUpvote) SyncCompletedGreen else MaterialTheme.colorScheme.error
                    val alpha = progress * 0.85f

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val widthPx = with(density) { maxWidth.toPx() }
                        val gradientBrush = if (isUpvote) {
                            Brush.horizontalGradient(
                                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                                startX = 0f,
                                endX = widthPx * 0.75f,
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, color.copy(alpha = alpha)),
                                startX = widthPx * 0.25f,
                                endX = widthPx,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(gradientBrush),
                            contentAlignment = if (isUpvote) Alignment.CenterStart else Alignment.CenterEnd
                        ) {
                            val icon = if (isUpvote) Icons.Default.ThumbUp else Icons.Default.ThumbDown
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .size(24.dp)
                                    .graphicsLayer {
                                        this.alpha = progress
                                        scaleX = 0.6f + 0.4f * progress
                                        scaleY = 0.6f + 0.4f * progress
                                    },
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        },
        content = {
            if (isEInk) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        translationX = -offsetPx
                    }
                ) {
                    content()
                }
            } else {
                content()
            }
        }
    )
}

@Composable
private fun FeedMetadata(
    feedTitle: String,
    feedFaviconUrl: String?,
    opacity: Float,
    isEInk: Boolean,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
    uppercase: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!feedFaviconUrl.isNullOrBlank()) {
            AsyncImage(
                model = feedFaviconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(ArticleRowDimens.FeedFaviconSize)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                colorFilter = if (isEInk) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                } else null
            )
        } else {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                modifier = Modifier.size(ArticleRowDimens.FeedFaviconSize),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = opacity)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = if (uppercase) feedTitle.uppercase() else feedTitle,
            style = labelStyle,
            color = MaterialTheme.colorScheme.primary.copy(alpha = opacity),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// FeedMetadata is not reusable on the full-image card: it paints itself in the primary colour,
// which disappears against the photo gradient.
@Composable
private fun FullImageFeedLabel(
    feedTitle: String,
    feedFaviconUrl: String?,
    labelStyle: TextStyle,
    color: Color,
    isEInk: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!feedFaviconUrl.isNullOrBlank()) {
            AsyncImage(
                model = feedFaviconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(ArticleRowDimens.FeedFaviconSize)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                colorFilter = if (isEInk) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                } else null
            )
        } else {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                modifier = Modifier.size(ArticleRowDimens.FeedFaviconSize),
                tint = color,
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = feedTitle,
            style = labelStyle,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val PinnedLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun formatArticleTimestamp(publishedAt: Long): String {
    val normalizedPublishedAt = publishedAt.toEpochMillis()
    val elapsed = (System.currentTimeMillis() - normalizedPublishedAt).coerceAtLeast(0L)
    return when {
        elapsed < DateUtils.MINUTE_IN_MILLIS -> "NOW"
        elapsed < DateUtils.HOUR_IN_MILLIS -> "${elapsed / DateUtils.MINUTE_IN_MILLIS}M"
        elapsed < DateUtils.DAY_IN_MILLIS -> "${elapsed / DateUtils.HOUR_IN_MILLIS}H"
        else -> "${elapsed / DateUtils.DAY_IN_MILLIS}D"
    }
}

private fun Long.toEpochMillis(): Long {
    return if (this in 1L until 100_000_000_000L) this * 1000L else this
}

@Composable
fun ArticleRow(
    article: Article,
    onClick: () -> Unit,
    showFeedMetadata: Boolean,
    fontSizeSp: Int = 16,
    fontFamily: String = "system",
    selected: Boolean = false,
    onFetchExtraImages: (suspend (Long) -> List<SlideshowImage>)? = null,
    modifier: Modifier = Modifier,
) {
    val opacity = if (article.isRead) 0.6f else 1.0f
    val isEInk = LocalEInkMode.current
    val tabletPaneWidth = LocalTabletPaneWidth.current
    val isInTabletPane = tabletPaneWidth != null
    val rowEndPadding = if (isInTabletPane) 0.dp else ArticleRowDimens.EndPadding
    val thumbnailSize = computeArticleThumbnailSize(tabletPaneWidth, rowEndPadding)

    val contentSnippet = article.snippetText?.takeIf { it.isNotBlank() }
    val thumbnailUrl = article.thumbnailUrl?.takeIf { it.isNotBlank() }
    val composeFontFamily = remember(fontFamily) { fontFamily.toComposeFontFamily() }

    val titleFontSize = fontSizeSp.sp
    val snippetFontSize = (fontSizeSp - 2).coerceAtLeast(10).sp
    val labelFontSize = MaterialTheme.typography.labelSmall.fontSize.takeOrElse { 11.sp }

    val titleLineHeight = titleFontSize * ArticleRowDimens.TitleLineHeightRatio
    val snippetLineHeight = snippetFontSize * ArticleRowDimens.SnippetLineHeightRatio
    val labelLineHeight = labelFontSize * ArticleRowDimens.LabelLineHeightRatio

    val titleStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = titleFontSize,
        fontWeight = FontWeight.Bold,
        fontFamily = composeFontFamily,
        lineHeight = titleLineHeight,
        lineHeightStyle = PinnedLineHeightStyle,
        platformStyle = NoFontPadding,
    )
    val snippetStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = snippetFontSize,
        fontFamily = composeFontFamily,
        lineHeight = snippetLineHeight,
        lineHeightStyle = PinnedLineHeightStyle,
        platformStyle = NoFontPadding,
    )
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = labelFontSize,
        lineHeight = labelLineHeight,
        lineHeightStyle = PinnedLineHeightStyle,
        platformStyle = NoFontPadding,
    )

    val density = LocalDensity.current
    val cardHeight = computeArticleRowHeight(
        titleLineHeight = with(density) { titleLineHeight.toDp() },
        snippetLineHeight = with(density) { snippetLineHeight.toDp() },
        feedLabelLineHeight = with(density) { labelLineHeight.toDp() },
        imageSize = thumbnailSize,
    )

    val accentColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RectangleShape,
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .height(cardHeight),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(ArticleRowDimens.SelectionBarWidth)
                    .fillMaxHeight()
                    .background(if (selected) accentColor else Color.Transparent)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(
                        start = ArticleRowDimens.StartPadding,
                        end = rowEndPadding,
                        top = ArticleRowDimens.VerticalPadding,
                        bottom = ArticleRowDimens.VerticalPadding,
                    ),
                verticalAlignment = Alignment.Top,
            ) {
                if (thumbnailUrl != null) {
                    // Centred rather than top-aligned: the text block is taller than the image, and
                    // pinning the image to the line-box top left it sitting above the title's ink.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(thumbnailSize)
                    ) {
                        ArticleCardSlideshow(
                            articleId = article.id,
                            mainImage = thumbnailUrl,
                            mainFocalX = article.focalX,
                            mainFocalY = article.focalY,
                            imageAlpha = opacity,
                            isEInk = isEInk,
                            fullImageMode = false,
                            onFetchExtraImages = onFetchExtraImages,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    }
                    Spacer(modifier = Modifier.width(ArticleRowDimens.ThumbnailGap))
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        text = article.title,
                        style = titleStyle,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = opacity),
                        maxLines = ArticleRowDimens.TitleMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )

                    contentSnippet?.let { snippet ->
                        Spacer(modifier = Modifier.height(ArticleRowDimens.TitleToSnippetGap))
                        Text(
                            text = snippet,
                            style = snippetStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = opacity),
                            maxLines = ArticleRowDimens.SnippetMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showFeedMetadata && !article.feedTitle.isNullOrBlank()) {
                            FeedMetadata(
                                feedTitle = article.feedTitle,
                                feedFaviconUrl = article.feedFaviconUrl,
                                opacity = opacity,
                                isEInk = isEInk,
                                labelStyle = labelStyle,
                                uppercase = false,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (article.vote != null) {
                                val isUp = article.vote == "show_more"
                                Icon(
                                    imageVector = if (isUp) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
                                    contentDescription = if (isUp) "Upvoted" else "Downvoted",
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isEInk) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = opacity)
                                    } else if (isUp) {
                                        SyncCompletedGreen.copy(alpha = opacity)
                                    } else {
                                        MaterialTheme.colorScheme.error.copy(alpha = opacity)
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            Text(
                                text = formatArticleTimestamp(article.publishedAt),
                                style = labelStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = opacity)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArticleRowFullImage(
    article: Article,
    onClick: () -> Unit,
    showFeedMetadata: Boolean,
    fontSizeSp: Int = 16,
    fontFamily: String = "system",
    selected: Boolean = false,
    onFetchExtraImages: (suspend (Long) -> List<SlideshowImage>)? = null,
    modifier: Modifier = Modifier,
) {
    val isEInk = LocalEInkMode.current
    val isInTabletPane = LocalTabletPaneWidth.current != null
    val cardEndPadding = if (isInTabletPane) 0.dp else 16.dp
    val thumbnailUrl = article.thumbnailUrl?.takeIf { it.isNotBlank() }
    val composeFontFamily = remember(fontFamily) { fontFamily.toComposeFontFamily() }

    if (thumbnailUrl == null) {
        ArticleRow(
            article = article,
            onClick = onClick,
            showFeedMetadata = showFeedMetadata,
            fontSizeSp = fontSizeSp,
            fontFamily = fontFamily,
            selected = selected,
            onFetchExtraImages = onFetchExtraImages,
            modifier = modifier,
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val clickModifier = if (isEInk) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier.clickable { onClick() }
    }
    val textOpacity = if (isEInk || !article.isRead) 1.0f else 0.6f
    val imageAlpha = if (isEInk || !article.isRead) 1.0f else 0.35f
    val accentColor = MaterialTheme.colorScheme.primary
    val fullImageLabelStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = (fontSizeSp - 2).coerceAtLeast(10).sp,
        lineHeight = (fontSizeSp).coerceAtLeast(12).sp,
        fontFamily = composeFontFamily,
    )

    if (isEInk) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = cardEndPadding, top = 10.dp, bottom = 10.dp)
                .then(clickModifier),
            shape = MaterialTheme.shapes.medium,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(MaterialTheme.shapes.medium)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    ArticleCardSlideshow(
                        articleId = article.id,
                        mainImage = thumbnailUrl,
                        mainFocalX = article.focalX,
                        mainFocalY = article.focalY,
                        imageAlpha = imageAlpha,
                        isEInk = true,
                        fullImageMode = true,
                        onFetchExtraImages = null,
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (showFeedMetadata && !article.feedTitle.isNullOrBlank()) {
                            FullImageFeedLabel(
                                feedTitle = article.feedTitle,
                                feedFaviconUrl = article.feedFaviconUrl,
                                labelStyle = fullImageLabelStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                isEInk = true,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (article.vote != null) {
                                val isUp = article.vote == "show_more"
                                Icon(
                                    imageVector = if (isUp) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
                                    contentDescription = if (isUp) "Upvoted" else "Downvoted",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = formatArticleTimestamp(article.publishedAt),
                                style = fullImageLabelStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = article.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp + 2).sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = composeFontFamily,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(accentColor),
                    )
                }
            }
        }
        return
    }

    val dateColor = Color.White.copy(alpha = 0.85f * textOpacity)
    val titleColor = Color.White.copy(alpha = textOpacity)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = cardEndPadding, top = 10.dp, bottom = 10.dp)
            .then(clickModifier),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.medium)
        ) {
            ArticleCardSlideshow(
                articleId = article.id,
                mainImage = thumbnailUrl,
                mainFocalX = article.focalX,
                mainFocalY = article.focalY,
                imageAlpha = imageAlpha,
                isEInk = false,
                fullImageMode = true,
                onFetchExtraImages = onFetchExtraImages,
                modifier = Modifier.fillMaxSize(),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(accentColor)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (article.vote != null) {
                        val isUp = article.vote == "show_more"
                        Icon(
                            imageVector = if (isUp) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
                            contentDescription = if (isUp) "Upvoted" else "Downvoted",
                            modifier = Modifier.size(12.dp),
                            tint = if (isUp) SyncCompletedGreen else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (showFeedMetadata && !article.feedTitle.isNullOrBlank()) {
                        FullImageFeedLabel(
                            feedTitle = article.feedTitle,
                            feedFaviconUrl = article.feedFaviconUrl,
                            labelStyle = fullImageLabelStyle,
                            color = dateColor,
                            isEInk = false,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = " · ",
                            style = fullImageLabelStyle,
                            color = dateColor,
                        )
                    }
                    Text(
                        text = formatArticleTimestamp(article.publishedAt),
                        style = fullImageLabelStyle,
                        color = dateColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp + 2).sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = composeFontFamily,
                    ),
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1B1F, name = "Article Rows - uniform height")
@Composable
private fun ArticleRowPreview() {
    val base = Article(
        id = 1L,
        feedId = 10L,
        title = "",
        url = "https://example.com/a",
        content = null,
        publishedAt = System.currentTimeMillis(),
        isRead = false,
        isSaved = false,
        thumbnailUrl = null,
        isCached = true,
        feedTitle = "HOBBY Watch",
        feedFaviconUrl = null,
    )

    AppTheme(isDarkTheme = true) {
        Column {
            ArticleRow(
                article = base.copy(
                    title = "【特集】台場「1:1獨角獸高達立像」本月最後機會！與Gundam Base限定R",
                    snippetText = "實物大獨角獸高達立像的展示期間即將結束，官方宣布本月為最後參觀機會。",
                ),
                onClick = {},
                showFeedMetadata = true,
            )
            ArticleRow(
                article = base.copy(id = 2L, title = "Short headline"),
                onClick = {},
                showFeedMetadata = true,
            )
            ArticleRow(
                article = base.copy(
                    id = 3L,
                    title = "iFixit's Galaxy Z Fold8 Teardown is Preview of Apple's Foldable Challenge",
                    snippetText = "Samsung launched new foldable smartphones earlier this month.",
                ),
                onClick = {},
                showFeedMetadata = true,
            )
        }
    }
}
