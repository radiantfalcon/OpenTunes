package com.opentunes.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val AmoledDarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = AccentBlueOnPill,
    primaryContainer = AccentBlueContainer,
    onPrimaryContainer = AccentBlue,
    secondary = AccentBlue,
    secondaryContainer = ActiveIndicator,
    onSecondaryContainer = White,
    background = PureBlack,
    onBackground = White,
    surface = PureBlack,
    onSurface = White,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    outline = DarkCardBorder
)

@Composable
fun OpenTunesTheme(
    dynamicColor: Boolean = true,
    amoledBlack: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = dynamicDarkColorScheme(context)
            if (amoledBlack) {
                dynamic.copy(
                    background = PureBlack,
                    surface = PureBlack,
                    surfaceVariant = DarkCard
                )
            } else dynamic
        }
        else -> AmoledDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
