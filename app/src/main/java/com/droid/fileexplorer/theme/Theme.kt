package com.droid.fileexplorer.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Win11BlueLight,
    onPrimary = Win11DarkBg,
    primaryContainer = Win11DarkItemSelected,
    secondary = Win11FolderYellow,
    background = Win11DarkBg,
    surface = Win11DarkSurface,
    surfaceVariant = Win11DarkSidebar,
    outline = Win11DarkBorder,
    onBackground = Win11DarkTextPrimary,
    onSurface = Win11DarkTextPrimary,
    onSurfaceVariant = Win11DarkTextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = Win11Blue,
    onPrimary = Win11LightSurface,
    primaryContainer = Win11LightItemSelected,
    secondary = Win11FolderDarkYellow,
    background = Win11LightBg,
    surface = Win11LightSurface,
    surfaceVariant = Win11LightSidebar,
    outline = Win11LightBorder,
    onBackground = Win11LightTextPrimary,
    onSurface = Win11LightTextPrimary,
    onSurfaceVariant = Win11LightTextSecondary
)

@Composable
fun FileExplorerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.surfaceVariant.toArgb()
                window.navigationBarColor = colorScheme.surfaceVariant.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
