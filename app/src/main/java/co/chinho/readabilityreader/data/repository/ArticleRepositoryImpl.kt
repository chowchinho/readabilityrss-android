package co.chinho.readabilityreader.data.repository

import android.util.Log
import androidx.room.withTransaction
import co.chinho.readabilityreader.data.local.ReadabilityDatabase
import co.chinho.readabilityreader.data.local.SnippetExtractor
import co.chinho.readabilityreader.data.local.dao.ArticleDao
import co.chinho.readabilityreader.data.local.dao.FeedDao
import co.chinho.readabilityreader.data.local.dao.GroupDao
import co.chinho.readabilityreader.data.local.dao.ReadStateQueueDao
import co.chinho.readabilityreader.data.local.entity.ArticleEntity
import co.chinho.readabilityreader.data.local.entity.ArticleImageEntity
import co.chinho.readabilityreader.data.local.entity.FeedEntity
import co.chinho.readabilityreader.data.local.entity.GroupEntity
import co.chinho.readabilityreader.data.local.entity.ReadStateQueueEntity
import co.chinho.readabilityreader.data.local.mapper.toDomain
import co.chinho.readabilityreader.data.local.model.ArticleWithFeedTitle
import co.chinho.readabilityreader.data.remote.FeverConnectionProvider
import co.chinho.readabilityreader.data.remote.FeverConnection
import co.chinho.readabilityreader.data.remote.dto.FeverItemDto
import co.chinho.readabilityreader.data.remote.dto.FeverResponse
import co.chinho.readabilityreader.domain.model.Article
import co.chinho.readabilityreader.domain.model.ArticleImage
import co.chinho.readabilityreader.domain.model.CacheStats
import co.chinho.readabilityreader.domain.repository.ArticleRepository
import co.chinho.readabilityreader.data.local.dao.LabelWeightDao
import co.chinho.readabilityreader.data.local.dao.VoteQueueDao
import co.chinho.readabilityreader.data.local.dao.EventQueueDao
import co.chinho.readabilityreader.data.local.entity.LabelWeightEntity
import co.chinho.readabilityreader.data.local.entity.VoteQueueEntity
import co.chinho.readabilityreader.data.local.entity.EventQueueEntity
import co.chinho.readabilityreader.data.remote.dto.LabelWeightDto
import co.chinho.readabilityreader.data.remote.dto.RankingEventDto
import co.chinho.readabilityreader.data.remote.dto.RankingFeedbackRequest
import co.chinho.readabilityreader.data.remote.dto.RankingVoteDto
import co.chinho.readabilityreader.data.local.model.FocalPointUpdate
import co.chinho.readabilityreader.data.remote.CachedImageHash
import co.chinho.readabilityreader.data.remote.dto.FocalPointsRequest
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URI

internal object ThumbnailUrlResolver {
    private val imageTagRegex =
        Regex("<(img|source)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val metaTagRegex =
        Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val htmlUrlAttributeRegex =
        Regex("(src|href)\\s*=\\s*([\"'])([^\"']+)\\2", RegexOption.IGNORE_CASE)
    private val htmlAttributeRegex =
        Regex("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*([\"'])(.*?)\\2")

    fun resolveThumbnailUrl(
        rawUrl: String?,
        proxyUrl: String?,
        articleUrl: String,
        readerBaseUrl: String,
        html: String?,
    ): String? {
        val resolvedRawUrl = resolveRemoteOrRelativeUrl(rawUrl, articleUrl, readerBaseUrl)
        if (resolvedRawUrl != null && (isCachedImageUrl(resolvedRawUrl) || isImageProxyUrl(resolvedRawUrl))) {
            return resolvedRawUrl
        }
        html?.let { htmlContent ->
            extractBestImageUrl(
                html = htmlContent,
                articleUrl = articleUrl,
                readerBaseUrl = readerBaseUrl,
            )
        }?.let { return it }
        return resolvedRawUrl ?: resolveRemoteOrRelativeUrl(proxyUrl, articleUrl, readerBaseUrl)
    }

    fun resolveStoredThumbnailUrl(
        storedUrl: String?,
        articleUrl: String,
        html: String?,
    ): String? {
        return resolveThumbnailUrl(
            rawUrl = storedUrl,
            proxyUrl = null,
            articleUrl = articleUrl,
            readerBaseUrl = "",
            html = html,
        )
    }

    fun resolveFaviconUrl(
        feedId: Long,
        feedUrl: String,
        siteUrl: String?,
        faviconUrl: String?,
        faviconProxyUrl: String?,
        readerBaseUrl: String?,
    ): String? {
        resolveReaderRelativeUrl(faviconProxyUrl, readerBaseUrl)?.let { return it }
        resolveReaderRelativeUrl(faviconUrl, readerBaseUrl)?.let { return it }
        readerBaseUrl?.let { return "$it/api/reader/favicon/$feedId" }

        val domainSource = siteUrl?.takeIf { it.isNotBlank() } ?: feedUrl
        val host = runCatching { URI(domainSource).host }
            .getOrNull()
            ?.removePrefix("www.")
            ?: return null
        return "https://www.google.com/s2/favicons?sz=64&domain_url=https://$host"
    }

    private fun resolveReaderRelativeUrl(
        candidateUrl: String?,
        readerBaseUrl: String?,
    ): String? {
        val rawUrl = candidateUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            rawUrl.startsWith("http://", ignoreCase = true) ||
                rawUrl.startsWith("https://", ignoreCase = true) -> rawUrl
            rawUrl.startsWith("/") && !readerBaseUrl.isNullOrBlank() -> "$readerBaseUrl$rawUrl"
            else -> rawUrl
        }
    }

    private fun isCachedImageUrl(imageUrl: String): Boolean {
        return "/api/reader/cached-image/" in imageUrl.lowercase()
    }

    private fun isImageProxyUrl(imageUrl: String): Boolean {
        return "/api/reader/image-proxy" in imageUrl.lowercase()
    }

    private fun scoreResolvedUrl(imageUrl: String): Int {
        return when {
            isCachedImageUrl(imageUrl) -> 600
            isImageProxyUrl(imageUrl) -> 500
            else -> 200
        }
    }

    fun normalizeArticleHtml(
        html: String,
        articleUrl: String,
        readerBaseUrl: String,
    ): String {
        return htmlUrlAttributeRegex.replace(html) { match ->
            val attribute = match.groupValues[1]
            val quote = match.groupValues[2]
            val rawUrl = match.groupValues[3]
            val resolvedUrl = resolveHtmlUrl(
                rawUrl = rawUrl,
                articleUrl = articleUrl,
                readerBaseUrl = readerBaseUrl,
            )
            "$attribute=$quote$resolvedUrl$quote"
        }
    }

    private fun extractBestImageUrl(
        html: String,
        articleUrl: String,
        readerBaseUrl: String,
    ): String? {
        val candidates = buildList {
            metaTagRegex.findAll(html).forEach { match ->
                addAll(extractMetaCandidates(match.value))
            }
            imageTagRegex.findAll(html).forEach { match ->
                addAll(extractTagCandidates(match.value))
            }
        }

        return candidates
            .asSequence()
            .mapNotNull { candidate ->
                resolveRemoteOrRelativeUrl(
                    candidate.rawUrl,
                    articleUrl = articleUrl,
                    readerBaseUrl = readerBaseUrl,
                )?.let { resolvedUrl ->
                    candidate.copy(rawUrl = resolvedUrl)
                }
            }
            .filter { candidate -> isUsableThumbnailUrl(candidate.rawUrl) }
            .sortedWith(
                compareByDescending<ThumbnailCandidate> { scoreResolvedUrl(it.rawUrl) + it.priority }
                    .thenByDescending { it.widthHint }
            )
            .map { it.rawUrl }
            .firstOrNull()
    }

    private fun extractMetaCandidates(tag: String): List<ThumbnailCandidate> {
        val attributes = extractAttributes(tag)
        val property = attributes["property"]?.lowercase()
        val name = attributes["name"]?.lowercase()
        val content = attributes["content"].orEmpty()
        return when {
            property == "og:image" || name == "og:image" ->
                listOf(ThumbnailCandidate(rawUrl = content, priority = 500))
            else -> emptyList()
        }
    }

    private fun extractTagCandidates(tag: String): List<ThumbnailCandidate> {
        val attributes = extractAttributes(tag)
        val tagName = Regex("^<(\\w+)", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
            ?: return emptyList()

        val candidates = mutableListOf<ThumbnailCandidate>()
        if (tagName == "source") {
            attributes["srcset"]?.let { srcset ->
                candidates += extractSrcsetCandidates(srcset, priority = 450)
            }
            attributes["data-srcset"]?.let { srcset ->
                candidates += extractSrcsetCandidates(srcset, priority = 440)
            }
        }

        attributes["data-full-image"]?.let { candidates += ThumbnailCandidate(it, priority = 420) }
        attributes["data-original"]?.let { candidates += ThumbnailCandidate(it, priority = 410) }
        attributes["data-src"]?.let { candidates += ThumbnailCandidate(it, priority = 400) }
        attributes["data-lazy-src"]?.let { candidates += ThumbnailCandidate(it, priority = 390) }
        attributes["data-srcset"]?.let { srcset ->
            candidates += extractSrcsetCandidates(srcset, priority = 380)
        }
        attributes["srcset"]?.let { srcset ->
            candidates += extractSrcsetCandidates(srcset, priority = 360)
        }
        attributes["src"]?.let { candidates += ThumbnailCandidate(it, priority = 320) }
        return candidates
    }

    private fun extractAttributes(tag: String): Map<String, String> {
        return htmlAttributeRegex.findAll(tag)
            .associate { match ->
                match.groupValues[1].lowercase() to match.groupValues[3].trim()
            }
    }

    private fun extractSrcsetCandidates(
        srcset: String,
        priority: Int,
    ): List<ThumbnailCandidate> {
        return srcset.split(",")
            .mapNotNull { entry ->
                val parts = entry.trim().split(Regex("\\s+"))
                val rawUrl = parts.firstOrNull().orEmpty()
                if (rawUrl.isBlank()) {
                    null
                } else {
                    val descriptor = parts.getOrNull(1).orEmpty()
                    val widthHint = descriptor.removeSuffix("w").toIntOrNull() ?: 0
                    ThumbnailCandidate(
                        rawUrl = rawUrl,
                        priority = priority,
                        widthHint = widthHint,
                    )
                }
            }
    }

    private fun resolveHtmlUrl(
        rawUrl: String,
        articleUrl: String,
        readerBaseUrl: String,
    ): String {
        val candidate = rawUrl.trim()
        if (candidate.isBlank()) return rawUrl
        if (
            candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true) ||
            candidate.startsWith("data:", ignoreCase = true) ||
            candidate.startsWith("mailto:", ignoreCase = true) ||
            candidate.startsWith("tel:", ignoreCase = true) ||
            candidate.startsWith("#") ||
            candidate.startsWith("javascript:", ignoreCase = true)
        ) {
            return candidate
        }
        if (candidate.startsWith("//")) {
            return "https:$candidate"
        }
        if (candidate.startsWith("/api/reader/")) {
            return "$readerBaseUrl$candidate"
        }
        return runCatching { URI(articleUrl).resolve(candidate).toString() }
            .getOrElse { rawUrl }
    }

    private fun resolveRemoteOrRelativeUrl(
        candidateUrl: String?,
        articleUrl: String,
        readerBaseUrl: String,
    ): String? {
        val rawUrl = candidateUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val resolvedUrl = when {
            rawUrl.startsWith("http://", ignoreCase = true) ||
                rawUrl.startsWith("https://", ignoreCase = true) -> rawUrl
            rawUrl.startsWith("//") -> "https:$rawUrl"
            rawUrl.startsWith("/") -> "$readerBaseUrl$rawUrl"
            else -> runCatching { URI(articleUrl).resolve(rawUrl).toString() }
                .getOrElse { rawUrl }
        }
        return resolvedUrl.takeIf(::isUsableThumbnailUrl)
    }

    private val slideshowExcludedPatterns = listOf(
        "favicon", "avatar", "badge", "pixel", "icon", "logo", "1x1", "spacer", "emoji", "tracking"
    )

    fun isValidSlideshowImageUrl(imageUrl: String): Boolean {
        val normalized = imageUrl.lowercase().trim()
        if (normalized.isBlank() || normalized.startsWith("data:")) return false
        if (normalized.endsWith(".svg") || ".svg?" in normalized) return false
        if (slideshowExcludedPatterns.any { pattern -> pattern in normalized && "/api/reader/cached-image/" !in normalized }) return false
        return isUsableThumbnailUrl(imageUrl)
    }

    private fun isUsableThumbnailUrl(imageUrl: String): Boolean {
        val normalized = imageUrl.lowercase()
        if (normalized.startsWith("data:")) return false
        if ("scorecardresearch.com" in normalized) return false
        if ("doubleclick.net" in normalized) return false
        if ("googlesyndication.com" in normalized) return false
        if ("google-analytics.com" in normalized) return false
        if ("sb.scorecardresearch.com" in normalized) return false
        if ("pixel" in normalized && "/api/reader/cached-image/" !in normalized) return false
        return true
    }

    private data class ThumbnailCandidate(
        val rawUrl: String,
        val priority: Int,
        val widthHint: Int = 0,
    )
}

/**
 * Rows the snippet backfill may convert in one sync. Sized to drain a full cache in a single pass:
 * with a smaller budget every un-backfilled row shows no preview at all, because the list projection
 * no longer transports `content`. It is a ceiling against a pathological cache, not a pacing knob.
 */
internal const val SNIPPET_BACKFILL_BUDGET = 6000

@Singleton
class ArticleRepositoryImpl @Inject constructor(
    private val database: ReadabilityDatabase,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val groupDao: GroupDao,
    private val readStateQueueDao: ReadStateQueueDao,
    private val articleImageDao: co.chinho.readabilityreader.data.local.dao.ArticleImageDao,
    private val feverConnectionProvider: FeverConnectionProvider,
    private val connectivityMonitor: ConnectivityMonitor,
    private val syncClock: SyncClock,
    private val articleImageCache: ArticleImageCache,
    private val hostReachabilityTracker: HostReachabilityTracker,
    private val labelWeightDao: LabelWeightDao,
    private val voteQueueDao: VoteQueueDao,
    private val eventQueueDao: EventQueueDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val localDatabaseStats: LocalDatabaseStats? = null,
) : ArticleRepository {

    private val syncMutex = Mutex()

    override fun getArticles(
        feedId: Long?,
        showRead: Boolean,
        groupId: Long?,
        stickyIds: List<Long>,
    ): Flow<List<Article>> {
        return combine(
            userPreferencesRepository.articleOrder,
            userPreferencesRepository.rankingAiEnabled,
        ) { order, aiEnabled ->
            order == "personalised" && aiEnabled
        }.flatMapLatest { sortPersonalised ->
            articleDao.observeArticles(
                feedId = feedId,
                showRead = showRead,
                groupId = groupId,
                sortPersonalised = sortPersonalised,
                stickyIds = stickyIds,
            ).map { entities -> entities.map(ArticleWithFeedTitle::toDomain) }
        }
    }

    override fun getArticle(articleId: Long): Flow<Article?> {
        return articleDao.observeArticle(articleId)
            .map { it?.toDomain() }
    }

    override suspend fun getArticleContent(articleId: Long): String? {
        return articleDao.getArticleContent(articleId)
    }

    override suspend fun getArticleImages(articleId: Long): List<ArticleImage> {
        val rows = articleImageDao.getOrderedImagesForArticle(articleId)
        if (rows.isNotEmpty()) {
            return rows
                .filter { row -> ThumbnailUrlResolver.isValidSlideshowImageUrl(row.imageUrl) }
                .map { row -> ArticleImage(url = row.imageUrl, focalX = row.focalX, focalY = row.focalY) }
        }

        // Pre-join-table articles have no rows. Extracting from HTML costs a content read, so it
        // only runs for that shrinking tail, and those images have no focal point to offer.
        val content = articleDao.getArticleContent(articleId) ?: return emptyList()
        return ArticleImageCache.extractImageUrls(content)
            .filter(ThumbnailUrlResolver::isValidSlideshowImageUrl)
            .distinct()
            .map { ArticleImage(url = it) }
    }

    override fun getSavedArticles(): Flow<List<Article>> {
        return articleDao.observeSavedArticles()
            .map { entities -> entities.map(ArticleWithFeedTitle::toDomain) }
    }

    override fun getArticleCount(): Flow<Int> {
        return articleDao.observeArticleCount()
    }

    override fun getCacheStats(): Flow<CacheStats> {
        return getArticleCount()
            .map { articleCount ->
                val usage = articleImageCache.diskCacheUsage()
                CacheStats(
                    articleCount = articleCount,
                    imageCount = usage.imageCount,
                    databaseBytes = localDatabaseStats?.databaseBytes() ?: 0L,
                    imageCacheUsedBytes = usage.usedBytes,
                    imageCacheMaxBytes = usage.maxBytes,
                )
            }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun syncFromServer(
        keepDays: Int,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): Int {
        if (!syncMutex.tryLock()) {
            Log.i("FeverSync", "Sync already in progress; skipping colliding sync request")
            return 0
        }
        try {
            val connections = feverConnectionProvider.getPotentialConnections()
            if (connections.isEmpty()) {
                Log.e("FeverSync", "No connections available")
                return 0
            }

            var lastError: Exception? = null
            
            for (connection in connections) {
                try {
                    Log.d("FeverSync", "Attempting sync with apiKey: ${connection.apiKey.take(5)}...")
                    val feedsResponse = connection.service.query(apiKey = connection.apiKey, feeds = "")
                    
                    Log.d("FeverSync", "Response received. Auth: ${feedsResponse.auth}")
                    
                    if (feedsResponse.auth == 0) {
                        Log.w("FeverSync", "Auth failed for this key. Trying next.")
                        continue // Try next key
                    }

                    Log.i("FeverSync", "Auth SUCCESS! Fetching everything in one go...")

                    flushReadStateQueue()

                    val metadataResponse = connection.service.query(
                        apiKey = connection.apiKey,
                        groups = "1",
                        feeds = "1",
                        unreadItemIds = "1",
                        savedItemIds = "1"
                    )

                    val localMaxArticleId = articleDao.getMaxArticleId()
                    val localMinArticleId = articleDao.getMinArticleId()
                    val localArticleCount = articleDao.getArticleCount()
                    val firstItemsResponse = connection.service.query(
                        apiKey = connection.apiKey,
                        items = "1",
                    )
                    val items = fetchPagedItems(
                        connection = connection,
                        firstPage = firstItemsResponse,
                        startingAfterId = localMaxArticleId,
                        oldestLocalId = localMinArticleId,
                        localArticleCount = localArticleCount,
                        keepDays = keepDays,
                        onProgress = onProgress,
                    )
                    val combinedResponse = metadataResponse.copy(items = items)

                    Log.d(
                        "FeverSync",
                        "Metadata Details: Groups=${metadataResponse.groups?.size}, " +
                            "Feeds=${metadataResponse.feeds?.size}, " +
                            "Mappings=${metadataResponse.feedsGroups?.size}, " +
                            "UnreadIds=${metadataResponse.unreadItemIds?.length ?: 0}, " +
                            "ServerTotal=${firstItemsResponse.totalItems}, " +
                            "LocalCount=$localArticleCount, " +
                            "FetchedItems=${items.size}, " +
                            "InitialSync=${localMaxArticleId == null}"
                    )

                    val syncedCount = processSyncResponse(
                        response = combinedResponse,
                        readerBaseUrl = connection.serverUrl.toReaderBaseUrl(),
                        onProgress = onProgress,
                    )

                    try {
                        performRankingSyncPass(connection)
                    } catch (e: Exception) {
                        Log.e("FeverSync", "Best-effort ranking pass failed", e)
                    }

                    try {
                        syncFocalPoints(connection)
                    } catch (e: Exception) {
                        Log.e("FeverSync", "Best-effort focal points sync failed", e)
                    }

                    Log.i(
                        "FeverSync",
                        "Sync complete. Groups added: ${combinedResponse.groups?.size ?: 0}, " +
                            "Feeds added: ${combinedResponse.feeds?.size ?: 0}, " +
                            "Items added: $syncedCount"
                    )
                    return syncedCount // Success!
                } catch (e: Exception) {
                    Log.e("FeverSync", "Network/Parsing error: ${e.message}", e)
                    lastError = e
                }
            }
            
            throw lastError ?: Exception("FEVER: Not Authorized. Please check your credentials.")
        } finally {
            syncMutex.unlock()
        }
    }

    internal suspend fun processSyncResponse(
        response: FeverResponse,
        readerBaseUrl: String,
        onProgress: suspend (current: Int, total: Int) -> Unit,
    ): Int {
        // Build feedId → groupId map from the feeds_groups array
        val feedIdToGroupId: Map<Long, Long> = response.feedsGroups.orEmpty()
            .flatMap { mapping ->
                mapping.feedIds.split(",")
                    .mapNotNull { it.trim().toLongOrNull() }
                    .map { feedId -> feedId to mapping.groupId }
            }
            .toMap()

        val syncedAt = syncClock.nowMillis()

        val unreadIds = response.unreadItemIds?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.toSet() ?: emptySet()
            
        val savedIds = response.savedItemIds?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.toSet() ?: emptySet()

        val faviconUrlsToWarm = mutableListOf<String>()

        database.withTransaction {
            val feeds = response.feeds.orEmpty()
            if (feeds.isNotEmpty()) {
                feedDao.upsertFeeds(
                    feeds.map { dto ->
                        val resolvedFaviconUrl = ThumbnailUrlResolver.resolveFaviconUrl(
                            feedId = dto.id,
                            feedUrl = dto.url,
                            siteUrl = dto.siteUrl,
                            faviconUrl = dto.favicon,
                            faviconProxyUrl = dto.faviconProxy,
                            readerBaseUrl = readerBaseUrl,
                        )
                        if (resolvedFaviconUrl != null) {
                            faviconUrlsToWarm += resolvedFaviconUrl
                        }
                        FeedEntity(
                            id = dto.id,
                            groupId = feedIdToGroupId[dto.id] ?: 0L,
                            title = dto.title,
                            url = dto.url,
                            siteUrl = dto.siteUrl,
                            faviconId = dto.faviconId,
                            faviconUrl = dto.favicon,
                            faviconProxyUrl = dto.faviconProxy,
                            lastSyncedAt = syncedAt,
                        )
                    }
                )
            }

            val groups = response.groups.orEmpty()
            if (groups.isNotEmpty()) {
                groupDao.upsertGroups(
                    groups.map { dto ->
                        GroupEntity(
                            id = dto.id,
                            title = dto.title,
                        )
                    }
                )
            }
        }

        if (faviconUrlsToWarm.isNotEmpty()) {
            articleImageCache.cacheUrls(faviconUrlsToWarm)
        }

        val items = response.items.orEmpty()
        if (items.isNotEmpty()) {
            // One transaction per article invalidated `articles` and `article_images` on every
            // commit, re-running the observed list query once per synced item. Chunking keeps the
            // observer emissions proportional to pages, not articles. `items` is already fully
            // resident (fetchPagedItems accumulates before returning), so this adds no memory.
            items.chunked(ARTICLE_WRITE_CHUNK_SIZE).forEach { chunk ->
                val chunkEntities = ArrayList<ArticleEntity>(chunk.size)
                val chunkJoinRows = ArrayList<ArticleImageEntity>(chunk.size)
                chunk.forEach { dto ->
                    val normalizedContent = dto.html?.let { html ->
                        ThumbnailUrlResolver.normalizeArticleHtml(
                            html = html,
                            articleUrl = dto.url,
                            readerBaseUrl = readerBaseUrl,
                        )
                    }
                    val resolvedThumbnailUrl = ThumbnailUrlResolver.resolveThumbnailUrl(
                        rawUrl = dto.mainImage,
                        proxyUrl = dto.mainImageProxy,
                        articleUrl = dto.url,
                        readerBaseUrl = readerBaseUrl,
                        html = dto.html,
                    )
                    val snippetText = normalizedContent?.let { SnippetExtractor.extract(it) ?: "" }

                    val articleEntity = ArticleEntity(
                        id = dto.id,
                        feedId = dto.feedId,
                        title = dto.title,
                        url = dto.url,
                        content = normalizedContent,
                        publishedAt = dto.createdOnTime.toEpochMillis(),
                        isRead = !unreadIds.contains(dto.id),
                        isSaved = savedIds.contains(dto.id),
                        thumbnailUrl = resolvedThumbnailUrl,
                        cachedAt = syncedAt,
                        contentCachedAt = normalizedContent?.let { syncedAt },
                        imagesCachedAt = null,
                        snippetText = snippetText,
                    )

                    val joinRows = buildArticleImageRows(
                        articleId = dto.id,
                        content = normalizedContent,
                        thumbnailUrl = resolvedThumbnailUrl,
                    )

                    // ImageCacheWorker (enqueued by ArticleSyncWorker) handles image warming
                    // out-of-band, so we don't block sync on per-article network fetches.
                    chunkEntities += articleEntity
                    chunkJoinRows += joinRows
                }

                database.withTransaction {
                    articleDao.upsertArticles(chunkEntities)
                    if (chunkJoinRows.isNotEmpty()) {
                        articleImageDao.insertAll(chunkJoinRows)
                    }
                }
            }
        }

        database.withTransaction {
            if (response.unreadItemIds != null) {
                articleDao.markAllAsRead()
                if (unreadIds.isNotEmpty()) {
                    for (chunk in unreadIds.chunked(SQL_BIND_CHUNK_SIZE)) {
                        articleDao.markAllInAsUnread(chunk)
                    }
                }
            }

            if (response.savedItemIds != null) {
                articleDao.markAllAsUnsaved()
                if (savedIds.isNotEmpty()) {
                    for (chunk in savedIds.chunked(SQL_BIND_CHUNK_SIZE)) {
                        articleDao.markAllInAsSaved(chunk)
                    }
                }
            }
        }

        return response.items?.size ?: 0
    }

    override suspend fun flushReadStateQueue() {
        if (!connectivityMonitor.isOnline()) return

        val connection = feverConnectionProvider.getActiveConnection() ?: return
        val queuedActions = readStateQueueDao.getQueuedActions()
        if (queuedActions.isEmpty()) return

        val semaphore = Semaphore(READ_STATE_FLUSH_CONCURRENCY)
        val completedIds = coroutineScope {
            queuedActions.map { action ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            connection.service.query(
                                apiKey = connection.apiKey,
                                mark = "item",
                                actionState = action.action,
                                id = action.articleId,
                            )
                        }.getOrNull()?.takeIf { it.auth == 1 }?.let { action.id }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        if (completedIds.isNotEmpty()) {
            readStateQueueDao.deleteByIds(completedIds)
        }
        flushRankingQueues(connection)
    }

    override suspend fun flushRankingQueues() {
        val connection = feverConnectionProvider.getActiveConnection() ?: return
        flushRankingQueues(connection)
    }

    private suspend fun flushRankingQueues(connection: FeverConnection) {
        if (!connectivityMonitor.isOnline()) return

        val voteItems = voteQueueDao.getAll()
        val eventItems = eventQueueDao.getAll()

        if (voteItems.isEmpty() && eventItems.isEmpty()) return

        val voteDtos = voteItems.map {
            RankingVoteDto(
                articleId = it.articleId,
                vote = it.vote,
                markRead = it.markRead,
            )
        }
        val eventDtos = eventItems.map {
            RankingEventDto(
                articleId = it.articleId,
                eventType = it.eventType,
                dwellSeconds = it.dwellSeconds,
            )
        }

        try {
            connection.rankingService.sendFeedback(
                RankingFeedbackRequest(
                    apiKey = connection.apiKey,
                    votes = voteDtos.ifEmpty { null },
                    events = eventDtos.ifEmpty { null },
                )
            )

            if (voteItems.isNotEmpty()) {
                voteQueueDao.deleteByIds(voteItems.map { it.id })
            }
            if (eventItems.isNotEmpty()) {
                eventQueueDao.deleteByIds(eventItems.map { it.id })
            }
        } catch (e: Exception) {
            Log.e("RankingSync", "Failed to flush ranking queues", e)
        }
    }

    private suspend fun performRankingSyncPass(connection: FeverConnection) {
        flushRankingQueues(connection)

        // The server bound is exclusive (`a.id > since_id`), so the lowest article we hold would
        // never be scored if we sent its id verbatim.
        val sinceId = articleDao.getMinNonSavedArticleId()?.minus(1)
        val response = connection.rankingService.getScores(
            apiKey = connection.apiKey,
            sinceId = sinceId,
        )

        userPreferencesRepository.setRankingAiEnabled(response.aiEnabled)

        if (!response.aiEnabled) {
            Log.i("RankingSync", "ai_enabled is false on server. Personalised ranking disabled.")
            return
        }

        val weightEntities = mutableListOf<LabelWeightEntity>()
        response.weights?.forEach { (axis, list) ->
            list.forEach { dto ->
                val canonical = (dto.canonical ?: dto.label).orEmpty()
                if (canonical.isNotBlank()) {
                    weightEntities.add(
                        LabelWeightEntity(
                            axis = axis,
                            canonicalLabel = canonical,
                            displayLabel = dto.label ?: canonical,
                            declared = dto.declared,
                            prior = dto.prior,
                            votes = dto.votes,
                            explicit = dto.explicit,
                            behavioural = dto.behavioural,
                            effective = dto.effective,
                        )
                    )
                }
            }
        }
        labelWeightDao.replaceAllWeights(weightEntities)

        val gson = Gson()
        val scoresList = response.scores.orEmpty()
        val extrasMap = response.extras.orEmpty()
        val tagsMap = response.tags.orEmpty().associateBy { it.id }

        database.withTransaction {
            articleDao.clearAllArticleScores()

            scoresList.forEach { arr ->
                if (arr.size() >= 2) {
                    val id = arr.get(0).asLong
                    val score = arr.get(1).asDouble
                    val extra = extrasMap[id.toString()]
                    val tag = tagsMap[id]

                    val freshness = extra?.freshness
                    val pairTerms = extra?.pairs?.let { gson.toJson(it) }
                    val primaryTopic = tag?.primary
                    // Canonical keys, not the original spellings: label_weights is keyed by
                    // canonical form, and this column exists only to join against it. The
                    // breakdown panel renders the display spelling from the matched weight row.
                    val secondaryTopics =
                        (tag?.secondaryCanonical ?: tag?.secondary)?.let { gson.toJson(it) }
                    val region = tag?.region
                    val articleType = tag?.type
                    val vote = tag?.vote

                    articleDao.updateArticleScoreAndExtras(
                        articleId = id,
                        score = score,
                        primaryTopic = primaryTopic,
                        secondaryTopics = secondaryTopics,
                        region = region,
                        articleType = articleType,
                        vote = vote,
                        freshness = freshness,
                        pairTerms = pairTerms,
                    )
                }
            }
        }
    }

    override suspend fun recordVote(articleId: Long, vote: String?, markRead: Boolean) {
        articleDao.updateArticleVote(articleId, vote)
        if (markRead) {
            articleDao.updateReadState(articleId, true)
            enqueueOrSendItemAction(articleId, ReadStateAction.READ)
        }

        eventQueueDao.deleteByArticleIdAndType(articleId, "read_without_vote")

        if (sendVoteImmediately(articleId, vote, markRead)) return

        voteQueueDao.insert(
            VoteQueueEntity(
                articleId = articleId,
                vote = vote,
                markRead = markRead,
                queuedAt = syncClock.nowMillis(),
            )
        )
    }

    /**
     * Returns false for every failure so the caller always falls through to the queue. The
     * send is wrapped because "online" only means a usable route existed a moment ago: on
     * unstable mobile the POST still throws, and an unwrapped throw here would skip the queue
     * insert and lose the vote to the server permanently.
     */
    private suspend fun sendVoteImmediately(
        articleId: Long,
        vote: String?,
        markRead: Boolean,
    ): Boolean {
        if (!connectivityMonitor.isOnline()) return false
        val connection = feverConnectionProvider.getActiveConnection() ?: return false

        return try {
            connection.rankingService.sendFeedback(
                RankingFeedbackRequest(
                    apiKey = connection.apiKey,
                    votes = listOf(
                        RankingVoteDto(
                            articleId = articleId,
                            vote = vote,
                            markRead = markRead,
                        )
                    ),
                )
            )
            true
        } catch (e: Exception) {
            Log.w("RankingSync", "Immediate vote send failed; queued for next sync", e)
            false
        }
    }

    override suspend fun recordEvent(articleId: Long, eventType: String, dwellSeconds: Int?) {
        if (eventType == "read_without_vote") {
            val existingVote = articleDao.getArticleVote(articleId)
            val queuedVotes = voteQueueDao.getAll().any { it.articleId == articleId }
            if (existingVote != null || queuedVotes) {
                return
            }
        }

        eventQueueDao.insert(
            EventQueueEntity(
                articleId = articleId,
                eventType = eventType,
                dwellSeconds = dwellSeconds,
                queuedAt = syncClock.nowMillis(),
            )
        )
    }

    override suspend fun markRead(articleId: Long, isRead: Boolean) {
        articleDao.updateReadState(articleId = articleId, isRead = isRead)
        enqueueOrSendItemAction(
            articleId = articleId,
            action = if (isRead) ReadStateAction.READ else ReadStateAction.UNREAD,
        )
        if (isRead) {
            recordEvent(articleId, "read_without_vote")
        }
    }

    override suspend fun markSaved(articleId: Long, isSaved: Boolean) {
        articleDao.updateSavedState(articleId = articleId, isSaved = isSaved)
        enqueueOrSendItemAction(
            articleId = articleId,
            action = if (isSaved) ReadStateAction.SAVED else ReadStateAction.UNSAVED,
        )
    }

    private suspend fun sendMarkFeedReadImmediately(feedId: Long, beforeTimestamp: Long): Boolean {
        if (!connectivityMonitor.isOnline()) return false
        val connection = feverConnectionProvider.getActiveConnection() ?: return false

        return try {
            val response = connection.service.query(
                apiKey = connection.apiKey,
                mark = "feed",
                actionState = "read",
                id = feedId,
                before = beforeTimestamp,
            )
            response.auth == 1
        } catch (e: Exception) {
            Log.w(TAG, "Immediate mark feed read failed; queued for next sync", e)
            false
        }
    }

    private suspend fun sendMarkGroupReadImmediately(groupId: Long, beforeTimestamp: Long): Boolean {
        if (!connectivityMonitor.isOnline()) return false
        val connection = feverConnectionProvider.getActiveConnection() ?: return false

        return try {
            val response = connection.service.query(
                apiKey = connection.apiKey,
                mark = "group",
                actionState = "read",
                id = groupId,
                before = beforeTimestamp,
            )
            response.auth == 1
        } catch (e: Exception) {
            Log.w(TAG, "Immediate mark group read failed; queued for next sync", e)
            false
        }
    }

    override suspend fun markFeedRead(feedId: Long) {
        val unreadIds = database.withTransaction {
            val ids = articleDao.getUnreadIdsForFeed(feedId)
            articleDao.markFeedRead(feedId)
            ids
        }
        if (unreadIds.isEmpty()) return

        val beforeTimestamp = syncClock.nowMillis() / 1000L
        if (!sendMarkFeedReadImmediately(feedId, beforeTimestamp)) {
            if (unreadIds.size > BULK_QUEUE_WARN_THRESHOLD) {
                Log.w(TAG, "Queued ${unreadIds.size} individual read actions for feed $feedId after a failed bulk mark")
            }
            val now = syncClock.nowMillis()
            readStateQueueDao.insertAll(
                unreadIds.map { id ->
                    ReadStateQueueEntity(
                        articleId = id,
                        action = ReadStateAction.READ.wireValue,
                        queuedAt = now,
                    )
                }
            )
        }
    }

    override suspend fun markGroupRead(groupId: Long, beforeTimestamp: Long) {
        val beforeMillis = beforeTimestamp.toEpochMillis()
        val unreadIds = database.withTransaction {
            val ids = articleDao.getUnreadIdsForGroup(groupId, beforeMillis)
            articleDao.markGroupRead(groupId, beforeMillis)
            ids
        }
        if (unreadIds.isEmpty()) return

        if (!sendMarkGroupReadImmediately(groupId, beforeTimestamp)) {
            if (unreadIds.size > BULK_QUEUE_WARN_THRESHOLD) {
                Log.w(TAG, "Queued ${unreadIds.size} individual read actions for group $groupId after a failed bulk mark")
            }
            val now = syncClock.nowMillis()
            readStateQueueDao.insertAll(
                unreadIds.map { id ->
                    ReadStateQueueEntity(
                        articleId = id,
                        action = ReadStateAction.READ.wireValue,
                        queuedAt = now,
                    )
                }
            )
        }
    }

    override suspend fun evictOldArticles(keepDays: Int) {
        val cutoffMillis = syncClock.nowMillis() - keepDays * MILLIS_PER_DAY
        val cutoffSeconds = cutoffMillis / 1000L

        // Strict join-table eviction. For each batch of evictable articles, ask the DB
        // which image URLs are referenced ONLY by them (i.e. no surviving article still
        // uses the URL). Hand those to Coil for disk deletion, then DELETE the rows —
        // FK CASCADE drops the matching article_images entries inside the same DB op.
        while (true) {
            val batch = articleDao.getEvictableArticleBatch(
                cutoffMillis,
                cutoffSeconds,
                EVICTION_BATCH_SIZE,
            )
            if (batch.isEmpty()) break

            val batchIds = batch.map { it.id }
            val urlsToRemove = articleImageDao.getUrlsOwnedOnlyBy(batchIds)

            articleImageCache.removeUrls(urlsToRemove)
            articleDao.deleteArticlesByIds(batchIds)

            if (batch.size < EVICTION_BATCH_SIZE) break
        }
    }

    override suspend fun clearAllCache() {
        database.withTransaction {
            readStateQueueDao.clearAll()
            articleDao.clearAll()
            feedDao.clearAll()
            groupDao.clearAll()
            articleImageDao.clearAll()
            labelWeightDao.clearAll()
            voteQueueDao.clearAll()
            eventQueueDao.clearAll()
            database.jottyQueueDao().clear()
        }
        articleImageCache.clearAll()
    }

    override suspend fun rewarmMissingThumbnails(maxToWarm: Int): Int {
        if (maxToWarm <= 0) return 0
        if (!connectivityMonitor.isOnline()) return 0
        val candidates = articleDao.getThumbnailArticlesForReconcile(THUMBNAIL_RECONCILE_LIMIT)
        if (candidates.isEmpty()) return 0

        var warmed = 0
        val now = syncClock.nowMillis()
        for (row in candidates) {
            if (warmed >= maxToWarm) break
            val url = row.thumbnailUrl
            if (articleImageCache.isCached(url)) {
                if (row.imagesCachedAt == null) articleDao.markImagesCached(row.id, now)
                continue
            }
            val host = runCatching { URI(url).host }.getOrNull().orEmpty()
            if (host.isNotEmpty() && hostReachabilityTracker.isFailing(host)) {
                Log.d("FeverSync", "Rewarm halted: host=$host marked failing")
                break
            }
            val ok = runCatching { articleImageCache.cacheUrl(url) }.getOrDefault(false)
            if (ok) {
                articleDao.markImagesCached(row.id, now)
                warmed++
            } else if (host.isNotEmpty() && hostReachabilityTracker.isFailing(host)) {
                Log.d("FeverSync", "Rewarm halted after failure: host=$host")
                break
            }
        }
        if (warmed > 0) {
            Log.i("FeverSync", "Rewarmed $warmed missing thumbnails (scanned ${candidates.size})")
        }
        return warmed
    }

    /**
     * Returns false for every failure so the caller always falls through to the queue. The send
     * is wrapped because "online" only means a usable route existed a moment ago: on unstable
     * mobile the POST still throws, and an unwrapped throw here escaped the calling
     * viewModelScope.launch and killed the process (four foreground crashes on 2026-09-01).
     */
    private suspend fun sendItemActionImmediately(
        articleId: Long,
        action: ReadStateAction,
    ): Boolean {
        if (!connectivityMonitor.isOnline()) return false
        val connection = feverConnectionProvider.getActiveConnection() ?: return false

        return try {
            val response = connection.service.query(
                apiKey = connection.apiKey,
                mark = "item",
                actionState = action.wireValue,
                id = articleId,
            )
            response.auth == 1
        } catch (e: Exception) {
            Log.w(TAG, "Immediate item action failed; queued for next sync", e)
            false
        }
    }

    private suspend fun enqueueOrSendItemAction(
        articleId: Long,
        action: ReadStateAction,
    ) {
        if (sendItemActionImmediately(articleId, action)) return

        readStateQueueDao.insert(
            ReadStateQueueEntity(
                articleId = articleId,
                action = action.wireValue,
                queuedAt = syncClock.nowMillis(),
            )
        )
    }

    override fun getLabelWeights(): Flow<List<co.chinho.readabilityreader.domain.model.LabelWeight>> {
        return labelWeightDao.observeAllWeights().map { entities ->
            entities.map { entity ->
                co.chinho.readabilityreader.domain.model.LabelWeight(
                    axis = entity.axis,
                    canonicalLabel = entity.canonicalLabel,
                    displayLabel = entity.displayLabel,
                    declared = entity.declared,
                    prior = entity.prior,
                    votes = entity.votes,
                    explicit = entity.explicit,
                    behavioural = entity.behavioural,
                    effective = entity.effective,
                )
            }
        }
    }

    override suspend fun syncFocalPoints() {
        val connection = feverConnectionProvider.getActiveConnection() ?: return
        try {
            syncFocalPoints(connection)
        } catch (e: Exception) {
            Log.e("FeverSync", "syncFocalPoints failed", e)
        }
    }

    internal suspend fun syncFocalPoints(connection: FeverConnection) {
        clearStaleOnDeviceFocalPoints()

        val urls = readUrlsNeedingFocal()
        if (urls.isEmpty()) return

        val hashToUrls = mutableMapOf<String, MutableList<String>>()
        for (url in urls) {
            val hash = CachedImageHash.fromUrl(url) ?: continue
            hashToUrls.getOrPut(hash) { mutableListOf() }.add(url)
        }

        val distinctHashes = hashToUrls.keys.toList()
        if (distinctHashes.isEmpty()) return

        distinctHashes.chunked(FOCAL_POINTS_PAGE_SIZE).forEach { hashChunk ->
            val response = connection.focalService.getFocalPoints(
                FocalPointsRequest(
                    apiKey = connection.apiKey,
                    hashes = hashChunk,
                )
            )
            val focalMap = response.focal.orEmpty()
            val updates = mutableListOf<FocalPointUpdate>()
            focalMap.forEach { (hash, coords) ->
                if (coords.size >= 2) {
                    val x = coords[0]
                    val y = coords[1]
                    val matchingUrls = hashToUrls[hash].orEmpty()
                    for (url in matchingUrls) {
                        updates.add(FocalPointUpdate(url, x, y))
                    }
                }
            }
            if (updates.isNotEmpty()) {
                articleImageDao.setFocalPoints(updates)
            }
        }
    }

    /**
     * Reads the pending set in pages and accumulates before any write. Unpaged, Room threw
     * `Couldn't read row 6364 from CursorWindow` on a 20,259-row result when a second concurrent
     * sync committed to `article_images` during the cursor walk (2026-08-21, Pixel 11 Pro).
     * Every page is read before the caller writes anything, so the offsets cannot shift under us.
     */
    private suspend fun readUrlsNeedingFocal(): List<String> {
        val urls = mutableListOf<String>()
        var offset = 0
        while (true) {
            val page = articleImageDao.getUrlsNeedingFocal(FOCAL_URL_READ_PAGE_SIZE, offset)
            if (page.isEmpty()) break
            urls += page
            if (page.size < FOCAL_URL_READ_PAGE_SIZE) break
            offset += FOCAL_URL_READ_PAGE_SIZE
        }
        return urls
    }

    /**
     * Runs once per install. Focal points written by the retired on-device ML Kit analyser are
     * valid but inferior, and the sync only touches `focalComputed = 0`, so without this they
     * would survive until their articles age out. Clearing the flag hands those rows back to the
     * server. Skipped on a device that never ran the analyser — there is nothing stale to reset.
     */
    private suspend fun clearStaleOnDeviceFocalPoints() {
        if (userPreferencesRepository.serverFocalResetDone.first()) return
        val alreadyComputed = articleImageDao.countFocalComputed()
        if (alreadyComputed > 0) {
            articleImageDao.clearFocalComputed()
            Log.i("FeverSync", "Reset $alreadyComputed on-device focal points for server refresh")
        }
        userPreferencesRepository.setServerFocalResetDone(true)
    }

    internal suspend fun fetchPagedItems(
        connection: FeverConnection,
        firstPage: FeverResponse,
        startingAfterId: Long?,
        oldestLocalId: Long?,
        localArticleCount: Int,
        keepDays: Int,
        onProgress: suspend (current: Int, total: Int) -> Unit,
    ): List<FeverItemDto> {
        val allItems = mutableListOf<FeverItemDto>()
        val serverTotalItems = firstPage.totalItems ?: 0
        val cutoffSeconds = (syncClock.nowMillis() - keepDays * MILLIS_PER_DAY) / 1000L

        if (startingAfterId != null) {
            val initialItems = firstPage.items.orEmpty()
                .filter { item -> item.id > startingAfterId && item.isNewerOrEqual(cutoffSeconds) }
            allItems += initialItems
            onProgress(allItems.size, serverTotalItems)

            // Even if some items in the first page were old, we should keep checking 
            // the 'sinceId' chain for a bit to ensure we didn't miss any newer IDs 
            // that might be interleaved (though rare in Fever API).
            // However, if the WHOLE page is old, we definitely stop.
            if (firstPage.items.orEmpty().isNotEmpty() && 
                firstPage.items.orEmpty().all { item -> item.isOlderThan(cutoffSeconds) }) {
                return allItems
            }

            var sinceId = startingAfterId
            while (true) {
                val response = connection.service.query(
                    apiKey = connection.apiKey,
                    items = "1",
                    sinceId = sinceId,
                )
                val page = response.items.orEmpty()
                if (page.isEmpty()) break

                val validItems = page.filter { item -> item.isNewerOrEqual(cutoffSeconds) }
                allItems += validItems
                onProgress(allItems.size, serverTotalItems)
                
                // If we found NO valid items in this page, we've gone too far back.
                if (validItems.isEmpty() && page.isNotEmpty()) break

                sinceId = page.maxOf { it.id }
            }

            // For "filling in gaps" older than what we have locally:
            if (
                oldestLocalId != null &&
                serverTotalItems > 0 &&
                localArticleCount + allItems.size < serverTotalItems
            ) {
                var nextMaxId = oldestLocalId
                while (true) {
                    val response = connection.service.query(
                        apiKey = connection.apiKey,
                        items = "1",
                        maxId = nextMaxId,
                    )
                    val page = response.items.orEmpty()
                    if (page.isEmpty()) break

                    val validItems = page.filter { item -> item.isNewerOrEqual(cutoffSeconds) }
                    allItems += validItems
                    onProgress(allItems.size, serverTotalItems)

                    // If we found NO valid items, or the page had old items, we stop filling the gap.
                    if (validItems.isEmpty() || validItems.size < page.size) break
                    nextMaxId = page.minOf { it.id }
                }
            }

            return allItems
        }

        // Initial sync case (no startingAfterId)
        val firstPageValid = firstPage.items.orEmpty().filter { item -> item.isNewerOrEqual(cutoffSeconds) }
        allItems += firstPageValid
        onProgress(allItems.size, serverTotalItems)

        // Stop only when the entire first page is older than the cutoff — FEVER ids are
        // monotonic by created_on_time, so an all-older page means deeper pages are too.
        val allOlder = firstPage.items.orEmpty().isNotEmpty() &&
            firstPage.items.orEmpty().all { item -> item.isOlderThan(cutoffSeconds) }
        if (allOlder) {
            if (allItems.isEmpty() && serverTotalItems > 0) {
                android.util.Log.i(
                    "FeverSync",
                    "Server has $serverTotalItems items total; 0 within the last $keepDays days. Consider increasing cache duration."
                )
            }
            return allItems
        }

        var nextMaxId = firstPage.items.orEmpty().minOfOrNull { it.id }
        while (true) {
            if (nextMaxId == null) break

            val response = connection.service.query(
                apiKey = connection.apiKey,
                items = "1",
                maxId = nextMaxId,
            )
            val page = response.items.orEmpty()
            if (page.isEmpty()) break

            val validItems = page.filter { item -> item.isNewerOrEqual(cutoffSeconds) }
            allItems += validItems
            onProgress(allItems.size, serverTotalItems)

            val pageAllOlder = page.all { item -> item.isOlderThan(cutoffSeconds) }
            if (pageAllOlder) break
            nextMaxId = page.minOf { it.id }
        }

        if (allItems.isEmpty() && serverTotalItems > 0) {
            android.util.Log.i(
                "FeverSync",
                "Server has $serverTotalItems items total; 0 within the last $keepDays days. Consider increasing cache duration."
            )
        }
        return allItems
    }

    override suspend fun backfillSnippets(): Int {
        var totalProcessed = 0
        var totalUpdated = 0
        while (totalProcessed < SNIPPET_BACKFILL_BUDGET) {
            val remainingBudget = SNIPPET_BACKFILL_BUDGET - totalProcessed
            val batchLimit = minOf(SNIPPET_BACKFILL_BATCH_SIZE, remainingBudget)
            val ids = articleDao.getIdsNeedingSnippet(batchLimit)
            if (ids.isEmpty()) break

            val updates = mutableListOf<Pair<Long, String>>()
            for (id in ids) {
                try {
                    val content = articleDao.getArticleContent(id)
                    val snippet = SnippetExtractor.extract(content) ?: ""
                    updates.add(id to snippet)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read content for article $id during snippet backfill", e)
                }
            }

            if (updates.isNotEmpty()) {
                database.withTransaction {
                    for ((id, snippet) in updates) {
                        articleDao.updateArticleSnippet(id, snippet)
                    }
                }
                totalUpdated += updates.size
            }

            totalProcessed += ids.size
            if (ids.size < batchLimit) break
        }
        return totalUpdated
    }

    private companion object {
        const val TAG = "FeverSync"
        const val BULK_QUEUE_WARN_THRESHOLD = 5000
        const val READ_STATE_FLUSH_CONCURRENCY = 5
        const val SQL_BIND_CHUNK_SIZE = 900
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        const val EVICTION_BATCH_SIZE = 200
        const val THUMBNAIL_RECONCILE_LIMIT = 1500
        const val ARTICLE_WRITE_CHUNK_SIZE = 200
        const val FOCAL_POINTS_PAGE_SIZE = 1000
        const val SNIPPET_BACKFILL_BATCH_SIZE = 200

        /** Row cap per cursor read. See `readUrlsNeedingFocal` for why this is paged. */
        const val FOCAL_URL_READ_PAGE_SIZE = 2000

        private fun Long?.toEpochMillis(): Long {
            val value = this ?: 0L
            return if (value in 1L until 100_000_000_000L) value * 1000L else value
        }
    }

    private fun String.toReaderBaseUrl(): String {
        val normalized = trim().removeSuffix("/")
        return normalized.removeSuffix("/fever")
    }

    private fun FeverItemDto.isNewerOrEqual(cutoffSeconds: Long): Boolean {
        val createdOn = createdOnTime ?: 0L
        return createdOn >= cutoffSeconds
    }

    private fun FeverItemDto.isOlderThan(cutoffSeconds: Long): Boolean {
        val createdOn = createdOnTime ?: 0L
        return createdOn < cutoffSeconds
    }
}

internal fun buildArticleImageRows(
    articleId: Long,
    content: String?,
    thumbnailUrl: String?,
): List<ArticleImageEntity> {
    val ordered = buildList {
        if (!content.isNullOrBlank()) {
            addAll(ArticleImageCache.extractImageUrls(content))
        }
        thumbnailUrl?.takeIf(String::isNotBlank)?.let(::add)
    }.distinct()

    return ordered.mapIndexed { index, url ->
        ArticleImageEntity(articleId = articleId, imageUrl = url, position = index)
    }
}







