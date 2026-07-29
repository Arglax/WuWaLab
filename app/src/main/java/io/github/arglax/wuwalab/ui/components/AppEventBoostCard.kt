package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.data.AppEventCalendar
import io.github.arglax.wuwalab.data.LocalAppEvent
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.CyanGlow
import io.github.arglax.wuwalab.ui.theme.GlassBorderSoft
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow

private fun accentFor(event: LocalAppEvent): Color = when (event) {
    LocalAppEvent.MIDWEEK_JUMP -> CyanGlow
    LocalAppEvent.WEEKEND_RUSH -> AmberGlow
}

private fun shortDuration(ms: Long): String {
    if (ms <= 0L) return "now"
    val days = ms / 86_400_000L
    val hours = (ms % 86_400_000L) / 3_600_000L
    val minutes = (ms % 3_600_000L) / 60_000L
    return when {
        days > 0 -> days.toString() + "d " + hours + "h"
        hours > 0 -> hours.toString() + "h " + minutes + "m"
        else -> minutes.toString() + "m"
    }
}

/**
 * The Dashboard's app-event strip.
 *
 * When Midweek Jump or Weekend Rush is running it shows the live multiplier
 * and how long is left; otherwise it counts down to the next one, so the
 * player always knows whether it is worth banking a claim for later.
 * [nowTick] is passed in so this rides the Dashboard's existing once-a-minute
 * tick instead of freezing at first composition.
 */
@Composable
fun AppEventBoostCard(nowTick: Long, modifier: Modifier = Modifier) {
    val active = remember(nowTick) { AppEventCalendar.activeEvent(nowTick) }
    val upcoming = remember(nowTick) { AppEventCalendar.nextEvent(nowTick) }

    if (active != null) {
        val accent = accentFor(active)
        val remaining = remember(nowTick) { AppEventCalendar.millisUntilEnd(nowTick) }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .pulsingGlow(accent, 18.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.06f))
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(active.title, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.32f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "LIVE",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        active.tagline,
                        color = TextSecondary,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Ends in " + shortDuration(remaining),
                        color = TextMuted,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                    Text(
                        "\u00d7" + active.multiplier.toInt(),
                        color = accent,
                        fontWeight = FontWeight.Black,
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize
                    )
                    Text(
                        "Argstrites",
                        color = TextMuted,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
            }
        }
        return
    }

    if (upcoming == null) return
    val (next, untilStart) = upcoming
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, GlassBorderSoft, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Next: " + next.title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.labelMedium.fontSize
            )
            Text(
                next.tagline,
                color = TextMuted,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "in " + shortDuration(untilStart),
            color = VioletGlow,
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
    }
}
