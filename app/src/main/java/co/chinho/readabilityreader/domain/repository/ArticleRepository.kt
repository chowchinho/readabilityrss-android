package co.chinho.readabilityreader.domain.repository

import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.model.ArticleImage
import co.chinho.readabilityreader.domain.model.CacheStats
import co.chinho.readabilityreader.domain.model.LabelWeight
import kotlinx.coroutines.flow.Flow

interface ArticleRepository {
    fun getArticles(
        feedId: Long?,
        showRead: Boolean,
        groupId: Long? = null,
        stickyIds: List<Long> = emptyList(),
    ): Flow<List<Article>>

    fun getArticle(articleId: Long): Flow<Article?>

    suspend fun getArticleContent(articleId: Long): String?

    suspend fun getArticleImages(articleId: Long): List<ArticleImage>

    fun getSavedArticles(): Flow<List<Article>>

    fun getArticleCount(): Flow<Int>

    fun getCacheStats(): Flow<CacheStats>

    suspend fun syncFromServer(
        keepDays: Int,
        onProgress: suspend (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Int

    suspend fun flushReadStateQueue()

    suspend fun markRead(articleId: Long, isRead: Boolean)

    suspend fun markSaved(articleId: Long, isSaved: Boolean)

    suspend fun markFeedRead(feedId: Long)

    suspend fun markGroupRead(groupId: Long, beforeTimestamp: Long)

    suspend fun evictOldArticles(keepDays: Int)

    suspend fun clearAllCache()

    suspend fun rewarmMissingThumbnails(maxToWarm: Int): Int

    suspend fun recordVote(articleId: Long, vote: String?, markRead: Boolean = false)

    suspend fun recordEvent(articleId: Long, eventType: String, dwellSeconds: Int? = null)

    suspend fun flushRankingQueues()
 
    fun getLabelWeights(): Flow<List<LabelWeight>>

    suspend fun syncFocalPoints()

    suspend fun backfillSnippets(): Int
}

