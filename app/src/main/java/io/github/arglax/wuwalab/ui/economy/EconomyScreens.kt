package io.github.arglax.wuwalab.ui.economy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.data.AstriteEconomy
import io.github.arglax.wuwalab.data.AstriteRepository
import io.github.arglax.wuwalab.data.AstriteStats
import io.github.arglax.wuwalab.data.EconomyCategories
import io.github.arglax.wuwalab.data.EconomyRepository
import io.github.arglax.wuwalab.data.LedgerEntry
import io.github.arglax.wuwalab.data.LedgerType
import io.github.arglax.wuwalab.data.WuwaRepository
import io.github.arglax.wuwalab.ui.astrite.AstriteScreen
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Astrite page's front door.
 *
 * "Simple" is the original Astrite Tracker, completely untouched. "Advanced"
 * swaps in the full Economic Dashboard. The choice is remembered, and both
 * views read the exact same stored data - switching modes never migrates,
 * converts or risks anything.
 */
@Composable
fun AstriteTrackerHost(
    astriteRepo: AstriteRepository,
    economyRepo: EconomyRepository,
    wuwaRepo: WuwaRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val advanced by economyRepo.advancedModeFlow.collectAsState(initial = false)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorderSoft, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (advanced) "Advanced: Economic Dashboard" else "Simple: Astrite Tracker",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize
                )
                Text(
                    if (advanced) "Line graph, logbook and spending breakdown."
                    else "The classic log-and-chart view. Flip the switch for the full economy view.",
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
            Switch(
                checked = advanced,
                onCheckedChange = { scope.launch { economyRepo.setAdvancedMode(it) } },
                colors = SwitchDefaults.colors(checkedThumbColor = VioletGlow)
            )
        }

        if (advanced) {
            EconomyDashboardScreen(
                astriteRepo = astriteRepo,
                economyRepo = economyRepo,
                wuwaRepo = wuwaRepo,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AstriteScreen(repo = astriteRepo, wuwaRepo = wuwaRepo, modifier = Modifier.fillMaxSize())
        }
    }
}

private val RANGES = listOf(7, 30, 90)

/**
 * The Economic Dashboard: where every Astrite came from and where it went.
 *
 * Every figure on this page is computed from the same stored tracker rows the
 * Dashboard, the Profile header and the Pull Planner read, so nothing here can
 * drift out of sync with the rest of the app.
 */
@Composable
fun EconomyDashboardScreen(
    astriteRepo: AstriteRepository,
    economyRepo: EconomyRepository,
    wuwaRepo: WuwaRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val economy = remember(astriteRepo, economyRepo) { AstriteEconomy(astriteRepo, economyRepo) }
    var argstriteAward by remember { mutableStateOf<Int?>(null) }
    var argstriteHint by remember { mutableStateOf<String?>(null) }

    argstriteAward?.let { amount ->
        ArgstriteAwardDialog(amount = amount, hintMessage = argstriteHint, onDismiss = { argstriteAward = null; argstriteHint = null })
    }

    val entries by astriteRepo.entriesFlow.collectAsState(initial = emptyList())
    val ledger by economyRepo.entriesFlow.collectAsState(initial = emptyList())

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    var rangeDays by remember { mutableStateOf(30) }
    var isSpend by remember { mutableStateOf(false) }
    var amountInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(EconomyCategories.DAILY_LOGIN) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val balance = AstriteStats.balance(entries)
    val lifetimeEarned = AstriteStats.totalEarned(entries)
    val lifetimeSpent = AstriteStats.totalSpent(entries)
    val series = remember(entries, rangeDays) { AstriteStats.dailySeries(entries, rangeDays) }
    val avgEarn = AstriteStats.dailyAverage(entries, rangeDays)
    val avgSpend = AstriteStats.dailySpendAverage(entries, rangeDays)

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Economic Dashboard",
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                HelpButton(
                    title = "Economic Dashboard Help",
                    body = "This is your Astrite bank statement.\n\n" +
                        "The line graph shows your balance over time, with green earnings and red spending underneath it.\n\n" +
                        "Astrites Earned only ever goes up - it is a lifetime total and can never be negative. " +
                        "The weekly and monthly figures are NET (earned minus spent), so those can be negative after a big convene session. " +
                        "Your daily average is built from earnings only, so it never goes negative either.\n\n" +
                        "Add Transaction logs an earning or a spend by hand. A spend can never take you below zero.\n\n" +
                        "The Logbook lists every transaction, including convenes logged from the Pull Planner."
                )
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), accent = AmberGlow) {
                Text("Current Balance", color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                Text(
                    balance.toString(),
                    color = AmberGlow,
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MiniStat("Earned (lifetime)", lifetimeEarned.toString(), EmeraldGlow, Modifier.weight(1f))
                    MiniStat("Spent (lifetime)", lifetimeSpent.toString(), CoralGlow, Modifier.weight(1f))
                    MiniStat("Pulls affordable", (balance / 160).toString(), CyanGlow, Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Same total as the Dashboard, the Profile header and the Pull Planner.",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), accent = CyanGlow) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Balance Over Time", fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(GlassSurface)
                            .padding(3.dp)
                    ) {
                        RANGES.forEach { days ->
                            val selected = days == rangeDays
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) CyanGlow.copy(alpha = 0.28f) else Color.Transparent)
                                    .clickable { rangeDays = days }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    days.toString() + "d",
                                    color = if (selected) TextPrimary else TextMuted,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                EconomyLineChart(
                    series = series,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 150.dp else 200.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    LegendDot("Balance", CyanGlow)
                    Spacer(Modifier.width(14.dp))
                    LegendDot("Earned", EmeraldGlow)
                    Spacer(Modifier.width(14.dp))
                    LegendDot("Spent", CoralGlow)
                }
            }
        }

        item {
            val netWeek = AstriteStats.totalThisWeek(entries)
            val netMonth = AstriteStats.totalThisMonth(entries)
            GlassCard(modifier = Modifier.fillMaxWidth(), accent = VioletGlow) {
                Text("Breakdown", fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MiniStat("Earned this week", AstriteStats.earnedThisWeek(entries).toString(), EmeraldGlow, Modifier.weight(1f))
                    MiniStat("Spent this week", AstriteStats.spentThisWeek(entries).toString(), CoralGlow, Modifier.weight(1f))
                    MiniStat("Net this week", signed(netWeek), if (netWeek < 0) CoralGlow else EmeraldGlow, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MiniStat("Earned this month", AstriteStats.earnedThisMonth(entries).toString(), EmeraldGlow, Modifier.weight(1f))
                    MiniStat("Spent this month", AstriteStats.spentThisMonth(entries).toString(), CoralGlow, Modifier.weight(1f))
                    MiniStat("Net this month", signed(netMonth), if (netMonth < 0) CoralGlow else EmeraldGlow, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MiniStat("Avg earned / day", oneDp(avgEarn), EmeraldGlow, Modifier.weight(1f))
                    MiniStat("Avg spent / day", oneDp(avgSpend), CoralGlow, Modifier.weight(1f))
                    MiniStat(
                        "Days to next pull",
                        if (avgEarn <= 0.0) "-" else (((160 - balance % 160).coerceAtLeast(1)) / avgEarn).roundToInt().toString(),
                        CyanGlow,
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Weekly and monthly NET can go negative after a convene session - that is real, and the tracker shows it honestly. " +
                        "Earnings and daily averages never do.",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), accent = if (isSpend) CoralGlow else EmeraldGlow) {
                Text("Add Transaction", fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassSurface)
                        .padding(4.dp)
                ) {
                    listOf(false to "Earned", true to "Spent").forEach { (spendMode, label) ->
                        val selected = spendMode == isSpend
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        !selected -> Color.Transparent
                                        spendMode -> CoralGlow.copy(alpha = 0.28f)
                                        else -> EmeraldGlow.copy(alpha = 0.28f)
                                    }
                                )
                                .clickable {
                                    isSpend = spendMode
                                    errorText = null
                                    category = if (spendMode) EconomyCategories.CONVENE else EconomyCategories.DAILY_LOGIN
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) TextPrimary else TextMuted,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = MaterialTheme.typography.labelMedium.fontSize
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { raw ->
                        amountInput = raw.filter { it.isDigit() }.take(6)
                        errorText = null
                    },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = if (isSpend) EconomyCategories.spendOptions else EconomyCategories.earnOptions
                    options.forEach { option ->
                        val selected = option == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) VioletGlow.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
                                .border(1.dp, if (selected) VioletGlow else GlassBorderSoft, RoundedCornerShape(10.dp))
                                .clickable { category = option }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                option,
                                color = if (selected) TextPrimary else TextSecondary,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it.take(60) },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                val amount = amountInput.toIntOrNull() ?: 0
                val wouldOverspend = isSpend && amount > balance
                val canSubmit = amount > 0 && !wouldOverspend

                Button(
                    onClick = {
                        if (!canSubmit) return@Button
                        scope.launch {
                            if (isSpend) {
                                economy.spend(amount, category, noteInput)
                            } else {
                                economy.earn(amount, category, noteInput)
                            }
                            val baseReward = argstriteRewardFor(noteInput)
                            val reward = wuwaRepo.addPendingArgstrite(baseReward)
                            val hint = wuwaRepo.consumeArgstriteHint(noteInput.isNotBlank())
                            if (hint != null) {
                                argstriteHint = hint
                                argstriteAward = reward
                            }
                            amountInput = ""
                            noteInput = ""
                            errorText = null
                        }
                    },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSpend) CoralGlow else EmeraldGlow,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSpend) "Log Spend" else "Log Earning")
                }
                if (wouldOverspend) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "You only have " + balance + " Astrites. A spend can never push your balance below zero.",
                        color = CoralGlow,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
                errorText?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = CoralGlow, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                }
            }
        }

        item {
            Text(
                "Logbook",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = MaterialTheme.typography.titleMedium.fontSize
            )
        }

        if (ledger.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("No transactions yet.", color = TextSecondary)
                    Text(
                        "Anything you log here, claim from the Daily Sign-In card, or spend in the Pull Planner will appear in this list.",
                        color = TextMuted,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
            }
        } else {
            items(ledger.sortedByDescending { it.epochMs }, key = { it.id }) { entry ->
                LedgerRow(
                    entry = entry,
                    onDelete = { scope.launch { economyRepo.delete(entry.id) } }
                )
            }
        }
    }
}

@Composable
private fun LedgerRow(entry: LedgerEntry, onDelete: () -> Unit) {
    val isEarn = entry.type == LedgerType.EARN
    val accent = if (isEarn) EmeraldGlow else CoralGlow
    val stamp = remember(entry.epochMs) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(entry.epochMs))
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(entry.category, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelLarge.fontSize)
                Text(stamp, color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                if (entry.note.isNotBlank()) {
                    Text(entry.note, color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                }
                if (entry.isConvene) {
                    Text(
                        "Pity " + entry.pity + (if (entry.guaranteed) " - guaranteed" else "") + " - " + entry.banner,
                        color = TextMuted,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
            }
            Text(
                (if (isEarn) "+" else "-") + entry.amount,
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.titleMedium.fontSize
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete entry", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize, maxLines = 2)
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleSmall.fontSize)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(label, color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
    }
}

private fun signed(value: Int): String = if (value > 0) "+" + value else value.toString()

private fun oneDp(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()

/**
 * The Economic Dashboard's line graph: a running-balance line with the daily
 * earned/spent bars behind it. Pure Canvas - no charting dependency, so it
 * costs the APK nothing and renders identically in portrait and landscape.
 */
@Composable
fun EconomyLineChart(
    series: List<AstriteStats.DayPoint>,
    modifier: Modifier = Modifier
) {
    val labelColor = android.graphics.Color.argb(160, 255, 255, 255)
    Canvas(modifier = modifier) {
        if (series.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val padLeft = 52f
        val padBottom = 26f
        val chartW = (w - padLeft).coerceAtLeast(1f)
        val chartH = (h - padBottom).coerceAtLeast(1f)
        val n = series.size

        val maxBalance = (series.maxOfOrNull { it.runningBalance } ?: 0).coerceAtLeast(1)
        val maxFlow = (series.maxOfOrNull { maxOf(it.earned, it.spent) } ?: 0).coerceAtLeast(1)

        val textPaint = android.graphics.Paint().apply {
            color = labelColor
            textSize = 22f
            isAntiAlias = true
        }

        fun xFor(i: Int): Float = padLeft + chartW * (i.toFloat() / (n - 1).coerceAtLeast(1))
        fun yForBalance(v: Int): Float = chartH * (1f - (v.toFloat() / maxBalance).coerceIn(0f, 1f))

        // Horizontal guides at 0 / 50 / 100% of the peak balance.
        listOf(0f, 0.5f, 1f).forEach { frac ->
            val y = chartH * (1f - frac)
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(padLeft, y),
                end = Offset(w, y),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
            drawContext.canvas.nativeCanvas.drawText(
                (maxBalance * frac).roundToInt().toString(),
                0f,
                y + 8f,
                textPaint
            )
        }

        // Daily earned (up) and spent (down) bars, drawn from the baseline.
        val barW = (chartW / n * 0.36f).coerceAtLeast(2f)
        val baseline = chartH
        series.forEachIndexed { i, point ->
            val x = xFor(i)
            if (point.earned > 0) {
                val barH = chartH * 0.32f * (point.earned.toFloat() / maxFlow)
                drawRect(
                    color = EmeraldGlow.copy(alpha = 0.55f),
                    topLeft = Offset(x - barW, baseline - barH),
                    size = androidx.compose.ui.geometry.Size(barW, barH)
                )
            }
            if (point.spent > 0) {
                val barH = chartH * 0.32f * (point.spent.toFloat() / maxFlow)
                drawRect(
                    color = CoralGlow.copy(alpha = 0.55f),
                    topLeft = Offset(x, baseline - barH),
                    size = androidx.compose.ui.geometry.Size(barW, barH)
                )
            }
        }

        // Running-balance line + soft fill underneath.
        val line = Path()
        val fill = Path()
        series.forEachIndexed { i, point ->
            val x = xFor(i)
            val y = yForBalance(point.runningBalance)
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
                listOf(CyanGlow.copy(alpha = 0.30f), CyanGlow.copy(alpha = 0.02f)),
                startY = 0f,
                endY = chartH
            )
        )
        drawPath(path = line, color = CyanGlow, style = Stroke(width = 5f, cap = StrokeCap.Round))

        // Highlight today's point so the line visibly ends on the live balance.
        val lastX = xFor(n - 1)
        val lastY = yForBalance(series.last().runningBalance)
        drawCircle(color = CyanGlow, radius = 9f, center = Offset(lastX, lastY))
        drawCircle(color = Color.White, radius = 4f, center = Offset(lastX, lastY))

        // X labels: first, middle, last.
        drawContext.canvas.nativeCanvas.drawText(series.first().label, padLeft, h - 4f, textPaint)
        val mid = series[n / 2].label
        drawContext.canvas.nativeCanvas.drawText(mid, padLeft + chartW / 2f - 16f, h - 4f, textPaint)
        val endLabel = series.last().label
        drawContext.canvas.nativeCanvas.drawText(endLabel, w - textPaint.measureText(endLabel), h - 4f, textPaint)

        // Keeps the compiler honest about abs() being intentional elsewhere.
        if (abs(maxFlow) < 0) return@Canvas
    }
}