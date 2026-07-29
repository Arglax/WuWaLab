package io.github.arglax.wuwalab.ui.astrite

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.data.AstriteEntry
import io.github.arglax.wuwalab.data.AstriteRepository
import io.github.arglax.wuwalab.data.AstriteStats
import io.github.arglax.wuwalab.data.ChartPeriod
import io.github.arglax.wuwalab.data.LuniteRepository
import io.github.arglax.wuwalab.data.WuwaRepository
import io.github.arglax.wuwalab.ui.components.AddAstriteEntryDialog
import io.github.arglax.wuwalab.ui.components.ArgstriteAwardDialog
import io.github.arglax.wuwalab.ui.components.AstriteBarChart
import io.github.arglax.wuwalab.ui.components.AstriteCheckInDialog
import io.github.arglax.wuwalab.ui.components.GlassCard
import io.github.arglax.wuwalab.ui.components.HelpButton
import io.github.arglax.wuwalab.ui.components.argstriteRewardFor
import io.github.arglax.wuwalab.ui.theme.*
import io.github.arglax.wuwalab.util.rememberTapFeedback
import io.github.arglax.wuwalab.util.rememberConfirmFeedback
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun AstriteScreen(repo: AstriteRepository, wuwaRepo: WuwaRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val luniteRepo = remember { LuniteRepository(context, repo) }
    val confirmFeedback = rememberConfirmFeedback()
    var argstriteAward by remember { mutableStateOf<Int?>(null) }
    var argstriteHint by remember { mutableStateOf<String?>(null) }

    argstriteAward?.let { amount ->
        ArgstriteAwardDialog(amount = amount, hintMessage = argstriteHint, onDismiss = { argstriteAward = null; argstriteHint = null })
    }

    var entries by remember { mutableStateOf<List<AstriteEntry>>(emptyList()) }
    var period by remember { mutableStateOf(ChartPeriod.DAILY) }
    var showDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<AstriteEntry?>(null) }

    var luniteEnabled by remember { mutableStateOf(false) }
    var checkedInToday by remember { mutableStateOf(false) }
    var showCheckInPopup by remember { mutableStateOf(false) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { entries = repo.getEntriesOnce() }
    LaunchedEffect(Unit) {
        luniteEnabled = luniteRepo.isEnabledOnce()
        checkedInToday = luniteRepo.hasCheckedInToday()
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            nowTick = System.currentTimeMillis()
        }
    }

    fun reload() {
        scope.launch {
            entries = repo.getEntriesOnce()
            checkedInToday = luniteRepo.hasCheckedInToday()
        }
    }

    if (showCheckInPopup) {
        AstriteCheckInDialog(onDismiss = { showCheckInPopup = false })
    }

    if (showDialog) {
        AddAstriteEntryDialog(
            initial = editingEntry,
            onDismiss = { showDialog = false; editingEntry = null },
            onSave = { entry, overwrite ->
                scope.launch {
                    if (overwrite) {
                        repo.upsertEntry(entry)
                    } else {
                        repo.addToDate(entry.dateIso, entry.amount, entry.source.ifBlank { "Manual Entry" })
                    }
                    val baseReward = argstriteRewardFor(entry.source)
                    val reward = wuwaRepo.addPendingArgstrite(baseReward)
                    // The award popup only ever appears for the one-time
                    // "hidden method" reveal - every log after that keeps
                    // earning Argstrites silently into the pending balance,
                    // with no interruption.
                    val hint = wuwaRepo.consumeArgstriteHint(entry.source.isNotBlank())
                    if (hint != null) {
                        argstriteHint = hint
                        argstriteAward = reward
                    }
                    showDialog = false
                    editingEntry = null
                    reload()
                }
            },
            onDelete = editingEntry?.let { toDelete ->
                {
                    scope.launch {
                        repo.deleteEntry(toDelete.dateIso)
                        showDialog = false
                        editingEntry = null
                        reload()
                    }
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_astrite),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Astrite Tracker",
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                        color = TextPrimary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HelpButton(
                        title = "Astrite Tracker Help",
                        body = "Log a day's Astrite gains with \"+ Log Entry\" - pick any date " +
                            "(past to backfill, future to pre-plan), enter the amount, and choose " +
                            "whether it overwrites or adds to whatever's already logged for that day.\n\n" +
                            "Tap any entry in the list below to edit or delete it.\n\n" +
                            "The chart above switches between Daily / Weekly / Monthly views, and " +
                            "the stat chips show your totals for this week, this month, and your " +
                            "30-day daily average.\n\n" +
                            "If you have the Lunite Pass enabled in Settings, a daily check-in card " +
                            "appears here too."
                    )
                    Spacer(Modifier.width(8.dp))
                    val logEntryFeedback = rememberTapFeedback()
                    Button(
                        onClick = { logEntryFeedback(); editingEntry = null; showDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = VioletGlowDeep, contentColor = androidx.compose.ui.graphics.Color.White)
                    ) { Text("+ Log Entry") }
                }
            }
        }

        if (luniteEnabled) {
            item {
                LuniteCheckInCard(
                    luniteRepo = luniteRepo,
                    checkedInToday = checkedInToday,
                    nowMs = nowTick,
                    onCheckIn = {
                        confirmFeedback()
                        scope.launch {
                            luniteRepo.checkIn()
                            checkedInToday = true
                            showCheckInPopup = true
                            reload()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatChip(
                    label = "This week",
                    value = AstriteStats.totalThisWeek(entries).toString(),
                    accent = CyanGlow,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "This month",
                    value = AstriteStats.totalThisMonth(entries).toString(),
                    accent = VioletGlow,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "Daily avg (30d)",
                    value = String.format(Locale.US, "%.0f", AstriteStats.dailyAverage(entries)),
                    accent = EmeraldGlow,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                PeriodToggle(selected = period, onSelect = { period = it })
                Spacer(Modifier.height(12.dp))
                val accent = when (period) {
                    ChartPeriod.DAILY -> CyanGlow
                    ChartPeriod.WEEKLY -> VioletGlow
                    ChartPeriod.MONTHLY -> EmeraldGlow
                }
                AstriteBarChart(
                    buckets = AstriteStats.buckets(entries, period),
                    accent = accent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Text(
                "Entries",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    "No entries logged yet. Tap \"+ Log entry\" to add today's Astrites, or backfill a past date.",
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        } else {
            items(entries.sortedByDescending { it.dateIso }, key = { it.dateIso }) { entry ->
                EntryRow(
                    entry = entry,
                    onClick = { editingEntry = entry; showDialog = true },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, accent = accent, contentPadding = PaddingValues(12.dp)) {
        Text(value, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = MaterialTheme.typography.titleMedium.fontSize)
        Text(label, color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize)
    }
}

@Composable
private fun PeriodToggle(selected: ChartPeriod, onSelect: (ChartPeriod) -> Unit) {
    val feedback = rememberTapFeedback()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassSurface)
            .padding(4.dp)
    ) {
        ChartPeriod.entries.forEach { p ->
            val isSelected = p == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) CyanGlowDeep.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { feedback(); onSelect(p) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    p.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = if (isSelected) TextPrimary else TextMuted,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = MaterialTheme.typography.labelMedium.fontSize
                )
            }
        }
    }
}

@Composable
private fun EntryRow(entry: AstriteEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_astrite),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.date.format(DateTimeFormatter.ofPattern("EEE, MMM d yyyy")),
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
                if (entry.source.isNotBlank()) {
                    Text(entry.source, color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                }
            }
            Text(
                "+${entry.amount}",
                color = AmberGlow,
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.titleMedium.fontSize
            )
        }
    }
}

/**
 * Only shown when Lunite Pass is enabled in Settings. Lets the player
 * confirm they've logged in and claimed their daily 90 Astrites - tapping
 * it both marks today as checked-in (silencing the 3 reminder alarms) and
 * adds +90 to today's Astrite tracker entry.
 */
@Composable
private fun LuniteCheckInCard(
    luniteRepo: LuniteRepository,
    checkedInToday: Boolean,
    nowMs: Long,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resetInMs = (luniteRepo.nextResetEpochMs(nowMs) - nowMs).coerceAtLeast(0)
    val hours = TimeUnit.MILLISECONDS.toHours(resetInMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(resetInMs) % 60

    GlassCard(
        modifier = modifier,
        accent = if (checkedInToday) EmeraldGlow else AmberGlow
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_astrite),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Lunite Pass Daily Login",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
                Text(
                    if (checkedInToday) "Checked in - today's 90 Astrites logged."
                    else "Reset in ${hours}h ${minutes}m - don't forget to log in!",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onCheckIn,
            enabled = !checkedInToday,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (checkedInToday) EmeraldGlowDeep else AmberGlow,
                contentColor = androidx.compose.ui.graphics.Color.Black
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (checkedInToday) "Checked in for today" else "I've logged in today")
        }
    }
}