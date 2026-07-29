package io.github.arglax.wuwalab.ui.planner

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.data.PlannerRepository
import io.github.arglax.wuwalab.data.PlannerState
import io.github.arglax.wuwalab.data.WuwaRepository
import io.github.arglax.wuwalab.gacha.GachaMath
import io.github.arglax.wuwalab.ui.components.ArgstriteAwardDialog
import io.github.arglax.wuwalab.ui.components.GlassCard
import io.github.arglax.wuwalab.ui.components.HelpButton
import io.github.arglax.wuwalab.ui.components.argstriteRewardFor
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.CyanGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.GlassBorderSoft
import io.github.arglax.wuwalab.ui.theme.GlassSurface
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow
import io.github.arglax.wuwalab.ui.theme.VioletGlowDeep
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Which shape of probability chart the Pull Planner is currently showing. */
enum class CurveType(val label: String) {
    CUMULATIVE("Cumulative"),
    NON_CUMULATIVE("Non-Cumulative")
}

/**
 * The Gacha Pull Planner page. The Astrite balance shown here is NOT its own
 * number - it's [astriteBalance], the same globally shared total the Dashboard
 * and Profile header display, passed down from the app-level state. Spending
 * pulls here writes back through [PlannerRepository.spendAstrites], so every
 * other screen updates the moment the spend lands.
 */
@Composable
fun GachaPlannerScreen(
    plannerRepo: PlannerRepository,
    astriteBalance: Int,
    // Earnings-only daily average, used to translate "how far away am I" into days.
    dailyAverageEarn: Double = 0.0,
    wuwaRepo: WuwaRepository? = null,
    achievementsRepo: io.github.arglax.wuwalab.data.AchievementsRepository? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var argstriteAward by remember { mutableStateOf<Int?>(null) }
    var argstriteHint by remember { mutableStateOf<String?>(null) }
    argstriteAward?.let { amount ->
        ArgstriteAwardDialog(amount = amount, hintMessage = argstriteHint, onDismiss = { argstriteAward = null; argstriteHint = null })
    }
    val planner by plannerRepo.stateFlow.collectAsState(initial = PlannerState())
    val banner = planner.selectedBanner
    val pity = planner.pityFor(banner)
    val guaranteed = planner.guaranteedFor(banner)

    // Local text mirrors of stored numbers so typing feels instant; strict
    // digit filtering enforces a numeric keyboard AND numeric content.
    var pityInput by remember(banner, pity) { mutableStateOf(pity.toString()) }
    var spendInput by remember { mutableStateOf("") }
    var spendUsingCustom by remember { mutableStateOf(false) }

    // Which chart the player is looking at: CUMULATIVE ("at least once by
    // pull N") or NON_CUMULATIVE ("exactly on pull N") - a toggle rather than
    // two separate charts, since they share the same axes and data source.
    var curveType by remember { mutableStateOf(CurveType.CUMULATIVE) }

    val pullLog by plannerRepo.pullLogFlow.collectAsState(initial = emptyList())

    val affordablePulls = GachaMath.pullsFromAstrites(astriteBalance.coerceAtLeast(0))
    val worstCase = GachaMath.worstCasePullsToFeatured(banner, pity, guaranteed)
    val horizon = maxOf(worstCase, affordablePulls, GachaMath.HARD_PITY).coerceAtMost(200)
    val cumulativeCurve = remember(banner, pity, guaranteed, horizon) {
        GachaMath.featuredCurve(banner, pity, guaranteed, horizon)
    }
    val nonCumulativeCurve = remember(banner, pity, guaranteed, horizon) {
        GachaMath.featuredCurveNonCumulative(banner, pity, guaranteed, horizon)
    }
    val curve = cumulativeCurve
    val displayedCurve = if (curveType == CurveType.CUMULATIVE) cumulativeCurve else nonCumulativeCurve
    val chanceNow = if (affordablePulls in 1..curve.size) curve[affordablePulls - 1] else 0.0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Gacha Pull Planner",
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            HelpButton(
                title = "Pull Planner Help",
                body = "1. Pick a banner (Character / Weapon / Standard) at the top.\n" +
                    "2. Enter your current pity (pulls since your last 5\u2605) and, on the " +
                    "Character banner, whether you're guaranteed the featured unit.\n" +
                    "3. Your Astrite Balance is shared with the Dashboard - it shows how " +
                    "many pulls you can currently afford.\n" +
                    "4. The Probability Curve chart shows your odds of the featured 5\u2605: " +
                    "toggle between Cumulative (\"at least once by pull N\") and " +
                    "Non-Cumulative (\"exactly on pull N\").\n" +
                    "5. \"Log a Spend\" deducts Astrites for the pulls you actually make - it " +
                    "can never take you below zero."
            )
        }

        // --- Banner selector ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorderSoft, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GachaMath.Banner.entries.forEach { b ->
                val selected = b == banner
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) VioletGlowDeep.copy(alpha = 0.35f) else Color.Transparent)
                        .clickable { scope.launch { plannerRepo.setSelectedBanner(b) } }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        b.shortLabel,
                        color = if (selected) TextPrimary else TextMuted,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize
                    )
                }
            }
        }

        Text(banner.label, color = TextSecondary, fontSize = MaterialTheme.typography.labelMedium.fontSize)

        // --- Global balance (shared with Dashboard/Profile) ---
        GlassCard(modifier = Modifier.fillMaxWidth(), accent = AmberGlow) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(R.drawable.ic_astrite), contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Astrite Balance", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                    Text(
                        astriteBalance.toString(),
                        color = AmberGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.titleLarge.fontSize
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Affordable Pulls", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                    Text(
                        "$affordablePulls",
                        color = CyanGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.titleLarge.fontSize
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "160 Astrites = 1 pull. This balance is the same tracker total shown on the Dashboard and the Profile header.",
                color = TextMuted,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
        }

        // --- Pity + guarantee inputs ---
        GlassCard(modifier = Modifier.fillMaxWidth(), accent = VioletGlow) {
            Text("Current Pity", fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pityInput,
                onValueChange = { raw ->
                    val filtered = raw.filter { it.isDigit() }.take(2)
                    pityInput = filtered
                    val parsed = filtered.toIntOrNull()
                    if (parsed != null) scope.launch { plannerRepo.setPity(banner, parsed) }
                },
                label = { Text("Pulls since last 5\u2605 (0-79)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    pity + 1 >= GachaMath.HARD_PITY -> "Next pull is HARD PITY - a 5\u2605 is guaranteed."
                    pity + 1 >= GachaMath.SOFT_PITY_START -> "You are inside soft pity - every pull's 5\u2605 rate is climbing fast."
                    else -> "Soft pity begins at pull ${GachaMath.SOFT_PITY_START}; hard pity at ${GachaMath.HARD_PITY}."
                },
                color = if (pity + 1 >= GachaMath.SOFT_PITY_START) CoralGlow else TextSecondary,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )

            if (banner == GachaMath.Banner.LIMITED_CHARACTER) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Guaranteed featured (lost last 50/50)", color = TextPrimary, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                        Text(
                            if (guaranteed) "Your next 5\u2605 WILL be the featured character."
                            else "Your next 5\u2605 is a 50/50 against the standard pool.",
                            color = TextSecondary,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        )
                    }
                    Switch(
                        checked = guaranteed,
                        onCheckedChange = { scope.launch { plannerRepo.setCharacterGuaranteed(it) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = VioletGlow)
                    )
                }
            }
            if (banner == GachaMath.Banner.LIMITED_WEAPON) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Forging Tide has NO 50/50 - the first 5\u2605 you pull is always the featured weapon.",
                    color = EmeraldGlow,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }

        // --- Verdict ---
        GlassCard(modifier = Modifier.fillMaxWidth(), accent = if (chanceNow >= 0.9) EmeraldGlow else if (chanceNow >= 0.5) AmberGlow else CoralGlow) {
            Text("Projection With Your Budget", fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                if (affordablePulls == 0) "You can't afford a pull yet - ${GachaMath.ASTRITES_PER_PULL} Astrites buys your first one."
                else "With $affordablePulls pull(s): ${"%.1f".format(chanceNow * 100)}% chance of the ${if (banner == GachaMath.Banner.STANDARD) "next 5\u2605" else "featured 5\u2605"}.",
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Worst case to force it: $worstCase pull(s) = ${GachaMath.astritesForPulls(worstCase)} Astrites.",
                color = TextSecondary,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
        }

        // --- How far from actually landing the character ---
        PullOutlookCard(
            banner = banner,
            pity = pity,
            guaranteed = guaranteed,
            balance = astriteBalance,
            dailyAverageEarn = dailyAverageEarn
        )

        // --- Projection chart ---
        GlassCard(modifier = Modifier.fillMaxWidth(), accent = CyanGlow) {
            Text("Probability Curve", fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                if (curveType == CurveType.CUMULATIVE)
                    "Cumulative chance of the ${if (banner == GachaMath.Banner.STANDARD) "next 5\u2605" else "featured 5\u2605"} by each pull, from your current pity. The vertical line marks how far your Astrites reach."
                else
                    "Chance of the ${if (banner == GachaMath.Banner.STANDARD) "next 5\u2605" else "featured 5\u2605"} landing on EXACTLY that pull (not before), from your current pity.",
                color = TextSecondary,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
            Spacer(Modifier.height(10.dp))

            // Cumulative / Non-Cumulative toggle.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassSurface)
                    .padding(4.dp)
            ) {
                CurveType.entries.forEach { type ->
                    val isSelected = type == curveType
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) CyanGlow.copy(alpha = 0.28f) else Color.Transparent)
                            .clickable { curveType = type }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            type.label,
                            color = if (isSelected) TextPrimary else TextMuted,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = MaterialTheme.typography.labelMedium.fontSize
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            ProbabilityProjectionChartV2(
                curve = displayedCurve,
                currentPity = pity,
                hardPityAtPull = (GachaMath.HARD_PITY - pity).coerceAtLeast(1),
                affordablePulls = affordablePulls,
                softPityAtPull = (GachaMath.SOFT_PITY_START - 1 - pity).coerceAtLeast(0),
                normalizeYAxis = curveType == CurveType.NON_CUMULATIVE,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        }

        // --- Spend (writes back to the global tracker) ---
        GlassCard(modifier = Modifier.fillMaxWidth(), accent = AmberGlow) {
            Text("Log a Spend", fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            // Quick-pick pull counts, plus an explicit "Custom" chip that
            // focuses the manual numeric field below for any other amount.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 10, affordablePulls.coerceAtLeast(1)).distinct().forEach { count ->
                    SpendQuickChip(
                        label = if (count == affordablePulls && count != 1 && count != 10) "Max ($count)" else "$count",
                        selected = !spendUsingCustom && spendInput == count.toString(),
                        onClick = {
                            spendUsingCustom = false
                            spendInput = count.toString()
                        }
                    )
                }
                SpendQuickChip(
                    label = "Custom",
                    selected = spendUsingCustom,
                    onClick = {
                        spendUsingCustom = true
                        spendInput = ""
                    }
                )
            }
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = spendInput,
                onValueChange = { raw ->
                    spendUsingCustom = true
                    spendInput = raw.filter { c -> c.isDigit() }.take(3)
                },
                label = { Text("Pulls to spend") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            val pulls = spendInput.toIntOrNull() ?: 0
            val spendCost = GachaMath.astritesForPulls(pulls)
            // Validation: never allow a spend that would take the Astrite
            // balance below zero - the button disables and an inline error
            // explains why instead of silently clamping or overspending.
            val wouldOverspend = pulls > 0 && spendCost > astriteBalance
            val canSpend = pulls > 0 && !wouldOverspend

            Button(
                onClick = {
                    if (!canSpend) return@Button
                    scope.launch {
                        plannerRepo.spendAstrites(pulls, banner.shortLabel)
                        achievementsRepo?.recordPullLogged(hadNote = true) // pull logs always carry an auto-generated note
                        wuwaRepo?.let { repo ->
                            val baseReward = argstriteRewardFor("$pulls pull(s) on ${banner.shortLabel}")
                            val reward = repo.addPendingArgstrite(baseReward)
                            val hint = repo.consumeArgstriteHint(true)
                            if (hint != null) {
                                argstriteHint = hint
                                argstriteAward = reward
                            }
                        }
                        spendInput = ""
                        spendUsingCustom = false
                    }
                },
                enabled = canSpend,
                colors = ButtonDefaults.buttonColors(containerColor = AmberGlow, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (pulls > 0) "Spend $spendCost Astrites ($pulls pulls)" else "Spend Astrites")
            }
            Spacer(Modifier.height(4.dp))
            if (wouldOverspend) {
                Text(
                    "Not enough Astrites - you have $astriteBalance but this would cost $spendCost. Your balance can never go negative.",
                    color = CoralGlow,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            } else {
                Text(
                    "Deducts from today's tracker entry - the Dashboard and Profile totals update instantly.",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }

        // --- Convene log (database-style record of every spend) ---
        PullSpendLogSection(
            log = pullLog,
            onDelete = { id -> scope.launch { plannerRepo.deletePullLogEntry(id) } }
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SpendQuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AmberGlow.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) AmberGlow else GlassBorderSoft, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) TextPrimary else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
    }
}

/**
 * Pure-Canvas line chart of the cumulative probability curve: gradient fill
 * under the line, dashed grid at 25/50/75/100%, a shaded soft-pity region and
 * an accent marker at the pull count the player can currently afford.
 */
@Composable
fun ProbabilityProjectionChart(
    curve: List<Double>,
    affordablePulls: Int,
    softPityAtPull: Int,
    modifier: Modifier = Modifier,
    // Non-cumulative values are usually small (a few percent per pull), so
    // scale the y-axis to the curve's own peak instead of a fixed 0-100%
    // range - otherwise the whole chart would look flat.
    normalizeYAxis: Boolean = false
) {
    val labelColor = android.graphics.Color.argb(160, 255, 255, 255)
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (curve.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val padLeft = 40f
        val padBottom = 26f
        val chartW = w - padLeft
        val chartH = h - padBottom
        val n = curve.size
        val axisMax = if (normalizeYAxis) (curve.maxOrNull() ?: 1.0).coerceAtLeast(0.01) else 1.0

        fun xFor(pullIndex: Int): Float = padLeft + chartW * (pullIndex.toFloat() / (n - 1).coerceAtLeast(1))
        fun yFor(p: Double): Float = chartH * (1f - (p / axisMax).toFloat().coerceIn(0f, 1f))

        val textPaint = android.graphics.Paint().apply {
            color = labelColor
            textSize = 22f
            isAntiAlias = true
        }

        // Grid lines at 0/25/50/75/100% of the axis's own max.
        listOf(0.0, 0.25, 0.5, 0.75, 1.0).forEach { p ->
            val y = yFor(p * axisMax)
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(padLeft, y),
                end = Offset(w, y),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
            val labelText = if (normalizeYAxis) "${"%.1f".format(p * axisMax * 100)}%" else "${(p * 100).roundToInt()}%"
            drawContext.canvas.nativeCanvas.drawText(labelText, 0f, y + 8f, textPaint)
        }

        // Soft-pity zone shading (from the pull where soft pity kicks in).
        if (softPityAtPull in 0 until n) {
            val zoneStart = xFor(softPityAtPull)
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFFFF7A6B).copy(alpha = 0.04f), Color(0xFFFF7A6B).copy(alpha = 0.14f)),
                    startX = zoneStart, endX = w
                ),
                topLeft = Offset(zoneStart, 0f),
                size = androidx.compose.ui.geometry.Size(w - zoneStart, chartH)
            )
        }

        // Curve path + gradient fill under it.
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
                listOf(Color(0xFF44E0FF).copy(alpha = 0.35f), Color(0xFF44E0FF).copy(alpha = 0.02f)),
                startY = 0f, endY = chartH
            )
        )
        drawPath(
            path = line,
            color = Color(0xFF44E0FF),
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )

        // Marker: how far the current Astrite budget reaches.
        if (affordablePulls in 1..n) {
            val x = xFor(affordablePulls - 1)
            val y = yFor(curve[affordablePulls - 1])
            drawLine(
                color = Color(0xFFFFC24B),
                start = Offset(x, 0f),
                end = Offset(x, chartH),
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
            )
            drawCircle(color = Color(0xFFFFC24B), radius = 9f, center = Offset(x, y))
            drawCircle(color = Color.White, radius = 4f, center = Offset(x, y))
        }

        // X-axis pull labels: start, mid, end.
        val startLabel = "1"
        val midLabel = "${n / 2}"
        val endLabel = "$n pulls"
        drawContext.canvas.nativeCanvas.drawText(startLabel, padLeft, h - 4f, textPaint)
        drawContext.canvas.nativeCanvas.drawText(midLabel, padLeft + chartW / 2f - 10f, h - 4f, textPaint)
        drawContext.canvas.nativeCanvas.drawText(endLabel, w - textPaint.measureText(endLabel), h - 4f, textPaint)
    }
    // Keep parameters "used" for previews even when the curve is empty.
    LaunchedEffect(curve.size, affordablePulls, softPityAtPull) { }
}