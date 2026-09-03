package co.chinho.readabilityreader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesRepository {

    override val syncIntervalHours: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.SYNC_INTERVAL_HOURS] ?: DEFAULT_SYNC_INTERVAL_HOURS }

    override val syncOnAppOpen: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.SYNC_ON_APP_OPEN] ?: DEFAULT_SYNC_ON_APP_OPEN }

    override val wifiOnlySync: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.WIFI_ONLY_SYNC] ?: DEFAULT_WIFI_ONLY_SYNC }

    override val chargingOnlySync: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.CHARGING_ONLY_SYNC] ?: DEFAULT_CHARGING_ONLY_SYNC }

    override val autoMarkReadDelaySec: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.AUTO_MARK_READ_DELAY_SEC] ?: DEFAULT_AUTO_MARK_READ_DELAY_SEC }

    override val cacheDurationDays: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.CACHE_DURATION_DAYS] ?: DEFAULT_CACHE_DURATION_DAYS }

    override val maxCacheSizeMb: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.MAX_CACHE_SIZE_MB] ?: DEFAULT_MAX_CACHE_SIZE_MB }

    override val prefetchCount: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.PREFETCH_COUNT] ?: DEFAULT_PREFETCH_COUNT }

    override val dockedCategoryCount: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.DOCKED_CATEGORY_COUNT] ?: DEFAULT_DOCKED_CATEGORY_COUNT }

    override val votePillFractionX: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.VOTE_PILL_FRACTION_X] ?: DEFAULT_VOTE_PILL_FRACTION }

    override val votePillFractionY: Flow<Float> =
        dataStore.data.map { preferences -> preferences[Keys.VOTE_PILL_FRACTION_Y] ?: DEFAULT_VOTE_PILL_FRACTION }

    override val articleListFontSizeSp: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.ARTICLE_LIST_FONT_SIZE_SP] ?: DEFAULT_ARTICLE_LIST_FONT_SIZE_SP }

    override val articleReaderFontSizeSp: Flow<Int> =
        dataStore.data.map { preferences -> preferences[Keys.ARTICLE_READER_FONT_SIZE_SP] ?: DEFAULT_ARTICLE_READER_FONT_SIZE_SP }

    override val articleListFontFamily: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.ARTICLE_LIST_FONT_FAMILY] ?: DEFAULT_ARTICLE_LIST_FONT_FAMILY }

    override val articleReaderFontFamily: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.ARTICLE_READER_FONT_FAMILY] ?: DEFAULT_ARTICLE_READER_FONT_FAMILY }

    override val showReadArticles: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.SHOW_READ_ARTICLES] ?: DEFAULT_SHOW_READ_ARTICLES }

    override val hideEmptyFeedSources: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.HIDE_EMPTY_FEED_SOURCES] ?: DEFAULT_HIDE_EMPTY_FEED_SOURCES }

    override val hideSavedWhenEmpty: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.HIDE_SAVED_WHEN_EMPTY] ?: DEFAULT_HIDE_SAVED_WHEN_EMPTY }

    override val externalLinkHandler: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.EXTERNAL_LINK_HANDLER] ?: DEFAULT_EXTERNAL_LINK_HANDLER }

    override val feedCategoryOrder: Flow<List<Long>> =
        dataStore.data.map { preferences ->
            preferences[Keys.FEED_CATEGORY_ORDER]
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                .orEmpty()
        }

    override val tabletArticleListWidthLandscape: Flow<Float> =
        dataStore.data.map { preferences ->
            preferences[Keys.TABLET_ARTICLE_LIST_WIDTH_LANDSCAPE] ?: DEFAULT_TABLET_ARTICLE_LIST_WIDTH
        }

    override val tabletArticleListWidthPortrait: Flow<Float> =
        dataStore.data.map { preferences ->
            preferences[Keys.TABLET_ARTICLE_LIST_WIDTH_PORTRAIT] ?: DEFAULT_TABLET_ARTICLE_LIST_WIDTH
        }

    override val tabletFeedPaneCollapsedLandscape: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[Keys.TABLET_FEED_PANE_COLLAPSED_LANDSCAPE] ?: DEFAULT_TABLET_FEED_PANE_COLLAPSED_LANDSCAPE
        }

    override val tabletFeedPaneCollapsedPortrait: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[Keys.TABLET_FEED_PANE_COLLAPSED_PORTRAIT] ?: DEFAULT_TABLET_FEED_PANE_COLLAPSED_PORTRAIT
        }

    override val themeMode: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.THEME_MODE] ?: DEFAULT_THEME_MODE }

    override val forceOffline: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.FORCE_OFFLINE] ?: DEFAULT_FORCE_OFFLINE }

    override val serverFocalResetDone: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.SERVER_FOCAL_RESET_DONE] ?: false }

    override val tabletMultiPaneLayout: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.TABLET_MULTI_PANE_LAYOUT] ?: DEFAULT_TABLET_MULTI_PANE_LAYOUT }

    override val jottyEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.JOTTY_ENABLED] ?: DEFAULT_JOTTY_ENABLED }

    override val jottyServerUrl: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.JOTTY_SERVER_URL] ?: DEFAULT_JOTTY_SERVER_URL }

    override val jottyDefaultCategory: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.JOTTY_DEFAULT_CATEGORY] ?: DEFAULT_JOTTY_DEFAULT_CATEGORY }

    override val showSavedArticlesSection: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.SHOW_SAVED_ARTICLES_SECTION] ?: DEFAULT_SHOW_SAVED_ARTICLES_SECTION }

    override val articleOrder: Flow<String> =
        dataStore.data.map { preferences -> preferences[Keys.ARTICLE_ORDER] ?: DEFAULT_ARTICLE_ORDER }

    override val rankingAiEnabled: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.RANKING_AI_ENABLED] ?: DEFAULT_RANKING_AI_ENABLED }

    override val allSourcesFullImage: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[Keys.ALL_SOURCES_FULL_IMAGE] ?: false }

    override val fullImageCategoryIds: Flow<Set<Long>> =
        dataStore.data.map { preferences ->
            preferences[Keys.FULL_IMAGE_CATEGORY_IDS]
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet()
                .orEmpty()
        }

    override suspend fun setSyncIntervalHours(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.SYNC_INTERVAL_HOURS] = value }
    }

    override suspend fun setSyncOnAppOpen(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.SYNC_ON_APP_OPEN] = value }
    }

    override suspend fun setWifiOnlySync(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.WIFI_ONLY_SYNC] = value }
    }

    override suspend fun setChargingOnlySync(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.CHARGING_ONLY_SYNC] = value }
    }

    override suspend fun setAutoMarkReadDelaySec(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.AUTO_MARK_READ_DELAY_SEC] = value }
    }

    override suspend fun setCacheDurationDays(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.CACHE_DURATION_DAYS] = value }
    }

    override suspend fun setMaxCacheSizeMb(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.MAX_CACHE_SIZE_MB] = value }
    }

    override suspend fun setPrefetchCount(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.PREFETCH_COUNT] = value }
    }

    override suspend fun setDockedCategoryCount(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.DOCKED_CATEGORY_COUNT] = value }
    }

    override suspend fun setVotePillFraction(x: Float, y: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.VOTE_PILL_FRACTION_X] = x
            preferences[Keys.VOTE_PILL_FRACTION_Y] = y
        }
    }

    override suspend fun setArticleListFontSizeSp(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.ARTICLE_LIST_FONT_SIZE_SP] = value }
    }

    override suspend fun setArticleReaderFontSizeSp(value: Int) {
        dataStore.edit { preferences -> preferences[Keys.ARTICLE_READER_FONT_SIZE_SP] = value }
    }

    override suspend fun setArticleListFontFamily(value: String) {
        dataStore.edit { preferences -> preferences[Keys.ARTICLE_LIST_FONT_FAMILY] = value }
    }

    override suspend fun setArticleReaderFontFamily(value: String) {
        dataStore.edit { preferences -> preferences[Keys.ARTICLE_READER_FONT_FAMILY] = value }
    }

    override suspend fun setShowReadArticles(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.SHOW_READ_ARTICLES] = value }
    }

    override suspend fun setHideEmptyFeedSources(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.HIDE_EMPTY_FEED_SOURCES] = value }
    }

    override suspend fun setHideSavedWhenEmpty(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.HIDE_SAVED_WHEN_EMPTY] = value }
    }

    override suspend fun setExternalLinkHandler(value: String) {
        dataStore.edit { preferences -> preferences[Keys.EXTERNAL_LINK_HANDLER] = value }
    }

    override suspend fun setFeedCategoryOrder(value: List<Long>) {
        dataStore.edit { preferences ->
            preferences[Keys.FEED_CATEGORY_ORDER] = value.joinToString(separator = ",")
        }
    }

    override suspend fun resetFeedCategoryOrder() {
        dataStore.edit { preferences -> preferences.remove(Keys.FEED_CATEGORY_ORDER) }
    }

    override suspend fun setTabletArticleListWidthLandscape(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.TABLET_ARTICLE_LIST_WIDTH_LANDSCAPE] = value }
    }

    override suspend fun setTabletArticleListWidthPortrait(value: Float) {
        dataStore.edit { preferences -> preferences[Keys.TABLET_ARTICLE_LIST_WIDTH_PORTRAIT] = value }
    }

    override suspend fun setTabletFeedPaneCollapsedLandscape(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.TABLET_FEED_PANE_COLLAPSED_LANDSCAPE] = value }
    }

    override suspend fun setTabletFeedPaneCollapsedPortrait(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.TABLET_FEED_PANE_COLLAPSED_PORTRAIT] = value }
    }

    override suspend fun setThemeMode(value: String) {
        dataStore.edit { preferences -> preferences[Keys.THEME_MODE] = value }
    }

    override suspend fun setForceOffline(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.FORCE_OFFLINE] = value }
    }

    override suspend fun setServerFocalResetDone(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.SERVER_FOCAL_RESET_DONE] = value }
    }

    override suspend fun setTabletMultiPaneLayout(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.TABLET_MULTI_PANE_LAYOUT] = value }
    }

    override suspend fun setJottyEnabled(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.JOTTY_ENABLED] = value }
    }

    override suspend fun setJottyServerUrl(value: String) {
        dataStore.edit { preferences -> preferences[Keys.JOTTY_SERVER_URL] = value }
    }

    override suspend fun setJottyDefaultCategory(value: String) {
        dataStore.edit { preferences -> preferences[Keys.JOTTY_DEFAULT_CATEGORY] = value }
    }

    override suspend fun setShowSavedArticlesSection(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.SHOW_SAVED_ARTICLES_SECTION] = value }
    }

    override suspend fun setArticleOrder(value: String) {
        dataStore.edit { preferences -> preferences[Keys.ARTICLE_ORDER] = value }
    }

    override suspend fun setRankingAiEnabled(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.RANKING_AI_ENABLED] = value }
    }

    override suspend fun setAllSourcesFullImage(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.ALL_SOURCES_FULL_IMAGE] = value }
    }

    override suspend fun setCategoryFullImage(groupId: Long, value: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.FULL_IMAGE_CATEGORY_IDS]
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?.toMutableSet()
                ?: mutableSetOf()
            if (value) current.add(groupId) else current.remove(groupId)
            preferences[Keys.FULL_IMAGE_CATEGORY_IDS] = current.joinToString(separator = ",")
        }
    }

    private object Keys {
        val SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
        val SYNC_ON_APP_OPEN = booleanPreferencesKey("sync_on_app_open")
        val WIFI_ONLY_SYNC = booleanPreferencesKey("wifi_only_sync")
        val CHARGING_ONLY_SYNC = booleanPreferencesKey("charging_only_sync")
        val AUTO_MARK_READ_DELAY_SEC = intPreferencesKey("auto_mark_read_delay_sec")
        val CACHE_DURATION_DAYS = intPreferencesKey("cache_duration_days")
        val MAX_CACHE_SIZE_MB = intPreferencesKey("max_cache_size_mb")
        val PREFETCH_COUNT = intPreferencesKey("prefetch_count")
        val ARTICLE_LIST_FONT_SIZE_SP = intPreferencesKey("article_list_font_size_sp")
        val ARTICLE_READER_FONT_SIZE_SP = intPreferencesKey("article_reader_font_size_sp")
        val ARTICLE_LIST_FONT_FAMILY = stringPreferencesKey("article_list_font_family")
        val ARTICLE_READER_FONT_FAMILY = stringPreferencesKey("article_reader_font_family")
        val SHOW_READ_ARTICLES = booleanPreferencesKey("show_read_articles")
        val HIDE_EMPTY_FEED_SOURCES = booleanPreferencesKey("hide_empty_feed_sources")
        val HIDE_SAVED_WHEN_EMPTY = booleanPreferencesKey("hide_saved_when_empty")
        val EXTERNAL_LINK_HANDLER = stringPreferencesKey("external_link_handler")
        val FEED_CATEGORY_ORDER = stringPreferencesKey("feed_category_order")
        val TABLET_ARTICLE_LIST_WIDTH_LANDSCAPE = floatPreferencesKey("tablet_article_list_width_landscape")
        val TABLET_ARTICLE_LIST_WIDTH_PORTRAIT = floatPreferencesKey("tablet_article_list_width_portrait")
        val TABLET_FEED_PANE_COLLAPSED_LANDSCAPE = booleanPreferencesKey("tablet_feed_pane_collapsed_landscape")
        val TABLET_FEED_PANE_COLLAPSED_PORTRAIT = booleanPreferencesKey("tablet_feed_pane_collapsed_portrait")
        val DOCKED_CATEGORY_COUNT = intPreferencesKey("docked_category_count")
        val VOTE_PILL_FRACTION_X = floatPreferencesKey("vote_pill_fraction_x")
        val VOTE_PILL_FRACTION_Y = floatPreferencesKey("vote_pill_fraction_y")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FORCE_OFFLINE = booleanPreferencesKey("force_offline")
        val SERVER_FOCAL_RESET_DONE = booleanPreferencesKey("server_focal_reset_done")
        val TABLET_MULTI_PANE_LAYOUT = booleanPreferencesKey("tablet_multi_pane_layout")
        val JOTTY_ENABLED = booleanPreferencesKey("jotty_enabled")
        val JOTTY_SERVER_URL = stringPreferencesKey("jotty_server_url")
        val JOTTY_DEFAULT_CATEGORY = stringPreferencesKey("jotty_default_category")
        val SHOW_SAVED_ARTICLES_SECTION = booleanPreferencesKey("show_saved_articles_section")
        val ARTICLE_ORDER = stringPreferencesKey("article_order")
        val RANKING_AI_ENABLED = booleanPreferencesKey("ranking_ai_enabled")
        val ALL_SOURCES_FULL_IMAGE = booleanPreferencesKey("all_sources_full_image")
        val FULL_IMAGE_CATEGORY_IDS = stringPreferencesKey("full_image_category_ids")
    }

    private companion object {
        const val DEFAULT_SYNC_INTERVAL_HOURS = 1
        const val DEFAULT_SYNC_ON_APP_OPEN = true
        const val DEFAULT_WIFI_ONLY_SYNC = true
        const val DEFAULT_CHARGING_ONLY_SYNC = false
        const val DEFAULT_AUTO_MARK_READ_DELAY_SEC = 2
        const val DEFAULT_CACHE_DURATION_DAYS = 7
        const val DEFAULT_MAX_CACHE_SIZE_MB = 500
        const val DEFAULT_PREFETCH_COUNT = 2
        const val DEFAULT_ARTICLE_LIST_FONT_SIZE_SP = 16
        const val DEFAULT_ARTICLE_READER_FONT_SIZE_SP = 18
        const val DEFAULT_ARTICLE_LIST_FONT_FAMILY = "system"
        const val DEFAULT_ARTICLE_READER_FONT_FAMILY = "serif"
        const val DEFAULT_SHOW_READ_ARTICLES = true
        const val DEFAULT_HIDE_EMPTY_FEED_SOURCES = false
        const val DEFAULT_HIDE_SAVED_WHEN_EMPTY = false
        const val DEFAULT_EXTERNAL_LINK_HANDLER = "system_browser"
        const val DEFAULT_TABLET_ARTICLE_LIST_WIDTH = 0.33f
        const val DEFAULT_TABLET_FEED_PANE_COLLAPSED_LANDSCAPE = false
        const val DEFAULT_TABLET_FEED_PANE_COLLAPSED_PORTRAIT = true
        // Bottom-right, matching where the pill sat before it became draggable.
        const val DEFAULT_VOTE_PILL_FRACTION = 1f
        // Keep in step with MaxDockedPerEnd, the UI's own fallback; data must not import ui.
        const val DEFAULT_DOCKED_CATEGORY_COUNT = 3
        const val DEFAULT_THEME_MODE = "system"
        const val DEFAULT_FORCE_OFFLINE = false
        const val DEFAULT_TABLET_MULTI_PANE_LAYOUT = true
        const val DEFAULT_JOTTY_ENABLED = false
        const val DEFAULT_JOTTY_SERVER_URL = ""
        const val DEFAULT_JOTTY_DEFAULT_CATEGORY = "ReadabilityAndroid"
        const val DEFAULT_SHOW_SAVED_ARTICLES_SECTION = true
        const val DEFAULT_ARTICLE_ORDER = "personalised"
        const val DEFAULT_RANKING_AI_ENABLED = true
    }
}
