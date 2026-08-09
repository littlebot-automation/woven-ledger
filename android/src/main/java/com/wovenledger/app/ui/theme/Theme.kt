package com.wovenledger.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = WovenNavy,
    onPrimary = WovenSurface,
    primaryContainer = WovenNavyTint,
    onPrimaryContainer = WovenNavyDark,
    secondary = WovenAmber,
    onSecondary = WovenSurface,
    secondaryContainer = WovenAmberTint,
    onSecondaryContainer = WovenNavyDark,
    tertiary = WovenSuccess,
    onTertiary = WovenSurface,
    tertiaryContainer = WovenSuccessTint,
    onTertiaryContainer = WovenNavyDark,
    error = WovenDanger,
    onError = WovenSurface,
    errorContainer = WovenDangerTint,
    onErrorContainer = WovenNavyDark,
    background = WovenPaper,
    onBackground = WovenInk,
    surface = WovenSurface,
    onSurface = WovenInk,
    surfaceVariant = WovenBorderSoft,
    onSurfaceVariant = WovenInkSoft,
    outline = WovenBorder,
    outlineVariant = WovenBorderSoft,
)

private val DarkColorScheme = darkColorScheme(
    primary = WovenNavyLight,
    onPrimary = WovenNavyDark,
    primaryContainer = WovenNavy,
    onPrimaryContainer = WovenNavyTint,
    secondary = WovenAmberLight,
    onSecondary = WovenNavyDark,
    secondaryContainer = WovenAmber,
    onSecondaryContainer = WovenAmberTint,
    tertiary = WovenSuccess,
    onTertiary = WovenNavyDark,
    error = WovenDanger,
    onError = WovenSurface,
    background = WovenDarkBackground,
    onBackground = WovenDarkInk,
    surface = WovenDarkSurface,
    onSurface = WovenDarkInk,
    surfaceVariant = WovenDarkSurface,
    onSurfaceVariant = WovenDarkInkSoft,
    outline = WovenDarkInkSoft,
)

/**
 * Dynamic colour is deliberately OFF by default. This app is the mobile face of a
 * branded portal; letting the device wallpaper repaint it would break that tie, and
 * it is why the app previously rendered in an unrelated palette.
 */
@Composable
fun WovenLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = WovenNavyDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
