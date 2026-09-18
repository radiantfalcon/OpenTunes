package com.opentunes.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass and Glassmorphism utilities for OpenTunes.
 * Simulates translucent frosted glass with specular edge reflections and subtle refraction.
 */
object Glassmorphism {
    fun specularBorder(
        topStartAlpha: Float = 0.35f,
        bottomEndAlpha: Float = 0.08f
    ): Brush {
        return Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = topStartAlpha),
                Color.White.copy(alpha = (topStartAlpha + bottomEndAlpha) / 2f),
                Color.White.copy(alpha = bottomEndAlpha)
            )
        )
    }

    fun glowBorder(
        glowColor: Color = AccentBlue,
        alpha: Float = 0.6f
    ): Brush {
        return Brush.linearGradient(
            listOf(
                glowColor.copy(alpha = alpha),
                Color.White.copy(alpha = 0.25f),
                glowColor.copy(alpha = alpha * 0.4f)
            )
        )
    }

    fun liquidBackground(
        topAlpha: Float = 0.16f,
        bottomAlpha: Float = 0.08f,
        tint: Color = Color.White
    ): Brush {
        return Brush.verticalGradient(
            listOf(
                tint.copy(alpha = topAlpha),
                tint.copy(alpha = bottomAlpha)
            )
        )
    }
}

/**
 * Modifier extension to apply frosted liquid glass styling with specular border.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    borderBrush: Brush = Glassmorphism.specularBorder(),
    bgBrush: Brush = Glassmorphism.liquidBackground()
): Modifier = this
    .clip(shape)
    .background(bgBrush, shape)
    .border(borderWidth, borderBrush, shape)
