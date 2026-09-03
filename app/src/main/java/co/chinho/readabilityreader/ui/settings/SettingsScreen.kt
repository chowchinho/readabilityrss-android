package co.chinho.readabilityreader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.chinho.readabilityreader.domain.model.ServerProfile
import co.chinho.readabilityreader.domain.model.CacheStats
import co.chinho.readabilityreader.domain.model.Group
import co.chinho.readabilityreader.ui.tablet.isTabletWidth
import co.chinho.readabilityreader.ui.theme.toComposeFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onCloseClick: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val syncIntervalHours by viewModel.syncIntervalHours.collectAsStateWithLifecycle()
    val syncOnAppOpen by viewModel.syncOnAppOpen.collectAsStateWithLifecycle()
    val wifiOnlySync by viewModel.wifiOnlySync.collectAsStateWithLifecycle()
    val chargingOnlySync by viewModel.chargingOnlySync.collectAsStateWithLifecycle()
    val autoMarkReadDelaySec by viewModel.autoMarkReadDelaySec.collectAsStateWithLifecycle()
    val cacheDurationDays by viewModel.cacheDurationDays.collectAsStateWithLifecycle()
    val maxCacheSizeMb by viewModel.maxCacheSizeMb.collectAsStateWithLifecycle()
    val prefetchCount by viewModel.prefetchCount.collectAsStateWithLifecycle()
    val cacheStats by viewModel.cacheStats.collectAsStateWithLifecycle()
    val articleListFontSizeSp by viewModel.articleListFontSizeSp.collectAsStateWithLifecycle()
    val articleReaderFontSizeSp by viewModel.articleReaderFontSizeSp.collectAsStateWithLifecycle()
    val articleListFontFamily by viewModel.articleListFontFamily.collectAsStateWithLifecycle()
    val articleReaderFontFamily by viewModel.articleReaderFontFamily.collectAsStateWithLifecycle()
    val showReadArticles by viewModel.showReadArticles.collectAsStateWithLifecycle()
    val hideEmptyFeedSources by viewModel.hideEmptyFeedSources.collectAsStateWithLifecycle()
    val dockedCategoryCount by viewModel.dockedCategoryCount.collectAsStateWithLifecycle()
    val hideSavedWhenEmpty by viewModel.hideSavedWhenEmpty.collectAsStateWithLifecycle()
    val externalLinkHandler by viewModel.externalLinkHandler.collectAsStateWithLifecycle()
    val feedCategories by viewModel.feedCategories.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val forceOffline by viewModel.forceOffline.collectAsStateWithLifecycle()
    val tabletMultiPaneLayout by viewModel.tabletMultiPaneLayout.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val jottyEnabled by viewModel.jottyEnabled.collectAsStateWithLifecycle()
    val jottyServerUrl by viewModel.jottyServerUrl.collectAsStateWithLifecycle()
    val jottyApiKey by viewModel.jottyApiKey.collectAsStateWithLifecycle()
    val jottyDefaultCategory by viewModel.jottyDefaultCategory.collectAsStateWithLifecycle()
    val showSavedArticlesSection by viewModel.showSavedArticlesSection.collectAsStateWithLifecycle()
    val articleOrder by viewModel.articleOrder.collectAsStateWithLifecycle()
    val rankingAiEnabled by viewModel.rankingAiEnabled.collectAsStateWithLifecycle()
    val debugSessionState by viewModel.debugSessionState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showClearCacheConfirmation by remember { mutableStateOf(false) }
    var showCategoryOrderDialog by remember { mutableStateOf(false) }
    var profileDialogState by remember { mutableStateOf<ProfileDialogState?>(null) }
    var pendingDeleteProfile by remember { mutableStateOf<ServerProfile?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                actions = {
                    if (onCloseClick != null) {
                        IconButton(onClick = onCloseClick) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close settings",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SettingsSection(title = "Sync Settings") {
                        SettingsDropdownRow("Sync interval", syncIntervalHours, syncIntervalOptions, viewModel::updateSyncInterval)
                        SettingsSwitchRow("Sync on app open", syncOnAppOpen, viewModel::updateSyncOnAppOpen)
                        SettingsSwitchRow("WiFi only sync", wifiOnlySync, viewModel::updateWifiOnlySync)
                        SettingsSwitchRow("Charging only sync", chargingOnlySync, viewModel::updateChargingOnlySync)
                        SettingsDropdownRow("Auto-mark as read delay", autoMarkReadDelaySec, autoMarkDelayOptions, viewModel::updateAutoMarkReadDelay)
                        BatteryOptimizationRow()
                    }
                }

                item {
                    SettingsSection(title = "Cache Settings") {
                        CacheUsageRow(stats = cacheStats, maxCacheSizeMb = maxCacheSizeMb)
                        SettingsDropdownRow("Cache duration", cacheDurationDays, cacheDurationOptions, viewModel::updateCacheDuration)
                        SettingsDropdownRow("Max cache size", maxCacheSizeMb, maxCacheSizeOptions, viewModel::updateMaxCacheSize)
                        SettingsSliderRow(
                            label = "Pre-fetch adjacent articles",
                            value = prefetchCount.toFloat(),
                            valueRange = 0f..5f,
                            steps = 4,
                            valueText = prefetchCount.toString(),
                            onValueChange = { viewModel.updatePrefetchCount(it.toInt()) },
                        )
                        Button(onClick = { showClearCacheConfirmation = true }) {
                            Text("Clear All Cached Data")
                        }
                    }
                }

                item {
                    SettingsSection(title = "Feed List") {
                        SettingsSwitchRow(
                            label = "Show Saved Articles section",
                            checked = showSavedArticlesSection,
                            onCheckedChange = viewModel::updateShowSavedArticlesSection,
                        )
                        SettingsSwitchRow(
                            label = "Hide empty feed sources",
                            checked = hideEmptyFeedSources,
                            onCheckedChange = viewModel::updateHideEmptyFeedSources,
                        )
                        SettingsSwitchRow(
                            label = "Hide Saved when empty",
                            checked = hideSavedWhenEmpty,
                            onCheckedChange = viewModel::updateHideSavedWhenEmpty,
                        )
                        SettingsDropdownRow(
                            "Docked categories",
                            dockedCategoryCount,
                            dockedCategoryCountOptions,
                            viewModel::updateDockedCategoryCount,
                        )
                        OutlinedButton(
                            onClick = { showCategoryOrderDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Edit Feed Category Order")
                        }
                    }
                }

                item {
                    SettingsSection(title = "Display Settings") {
                        SettingsSliderRow(
                            label = "Article list font size",
                            value = articleListFontSizeSp.toFloat(),
                            valueRange = 12f..24f,
                            steps = 11,
                            valueText = "${articleListFontSizeSp}sp",
                            onValueChange = { viewModel.updateArticleListFontSize(it.toInt()) },
                        )
                        PreviewText("Article list preview", articleListFontSizeSp, articleListFontFamily)
                        SettingsDropdownRow("Article list font family", articleListFontFamily, fontFamilyOptions, viewModel::updateArticleListFontFamily)
                        SettingsSliderRow(
                            label = "Article reader font size",
                            value = articleReaderFontSizeSp.toFloat(),
                            valueRange = 14f..32f,
                            steps = 17,
                            valueText = "${articleReaderFontSizeSp}sp",
                            onValueChange = { viewModel.updateArticleReaderFontSize(it.toInt()) },
                        )
                        PreviewText("Reader preview text", articleReaderFontSizeSp, articleReaderFontFamily)
                        SettingsDropdownRow("Article reader font family", articleReaderFontFamily, fontFamilyOptions, viewModel::updateArticleReaderFontFamily)
                        SettingsSwitchRow("Show read articles", showReadArticles, viewModel::updateShowReadArticles)
                        if (isTabletWidth()) {
                            SettingsSwitchRow(
                                label = "Tablet multi-pane layout",
                                checked = tabletMultiPaneLayout,
                                onCheckedChange = viewModel::updateTabletMultiPaneLayout,
                            )
                        }
                        SettingsDropdownRow("External links", externalLinkHandler, externalLinkOptions, viewModel::updateExternalLinkHandler)
                        if (rankingAiEnabled) {
                            SettingsDropdownRow("Article order", articleOrder, articleOrderOptions, viewModel::updateArticleOrder)
                        } else {
                            Column {
                                SettingsDropdownRow("Article order", "latest", articleOrderOptions) { }
                                Text(
                                    text = "Personalised ranking is disabled on the server (ai_enabled: false)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    SettingsSection(title = "Appearance") {
                        SettingsRadioGroup("Theme", themeMode, themeOptions, viewModel::updateTheme)
                    }
                }

                item {
                    SettingsSection(
                        title = "Server Profiles",
                        action = {
                            OutlinedButton(onClick = { profileDialogState = ProfileDialogState() }) {
                                Text("Add Profile")
                            }
                        }
                    ) {
                        if (profiles.isEmpty()) {
                            Text(
                                text = "No server profiles configured yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                profiles.forEach { profile ->
                                    ProfileCard(
                                        profile = profile,
                                        onActivate = { viewModel.setActiveProfile(profile.id) },
                                        onEdit = {
                                            profileDialogState = ProfileDialogState(
                                                id = profile.id,
                                                name = profile.name,
                                                serverUrl = profile.serverUrl,
                                                username = profile.username,
                                            )
                                        },
                                        onDelete = { pendingDeleteProfile = profile },
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SettingsSection(title = "Mode") {
                        SettingsSwitchRow("Force offline", forceOffline, viewModel::updateForceOffline)
                    }
                }

                item {
                    JottySettingsSection(
                        enabled = jottyEnabled,
                        serverUrl = jottyServerUrl,
                        apiKey = jottyApiKey,
                        defaultCategory = jottyDefaultCategory,
                        onEnabledChange = viewModel::updateJottyEnabled,
                        onServerUrlChange = viewModel::updateJottyServerUrl,
                        onApiKeyChange = viewModel::updateJottyApiKey,
                        onDefaultCategoryChange = viewModel::updateJottyDefaultCategory,
                        onTestConnection = viewModel::testJottyConnection,
                    )
                }

                item {
                    SettingsSection(title = "Diagnostics") {
                        DebugSessionRow(
                            state = debugSessionState,
                            onStart = viewModel::startDebugSession,
                            onStop = viewModel::stopDebugSession,
                            onShareLast = {
                                viewModel.lastDebugSessionFile()?.let { file ->
                                    shareDebugSessionFile(context, file)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showClearCacheConfirmation) {
        ConfirmDialog(
            title = "Clear cached data?",
            text = "This removes synced feeds and cached articles from local storage.",
            confirmLabel = "Clear",
            onConfirm = {
                showClearCacheConfirmation = false
                viewModel.clearCache()
            },
            onDismiss = { showClearCacheConfirmation = false },
        )
    }

    profileDialogState?.let { dialogState ->
        ProfileEditorDialog(
            state = dialogState,
            onDismiss = { profileDialogState = null },
            onSave = { id, name, serverUrl, username, password ->
                viewModel.saveProfile(id, name, serverUrl, username, password)
                profileDialogState = null
            },
        )
    }

    pendingDeleteProfile?.let { profile ->
        ConfirmDialog(
            title = "Delete profile?",
            text = "Delete ${profile.name} and remove its saved password?",
            confirmLabel = "Delete",
            onConfirm = {
                pendingDeleteProfile = null
                viewModel.deleteProfile(profile.id)
            },
            onDismiss = { pendingDeleteProfile = null },
        )
    }

    if (showCategoryOrderDialog) {
        FeedCategoryOrderDialog(
            categories = feedCategories,
            onDismiss = { showCategoryOrderDialog = false },
            onSave = { orderedIds, isDefaultOrder ->
                if (isDefaultOrder) {
                    viewModel.resetFeedCategoryOrder()
                } else {
                    viewModel.saveFeedCategoryOrder(orderedIds)
                }
                showCategoryOrderDialog = false
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                action?.invoke()
            }
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun CacheUsageRow(
    stats: CacheStats?,
    maxCacheSizeMb: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Current usage", style = MaterialTheme.typography.bodyLarge)
        if (stats == null) {
            Text(
                text = "Calculating cache usage…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val unlimited = maxCacheSizeMb <= 0
            val usedText = formatBytes(stats.totalUsedBytes)
            val sizeLine = if (unlimited) {
                "$usedText used · No size limit"
            } else {
                val maxText = formatBytes(stats.imageCacheMaxBytes)
                val percent = if (stats.imageCacheMaxBytes > 0) {
                    (stats.totalUsedBytes.toFloat() / stats.imageCacheMaxBytes).coerceIn(0f, 1f)
                } else {
                    0f
                }
                "$usedText / $maxText  (${(percent * 100).toInt()}%)"
            }

            Text(
                text = sizeLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (!unlimited && stats.imageCacheMaxBytes > 0) {
                val fraction = (stats.totalUsedBytes.toFloat() / stats.imageCacheMaxBytes)
                    .coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = "${stats.articleCount} articles (${formatBytes(stats.databaseBytes)}) · " +
                    "${stats.imageCount} images (${formatBytes(stats.imageCacheUsedBytes)})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BatteryOptimizationRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isIgnoring by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoring = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Background sync reliability",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = if (isIgnoring) {
                    "Battery optimization disabled — periodic sync runs reliably."
                } else {
                    "Periodic sync may be deferred for hours by Doze. Tap to disable battery optimization."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = {
                val intent = if (isIgnoring) {
                    android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                } else {
                    @android.annotation.SuppressLint("BatteryLife")
                    val request = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    request.data = android.net.Uri.parse("package:${context.packageName}")
                    request
                }
                runCatching { context.startActivity(intent) }
            }
        ) {
            Text(if (isIgnoring) "Manage" else "Allow")
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun DebugSessionRow(
    state: co.chinho.readabilityreader.util.DebugSessionRecorder.State,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShareLast: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Network debug capture",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = if (state.isActive) {
                "Recording image loads, connectivity changes, and uncaught network errors. Auto-stops after 5 minutes."
            } else {
                "Capture 5 minutes of image-load and connectivity activity to a shareable log file. Use before/during a Tube ride to diagnose offline issues."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isActive) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                ) { Text("Stop now") }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                ) { Text("Start 5-min capture") }
            }
            OutlinedButton(
                onClick = onShareLast,
                enabled = state.lastSession != null,
                modifier = Modifier.weight(1f),
            ) { Text("Share last log") }
        }
        state.lastSession?.let { file ->
            Text(
                text = "Last log: ${file.name} (${file.length() / 1024} KB)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun shareDebugSessionFile(context: android.content.Context, file: java.io.File) {
    val authority = "${context.packageName}.fileprovider"
    val uri = runCatching {
        androidx.core.content.FileProvider.getUriForFile(context, authority, file)
    }.getOrNull() ?: return
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = android.content.Intent.createChooser(intent, "Share debug log").apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun <T> SettingsDropdownRow(
    label: String,
    selectedValue: T,
    options: List<SettingOption<T>>,
    onValueSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: selectedValue.toString()

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selectedLabel)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onValueSelected(option.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsRadioGroup(
    label: String,
    selectedValue: String,
    options: List<SettingOption<String>>,
    onValueSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        options.forEach { option ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedValue == option.value,
                    onClick = { onValueSelected(option.value) },
                )
                Text(option.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PreviewText(
    text: String,
    fontSizeSp: Int,
    fontFamily: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            fontSize = fontSizeSp.sp,
            fontFamily = fontFamily.toComposeFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileCard(
    profile: ServerProfile,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                if (profile.isActive) {
                    AssistChip(onClick = { }, label = { Text("Active") })
                }
            }
            Text(profile.serverUrl, style = MaterialTheme.typography.bodyMedium)
            Text(profile.username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!profile.isActive) {
                    OutlinedButton(onClick = onActivate) {
                        Text("Set Active")
                    }
                }
                OutlinedButton(onClick = onEdit) {
                    Text("Edit")
                }
                OutlinedButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun FeedCategoryOrderDialog(
    categories: List<Group>,
    onDismiss: () -> Unit,
    onSave: (List<Long>, Boolean) -> Unit,
) {
    val defaultCategories = remember(categories) {
        categories.sortedWith(
            compareBy<Group> { it.id == -1L }
                .thenBy { it.title.lowercase() }
        )
    }
    var orderedCategories by remember(categories) {
        mutableStateOf(categories.map { FeedCategoryOrderItem(id = it.id, title = it.title) })
    }
    var draggedItemId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val dragThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Feed Category Order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Press and hold a category, then drag to rearrange the feed list order.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        itemsIndexed(
                            items = orderedCategories,
                            key = { _, item -> item.id },
                        ) { _, item ->
                            val isDragging = draggedItemId == item.id

                            Surface(
                                tonalElevation = if (isDragging) 8.dp else 0.dp,
                                shape = MaterialTheme.shapes.medium,
                                color = if (isDragging) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        translationY = if (isDragging) dragOffsetY else 0f
                                    }
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .pointerInput(item.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedItemId = item.id
                                                dragOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                draggedItemId = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggedItemId = null
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                if (draggedItemId != item.id) return@detectDragGesturesAfterLongPress

                                                change.consume()
                                                dragOffsetY += dragAmount.y

                                                var currentIndex = orderedCategories.indexOfFirst { it.id == item.id }
                                                if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                                                while (dragOffsetY > dragThresholdPx && currentIndex < orderedCategories.lastIndex) {
                                                    orderedCategories = orderedCategories.toMutableList().apply {
                                                        add(currentIndex + 1, removeAt(currentIndex))
                                                    }
                                                    dragOffsetY -= dragThresholdPx
                                                    currentIndex += 1
                                                }

                                                while (dragOffsetY < -dragThresholdPx && currentIndex > 0) {
                                                    orderedCategories = orderedCategories.toMutableList().apply {
                                                        add(currentIndex - 1, removeAt(currentIndex))
                                                    }
                                                    dragOffsetY += dragThresholdPx
                                                    currentIndex -= 1
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isDragging) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.DragHandle,
                                        contentDescription = "Drag handle",
                                        tint = if (isDragging) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val resetItems = defaultCategories.map {
                            FeedCategoryOrderItem(id = it.id, title = it.title)
                        }
                        orderedCategories = resetItems
                        draggedItemId = null
                        dragOffsetY = 0f
                    }
                ) {
                    Text("Reset")
                }
                TextButton(
                    onClick = {
                        val orderedIds = orderedCategories.map { it.id }
                        val defaultIds = defaultCategories.map { it.id }
                        onSave(orderedIds, orderedIds == defaultIds)
                    }
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ProfileEditorDialog(
    state: ProfileDialogState,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String, String, String) -> Unit,
) {
    var name by remember(state) { mutableStateOf(state.name) }
    var serverUrl by remember(state) { mutableStateOf(state.serverUrl) }
    var username by remember(state) { mutableStateOf(state.username) }
    var password by remember(state) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.id == null) "Add Profile" else "Edit Profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (state.id == null) "Password" else "Password (leave blank to keep)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(state.id, name, serverUrl, username, password) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun JottySettingsSection(
    enabled: Boolean,
    serverUrl: String,
    apiKey: String,
    defaultCategory: String,
    onEnabledChange: (Boolean) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onDefaultCategoryChange: (String) -> Unit,
    onTestConnection: () -> Unit,
) {
    var serverUrlField by remember(serverUrl) { mutableStateOf(serverUrl) }
    var apiKeyField by remember(apiKey) { mutableStateOf(apiKey) }
    var categoryField by remember(defaultCategory) { mutableStateOf(defaultCategory) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    SettingsSection(title = "Jotty Integration") {
        SettingsSwitchRow(
            label = "Enable Jotty integration",
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
        Text(
            text = "When enabled, saving an article also POSTs it to Jotty as a new note in the chosen category.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = serverUrlField,
            onValueChange = { serverUrlField = it },
            label = { Text("Server URL (e.g. https://jotty.example.com)") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKeyField,
            onValueChange = { apiKeyField = it },
            label = { Text("API key (ck_…)") },
            singleLine = true,
            enabled = enabled,
            visualTransformation = if (apiKeyVisible) {
                androidx.compose.ui.text.input.VisualTransformation.None
            } else {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            },
            trailingIcon = {
                TextButton(onClick = { apiKeyVisible = !apiKeyVisible }, enabled = enabled) {
                    Text(if (apiKeyVisible) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = categoryField,
            onValueChange = { categoryField = it },
            label = { Text("Default category") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    onServerUrlChange(serverUrlField)
                    onApiKeyChange(apiKeyField)
                    onDefaultCategoryChange(categoryField)
                },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text("Save")
            }
            OutlinedButton(
                onClick = {
                    onServerUrlChange(serverUrlField)
                    onApiKeyChange(apiKeyField)
                    onDefaultCategoryChange(categoryField)
                    onTestConnection()
                },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text("Test connection")
            }
        }
    }
}

private data class SettingOption<T>(
    val value: T,
    val label: String,
)

private data class FeedCategoryOrderItem(
    val id: Long,
    val title: String,
)

private data class ProfileDialogState(
    val id: Long? = null,
    val name: String = "",
    val serverUrl: String = "",
    val username: String = "",
)

private val syncIntervalOptions = listOf(
    SettingOption(1, "1 hour"),
    SettingOption(3, "3 hours"),
    SettingOption(6, "6 hours"),
    SettingOption(24, "Daily"),
)

private val autoMarkDelayOptions = listOf(
    SettingOption(2, "2 seconds"),
    SettingOption(5, "5 seconds"),
    SettingOption(10, "10 seconds"),
    SettingOption(0, "Disabled"),
)

private val cacheDurationOptions = listOf(
    SettingOption(1, "1 day"),
    SettingOption(3, "3 days"),
    SettingOption(5, "5 days"),
    SettingOption(7, "7 days"),
    SettingOption(14, "14 days"),
    SettingOption(30, "30 days"),
)

private val maxCacheSizeOptions = listOf(
    SettingOption(250, "250 MB"),
    SettingOption(500, "500 MB"),
    SettingOption(1024, "1 GB"),
    SettingOption(2048, "2 GB"),
    SettingOption(0, "Unlimited"),
)

private val dockedCategoryCountOptions = listOf(
    SettingOption(0, "Off"),
    SettingOption(1, "1"),
    SettingOption(2, "2"),
    SettingOption(3, "3"),
    SettingOption(4, "4"),
    SettingOption(5, "5"),
    SettingOption(6, "6"),
)

private val fontFamilyOptions = listOf(
    SettingOption("system", "System"),
    SettingOption("serif", "Serif"),
    SettingOption("sans-serif", "Sans Serif"),
    SettingOption("monospace", "Monospace"),
)

private val externalLinkOptions = listOf(
    SettingOption("system_browser", "System browser"),
    SettingOption("custom_tabs", "Chrome Custom Tabs"),
)

private val articleOrderOptions = listOf(
    SettingOption("personalised", "Personalised"),
    SettingOption("latest", "Latest"),
)

private val themeOptions = listOf(
    SettingOption("system", "System"),
    SettingOption("light", "Light"),
    SettingOption("dark", "Dark"),
    SettingOption("eink_light", "E-Ink Light"),
    SettingOption("eink_dark", "E-Ink Dark"),
)
