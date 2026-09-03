package co.chinho.readabilityreader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import co.chinho.readabilityreader.data.local.entity.ArticleImageEntity
import co.chinho.readabilityreader.data.local.model.FocalPointUpdate

@Dao
interface ArticleImageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<ArticleImageEntity>)

    /**
     * Document order matters: the cover is position 0 and the slideshow plays forward from it.
     * The primary key is (articleId, image_url), so no DISTINCT is needed.
     */
    @Query(
        """
        SELECT image_url, focalX, focalY FROM article_images
        WHERE articleId = :articleId
        ORDER BY position ASC
        """
    )
    suspend fun getOrderedImagesForArticle(articleId: Long): List<co.chinho.readabilityreader.data.local.model.ArticleImageRow>

    /**
     * Strict eviction lookup. Returns image URLs that belong to articles in [ids]
     * AND that no article *outside* [ids] references. Uses NOT EXISTS for index-driven
     * lookups on the `image_url` index — work scales with the batch size, not table size.
     */
    @Query(
        """
        SELECT DISTINCT ai.image_url FROM article_images AS ai
        WHERE ai.articleId IN (:ids)
          AND NOT EXISTS (
              SELECT 1 FROM article_images AS ai2
              WHERE ai2.image_url = ai.image_url
                AND ai2.articleId NOT IN (:ids)
          )
        """
    )
    suspend fun getUrlsOwnedOnlyBy(ids: List<Long>): List<String>

    /**
     * Image-warming worker's URL source. Returns all distinct URLs referenced by saved
     * articles OR articles within the retention window. Dual-cutoff handles FEVER
     * timestamps that are sometimes seconds and sometimes milliseconds — mirrors the
     * pattern in `ArticleDao.getEvictableArticleBatch`.
     *
     * Covers come first, newest first. Covers are the article-list thumbnails, and they are
     * also what the concurrent analyse phase needs on disk — unordered, the downloader spent
     * its run on body images nobody was waiting for while the analyser starved (872 of 888
     * pending covers had no bytes on disk, measured 2026-08-11).
     *
     * `GROUP BY` rather than `DISTINCT`: SQLite rejects an ORDER BY term that is not in the
     * result set of a DISTINCT query, and the aggregates below are exactly such terms.
     */
    @Query(
        """
        SELECT ai.image_url
        FROM article_images AS ai
        INNER JOIN articles AS a ON a.id = ai.articleId
        WHERE a.isSaved = 1
           OR (a.publishedAt >= 100000000000 AND a.publishedAt >= :cutoffMillis)
           OR (a.publishedAt > 0 AND a.publishedAt < 100000000000 AND a.publishedAt >= :cutoffSeconds)
        GROUP BY ai.image_url
        ORDER BY
            MAX(CASE WHEN a.thumbnailUrl = ai.image_url THEN 1 ELSE 0 END) DESC,
            MAX(
                CASE WHEN a.publishedAt >= 100000000000
                     THEN a.publishedAt
                     ELSE a.publishedAt * 1000
                END
            ) DESC,
            ai.image_url ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getWarmableUrls(
        cutoffMillis: Long,
        cutoffSeconds: Long,
        limit: Int,
        offset: Int,
    ): List<String>

    @Query("DELETE FROM article_images")
    suspend fun clearAll()

    /**
     * Paged deliberately. Unpaged, this returned 20,259 rows on a real device and threw
     * `Couldn't read row 6364 from CursorWindow` when a concurrent sync committed to
     * `article_images` mid-iteration — a long cursor walk is a wide window for that race.
     * The caller accumulates every page before it writes anything, so offsets stay stable.
     */
    @Query(
        """
        SELECT DISTINCT image_url FROM article_images
        WHERE focalComputed = 0
        ORDER BY image_url
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getUrlsNeedingFocal(limit: Int, offset: Int): List<String>

    @Query("SELECT COUNT(*) FROM article_images WHERE focalComputed = 1")
    suspend fun countFocalComputed(): Int

    /**
     * One-shot migration off the retired on-device ML Kit analyser. Its focal points are valid
     * but inferior — measured on 2026-08-21, it returned centre for 319 images where the server
     * found a subject — and the sync only touches `focalComputed = 0`, so they would otherwise
     * never be replaced. Clearing the flag lets the next sync repopulate them from the server.
     */
    @Query("UPDATE article_images SET focalComputed = 0")
    suspend fun clearFocalComputed()

    @Query(
        """
        UPDATE article_images
        SET focalX = :focalX, focalY = :focalY, focalComputed = 1
        WHERE image_url = :imageUrl
        """
    )
    suspend fun setFocalForUrl(imageUrl: String, focalX: Int, focalY: Int)

    @RawQuery
    suspend fun executeRawUpdate(query: SupportSQLiteQuery): Int

    @Transaction
    suspend fun setFocalPoints(updates: List<FocalPointUpdate>) {
        if (updates.isEmpty()) return
        // 5 parameters per update: 2 in focalX CASE, 2 in focalY CASE, 1 in WHERE IN.
        // 150 updates * 5 = 750 parameters, safely under the 900 parameter limit.
        for (chunk in updates.chunked(150)) {
            val sb = StringBuilder("UPDATE article_images SET focalX = CASE image_url ")
            val args = ArrayList<Any>(chunk.size * 5)
            for (u in chunk) {
                sb.append("WHEN ? THEN ? ")
                args.add(u.imageUrl)
                args.add(u.focalX)
            }
            sb.append("END, focalY = CASE image_url ")
            for (u in chunk) {
                sb.append("WHEN ? THEN ? ")
                args.add(u.imageUrl)
                args.add(u.focalY)
            }
            sb.append("END, focalComputed = 1 WHERE image_url IN (")
            for (i in chunk.indices) {
                if (i > 0) sb.append(",")
                sb.append("?")
                args.add(chunk[i].imageUrl)
            }
            sb.append(")")
            executeRawUpdate(SimpleSQLiteQuery(sb.toString(), args.toArray()))
        }
    }
}
