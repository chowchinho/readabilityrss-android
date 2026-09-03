package co.chinho.readabilityreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import co.chinho.readabilityreader.domain.model.ServerProfile
import co.chinho.readabilityreader.domain.repository.CredentialRepository
import co.chinho.readabilityreader.domain.repository.ServerProfileRepository
import co.chinho.readabilityreader.domain.repository.JottyRepository
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.domain.model.CacheStats
import co.chinho.readabilityreader.domain.usecase.ClearAllCacheUseCase
import co.chinho.readabilityreader.domain.usecase.GetCacheStatsUseCase
import co.chinho.readabilityreader.domain.usecase.GetGroupsUseCase
import co.chinho.readabilityreader.domain.usecase.TestJottyConnectionUseCase
import co.chinho.readabilityreader.util.DebugSessionRecorder
import co.chinho.readabilityreader.worker.SyncScheduler
import co.chinho.readabilityreader.ui.feeds.MaxDockedPerEnd
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val clearAllCacheUseCase: ClearAllCacheUseCase,
    private val getCacheStatsUseCase: GetCacheStatsUseCase,
    private val getGroupsUseCase: GetGroupsUseCase,
    private val serverProfileRepository: ServerProfileRepository,
    private val credentialRepository: CredentialRepository,
    private val syncScheduler: SyncScheduler,
    private val workManager: WorkManager,
    private val debugSessionRecorder: DebugSessionRecorder,
    private val testJottyConnectionUseCase: TestJottyConnectionUseCase,
) : ViewModel() {

    val debugSessionState: StateFlow<DebugSessionRecorder.State> =
        debugSessionRecorder.state.stateInViewModel(DebugSessionRecorder.State())

    val syncIntervalHours: StateFlow<Int> = userPreferencesRepository.syncIntervalHours.stateInViewModel(1)
    val syncOnAppOpen: StateFlow<Boolean> = userPreferencesRepository.syncOnAppOpen.stateInViewModel(true)
    val wifiOnlySync: StateFlow<Boolean> = userPreferencesRepository.wifiOnlySync.stateInViewModel(true)
    val chargingOnlySync: StateFlow<Boolean> = userPreferencesRepository.chargingOnlySync.stateInViewModel(false)
    val autoMarkReadDelaySec: StateFlow<Int> = userPreferencesRepository.autoMarkReadDelaySec.stateInViewModel(2)

    val cacheDurationDays: StateFlow<Int> = userPreferencesRepository.cacheDurationDays.stateInViewModel(7)
    val maxCacheSizeMb: StateFlow<Int> = userPreferencesRepository.maxCacheSizeMb.stateInViewModel(500)
    val prefetchCount: StateFlow<Int> = userPreferencesRepository.prefetchCount.stateInViewModel(2)
    val cacheStats: StateFlow<CacheStats?> = getCacheStatsUseCase().stateInViewModel(null)

    val articleListFontSizeSp: StateFlow<Int> = userPreferencesRepository.articleListFontSizeSp.stateInViewModel(16)
    val articleReaderFontSizeSp: StateFlow<Int> = userPreferencesRepository.articleReaderFontSizeSp.stateInViewModel(18)
    val articleListFontFamily: StateFlow<String> = userPreferencesRepository.articleListFontFamily.stateInViewModel("system")
    val articleReaderFontFamily: StateFlow<String> = userPreferencesRepository.articleReaderFontFamily.stateInViewModel("serif")
    val showReadArticles: StateFlow<Boolean> = userPreferencesRepository.showReadArticles.stateInViewModel(true)
    val hideEmptyFeedSources: StateFlow<Boolean> = userPreferencesRepository.hideEmptyFeedSources.stateInViewModel(false)
    val hideSavedWhenEmpty: StateFlow<Boolean> = userPreferencesRepository.hideSavedWhenEmpty.stateInViewModel(false)
    val externalLinkHandler: StateFlow<String> = userPreferencesRepository.externalLinkHandler.stateInViewModel("system_browser")
    val articleOrder: StateFlow<String> = userPreferencesRepository.articleOrder.stateInViewModel("personalised")
    val rankingAiEnabled: StateFlow<Boolean> = userPreferencesRepository.rankingAiEnabled.stateInViewModel(true)
    val feedCategories = getGroupsUseCase().stateInViewModel(emptyList())

    val themeMode: StateFlow<String> = userPreferencesRepository.themeMode.stateInViewModel("system")
    val forceOffline: StateFlow<Boolean> = userPreferencesRepository.forceOffline.stateInViewModel(false)
    val tabletMultiPaneLayout: StateFlow<Boolean> = userPreferencesRepository.tabletMultiPaneLayout.stateInViewModel(true)
    val profiles: StateFlow<List<ServerProfile>> = serverProfileRepository.observeProfiles().stateInViewModel(emptyList())

    val jottyEnabled: StateFlow<Boolean> = userPreferencesRepository.jottyEnabled.stateInViewModel(false)
    val jottyServerUrl: StateFlow<String> = userPreferencesRepository.jottyServerUrl.stateInViewModel("")
    val jottyDefaultCategory: StateFlow<String> = userPreferencesRepository.jottyDefaultCategory.stateInViewModel("ReadabilityAndroid")
    val showSavedArticlesSection: StateFlow<Boolean> = userPreferencesRepository.showSavedArticlesSection.stateInViewModel(true)

    val dockedCategoryCount: StateFlow<Int> = userPreferencesRepository.dockedCategoryCount.stateInViewModel(MaxDockedPerEnd)

    private val _jottyApiKey = kotlinx.coroutines.flow.MutableStateFlow("")
    val jottyApiKey: StateFlow<String> = _jottyApiKey

    init {
        viewModelScope.launch {
            _jottyApiKey.value = credentialRepository.getJottyApiKey().orEmpty()
        }
    }

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    fun updateSyncInterval(value: Int) {
        writePreference {
            userPreferencesRepository.setSyncIntervalHours(value)
            syncScheduler.schedulePeriodicSync(
                intervalHours = value,
                wifiOnly = wifiOnlySync.value,
                chargingOnly = chargingOnlySync.value,
            )
        }
    }

    fun updateSyncOnAppOpen(value: Boolean) = writePreference { userPreferencesRepository.setSyncOnAppOpen(value) }

    fun updateWifiOnlySync(value: Boolean) {
        writePreference {
            userPreferencesRepository.setWifiOnlySync(value)
            syncScheduler.schedulePeriodicSync(
                intervalHours = syncIntervalHours.value,
                wifiOnly = value,
                chargingOnly = chargingOnlySync.value,
            )
        }
    }

    fun updateChargingOnlySync(value: Boolean) {
        writePreference {
            userPreferencesRepository.setChargingOnlySync(value)
            syncScheduler.schedulePeriodicSync(
                intervalHours = syncIntervalHours.value,
                wifiOnly = wifiOnlySync.value,
                chargingOnly = value,
            )
        }
    }

    fun updateAutoMarkReadDelay(value: Int) = writePreference { userPreferencesRepository.setAutoMarkReadDelaySec(value) }

    fun updateCacheDuration(value: Int) = writePreference { userPreferencesRepository.setCacheDurationDays(value) }

    fun updateMaxCacheSize(value: Int) = writePreference { userPreferencesRepository.setMaxCacheSizeMb(value) }

    fun updatePrefetchCount(value: Int) = writePreference { userPreferencesRepository.setPrefetchCount(value) }

    fun updateArticleListFontSize(value: Int) = writePreference { userPreferencesRepository.setArticleListFontSizeSp(value) }

    fun updateArticleReaderFontSize(value: Int) = writePreference { userPreferencesRepository.setArticleReaderFontSizeSp(value) }

    fun updateArticleListFontFamily(value: String) = writePreference { userPreferencesRepository.setArticleListFontFamily(value) }

    fun updateArticleReaderFontFamily(value: String) = writePreference { userPreferencesRepository.setArticleReaderFontFamily(value) }

    fun updateShowReadArticles(value: Boolean) = writePreference { userPreferencesRepository.setShowReadArticles(value) }

    fun updateHideEmptyFeedSources(value: Boolean) = writePreference { userPreferencesRepository.setHideEmptyFeedSources(value) }

    fun updateHideSavedWhenEmpty(value: Boolean) = writePreference { userPreferencesRepository.setHideSavedWhenEmpty(value) }

    fun updateDockedCategoryCount(value: Int) = writePreference { userPreferencesRepository.setDockedCategoryCount(value) }

    fun updateExternalLinkHandler(value: String) = writePreference { userPreferencesRepository.setExternalLinkHandler(value) }

    fun updateArticleOrder(value: String) = writePreference { userPreferencesRepository.setArticleOrder(value) }

    fun saveFeedCategoryOrder(categoryIds: List<Long>) = writePreference {
        userPreferencesRepository.setFeedCategoryOrder(categoryIds)
        _messages.emit("Feed category order saved")
    }

    fun resetFeedCategoryOrder() = writePreference {
        userPreferencesRepository.resetFeedCategoryOrder()
        _messages.emit("Feed category order reset")
    }

    fun updateTheme(value: String) = writePreference { userPreferencesRepository.setThemeMode(value) }

    fun updateForceOffline(value: Boolean) = writePreference { userPreferencesRepository.setForceOffline(value) }


    fun updateTabletMultiPaneLayout(value: Boolean) = writePreference { userPreferencesRepository.setTabletMultiPaneLayout(value) }

    fun updateJottyEnabled(value: Boolean) = writePreference { userPreferencesRepository.setJottyEnabled(value) }

    fun updateJottyServerUrl(value: String) = writePreference { userPreferencesRepository.setJottyServerUrl(value.trim()) }

    fun updateJottyDefaultCategory(value: String) = writePreference { userPreferencesRepository.setJottyDefaultCategory(value.trim()) }

    fun updateJottyApiKey(value: String) {
        viewModelScope.launch {
            runCatching {
                if (value.isBlank()) {
                    credentialRepository.deleteJottyApiKey()
                } else {
                    credentialRepository.saveJottyApiKey(value.trim())
                }
                _jottyApiKey.value = value.trim()
            }.onFailure { _messages.emit(it.message ?: "Failed to save API key") }
        }
    }

    fun updateShowSavedArticlesSection(value: Boolean) = writePreference {
        userPreferencesRepository.setShowSavedArticlesSection(value)
    }

    fun testJottyConnection() {
        viewModelScope.launch {
            val result = testJottyConnectionUseCase(
                serverUrl = jottyServerUrl.value,
                apiKey = jottyApiKey.value,
            )
            val message = when (result) {
                is JottyRepository.TestResult.Ok -> "Jotty connection OK"
                is JottyRepository.TestResult.Failed -> "Jotty test failed: ${result.message}"
            }
            _messages.emit(message)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            runCatching {
                clearAllCacheUseCase()
                workManager.pruneWork()
            }
                .onSuccess { _messages.emit("Cached data cleared") }
                .onFailure { _messages.emit(it.message ?: "Failed to clear cache") }
        }
    }

    fun saveProfile(
        profileId: Long?,
        name: String,
        serverUrl: String,
        username: String,
        password: String,
    ) {
        viewModelScope.launch {
            val trimmedName = name.trim()
            val trimmedServerUrl = serverUrl.trim()
            val trimmedUsername = username.trim()

            if (trimmedName.isBlank() || trimmedServerUrl.isBlank() || trimmedUsername.isBlank()) {
                _messages.emit("Name, server URL, and username are required")
                return@launch
            }

            val existingProfile = profileId?.let { serverProfileRepository.getProfile(it) }
            val resolvedPassword = when {
                password.isNotBlank() -> password
                existingProfile != null -> credentialRepository.getPassword(existingProfile.id)
                else -> null
            }

            if (resolvedPassword.isNullOrBlank()) {
                _messages.emit("Password is required")
                return@launch
            }

            runCatching {
                val savedId = serverProfileRepository.saveProfile(
                    ServerProfile(
                        id = existingProfile?.id ?: 0L,
                        name = trimmedName,
                        serverUrl = trimmedServerUrl,
                        username = trimmedUsername,
                        isActive = existingProfile?.isActive ?: profiles.value.isEmpty(),
                    )
                )
                val targetId = existingProfile?.id ?: savedId
                credentialRepository.savePassword(targetId, resolvedPassword)
                if (existingProfile?.isActive != false) {
                    serverProfileRepository.setActiveProfile(targetId)
                }
            }.onSuccess {
                _messages.emit(if (profileId == null) "Profile added" else "Profile updated")
            }.onFailure {
                _messages.emit(it.message ?: "Failed to save profile")
            }
        }
    }

    fun setActiveProfile(profileId: Long) {
        viewModelScope.launch {
            runCatching { serverProfileRepository.setActiveProfile(profileId) }
                .onFailure { _messages.emit(it.message ?: "Failed to activate profile") }
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            runCatching {
                val wasActive = serverProfileRepository.getActiveProfile()?.id == profileId
                serverProfileRepository.deleteProfile(profileId)
                credentialRepository.deletePassword(profileId)
                if (wasActive) {
                    serverProfileRepository.getFirstProfile()?.let { serverProfileRepository.setActiveProfile(it.id) }
                }
            }.onSuccess {
                _messages.emit("Profile deleted")
            }.onFailure {
                _messages.emit(it.message ?: "Failed to delete profile")
            }
        }
    }

    fun startDebugSession() {
        debugSessionRecorder.start()
        viewModelScope.launch { _messages.emit("Debug session started (5 min)") }
    }

    fun stopDebugSession() {
        debugSessionRecorder.stop()
        viewModelScope.launch { _messages.emit("Debug session stopped") }
    }

    fun lastDebugSessionFile(): File? = debugSessionRecorder.state.value.lastSession

    private fun writePreference(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onFailure { _messages.emit(it.message ?: "Failed to update setting") }
        }
    }

    private fun <T> Flow<T>.stateInViewModel(initialValue: T): StateFlow<T> {
        return stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initialValue,
        )
    }
}
