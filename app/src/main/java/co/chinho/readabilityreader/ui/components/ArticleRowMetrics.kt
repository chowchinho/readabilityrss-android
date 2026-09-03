package co.chinho.readabilityreader.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object ArticleRowDimens {
    val SelectionBarWidth = 4.dp

    // 12 + the 4dp selection bar puts the image 16dp from the screen edge, matching EndPadding.
    val StartPadding = 12.dp
    val EndPadding = 16.dp

    val VerticalPadding = 12.dp
    val ThumbnailGap = 12.dp
    val TitleToSnippetGap = 3.dp
    val MinSnippetToFeedGap = 6.dp
    val FeedFaviconSize = 16.dp

    // Square, and deliberately just under the 125.4dp content box at 16sp so the text block still
    // sets the card height. Raising this past the content box grows every card one dp for one.
    val PhoneThumbnailSize = 125.dp
    val MinThumbnailSize = 64.dp
    const val TabletThumbnailFraction = 0.33f

    const val TitleLineHeightRatio = 1.30f
    const val SnippetLineHeightRatio = 1.40f
    const val LabelLineHeightRatio = 1.35f

    const val TitleMaxLines = 2
    const val SnippetMaxLines = 3
}

/**
 * Assumes the worst case — a full [ArticleRowDimens.TitleMaxLines]-line title and a full
 * [ArticleRowDimens.SnippetMaxLines]-line snippet — so every card in a list is the same height
 * whatever its content. Shorter content is absorbed by the weighted spacer above the feed row.
 */
fun computeArticleRowHeight(
    titleLineHeight: Dp,
    snippetLineHeight: Dp,
    feedLabelLineHeight: Dp,
    imageSize: Dp,
): Dp {
    val feedRowHeight = maxOf(ArticleRowDimens.FeedFaviconSize, feedLabelLineHeight)
    val textBlockHeight =
        titleLineHeight * ArticleRowDimens.TitleMaxLines +
            ArticleRowDimens.TitleToSnippetGap +
            snippetLineHeight * ArticleRowDimens.SnippetMaxLines +
            ArticleRowDimens.MinSnippetToFeedGap +
            feedRowHeight
    return maxOf(imageSize, textBlockHeight) + ArticleRowDimens.VerticalPadding * 2
}

fun computeArticleThumbnailSize(tabletPaneWidth: Dp?, rowEndPadding: Dp): Dp {
    if (tabletPaneWidth == null) return ArticleRowDimens.PhoneThumbnailSize

    val contentWidth = tabletPaneWidth -
        ArticleRowDimens.SelectionBarWidth -
        ArticleRowDimens.StartPadding -
        rowEndPadding -
        ArticleRowDimens.ThumbnailGap

    return (contentWidth * ArticleRowDimens.TabletThumbnailFraction)
        .coerceIn(ArticleRowDimens.MinThumbnailSize, ArticleRowDimens.PhoneThumbnailSize)
}
