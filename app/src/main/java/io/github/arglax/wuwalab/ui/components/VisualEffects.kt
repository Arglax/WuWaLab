package io.github.arglax.wuwalab.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Premium visual effects shared across the app (Profile header, planner
 * verdict cards, anything that should feel "special"). Complements the
 * existing [pulsingGlow] in GlassComponents.kt - that one breathes alpha;
 * these two SWEEP color around the border and glow behind the shape.
 */

/**
 * An animated, rotating sweep-gradient border. The gradient spins forever
 * (rotation animated via infiniteTransition), producing the classic
 * "energy ring" look without any bundled GIF/texture assets.
 */
fun Modifier.animatedSweepBorder(
    colors: List<Color>,
    cornerRadius: Dp = 18.dp,
    strokeWidth: Dp = 1.5.dp,
    durationMs: Int = 3200
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "sweepBorder")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )
    drawBehind {
        val radiusPx = cornerRadius.toPx()
        val strokePx = strokeWidth.toPx()
        // Rotating the draw context (not the brush) keeps the sweep's center
        // pinned to the shape's center at every angle.
        rotate(degrees = angle, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawRoundRect(
                brush = Brush.sweepGradient(colors + colors.first(), center = Offset(size.width / 2f, size.height / 2f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx, radiusPx),
                style = Stroke(width = strokePx)
            )
        }
    }
}

/**
 * A fixed (non-animated) sweep-gradient border - same painterly look as
 * [animatedSweepBorder] but frozen at a single angle, since a perpetually
 * spinning ring around the Profile header reads as a bug rather than
 * "premium" once you stare at it for more than a few seconds.
 */
fun Modifier.staticSweepBorder(
    colors: List<Color>,
    cornerRadius: Dp = 18.dp,
    strokeWidth: Dp = 1.5.dp,
    angle: Float = 20f // kept for API compatibility
): Modifier = drawBehind {
    val strokePx = strokeWidth.toPx()
    val inset = strokePx / 2f
    val radiusPx = cornerRadius.toPx() - inset

    drawRoundRect(
        brush = Brush.linearGradient(
            colors = colors,
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        ),
        topLeft = Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(
            size.width - strokePx,
            size.height - strokePx
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
            radiusPx,
            radiusPx
        ),
        style = Stroke(strokePx)
    )
}

/**
 * A soft ambient glow painted BEHIND the composable - a blurred-looking halo
 * built from concentric translucent strokes (cheap, no RenderEffect needed,
 * works on every API level this app supports).
 */
fun Modifier.ambientGlow(
    color: Color,
    cornerRadius: Dp = 18.dp,
    spread: Dp = 10.dp
): Modifier = drawBehind {
    val radiusPx = cornerRadius.toPx()
    val spreadPx = spread.toPx()
    val steps = 6
    for (i in steps downTo 1) {
        val t = i.toFloat() / steps
        drawRoundRect(
            color = color.copy(alpha = 0.05f * (1f - t) + 0.02f),
            topLeft = Offset(-spreadPx * t, -spreadPx * t),
            size = androidx.compose.ui.geometry.Size(size.width + 2 * spreadPx * t, size.height + 2 * spreadPx * t),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx + spreadPx * t, radiusPx + spreadPx * t)
        )
    }
}

/**
 * A shimmering animated gradient fill for "premium" surfaces - a linear
 * gradient whose anchor slides back and forth forever.
 */
fun Modifier.animatedGradientFill(
    colors: List<Color>,
    cornerRadius: Dp = 18.dp,
    durationMs: Int = 4000
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "gradientFill")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientShift"
    )
    clip(RoundedCornerShape(cornerRadius)).drawBehind {
        val span = size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(-span + shift * 2 * span, 0f),
                end = Offset(shift * 2 * span, size.height)
            )
        )
    }
}

/**
 * Coil boilerplate for loading polished remote/border textures. Until real
 * artwork URLs are wired in, [DEFAULT_TEXTURE_URL] points at a tiny base64
 * PNG (a translucent white pixel) so this renders harmlessly out of the box -
 * swap in any https:// image URL and it just works, with crossfade.
 *
 * Requires the Coil dependency (see integration guide):
 *   implementation("io.coil-kt:coil-compose:2.7.0")
 */
const val DEFAULT_TEXTURE_URL: String =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

@Composable
fun GlowTextureImage(
    url: String = DEFAULT_TEXTURE_URL,
    cornerRadius: Dp = 18.dp,
    accent: Color = Color(0xFFB08CFF),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .animatedSweepBorder(
                colors = listOf(accent, accent.copy(alpha = 0.1f), Color.White.copy(alpha = 0.6f), accent),
                cornerRadius = cornerRadius
            )
            .padding(2.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White.copy(alpha = 0.04f))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}