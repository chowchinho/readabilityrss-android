package co.chinho.readabilityreader.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import co.chinho.readabilityreader.data.local.model.ArticleImageWarmRow
import co.chinho.readabilityreader.data.local.model.ArticleUrlsRow
import co.chinho.readabilityreader.data.local.model.ArticleWithFeedTitle
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query(
        """
        SELECT
            articles.id, articles.feedId, articles.title, articles.url, '' AS content, articles.snippetText,
            articles.publishedAt, articles.isRead, articles.isSaved, articles.thumbnailUrl,
            articles.cachedAt, articles.contentCachedAt, articles.imagesCachedAt,
            articles.score, articles.primaryTopic, articles.secondaryTopics, articles.region,
            articles.articleType, articles.vote, articles.freshness, articles.pairTerms,
            feeds.title AS feedTitle,
            cover.focalX AS coverFocalX, cover.focalY AS coverFocalY
        FROM articles
        LEFT JOIN feeds ON articles.feedId = feeds.id
        LEFT JOIN article_images AS cover
            ON cover.articleId = articles.id AND cover.image_url = articles.thumbnailUrl
        WHERE (:feedId IS NULL OR articles.feedId = :feedId)
          AND (:groupId IS NULL OR articles.feedId IN (SELECT id FROM feeds WHERE groupId = :groupId))
          AND (:showRead = 1 OR isRead = 0 OR articles.id IN (:stickyIds))
        ORDER BY
            CASE WHEN :sortPersonalised = 1 AND articles.score IS NOT NULL THEN 0 ELSE 1 END ASC,
            CASE WHEN :sortPersonalised = 1 THEN articles.score END DESC,
            articles.publishedAt DESC
        """
    )
    fun observeArticles(
        feedId: Long?,
        showRead: Boolean,
        groupId: Long? = null,
        sortPersonalised: Boolean = false,
        stickyIds: List<Long>,
    ): Flow<List<ArticleWithFeedTitle>>

    @Query(
        """
        SELECT
            articles.id, articles.feedId, articles.title, articles.url, '' AS content, articles.snippetText,
            articles.publishedAt, articles.isRead, articles.isSaved, articles.thumbnailUrl,
            articles.cachedAt, articles.contentCachedAt, articles.imagesCachedAt,
            articles.score, articles.primaryTopic, articles.secondaryTopics, articles.region,
            articles.articleType, articles.vote, articles.freshness, articles.pairTerms,
            feeds.title AS feedTitle,
            cover.focalX AS coverFocalX, cover.focalY AS coverFocalY
        FROM articles
        LEFT JOIN feeds ON articles.feedId = feeds.id
        LEFT JOIN article_images AS cover
            ON cover.articleId = articles.id AND cover.image_url = articles.thumbnailUrl
        WHERE isSaved = 1
        ORDER BY publishedAt DESC
        """
    )
    fun observeSavedArticles(): Flow<List<ArticleWithFeedTitle>>

    @Query(
        """
        SELECT 
            articles.*,
            feeds.title AS feedTitle,
            cover.focalX AS coverFocalX, cover.focalY AS coverFocalY
        FROM articles
        LEFT JOIN feeds ON articles.feedId = feeds.id
        LEFT JOIN article_images AS cover
            ON cover.articleId = articles.id AND cover.image_url = articles.thumbnailUrl
        WHERE articles.id = :articleId
        """
    )
    fun observeArticle(articleId: Long): Flow<ArticleWithFeedTitle?>

    @Query("SELECT content FROM articles WHERE id = :articleId")
    suspend fun getArticleContent(articleId: Long): String?

    // Newest first: a partial backfill must cover the rows the user is actually looking at.
    @Query("SELECT id FROM articles WHERE snippetText IS NULL ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun getIdsNeedingSnippet(limit: Int): List<Long>

    @Query("UPDATE articles SET snippetText = :snippet WHERE id = :articleId")
    suspend fun updateArticleSnippet(articleId: Long, snippet: String)

    @Query("SELECT MAX(id) FROM articles")
    suspend fun getMaxArticleId(): Long?

    @Query("SELECT MIN(id) FROM articles")
    suspend fun getMinArticleId(): Long?

    @Query("SELECT MIN(id) FROM articles WHERE isSaved = 0")
    suspend fun getMinNonSavedArticleId(): Long?

    @Query("SELECT COUNT(*) FROM articles")
    fun observeArticleCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun getArticleCount(): Int

    @Upsert
    suspend fun upsertArticles(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET isRead = :isRead WHERE id = :articleId")
    suspend fun updateReadState(articleId: Long, isRead: Boolean)

    @Query("UPDATE articles SET isSaved = :isSaved WHERE id = :articleId")
    suspend fun updateSavedState(articleId: Long, isSaved: Boolean)

    @Query("SELECT id FROM articles WHERE feedId = :feedId AND isRead = 0")
    suspend fun getUnreadIdsForFeed(feedId: Long): List<Long>

    @Query(
        """
        SELECT id FROM articles
        WHERE feedId IN (SELECT id FROM feeds WHERE groupId = :groupId)
          AND publishedAt <= :beforeTimestamp
          AND isRead = 0
        """
    )
    suspend fun getUnreadIdsForGroup(groupId: Long, beforeTimestamp: Long): List<Long>

    @Query("UPDATE articles SET isRead = 1 WHERE feedId = :feedId")
    suspend fun markFeedRead(feedId: Long)

    @Query(
        """
        UPDATE articles
        SET isRead = 1
        WHERE feedId IN (SELECT id FROM feeds WHERE groupId = :groupId)
          AND publishedAt <= :beforeTimestamp
        """
    )
    suspend fun markGroupRead(groupId: Long, beforeTimestamp: Long)

    // Evictable = unsaved AND (already read OR older than the retention window).
    // Read articles are dropped regardless of age; unread are kept until they age out.
    // We deliberately exclude `content` from this batch: a single oversized HTML blob
    // (>2 MB) exceeds Android's CursorWindow and would fail the whole batch. The caller
    // pulls content per id via getArticleContent and tolerates per-row failures.
    @Query(
        """
        SELECT id, thumbnailUrl FROM articles
        WHERE isSaved = 0
          AND (
            isRead = 1
            OR (publishedAt >= 100000000000 AND publishedAt < :cutoffTimestampMillis)
            OR (publishedAt > 0 AND publishedAt < 100000000000 AND publishedAt < :cutoffTimestampSeconds)
          )
        ORDER BY id
        LIMIT :limit
        """
    )
    suspend fun getEvictableArticleBatch(
        cutoffTimestampMillis: Long,
        cutoffTimestampSeconds: Long,
        limit: Int,
    ): List<ArticleUrlsRow>

    @Query("DELETE FROM articles WHERE id IN (:ids)")
    suspend fun deleteArticlesByIds(ids: List<Long>)

    @Query("SELECT id, thumbnailUrl FROM articles WHERE isSaved = 1")
    suspend fun getSavedArticleUrls(): List<ArticleUrlsRow>

    @Query("DELETE FROM articles")
    suspend fun clearAll()
    
    @Query("UPDATE articles SET isRead = 1 WHERE id NOT IN (:unreadIds)")
    suspend fun markAllNotInAsRead(unreadIds: Collection<Long>)
    
    @Query("UPDATE articles SET isRead = 1")
    suspend fun markAllAsRead()
    
    @Query("UPDATE articles SET isRead = 0 WHERE id IN (:unreadIds)")
    suspend fun markAllInAsUnread(unreadIds: Collection<Long>)
    
    @Query("UPDATE articles SET isSaved = 0 WHERE id NOT IN (:savedIds)")
    suspend fun markAllNotInAsUnsaved(savedIds: Collection<Long>)
    
    @Query("UPDATE articles SET isSaved = 0")
    suspend fun markAllAsUnsaved()
    
    @Query("UPDATE articles SET isSaved = 1 WHERE id IN (:savedIds)")
    suspend fun markAllInAsSaved(savedIds: Collection<Long>)

    // Reconcile against Coil's disk cache, not the imagesCachedAt flag: the flag can go stale
    // (bytes evicted from Coil while the flag stays set), so we re-scan recent thumbnails
    // regardless of flag and let the caller probe disk presence.
    @Query(
        """
        SELECT id, thumbnailUrl AS thumbnailUrl, imagesCachedAt FROM articles
        WHERE thumbnailUrl IS NOT NULL AND thumbnailUrl != ''
        ORDER BY publishedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getThumbnailArticlesForReconcile(limit: Int): List<ArticleImageWarmRow>

    @Query("UPDATE articles SET imagesCachedAt = :timestamp WHERE id = :articleId")
    suspend fun markImagesCached(articleId: Long, timestamp: Long)

    @Query("UPDATE articles SET score = NULL, freshness = NULL, pairTerms = NULL")
    suspend fun clearAllArticleScores()

    @Query(
        """
        UPDATE articles
        SET score = :score,
            primaryTopic = COALESCE(:primaryTopic, primaryTopic),
            secondaryTopics = COALESCE(:secondaryTopics, secondaryTopics),
            region = COALESCE(:region, region),
            articleType = COALESCE(:articleType, articleType),
            vote = COALESCE(:vote, vote),
            freshness = :freshness,
            pairTerms = :pairTerms
        WHERE id = :articleId
        """
    )
    suspend fun updateArticleScoreAndExtras(
        articleId: Long,
        score: Double?,
        primaryTopic: String?,
        secondaryTopics: String?,
        region: String?,
        articleType: String?,
        vote: String?,
        freshness: Double?,
        pairTerms: String?,
    )

    @Query("UPDATE articles SET vote = :vote WHERE id = :articleId")
    suspend fun updateArticleVote(articleId: Long, vote: String?)

    @Query("SELECT vote FROM articles WHERE id = :articleId")
    suspend fun getArticleVote(articleId: Long): String?
}
