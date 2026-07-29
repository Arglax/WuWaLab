package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.data.AvatarBorderStyle
import io.github.arglax.wuwalab.ui.theme.VioletGlow

/**
 * Applies the correct border treatment for an [AvatarBorderStyle] to
 * whatever it's chained onto (almost always an avatar `Image`'s modifier).
 * Centralizing this here means every place that renders an avatar - the
 * Profile header, the Profile Stats dialog, the Shop grid, Edit Profile's
 * picker - stays a one-liner instead of repeating a `when` over rarity.
 *
 * - `null` (nothing equipped): falls back to the original static violet
 *   hairline every avatar has always had, so unequipped users see no change.
 * - COMMON/RARE/EPIC: a static `Modifier.border()` painted with the style's
 *   gradient colors.
 * - LEGENDARY_ANIMATED: the rotating rainbow sweep from
 *   `Modifier.animatedSweepBorder` (VisualEffects.kt) - drawn with
 *   `drawBehind` under the hood, so this never forces an extra layout pass.
 *
 * The same [cornerRadius] is always used for the clip AND the border/sweep,
 * so the stroke traces the image's edge exactly with no visible gap or seam.
 */
fun Modifier.rarityAvatarBorder(
    style: AvatarBorderStyle?,
    cornerRadius: Dp = 12.dp,
    strokeWidth: Dp = 2.dp
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return when (style) {
        null -> this
            .clip(shape)
            .border(1.dp, VioletGlow.copy(alpha = 0.5f), shape)

        AvatarBorderStyle.LEGENDARY_ANIMATED -> this
            .animatedSweepBorder(colors = style.colors, cornerRadius = cornerRadius, strokeWidth = strokeWidth)
            .clip(shape)

        else -> this
            .clip(shape)
            .border(strokeWidth, Brush.linearGradient(style.colors), shape)
    }
}

/**
 * A reusable wrapper Composable version of [rarityAvatarBorder] for spots
 * that build a whole tile (e.g. the Shop grid) rather than just decorating
 * an existing `Image` modifier - wraps [content] in a Box carrying the
 * correct border/clip for [style].
 */
@Composable
fun RarityBorderBox(
    style: AvatarBorderStyle?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    strokeWidth: Dp = 2.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.rarityAvatarBorder(style, cornerRadius, strokeWidth)
    ) {
        content()
    }
}