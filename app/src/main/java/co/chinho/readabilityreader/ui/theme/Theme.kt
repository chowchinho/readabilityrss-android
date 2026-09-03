package co.chinho.readabilityreader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = SlateLightPrimary,
    onPrimary = SlateLightOnPrimary,
    primaryContainer = SlateLightPrimaryContainer,
    onPrimaryContainer = SlateLightOnPrimaryContainer,
    secondary = SlateLightSecondary,
    onSecondary = SlateLightOnSecondary,
    secondaryContainer = SlateLightSecondaryContainer,
    onSecondaryContainer = SlateLightOnSecondaryContainer,
    background = SlateLightBackground,
    onBackground = SlateLightOnBackground,
    surface = SlateLightSurface,
    onSurface = SlateLightOnSurface,
    surfaceVariant = SlateLightSurfaceVariant,
    onSurfaceVariant = SlateLightOnSurfaceVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = SlateDarkPrimary,
    onPrimary = SlateDarkOnPrimary,
    primaryContainer = SlateDarkPrimaryContainer,
    onPrimaryContainer = SlateDarkOnPrimaryContainer,
    secondary = SlateDarkSecondary,
    onSecondary = SlateDarkOnSecondary,
    secondaryContainer = SlateDarkSecondaryContainer,
    onSecondaryContainer = SlateDarkOnSecondaryContainer,
    background = SlateDarkBackground,
    onBackground = SlateDarkOnBackground,
    surface = SlateDarkSurface,
    onSurface = SlateDarkOnSurface,
    surfaceVariant = SlateDarkSurfaceVariant,
    onSurfaceVariant = SlateDarkOnSurfaceVariant
)

// E-Ink Light: Ultra High Contrast, Pure B&W, light background
private val EInkLightColorScheme = lightColorScheme(
    primary = EInkBlack,
    onPrimary = EInkWhite,
    primaryContainer = EInkWhite,
    onPrimaryContainer = EInkBlack,
    secondary = EInkBlack,
    onSecondary = EInkWhite,
    secondaryContainer = EInkWhite,
    onSecondaryContainer = EInkBlack,
    background = EInkWhite,
    onBackground = EInkBlack,
    surface = EInkWhite,
    onSurface = EInkBlack,
    surfaceVariant = EInkLightGray,
    onSurfaceVariant = EInkBlack,
    error = EInkBlack,
    onError = EInkWhite
)

// E-Ink Dark: Ultra High Contrast, Pure B&W, dark background
private val EInkDarkColorScheme = darkColorScheme(
    primary = EInkDarkOnBackground,
    onPrimary = EInkDarkBackground,
    primaryContainer = EInkDarkSurfaceVariant,
    onPrimaryContainer = EInkDarkOnBackground,
    secondary = EInkDarkOnBackground,
    onSecondary = EInkDarkBackground,
    secondaryContainer = EInkDarkSurfaceVariant,
    onSecondaryContainer = EInkDarkOnBackground,
    background = EInkDarkBackground,
    onBackground = EInkDarkOnBackground,
    surface = EInkDarkSurface,
    onSurface = EInkDarkOnSurface,
    surfaceVariant = EInkDarkSurfaceVariant,
    onSurfaceVariant = EInkDarkOnSurfaceVariant,
    error = EInkDarkOnBackground,
    onError = EInkDarkBackground
)

val LocalEInkMode = staticCompositionLocalOf { false }

@Composable
fun AppTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    isEInkMode: Boolean = false,
    isEInkDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isEInkDark -> EInkDarkColorScheme
        isEInkMode -> EInkLightColorScheme
        isDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val isLightStatusBar = !isDarkTheme && !isEInkMode && !isEInkDark

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightStatusBar
        }
    }

    CompositionLocalProvider(LocalEInkMode provides (isEInkMode || isEInkDark)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
