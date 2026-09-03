package co.chinho.readabilityreader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import co.chinho.readabilityreader.domain.repository.UserPreferencesRepository
import co.chinho.readabilityreader.domain.usecase.IsServerConfiguredUseCase
import co.chinho.readabilityreader.ui.components.SyncStatus
import co.chinho.readabilityreader.ui.navigation.AppNavigation
import co.chinho.readabilityreader.ui.navigation.Screen
import co.chinho.readabilityreader.ui.tablet.TABLET_SHELL_ROUTE
import co.chinho.readabilityreader.ui.tablet.TabletAppNavigation
import co.chinho.readabilityreader.ui.tablet.TabletDimens
import co.chinho.readabilityreader.worker.SyncScheduler
import co.chinho.readabilityreader.worker.SyncStatusObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import co.chinho.readabilityreader.data.local.PreFlightMigrationHelper
import co.chinho.readabilityreader.data.local.PreFlightState
import co.chinho.readabilityreader.data.local.FlushResult
import co.chinho.readabilityreader.ui.theme.AppTheme
import androidx.compose.material3.Text

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var isServerConfiguredUseCase: IsServerConfiguredUseCase

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var syncStatusObserver: SyncStatusObserver

    @Inject
    lateinit var preFlightHelper: PreFlightMigrationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val configuration = LocalConfiguration.current
            val isTabletWidth = configuration.screenWidthDp.dp >= TabletDimens.TabletBreakpointDp &&
                windowSizeClass.widthSizeClass != androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact
            val tabletMultiPaneLayout by userPreferencesRepository.tabletMultiPaneLayout
                .collectAsStateWithLifecycle(initialValue = true)
            val useMultiPaneLayout = isTabletWidth && tabletMultiPaneLayout
            var startDestination by remember { mutableStateOf<String?>(null) }
            val themeMode by userPreferencesRepository.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val systemDarkTheme = isSystemInDarkTheme()
            val isEInkMode = themeMode == "eink_light"
            val isEInkDark = themeMode == "eink_dark"
            val isDarkTheme = when (themeMode) {
                "dark" -> true
                "light", "eink_light", "eink_dark" -> false
                else -> systemDarkTheme
            }
            val syncStatus by syncStatusObserver.syncStatus.collectAsStateWithLifecycle(
                initialValue = SyncStatus(),
            )

            var preFlightState by remember { mutableStateOf<PreFlightState>(PreFlightState.Checking) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                preFlightState = preFlightHelper.checkUpgradePending()
            }

            LaunchedEffect(preFlightState) {
                if (preFlightState == PreFlightState.Acknowledged) {
                    val syncIntervalHours = userPreferencesRepository.syncIntervalHours.first()
                    val wifiOnlySync = userPreferencesRepository.wifiOnlySync.first()
                    val chargingOnlySync = userPreferencesRepository.chargingOnlySync.first()
                    val syncOnAppOpen = userPreferencesRepository.syncOnAppOpen.first()

                    syncScheduler.schedulePeriodicSync(
                        intervalHours = syncIntervalHours,
                        wifiOnly = wifiOnlySync,
                        chargingOnly = chargingOnlySync,
                    )

                    val launchedFromSyncShortcut = intent?.action == ACTION_SYNC_NOW
                    if (syncOnAppOpen || launchedFromSyncShortcut) {
                        syncScheduler.triggerOneTimeSync()
                    }

                    startDestination = if (isServerConfiguredUseCase()) {
                        Screen.FeedList.route
                    } else {
                        Screen.ServerSetup.route
                    }
                }
            }

            if (preFlightState != PreFlightState.Acknowledged) {
                AppTheme(
                    isDarkTheme = isDarkTheme,
                    isEInkMode = isEInkMode,
                    isEInkDark = isEInkDark,
                ) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            when (val state = preFlightState) {
                                is PreFlightState.Checking -> {
                                    androidx.compose.material3.CircularProgressIndicator()
                                }
                                is PreFlightState.Flushing -> {
                                    Column(
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Syncing pending changes...",
                                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                                is PreFlightState.UpgradePending, is PreFlightState.FlushFailed -> {
                                    val profile = (state as? PreFlightState.UpgradePending)?.profile
                                        ?: (state as PreFlightState.FlushFailed).profile
                                    val actions = (state as? PreFlightState.UpgradePending)?.actions
                                        ?: (state as PreFlightState.FlushFailed).actions
                                    val errorMessage = (state as? PreFlightState.FlushFailed)?.message

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Database upgrade pending",
                                            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "The app's storage format is being updated. We tried to sync ${actions.size} pending changes (reads / saves) to the server but couldn't reach it.",
                                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        if (errorMessage != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Error: $errorMessage",
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(24.dp))
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                preFlightState = PreFlightState.Flushing
                                                scope.launch {
                                                    val result = preFlightHelper.flushQueue(profile, actions)
                                                    when (result) {
                                                        is FlushResult.Success -> {
                                                            preFlightHelper.acknowledgeMigration()
                                                            preFlightState = PreFlightState.Acknowledged
                                                            recreate()
                                                        }
                                                        is FlushResult.Partial -> {
                                                            val checkResult = preFlightHelper.checkUpgradePending()
                                                            if (checkResult is PreFlightState.UpgradePending) {
                                                                preFlightState = PreFlightState.FlushFailed(
                                                                    message = "Sync partially succeeded. ${result.remaining} remaining.",
                                                                    profile = checkResult.profile,
                                                                    actions = checkResult.actions
                                                                )
                                                            } else {
                                                                preFlightState = checkResult
                                                            }
                                                        }
                                                        is FlushResult.Failure -> {
                                                            preFlightState = PreFlightState.FlushFailed(
                                                                message = result.error.message ?: "Network error",
                                                                profile = profile,
                                                                actions = actions
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Connect and retry")
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = {
                                                preFlightHelper.acknowledgeMigration()
                                                preFlightState = PreFlightState.Acknowledged
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Continue anyway")
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            } else if (startDestination != null) {
                Scaffold { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        if (useMultiPaneLayout) {
                            TabletAppNavigation(
                                navController = rememberNavController(),
                                syncStatus = syncStatus,
                                isDarkTheme = isDarkTheme,
                                isEInkMode = isEInkMode,
                                isEInkDark = isEInkDark,
                                startDestination = if (startDestination == Screen.ServerSetup.route) {
                                    Screen.ServerSetup.route
                                } else {
                                    TABLET_SHELL_ROUTE
                                },
                            )
                        } else {
                            AppNavigation(
                                navController = rememberNavController(),
                                syncStatus = syncStatus,
                                isDarkTheme = isDarkTheme,
                                isEInkMode = isEInkMode,
                                isEInkDark = isEInkDark,
                                startDestination = startDestination!!,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_SYNC_NOW) {
            syncScheduler.triggerOneTimeSync()
        }
    }

    companion object {
        const val ACTION_SYNC_NOW = "co.chinho.readabilityreader.action.SYNC_NOW"
    }
}
