package com.mobiletocursor.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Premium dark color palette ──
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6C63FF),        // Vibrant purple
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D35A0),
    secondary = Color(0xFF03DAC6),      // Teal accent
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E2E),
    surfaceVariant = Color(0xFF2A2A3C),
    onBackground = Color(0xFFE6E6E6),
    onSurface = Color(0xFFE6E6E6),
    outline = Color(0xFF444466),
    error = Color(0xFFCF6679),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6C63FF),
    onPrimary = Color.White,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

@Composable
fun MobileToCursorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    // Make status bar match theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
