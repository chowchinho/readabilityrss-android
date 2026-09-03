package co.chinho.readabilityreader.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val syncIntervalHours: Flow<Int>
    val syncOnAppOpen: Flow<Boolean>
    val wifiOnlySync: Flow<Boolean>
    val chargingOnlySync: Flow<Boolean>
    val autoMarkReadDelaySec: Flow<Int>

    val cacheDurationDays: Flow<Int>
    val maxCacheSizeMb: Flow<Int>
    val prefetchCount: Flow<Int>

    val articleListFontSizeSp: Flow<Int>
    val articleReaderFontSizeSp: Flow<Int>
    val articleListFontFamily: Flow<String>
    val articleReaderFontFamily: Flow<String>
    val showReadArticles: Flow<Boolean>
    val hideEmptyFeedSources: Flow<Boolean>
    val hideSavedWhenEmpty: Flow<Boolean>
    val externalLinkHandler: Flow<String>
    val feedCategoryOrder: Flow<List<Long>>
    val dockedCategoryCount: Flow<Int>
    val votePillFractionX: Flow<Float>
    val votePillFractionY: Flow<Float>
    val tabletArticleListWidthLandscape: Flow<Float>
    val tabletArticleListWidthPortrait: Flow<Float>
    val tabletFeedPaneCollapsedLandscape: Flow<Boolean>
    val tabletFeedPaneCollapsedPortrait: Flow<Boolean>

    val themeMode: Flow<String>

    val forceOffline: Flow<Boolean>

    val serverFocalResetDone: Flow<Boolean>

    val tabletMultiPaneLayout: Flow<Boolean>

    val jottyEnabled: Flow<Boolean>
    val jottyServerUrl: Flow<String>
    val jottyDefaultCategory: Flow<String>
    val showSavedArticlesSection: Flow<Boolean>

    val articleOrder: Flow<String>
    val rankingAiEnabled: Flow<Boolean>

    val allSourcesFullImage: Flow<Boolean>
    val fullImageCategoryIds: Flow<Set<Long>>

    suspend fun setSyncIntervalHours(value: Int)
    suspend fun setSyncOnAppOpen(value: Boolean)
    suspend fun setWifiOnlySync(value: Boolean)
    suspend fun setChargingOnlySync(value: Boolean)
    suspend fun setAutoMarkReadDelaySec(value: Int)
    suspend fun setCacheDurationDays(value: Int)
    suspend fun setMaxCacheSizeMb(value: Int)
    suspend fun setPrefetchCount(value: Int)
    suspend fun setArticleListFontSizeSp(value: Int)
    suspend fun setArticleReaderFontSizeSp(value: Int)
    suspend fun setArticleListFontFamily(value: String)
    suspend fun setArticleReaderFontFamily(value: String)
    suspend fun setShowReadArticles(value: Boolean)
    suspend fun setHideEmptyFeedSources(value: Boolean)
    suspend fun setHideSavedWhenEmpty(value: Boolean)
    suspend fun setExternalLinkHandler(value: String)
    suspend fun setFeedCategoryOrder(value: List<Long>)
    suspend fun setDockedCategoryCount(value: Int)
    suspend fun setVotePillFraction(x: Float, y: Float)
    suspend fun resetFeedCategoryOrder()
    suspend fun setTabletArticleListWidthLandscape(value: Float)
    suspend fun setTabletArticleListWidthPortrait(value: Float)
    suspend fun setTabletFeedPaneCollapsedLandscape(value: Boolean)
    suspend fun setTabletFeedPaneCollapsedPortrait(value: Boolean)
    suspend fun setThemeMode(value: String)
    suspend fun setForceOffline(value: Boolean)
    suspend fun setServerFocalResetDone(value: Boolean)
    suspend fun setTabletMultiPaneLayout(value: Boolean)

    suspend fun setJottyEnabled(value: Boolean)
    suspend fun setJottyServerUrl(value: String)
    suspend fun setJottyDefaultCategory(value: String)
    suspend fun setShowSavedArticlesSection(value: Boolean)

    suspend fun setArticleOrder(value: String)
    suspend fun setRankingAiEnabled(value: Boolean)

    suspend fun setAllSourcesFullImage(value: Boolean)
    suspend fun setCategoryFullImage(groupId: Long, value: Boolean)
}
