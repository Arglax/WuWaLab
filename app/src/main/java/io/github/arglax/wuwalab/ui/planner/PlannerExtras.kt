package io.github.arglax.wuwalab.ui.planner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.data.LedgerEntry
import io.github.arglax.wuwalab.gacha.GachaMath
import io.github.arglax.wuwalab.ui.components.GlassCard
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.CyanGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * "How far am I from actually landing this character?" - expressed in the
 * three units players think in: Astrites, pulls, and days at their current
 * earning pace.
 *
 * The yardstick is the WORST case (lose the 50/50 at hard pity, then hard pity
 * again), because that is the number that guarantees the character rather than
 * merely making it likely. Getting there early is a bonus, never a shortfall.
 */
object PullOutlook {

    data class Outlook(
        val headline: String,
        val detail: String,
        val progress: Float,
        val pullsToGuarantee: Int,
        val astritesToGuarantee: Int,
        val astritesStillNeeded: Int,
        val medianPulls: Int?,
        val chanceNow: Double
    )

    fun compute(
        banner: GachaMath.Banner,
        pity: Int,
        guaranteed: Boolean,
        balance: Int,
        dailyAverageEarn: Double = 0.0
    ): Outlook {
        val safeBalance = balance.coerceAtLeast(0)
        val pullsToGuarantee = GachaMath.worstCasePullsToFeatured(banner, pity, guaranteed)
        val astritesToGuarantee = GachaMath.astritesForPulls(pullsToGuarantee)
        val stillNeeded = (astritesToGuarantee - safeBalance).coerceAtLeast(0)
        val progress = if (astritesToGuarantee <= 0) 1f
        else (safeBalance.toFloat() / astritesToGuarantee).coerceIn(0f, 1f)

        val affordable = GachaMath.pullsFromAstrites(safeBalance)
        val curve = GachaMath.featuredCurve(banner, pity, guaranteed, pullsToGuarantee.coerceAtLeast(1))
        val chanceNow = if (affordable in 1..curve.size) curve[affordable - 1] else 0.0
        val medianIndex = curve.indexOfFirst { it >= 0.5 }
        val medianPulls = if (medianIndex >= 0) medianIndex + 1 else null

        val unit = if (banner == GachaMath.Banner.STANDARD) "next 5-star" else "featured 5-star"

        val headline = when {
            stillNeeded == 0 -> "You can force the " + unit + " right now."
            progress >= 0.75f -> "Nearly there - one good week away."
            progress >= 0.40f -> "Over halfway to a guaranteed " + unit + "."
            progress >= 0.15f -> "Building up. Keep logging your dailies."
            else -> "Long road ahead - but every daily counts."
        }

        val daysText = if (stillNeeded > 0 && dailyAverageEarn > 0.0) {
            val days = ceil(stillNeeded / dailyAverageEarn).roundToInt()
            " At your recent pace of " + ((dailyAverageEarn * 10).roundToInt() / 10.0) +
                " Astrites a day, that is about " + days + " day(s)."
        } else ""

        val detail = if (stillNeeded == 0) {
            "You hold enough for all " + pullsToGuarantee + " pulls of the worst-case run, so the " +
                unit + " is yours whatever the RNG does. Right now your odds within budget are " +
                pct(chanceNow) + "."
        } else {
            stillNeeded.toString() + " more Astrites (" + ceil(stillNeeded / 160.0).roundToInt() +
                " pulls) guarantees it even with the worst luck." + daysText +
                " With what you hold today your odds are " + pct(chanceNow) + "."
        }

        return Outlook(
            headline = headline,
            detail = detail,
            progress = progress,
            pullsToGuarantee = pullsToGuarantee,
            astritesToGuarantee = astritesToGuarantee,
            astritesStillNeeded = stillNeeded,
            medianPulls = medianPulls,
            chanceNow = chanceNow
        )
    }

    private fun pct(p: Double): String = ((p * 1000).roundToInt() / 10.0).toString() + "%"
}

/** "How far are you from making it big" - the headline card on the Pull Planner. */
@Composable
fun PullOutlookCard(
    banner: GachaMath.Banner,
    pity: Int,
    guaranteed: Boolean,
    balance: Int,
    dailyAverageEarn: Double,
    modifier: Modifier = Modifier
) {
    val outlook = remember(banner, pity, guaranteed, balance, dailyAverageEarn) {
        PullOutlook.compute(banner, pity, guaranteed, balance, dailyAverageEarn)
    }
    val accent = when {
        outlook.progress >= 0.99f -> EmeraldGlow
        outlook.progress >= 0.5f -> AmberGlow
        else -> VioletGlow
    }

    GlassCard(modifier = modifier.fillMaxWidth(), accent = accent) {
        Text("Road to the 5-star", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
        Spacer(Modifier.height(2.dp))
        Text(outlook.headline, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { outlook.progress },
            color = accent,
            trackColor = Color.White.copy(alpha = 0.10f),
            modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(50))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            (outlook.progress * 100).roundToInt().toString() + "% of the " + outlook.astritesToGuarantee +
                " Astrites a guaranteed pull can cost",
            color = TextMuted,
            fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
        Spacer(Modifier.height(8.dp))
        Text(outlook.detail, color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
        outlook.medianPulls?.let { median ->
            Spacer(Modifier.height(6.dp))
            Text(
                "Coin-flip point: around pull " + median + " is where you cross 50/50 odds.",
                color = CyanGlow,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
        }
    }
}

/**
 * The Pull Planner's convene log - a proper record rather than a running
 * total. Each row is one logged spend with its banner, pull count, cost and
 * the pity/guarantee state at that moment, straight out of the same logbook
 * the Economic Dashboard reads.
 */
@Composable
fun PullSpendLogSection(
    log: List<LedgerEntry>,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth(), accent = CoralGlow) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Convene Log", fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            Text(
                log.size.toString() + " entr" + (if (log.size == 1) "y" else "ies"),
                color = TextMuted,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Every spend you log lands here and in the Economic Dashboard logbook.",
            color = TextMuted,
            fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
        Spacer(Modifier.height(10.dp))

        if (log.isEmpty()) {
            Text("Nothing logged yet.", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
            return@GlassCard
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            LogHeader("When", 0.30f)
            LogHeader("Banner", 0.24f)
            LogHeader("Pulls", 0.14f)
            LogHeader("Cost", 0.20f)
            Spacer(Modifier.width(30.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            log.sortedByDescending { it.epochMs }.take(50).forEach { entry ->
                val stamp = remember(entry.epochMs) {
                    SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(entry.epochMs))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        LogCell(stamp, 0.30f, TextSecondary)
                        LogCell(entry.banner.ifBlank { "-" }, 0.24f, TextPrimary)
                        LogCell(entry.pulls.toString(), 0.14f, CyanGlow)
                        LogCell("-" + entry.amount, 0.20f, CoralGlow)
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { onDelete(entry.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove log row", tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                    if (entry.pity >= 0) {
                        Text(
                            "Pity at spend: " + entry.pity + (if (entry.guaranteed) " (guaranteed)" else " (50/50)"),
                            color = TextMuted,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.LogHeader(text: String, weight: Float) {
    Text(
        text,
        color = TextMuted,
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier.weight(weight)
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.LogCell(text: String, weight: Float, color: Color) {
    Text(
        text,
        color = color,
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
        maxLines = 1,
        modifier = Modifier.weight(weight)
    )
}

/**
 * The probability curve, now with the player's own pity marked on it.
 *
 * Three markers, all of them things a player actually asks about:
 *  - a violet dot + "You are here" at pull 1, anchored to the pity they typed in;
 *  - the amber budget line showing how far their Astrites reach;
 *  - the red hard-pity line where a 5-star becomes mathematically certain.
 */
@Composable
fun ProbabilityProjectionChartV2(
    curve: List<Double>,
    currentPity: Int,
    hardPityAtPull: Int,
    affordablePulls: Int,
    softPityAtPull: Int,
    modifier: Modifier = Modifier,
    normalizeYAxis: Boolean = false
) {
    Canvas(modifier = modifier) {
        if (curve.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val padLeft = 44f
        val padBottom = 26f
        val chartW = (w - padLeft).coerceAtLeast(1f)
        val chartH = (h - padBottom).coerceAtLeast(1f)
        val n = curve.size
        val axisMax = if (normalizeYAxis) (curve.maxOrNull() ?: 1.0).coerceAtLeast(0.01) else 1.0

        fun xFor(i: Int): Float = padLeft + chartW * (i.toFloat() / (n - 1).coerceAtLeast(1))
        fun yFor(p: Double): Float = chartH * (1f - (p / axisMax).toFloat().coerceIn(0f, 1f))

        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(160, 255, 255, 255)
            textSize = 22f
            isAntiAlias = true
        }
        val markerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(235, 200, 175, 255)
            textSize = 21f
            isAntiAlias = true
        }

        listOf(0.0, 0.25, 0.5, 0.75, 1.0).forEach { p ->
            val y = yFor(p * axisMax)
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(padLeft, y),
                end = Offset(w, y),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
            val labelText = if (normalizeYAxis) ((p * axisMax * 1000).roundToInt() / 10.0).toString() + "%"
            else (p * 100).roundToInt().toString() + "%"
            drawContext.canvas.nativeCanvas.drawText(labelText, 0f, y + 8f, textPaint)
        }

        if (softPityAtPull in 0 until n) {
            val zoneStart = xFor(softPityAtPull)
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(CoralGlow.copy(alpha = 0.04f), CoralGlow.copy(alpha = 0.14f)),
                    startX = zoneStart,
                    endX = w
                ),
                topLeft = Offset(zoneStart, 0f),
                size = androidx.compose.ui.geometry.Size(w - zoneStart, chartH)
            )
        }

        val line = Path()
        val fill = Path()
        curve.forEachIndexed { i, p ->
            val x = xFor(i)
            val y = yFor(p)
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, chartH)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(xFor(n - 1), chartH)
        fill.close()

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(CyanGlow.copy(alpha = 0.35f), CyanGlow.copy(alpha = 0.02f)),
                startY = 0f,
                endY = chartH
            )
        )
        drawPath(path = line, color = CyanGlow, style = Stroke(width = 5f, cap = StrokeCap.Round))

        // Hard-pity line: the pull at which a 5-star is mathematically certain.
        if (hardPityAtPull in 1..n) {
            val x = xFor(hardPityAtPull - 1)
            drawLine(
                color = CoralGlow.copy(alpha = 0.85f),
                start = Offset(x, 0f),
                end = Offset(x, chartH),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 7f))
            )
        }

        // Budget line: how far the current Astrite balance reaches.
        if (affordablePulls in 1..n) {
            val x = xFor(affordablePulls - 1)
            val y = yFor(curve[affordablePulls - 1])
            drawLine(
                color = AmberGlow,
                start = Offset(x, 0f),
                end = Offset(x, chartH),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
            )
            drawCircle(color = AmberGlow, radius = 9f, center = Offset(x, y))
            drawCircle(color = Color.White, radius = 4f, center = Offset(x, y))
        }

        // "You are here": the curve's origin, which IS the player's live pity.
        val startX = xFor(0)
        val startY = yFor(curve[0])
        drawCircle(color = VioletGlow, radius = 11f, center = Offset(startX, startY))
        drawCircle(color = Color.White, radius = 5f, center = Offset(startX, startY))
        drawContext.canvas.nativeCanvas.drawText(
            "Pity " + currentPity,
            (startX + 10f).coerceAtMost(w - 90f),
            (startY - 12f).coerceAtLeast(20f),
            markerPaint
        )

        drawContext.canvas.nativeCanvas.drawText("1", padLeft, h - 4f, textPaint)
        drawContext.canvas.nativeCanvas.drawText((n / 2).toString(), padLeft + chartW / 2f - 10f, h - 4f, textPaint)
        val endLabel = n.toString() + " pulls"
        drawContext.canvas.nativeCanvas.drawText(endLabel, w - textPaint.measureText(endLabel), h - 4f, textPaint)
    }
}