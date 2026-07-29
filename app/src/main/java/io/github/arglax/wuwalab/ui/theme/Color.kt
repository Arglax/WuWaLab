package io.github.arglax.wuwalab.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---- Core "next-gen glass" palette -----------------------------------------
// Deep space-navy base with cyan/violet glow accents, tuned for translucent
// glass cards floating over a gradient backdrop.

val SpaceDeep = Color(0xFF05070F)      // app background base (near-black navy)
val SpaceMid = Color(0xFF0C1024)
val SpaceViolet = Color(0xFF171034)

val GlassSurface = Color(0x33FFFFFF)     // translucent white glass fill (~20% alpha)
val GlassSurfaceStrong = Color(0x4DFFFFFF)
val GlassBorder = Color(0x59FFFFFF)      // hairline glass edge highlight
val GlassBorderSoft = Color(0x24FFFFFF)

val CyanGlow = Color(0xFF5CE1E6)         // waveplate cyan
val CyanGlowDeep = Color(0xFF17A6C4)
val EmeraldGlow = Color(0xFF52E39A)      // waveplate crystal green
val EmeraldGlowDeep = Color(0xFF12B075)
val VioletGlow = Color(0xFF9B7BFF)       // accent / brand
val VioletGlowDeep = Color(0xFF6C4CE0)
val AmberGlow = Color(0xFFFFC15E)        // "upcoming" accent
val CoralGlow = Color(0xFFFF6E6E)        // "overloaded" / warning accent

val TextPrimary = Color(0xFFF3F5FF)
val TextSecondary = Color(0xFFAEB4D6)
val TextMuted = Color(0xFF767CA0)

// Status colors
val StatusDepleted = Color(0xFF6B7094)   // gray - resource sitting at exactly 0
val StatusRegenerating = CyanGlow
val StatusFull = Color(0xFFFFD54A)       // yellow - sitting exactly at the cap
val StatusOverloaded = CoralGlow         // red - pushed past the cap (manual override)

// ---- Gradients ---------------------------------------------------------
val BackgroundGradient = Brush.verticalGradient(
    colors = listOf(SpaceDeep, SpaceMid, SpaceViolet, SpaceDeep)
)

val HeroGlowGradient = Brush.radialGradient(
    colors = listOf(VioletGlowDeep.copy(alpha = 0.35f), Color.Transparent)
)

fun glassCardGradient() = Brush.linearGradient(
    colors = listOf(GlassSurfaceStrong, GlassSurface, GlassSurface.copy(alpha = 0.08f))
)

fun accentGlowGradient(accent: Color) = Brush.linearGradient(
    colors = listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.02f))
)

fun accentBorderGradient(accent: Color) = Brush.linearGradient(
    colors = listOf(accent.copy(alpha = 0.9f), GlassBorderSoft)
)

// ---- Legacy Material default swatches (kept so nothing else breaks) -------
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)