package com.mobiletocursor.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Animated glowing orbs background — matches the Vexra website aesthetic.
 *
 * Three radial gradient orbs slowly breathe (scale + alpha) on a #000 canvas.
 * Used as the base layer behind all screens.
 */
@Composable
fun VexraGlowBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "glow")

    val alpha1 by transition.animateFloat(
        initialValue = 0.10f, targetValue = 0.18f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "orb1",
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.08f, targetValue = 0.14f,
        animationSpec = infiniteRepeatable(tween(5500), RepeatMode.Reverse),
        label = "orb2",
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.06f, targetValue = 0.12f,
        animationSpec = infiniteRepeatable(tween(7000), RepeatMode.Reverse),
        label = "orb3",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Top-right purple orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF7c5ce7).copy(alpha = alpha1),
                    Color.Transparent,
                ),
                center = Offset(w * 0.85f, h * 0.08f),
                radius = w * 0.7f,
            ),
            radius = w * 0.7f,
            center = Offset(w * 0.85f, h * 0.08f),
        )

        // Bottom-left blue orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF508CFF).copy(alpha = alpha2),
                    Color.Transparent,
                ),
                center = Offset(w * 0.15f, h * 0.92f),
                radius = w * 0.6f,
            ),
            radius = w * 0.6f,
            center = Offset(w * 0.15f, h * 0.92f),
        )

        // Center subtle white glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = alpha3 * 0.3f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.5f, h * 0.4f),
                radius = w * 0.5f,
            ),
            radius = w * 0.5f,
            center = Offset(w * 0.5f, h * 0.4f),
        )
    }
}
