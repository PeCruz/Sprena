package br.com.sprena.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// =============================================================================
// Sprena Theme
// =============================================================================

private val LightColorScheme =
    lightColorScheme(
        primary = SprenaPrimary,
        onPrimary = SprenaOnPrimary,
        primaryContainer = SprenaPrimaryContainer,
        onPrimaryContainer = SprenaOnPrimaryContainer,
        secondary = SprenaSecondary,
        onSecondary = SprenaOnSecondary,
        secondaryContainer = SprenaSecondaryContainer,
        onSecondaryContainer = SprenaOnSecondaryContainer,
        tertiary = SprenaTertiary,
        onTertiary = SprenaOnTertiary,
        tertiaryContainer = SprenaTertiaryContainer,
        onTertiaryContainer = SprenaOnTertiaryContainer,
        error = SprenaError,
        onError = SprenaOnError,
        errorContainer = SprenaErrorContainer,
        onErrorContainer = SprenaOnErrorContainer,
        background = SprenaBackground,
        onBackground = SprenaOnBackground,
        surface = SprenaSurface,
        onSurface = SprenaOnSurface,
        surfaceVariant = SprenaSurfaceVariant,
        onSurfaceVariant = SprenaOnSurfaceVariant,
        outline = SprenaOutline,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = SprenaDarkPrimary,
        onPrimary = SprenaDarkOnPrimary,
        primaryContainer = SprenaDarkPrimaryContainer,
        onPrimaryContainer = SprenaDarkOnPrimaryContainer,
        secondary = SprenaDarkSecondary,
        onSecondary = SprenaDarkOnSecondary,
        secondaryContainer = SprenaDarkSecondaryContainer,
        onSecondaryContainer = SprenaDarkOnSecondaryContainer,
        tertiary = SprenaDarkTertiary,
        onTertiary = SprenaDarkOnTertiary,
        tertiaryContainer = SprenaDarkTertiaryContainer,
        onTertiaryContainer = SprenaDarkOnTertiaryContainer,
        error = SprenaDarkError,
        onError = SprenaDarkOnError,
        errorContainer = SprenaDarkErrorContainer,
        onErrorContainer = SprenaDarkOnErrorContainer,
        background = SprenaDarkBackground,
        onBackground = SprenaDarkOnBackground,
        surface = SprenaDarkSurface,
        onSurface = SprenaDarkOnSurface,
        surfaceVariant = SprenaDarkSurfaceVariant,
        onSurfaceVariant = SprenaDarkOnSurfaceVariant,
        outline = SprenaDarkOutline,
    )

/**
 * Tema Sprena com suporte a dark/light mode.
 */
@Composable
fun SprenaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SprenaTypography,
        content = content,
    )
}
