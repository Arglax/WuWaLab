package io.github.arglax.wuwalab.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import io.github.arglax.wuwalab.data.ResourceStatus
import io.github.arglax.wuwalab.ui.theme.GlassBorder
import io.github.arglax.wuwalab.ui.theme.GlassBorderSoft
import io.github.arglax.wuwalab.ui.theme.SpaceDeep
import io.github.arglax.wuwalab.ui.theme.StatusDepleted
import io.github.arglax.wuwalab.ui.theme.StatusFull
import io.github.arglax.wuwalab.ui.theme.StatusOverloaded
import io.github.arglax.wuwalab.ui.theme.StatusRegenerating
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.accentBorderGradient
import io.github.arglax.wuwalab.ui.theme.accentGlowGradient
import io.github.arglax.wuwalab.ui.theme.glassCardGradient
import io.github.arglax.wuwalab.util.rememberTapFeedback

/**
 * A soft "breathing" glow border that pulses opacity on an infinite loop -
 * the lightweight, no-external-assets way to give a card some life (e.g. for
 * Live event banners). Wrap a Box/Card modifier chain with this, placed
 * *before* `.clip()`/`.background()` so the glow sits on the outer edge.
 *
 * We deliberately don't ship animated GIF/WebP borders here: bundling a
 * flame/shine GIF per accent color would balloon the APK for a purely
 * decorative touch, and GIF playback in Compose needs an extra image-loading
 * library (e.g. Coil w/ ImageDecoder) with its own perf tradeoffs on a
 * home-screen-adjacent, battery-sensitive app. This gets ~90% of the "alive"
 * feeling for ~0 extra weight; swap in Coil's `rememberDrawablePainter` +
 * an actual GIF asset later if you want literal flame/shine artwork.
 */
@Composable
fun Modifier.pulsingGlow(color: Color, cornerRadius: androidx.compose.ui.unit.Dp = 22.dp): Modifier {
    val transition = rememberInfiniteTransition(label = "pulsingGlow")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween<Float>(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsingGlowAlpha"
    )
    return this.border(1.5.dp, color.copy(alpha = alpha), RoundedCornerShape(cornerRadius))
}


@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (accent != null) accentGlowGradient(accent) else glassCardGradient())
            .border(
                width = 1.dp,
                brush = if (accent != null) accentBorderGradient(accent) else
                    androidx.compose.ui.graphics.SolidColor(GlassBorderSoft),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * Pill badge showing Depleted / Regenerating / Full / Overloaded with a
 * matching glow color and icon:
 *   Depleted     - gray, no icon (exactly 0)
 *   Regenerating - blue/green, animated "." ".." "..." loop (0 < x < cap)
 *   Full         - yellow, caution icon (exactly at the cap)
 *   Overloaded   - red, hazard icon (past the cap via manual override)
 */
@Composable
fun StatusPill(status: ResourceStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        ResourceStatus.DEPLETED -> "Depleted" to StatusDepleted
        ResourceStatus.REGENERATING -> "Regenerating" to StatusRegenerating
        ResourceStatus.FULL -> "Full" to StatusFull
        ResourceStatus.OVERLOADED -> "Overloaded" to StatusOverloaded
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        when (status) {
            ResourceStatus.FULL -> Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            ResourceStatus.OVERLOADED -> Icon(Icons.Filled.Dangerous, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            else -> Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Row(modifier = Modifier.padding(start = 6.dp)) {
            Text(label, color = color, fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.Bold)
            if (status == ResourceStatus.REGENERATING) {
                Text(regeneratingDots(), color = color, fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Cycles "." -> ".." -> "..." -> "." on a loop, like a loading indicator. */
@Composable
private fun regeneratingDots(): String {
    var dotCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500L)
            dotCount = (dotCount % 3) + 1
        }
    }
    return ".".repeat(dotCount)
}

/**
 * A per-page "(?) Help" affordance, meant to sit in a screen's title row so
 * every page has a quick, consistent way to explain itself to new users.
 * Unlike [TooltipIcon] (a small inline popup for one specific field), this
 * opens a full-width [AlertDialog] since page-level help is usually a short
 * list of bullet points rather than a single sentence.
 */
@Composable
fun HelpButton(title: String, body: String, modifier: Modifier = Modifier) {
    var showHelp by remember { mutableStateOf(false) }
    val feedback = rememberTapFeedback()

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, GlassBorder, CircleShape)
            .clickable {
                feedback()
                showHelp = true
            },
        contentAlignment = Alignment.Center
    ) {
        Text("?", color = TextPrimary, fontSize = MaterialTheme.typography.bodyMedium.fontSize, fontWeight = FontWeight.Bold)
    }

    if (showHelp) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(body, color = TextSecondary) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showHelp = false }) { Text("Got it") }
            }
        )
    }
}

/**
 * A small "(?)" affordance that reveals an info popup on tap - used for the
 * waveplate manual-update disclaimer. Self-contained: owns its own open/close
 * state so it can be dropped anywhere without extra plumbing.
 */
@Composable
fun TooltipIcon(title: String, body: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, GlassBorder, CircleShape)
                .clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Text("?", color = TextPrimary, fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.Bold)
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { expanded = false }
            ) {
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .padding(top = 26.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SpaceDeep.copy(alpha = 0.97f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { }
                            .padding(14.dp)
                    ) {
                        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize)
                        Text(
                            body,
                            color = TextSecondary,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            "Tap anywhere to close",
                            color = TextMuted,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}