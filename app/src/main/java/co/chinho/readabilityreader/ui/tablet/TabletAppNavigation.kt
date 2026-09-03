package co.chinho.readabilityreader.ui.tablet

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import co.chinho.readabilityreader.ui.components.SyncStatus
import co.chinho.readabilityreader.ui.navigation.Screen
import co.chinho.readabilityreader.ui.setup.ServerSetupScreen
import co.chinho.readabilityreader.ui.theme.AppTheme

@Composable
fun TabletAppNavigation(
    navController: NavHostController,
    syncStatus: SyncStatus,
    isDarkTheme: Boolean = false,
    isEInkMode: Boolean = false,
    isEInkDark: Boolean = false,
    startDestination: String = TABLET_SHELL_ROUTE,
    modifier: Modifier = Modifier,
) {
    AppTheme(
        isDarkTheme = isDarkTheme,
        isEInkMode = isEInkMode,
        isEInkDark = isEInkDark,
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier,
        ) {
            composable(TABLET_SHELL_ROUTE) {
                TabletShell(syncStatus = syncStatus)
            }
            composable(Screen.ServerSetup.route) {
                ServerSetupScreen(
                    onSetupComplete = {
                        navController.navigate(TABLET_SHELL_ROUTE) {
                            popUpTo(Screen.ServerSetup.route) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

const val TABLET_SHELL_ROUTE = "tablet_shell"
