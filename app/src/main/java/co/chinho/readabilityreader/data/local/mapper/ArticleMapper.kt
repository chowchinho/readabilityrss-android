package co.chinho.readabilityreader.data.local.mapper

import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import co.chinho.readabilityreader.data.local.model.ArticleWithFeedTitle
import co.chinho.readabilityreader.data.repository.ThumbnailUrlResolver
import co.chinho.readabilityreader.domain.model.Article

fun ArticleEntity.toDomain(
    feedTitle: String? = null,
    focalX: Int = 50,
    focalY: Int = 50,
): Article = Article(
    id = id,
    feedId = feedId,
    title = title,
    url = url,
    content = content,
    publishedAt = publishedAt,
    isRead = isRead,
    isSaved = isSaved,
    thumbnailUrl = ThumbnailUrlResolver.resolveStoredThumbnailUrl(
        storedUrl = thumbnailUrl,
        articleUrl = url,
        html = content,
    ),
    isCached = contentCachedAt != null || imagesCachedAt != null,
    feedTitle = feedTitle,
    focalX = focalX,
    focalY = focalY,
    score = score,
    primaryTopic = primaryTopic,
    secondaryTopics = secondaryTopics,
    region = region,
    articleType = articleType,
    vote = vote,
    freshness = freshness,
    pairTerms = pairTerms,
    snippetText = snippetText,
)

fun ArticleWithFeedTitle.toDomain(): Article = article.toDomain(
    feedTitle = feedTitle,
    focalX = coverFocalX ?: 50,
    focalY = coverFocalY ?: 50,
)
