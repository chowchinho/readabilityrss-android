package co.chinho.readabilityreader.ui.tablet

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import co.chinho.readabilityreader.ui.articles.ArticleListScreen
import co.chinho.readabilityreader.ui.components.SyncProgressTopBorder
import co.chinho.readabilityreader.ui.components.SyncStatus
import co.chinho.readabilityreader.ui.components.SyncStatusBottomBar
import co.chinho.readabilityreader.ui.feeds.FeedListScreen
import co.chinho.readabilityreader.ui.navigation.Screen
import co.chinho.readabilityreader.ui.reader.ArticleReaderScreen
import co.chinho.readabilityreader.ui.saved.SavedArticlesScreen
import co.chinho.readabilityreader.ui.settings.SettingsScreen

@Composable
fun TabletShell(
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier,
    viewModel: TabletShellViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val articleListNavController = rememberNavController()
    val readerNavController = rememberNavController()
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isPortrait) {
        viewModel.setPortrait(isPortrait)
    }

    BackHandler {
        when {
            state.savedVisible -> viewModel.hideSaved()
            state.settingsVisible -> viewModel.hideSettings()
            state.selectedArticleId != null && state.feedPaneExpanded -> viewModel.setFeedPaneExpanded(false)
            state.selectedArticleId != null && !state.feedPaneExpanded -> viewModel.setFeedPaneExpanded(true)
            else -> showExitDialog = true
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SyncProgressTopBorder(syncStatus = syncStatus)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val density = LocalDensity.current
            val screenWidth = maxWidth
            
            val minArticleWidth = maxOf(screenWidth * TabletDimens.ArticleListMinFraction, TabletDimens.ArticleListMinDp)
            val maxArticleWidth = minOf(screenWidth * TabletDimens.ArticleListMaxFraction, screenWidth - TabletDimens.ReaderMinDp).coerceAtLeast(minArticleWidth)
            val articleListWidth = (screenWidth * state.articleListWidthFraction).coerceIn(minArticleWidth, maxArticleWidth)

            val isLayout1 = state.selectedArticleId == null
            Row(modifier = Modifier.fillMaxSize()) {
                if (isLayout1) {
                    Box(
                        modifier = Modifier
                            .weight(0.33f)
                            .fillMaxHeight(),
                    ) {
                        FeedListScreen(
                            selectedFeedId = state.selectedFeedId,
                            selectedGroupId = state.selectedGroupId,
                            showSyncStatusBottomBar = true,
                            onFeedPaneToggleClick = null,
                            onCategoryClick = { groupId ->
                                viewModel.selectGroup(groupId)
                                articleListNavController.navigate(
                                    Screen.ArticleList.createRouteForGroup(groupId)
                                ) {
                                    popUpTo(articleListNavController.graph.startDestinationId) {
                                        inclusive = true
                                    }
                                }
                            },
                            onFeedClick = { feedId ->
                                val routeFeedId = feedId ?: 0L
                                viewModel.selectFeed(routeFeedId)
                                articleListNavController.navigate(Screen.ArticleList.createRoute(routeFeedId)) {
                                    popUpTo(articleListNavController.graph.startDestinationId) {
                                        inclusive = true
                                    }
                                }
                            },
                            onSavedClick = viewModel::showSaved,
                            onSettingsClick = viewModel::showSettings,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(0.67f)
                            .fillMaxHeight(),
                    ) {
                        ArticleListPane(
                            navController = articleListNavController,
                            selectedArticleId = state.selectedArticleId,
                            startRoute = state.selectedGroupId?.let {
                                Screen.ArticleList.createRouteForGroup(it)
                            } ?: Screen.ArticleList.createRoute(state.selectedFeedId ?: 0L),
                            onFeedPaneToggleClick = null,
                            onArticleClick = { articleId ->
                                viewModel.selectArticle(articleId)
                                readerNavController.navigate(
                                    Screen.ArticleReader.createRoute(
                                        articleId = articleId,
                                        feedId = state.selectedFeedId ?: 0L,
                                    ),
                                ) {
                                    popUpTo(EMPTY_READER_ROUTE) { inclusive = false }
                                }
                            },
                        )

                        if (state.savedVisible) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
                                SavedArticlesScreen(
                                    onArticleClick = { articleId ->
                                        viewModel.selectArticle(articleId)
                                        readerNavController.navigate(
                                            Screen.ArticleReader.createRoute(articleId, state.selectedFeedId ?: 0L)
                                        ) {
                                            popUpTo(EMPTY_READER_ROUTE) { inclusive = false }
                                        }
                                        viewModel.hideSaved()
                                    },
                                    onCloseClick = viewModel::hideSaved,
                                )
                            }
                        }

                        if (state.settingsVisible) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
                                SettingsScreen(onCloseClick = viewModel::hideSettings)
                            }
                        }
                    }

                    Box(modifier = Modifier.width(0.dp)) {
                        ReaderPane(
                            navController = readerNavController,
                            selectedFeedId = state.selectedFeedId ?: 0L,
                            onArticleChanged = viewModel::selectArticle,
                        )
                    }
                } else {
                    val effectivePaneWidth = articleListWidth.coerceIn(minArticleWidth, maxArticleWidth)
                    Box(
                        modifier = Modifier
                            .width(effectivePaneWidth)
                            .fillMaxHeight(),
                    ) {
                        CompositionLocalProvider(LocalTabletPaneWidth provides effectivePaneWidth) {
                            ArticleListPane(
                                navController = articleListNavController,
                                selectedArticleId = state.selectedArticleId,
                                startRoute = state.selectedGroupId?.let {
                                    Screen.ArticleList.createRouteForGroup(it)
                                } ?: Screen.ArticleList.createRoute(state.selectedFeedId ?: 0L),
                                onFeedPaneToggleClick = if (state.feedPaneExpanded) {
                                    { viewModel.setFeedPaneExpanded(false) }
                                } else {
                                    { viewModel.setFeedPaneExpanded(true) }
                                },
                                onArticleClick = { articleId ->
                                    viewModel.selectArticle(articleId)
                                    readerNavController.navigate(
                                        Screen.ArticleReader.createRoute(
                                            articleId = articleId,
                                            feedId = state.selectedFeedId ?: 0L,
                                        ),
                                    ) {
                                        popUpTo(EMPTY_READER_ROUTE) { inclusive = false }
                                    }
                                },
                            )
                        }

                        if (state.feedPaneExpanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                                FeedListScreen(
                                    selectedFeedId = state.selectedFeedId,
                                    selectedGroupId = state.selectedGroupId,
                                    showSyncStatusBottomBar = true,
                                    onFeedPaneToggleClick = { viewModel.setFeedPaneExpanded(false) },
                                    onCategoryClick = { groupId ->
                                        coroutineScope.launch {
                                            val firstId = viewModel.selectGroupAndGetFirstArticle(groupId)
                                            articleListNavController.navigate(
                                                Screen.ArticleList.createRouteForGroup(groupId)
                                            ) {
                                                popUpTo(articleListNavController.graph.startDestinationId) {
                                                    inclusive = true
                                                }
                                            }
                                            if (firstId != null) {
                                                readerNavController.navigate(
                                                    Screen.ArticleReader.createRoute(firstId, 0L)
                                                ) {
                                                    popUpTo(EMPTY_READER_ROUTE) { inclusive = false }
                                                }
                                            } else {
                                                readerNavController.navigate(EMPTY_READER_ROUTE) {
                                                    popUpTo(readerNavController.graph.startDestinationId) {
                                                        inclusive = true
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onFeedClick = { feedId ->
                                        val routeFeedId = feedId ?: 0L
                                        coroutineScope.launch {
                                            val firstId = viewModel.selectFeedAndGetFirstArticle(routeFeedId)
                                            articleListNavController.navigate(Screen.ArticleList.createRoute(routeFeedId)) {
                                                popUpTo(articleListNavController.graph.startDestinationId) {
                                                    inclusive = true
                                                }
                                            }
                                            if (firstId != null) {
                                                readerNavController.navigate(Screen.ArticleReader.createRoute(firstId, routeFeedId)) {
                                                    popUpTo(EMPTY_READER_ROUTE) { inclusive = false }
                                                }
                                            } else {
                                                readerNavController.navigate(EMPTY_READER_ROUTE) {
                                                    popUpTo(readerNavController.graph.startDestinationId) {
                                                        inclusive = true
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onSavedClick = viewModel::showSaved,
                                    onSettingsClick = viewModel::showSettings,
                                )
                            }
                        }
                    }

                    TabletPaneDivider(
                        onDrag = { dragPx ->
                            val deltaDp = with(density) { dragPx.toDp() }
                            val nextWidth = (articleListWidth + deltaDp).coerceIn(minArticleWidth, maxArticleWidth)
                            viewModel.setArticleListWidthFraction((nextWidth / screenWidth).coerceIn(0f, 1f), persist = false)
                        },
                        onDragFinished = {
                            viewModel.persistCurrentArticleListWidth()
                        },
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        ReaderPane(
                            navController = readerNavController,
                            selectedFeedId = state.selectedFeedId ?: 0L,
                            onArticleChanged = viewModel::selectArticle,
                        )
                        if (state.savedVisible) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
                                SavedArticlesScreen(
                                    onArticleClick = { articleId ->
                                        viewModel.selectArticle(articleId)
                                        readerNavController.navigate(
                                            Screen.ArticleReader.createRoute(articleId, state.selectedFeedId ?: 0L)
                                        ) {
                                            popUpTo(EMPTY_READER_ROUTE) { inclusive = false }
                                        }
                                        viewModel.hideSaved()
                                    },
                                    onCloseClick = viewModel::hideSaved,
                                )
                            }
                        }
                        if (state.settingsVisible) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
                                SettingsScreen(onCloseClick = viewModel::hideSettings)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit app?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finish()
                    },
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ArticleListPane(
    navController: NavHostController,
    selectedArticleId: Long?,
    startRoute: String,
    onFeedPaneToggleClick: (() -> Unit)?,
    onArticleClick: (Long) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(
            route = Screen.ArticleList.route,
            arguments = listOf(
                navArgument(Screen.ArticleList.NAV_ARG_FEED_ID) {
                    type = NavType.LongType
                },
                navArgument(Screen.ArticleList.NAV_ARG_GROUP_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) {
            ArticleListScreen(
                selectedArticleId = selectedArticleId,
                onFeedPaneToggleClick = onFeedPaneToggleClick,
                onArticleClick = onArticleClick,
                onBackClick = {},
            )
        }
    }
}

@Composable
private fun ReaderPane(
    navController: NavHostController,
    selectedFeedId: Long,
    onArticleChanged: (Long) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = EMPTY_READER_ROUTE,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(EMPTY_READER_ROUTE) {
            EmptyReaderPlaceholder()
        }
        composable(
            route = Screen.ArticleReader.route,
            arguments = listOf(
                navArgument(Screen.ArticleReader.NAV_ARG_ARTICLE_ID) {
                    type = NavType.LongType
                },
                navArgument(Screen.ArticleReader.NAV_ARG_FEED_ID) {
                    type = NavType.LongType
                    defaultValue = selectedFeedId
                },
                navArgument(Screen.ArticleReader.NAV_ARG_IS_SAVED) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) {
            ArticleReaderScreen(
                showBackButton = false,
                onBackClick = {},
                onArticleChanged = onArticleChanged,
            )
        }
    }
}

private const val EMPTY_READER_ROUTE = "empty"
