package co.chinho.readabilityreader.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import co.chinho.readabilityreader.ui.articles.ArticleListScreen
import co.chinho.readabilityreader.ui.components.SyncProgressTopBorder
import co.chinho.readabilityreader.ui.components.SyncStatus
import co.chinho.readabilityreader.ui.feeds.FeedListScreen
import co.chinho.readabilityreader.ui.reader.ArticleReaderScreen
import co.chinho.readabilityreader.ui.saved.SavedArticlesScreen
import co.chinho.readabilityreader.ui.settings.SettingsScreen
import co.chinho.readabilityreader.ui.setup.ServerSetupScreen
import co.chinho.readabilityreader.ui.theme.AppTheme

@Composable
fun AppNavigation(
    navController: NavHostController,
    syncStatus: SyncStatus,
    isDarkTheme: Boolean = false,
    isEInkMode: Boolean = false,
    isEInkDark: Boolean = false,
    startDestination: String = Screen.FeedList.route,
) {
    val isAnyEInk = isEInkMode || isEInkDark

    AppTheme(
        isDarkTheme = isDarkTheme,
        isEInkMode = isEInkMode,
        isEInkDark = isEInkDark,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SyncProgressTopBorder(syncStatus = syncStatus)

            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    if (isAnyEInk) fadeIn(animationSpec = snap())
                    else slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300),
                    )
                },
                exitTransition = {
                    if (isAnyEInk) fadeOut(animationSpec = snap())
                    else slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(300),
                    )
                },
                popEnterTransition = {
                    if (isAnyEInk) fadeIn(animationSpec = snap())
                    else slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300),
                    )
                },
                popExitTransition = {
                    if (isAnyEInk) fadeOut(animationSpec = snap())
                    else slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(300),
                    )
                },
            ) {
                composable(
                    route = Screen.FeedList.route,
                    exitTransition = {
                        if (targetState.destination.route == Screen.ArticleList.route) {
                            if (isAnyEInk) fadeOut(animationSpec = snap())
                            else fadeOut(animationSpec = tween(CROSSFADE_DURATION_MS))
                        } else null
                    },
                    popEnterTransition = {
                        if (initialState.destination.route == Screen.ArticleList.route) {
                            if (isAnyEInk) fadeIn(animationSpec = snap())
                            else fadeIn(animationSpec = tween(CROSSFADE_DURATION_MS))
                        } else null
                    },
                ) {
                    FeedListScreen(
                        onFeedClick = { feedId ->
                            if (feedId == null) {
                                navController.navigate(Screen.ArticleList.createRoute(0L))
                            } else {
                                navController.navigate(Screen.ArticleList.createRoute(feedId))
                            }
                        },
                        onCategoryClick = { groupId ->
                            navController.navigate(Screen.ArticleList.createRouteForGroup(groupId))
                        },
                        onSavedClick = {
                            navController.navigate(Screen.SavedArticles.route)
                        },
                        onSettingsClick = {
                            navController.navigate(Screen.Settings.route)
                        },
                    )
                }

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
                    enterTransition = {
                        if (initialState.destination.route == Screen.FeedList.route) {
                            if (isAnyEInk) fadeIn(animationSpec = snap())
                            else fadeIn(animationSpec = tween(CROSSFADE_DURATION_MS))
                        } else null
                    },
                    exitTransition = {
                        if (targetState.destination.route == Screen.ArticleReader.route) {
                            if (isAnyEInk) fadeOut(animationSpec = snap())
                            else fadeOut(animationSpec = tween(CROSSFADE_DURATION_MS))
                        } else null
                    },
                    popEnterTransition = {
                        if (initialState.destination.route == Screen.ArticleReader.route) {
                            if (isAnyEInk) fadeIn(animationSpec = snap())
                            else fadeIn(animationSpec = tween(CROSSFADE_DURATION_MS))
                        } else null
                    },
                    popExitTransition = {
                        if (targetState.destination.route == Screen.FeedList.route) {
                            if (isAnyEInk) fadeOut(animationSpec = snap())
                            else fadeOut(animationSpec = tween(CROSSFADE_DURATION_MS))
                        } else null
                    },
                ) { backStackEntry ->
                    val feedId =
                        backStackEntry.arguments?.getLong(Screen.ArticleList.NAV_ARG_FEED_ID) ?: 0L
                    ArticleListScreen(
                        onArticleClick = { articleId ->
                            navController.navigate(
                                Screen.ArticleReader.createRoute(
                                    articleId,
                                    feedId = feedId,
                                ),
                            )
                        },
                        onBackClick = { navController.popBackStack() },
                    )
                }

                composable(
                    route = Screen.ArticleReader.route,
                    arguments = listOf(
                        navArgument(Screen.ArticleReader.NAV_ARG_ARTICLE_ID) {
                            type = NavType.LongType
                        },
                        navArgument(Screen.ArticleReader.NAV_ARG_FEED_ID) {
                            type = NavType.LongType
                            defaultValue = 0L
                        },
                        navArgument(Screen.ArticleReader.NAV_ARG_IS_SAVED) {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                    enterTransition = {
                        if (initialState.destination.route == Screen.ArticleList.route) {
                            if (isAnyEInk) fadeIn(animationSpec = snap())
                            else fadeIn(animationSpec = tween(CROSSFADE_DURATION_MS))
                        } else null
                    },
                    popExitTransition = {
                        if (targetState.destination.route == Screen.ArticleList.route) {
                            if (isAnyEInk) fadeOut(animationSpec = snap())
                            else fadeOut(animationSpec = tween(CROSSFADE_DURATION_MS))
                        } else null
                    },
                ) {
                    ArticleReaderScreen(onBackClick = { navController.popBackStack() })
                }

                composable(Screen.SavedArticles.route) {
                    SavedArticlesScreen(onArticleClick = { articleId ->
                        navController.navigate(
                            Screen.ArticleReader.createRoute(
                                articleId,
                                isSaved = true,
                            ),
                        )
                    })
                }

                composable(Screen.Settings.route) {
                    SettingsScreen()
                }

                composable(Screen.ServerSetup.route) {
                    ServerSetupScreen(
                        onSetupComplete = {
                            navController.navigate(Screen.FeedList.route) {
                                popUpTo(Screen.ServerSetup.route) { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
    }
}

private const val CROSSFADE_DURATION_MS = 240
