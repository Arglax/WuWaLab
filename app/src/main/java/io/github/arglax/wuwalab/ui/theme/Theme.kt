package io.github.arglax.wuwalab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val WuwaDarkColorScheme = darkColorScheme(
    primary = CyanGlow,
    onPrimary = SpaceDeep,
    secondary = VioletGlow,
    onSecondary = SpaceDeep,
    tertiary = EmeraldGlow,
    onTertiary = SpaceDeep,
    background = SpaceDeep,
    onBackground = TextPrimary,
    surface = SpaceMid,
    onSurface = TextPrimary,
    surfaceVariant = SpaceViolet,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    error = CoralGlow,
    onError = SpaceDeep
)

/**
 * WuWaLab always renders in this dark, glossy/glass theme regardless of
 * system light/dark mode or dynamic color - the translucent glass cards are
 * designed against this exact palette, so letting Material's dynamic color
 * override it would break the glow/contrast tuning.
 */
@Composable
fun WuWaLabTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WuwaDarkColorScheme,
        typography = Typography,
        content = content
    )
}