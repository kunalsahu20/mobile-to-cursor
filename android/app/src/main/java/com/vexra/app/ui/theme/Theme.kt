package com.vexra.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Vexra premium dark theme — pure black with purple accent.
 *
 * Design tokens from Stitch-generated mobile pairing screen:
 *   Background  #000000  |  Card     #0d0d10
 *   Border      #1a1a20  |  Accent   #7c5ce7
 *   Text white  #e8e8ec  |  Text dim #555560
 */

val VexraBg = Color(0xFF000000)
val VexraCard = Color(0xFF0d0d10)
val VexraBorder = Color(0xFF1a1a20)
val VexraAccent = Color(0xFF7c5ce7)
val VexraAccentLight = Color(0xFF9b7dff)
val VexraTextPrimary = Color(0xFFe8e8ec)
val VexraTextMuted = Color(0xFF8a8a94)
val VexraTextDim = Color(0xFF555560)
val VexraGreen = Color(0xFF34d399)
val VexraYellow = Color(0xFFfbbf24)
val VexraRed = Color(0xFFf87171)

private val VexraDarkColors = darkColorScheme(
    primary = VexraAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D2B8C),
    secondary = VexraAccentLight,
    onSecondary = Color.Black,
    background = VexraBg,
    surface = VexraCard,
    surfaceVariant = Color(0xFF111116),
    onBackground = VexraTextPrimary,
    onSurface = VexraTextPrimary,
    outline = VexraBorder,
    error = VexraRed,
)

@Composable
fun MobileToCursorTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = VexraBg.toArgb()
            window.navigationBarColor = VexraBg.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = VexraDarkColors,
        content = content,
    )
}
