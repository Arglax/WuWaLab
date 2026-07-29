package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One day's Astrite movement. [dateIso] is "YYYY-MM-DD" so entries sort and
 * compare as plain strings.
 *
 * ### Why earned and spent are stored separately
 * A day now tracks BOTH sides of the ledger instead of a single net figure:
 *
 *   [earned] - only ever grows, can never be negative. This is what the
 *              "Astrites Earned" headline and the daily average are built on,
 *              which is precisely why neither of them can ever read negative.
 *   [spent]  - only ever grows, can never be negative.
 *   [amount] - the day's NET (earned - spent). This one CAN be negative, and
 *              that's intentional: a week where you convened more than you
 *              collected genuinely was a net loss, and the tracker should be
 *              honest about it.
 *
 * Older saves that only stored [amount] are migrated on read: a positive net
 * is treated as pure earnings, a negative net as pure spending.
 */
data class AstriteEntry(
    val dateIso: String,
    val amount: Int,
    val source: String = "",
    val earned: Int = amount.coerceAtLeast(0),
    val spent: Int = (-amount).coerceAtLeast(0)
) {
    val date: LocalDate get() = LocalDate.parse(dateIso)
    val net: Int get() = earned - spent

    companion object {
        val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        fun forDate(date: LocalDate, amount: Int, source: String = "") =
            AstriteEntry(date.format(ISO), amount, source)

        /** Builds an entry from explicit earned/spent halves. */
        fun of(dateIso: String, earned: Int, spent: Int, source: String = ""): AstriteEntry {
            val e = earned.coerceAtLeast(0)
            val s = spent.coerceAtLeast(0)
            return AstriteEntry(dateIso = dateIso, amount = e - s, source = source, earned = e, spent = s)
        }
    }
}

enum class ChartPeriod { DAILY, WEEKLY, MONTHLY }

private val Context.astriteDataStore by preferencesDataStore(name = "wuwa_astrites")

/**
 * Everything is manual entry, by design - there's no legal/sanctioned way to
 * read live values out of the Wuthering Waves client, so this just stores
 * whatever the player logs, one row per date.
 */
class AstriteRepository(private val context: Context) {

    private val KEY_ENTRIES = stringPreferencesKey("astrite_entries_json")

    val entriesFlow: Flow<List<AstriteEntry>> = context.astriteDataStore.data.map { prefs ->
        parseEntries(prefs[KEY_ENTRIES] ?: "[]")
    }

    suspend fun getEntriesOnce(): List<AstriteEntry> = entriesFlow.first()

    /** Adds or overwrites the row for [entry.dateIso] - one row per day. */
    suspend fun upsertEntry(entry: AstriteEntry) {
        val current = getEntriesOnce().associateBy { it.dateIso }.toMutableMap()
        current[entry.dateIso] = entry
        saveAll(current.values.sortedBy { it.dateIso })
    }

    suspend fun deleteEntry(dateIso: String) {
        saveAll(getEntriesOnce().filterNot { it.dateIso == dateIso })
    }

    /** Credits [amount] to [dateIso]'s EARNED column, stacking with what's there. */
    suspend fun addEarnToDate(dateIso: String, amount: Int, source: String) {
        val add = amount.coerceAtLeast(0)
        if (add == 0) return
        val existing = getEntriesOnce().firstOrNull { it.dateIso == dateIso }
        upsertEntry(
            AstriteEntry.of(
                dateIso = dateIso,
                earned = (existing?.earned ?: 0) + add,
                spent = existing?.spent ?: 0,
                source = mergeSource(existing?.source, source)
            )
        )
    }

    /** Debits [amount] to [dateIso]'s SPENT column, stacking with what's there. */
    suspend fun addSpendToDate(dateIso: String, amount: Int, source: String) {
        val sub = amount.coerceAtLeast(0)
        if (sub == 0) return
        val existing = getEntriesOnce().firstOrNull { it.dateIso == dateIso }
        upsertEntry(
            AstriteEntry.of(
                dateIso = dateIso,
                earned = existing?.earned ?: 0,
                spent = (existing?.spent ?: 0) + sub,
                source = mergeSource(existing?.source, source)
            )
        )
    }

    /**
     * Legacy convenience kept so existing callers (Lunite check-in, Daily
     * Sign-In, the overlay quick-add) keep working untouched: a positive
     * [amount] is an earning, a negative one is a spend.
     */
    suspend fun addToDate(dateIso: String, amount: Int, source: String) {
        if (amount >= 0) addEarnToDate(dateIso, amount, source)
        else addSpendToDate(dateIso, -amount, source)
    }

    /**
     * Authoritative override for today's EARNED total - used by the "Edit
     * Initial Astrite" control, which resets the day's baseline rather than
     * adding to it. Today's spending is left untouched.
     */
    suspend fun overrideTodayAmount(amount: Int, source: String = "Manual Override") {
        val today = LocalDate.now().format(AstriteEntry.ISO)
        val existing = getEntriesOnce().firstOrNull { it.dateIso == today }
        upsertEntry(
            AstriteEntry.of(
                dateIso = today,
                earned = amount.coerceAtLeast(0),
                spent = existing?.spent ?: 0,
                source = source
            )
        )
    }

    private fun mergeSource(existing: String?, incoming: String): String = when {
        existing.isNullOrBlank() -> incoming
        existing.contains(incoming) -> existing
        else -> "$existing + $incoming"
    }

    private suspend fun saveAll(entries: List<AstriteEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("date", e.dateIso)
                    put("amount", e.net)
                    put("earned", e.earned)
                    put("spent", e.spent)
                    put("source", e.source)
                }
            )
        }
        context.astriteDataStore.edit { it[KEY_ENTRIES] = arr.toString() }
    }

    private fun parseEntries(json: String): List<AstriteEntry> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val amount = o.optInt("amount", 0)
            val hasSplit = o.has("earned") || o.has("spent")
            AstriteEntry.of(
                dateIso = o.getString("date"),
                earned = if (hasSplit) o.optInt("earned", 0) else amount.coerceAtLeast(0),
                spent = if (hasSplit) o.optInt("spent", 0) else (-amount).coerceAtLeast(0),
                source = o.optString("source", "")
            )
        }.sortedBy { it.dateIso }
    } catch (_: Exception) {
        emptyList()
    }
}

/** Aggregation helpers shared by the chart, the stat cards and the Economic Dashboard. */
object AstriteStats {

    data class Bucket(val label: String, val total: Int)

    /** One day of the Economic Dashboard's line graph. */
    data class DayPoint(
        val date: LocalDate,
        val label: String,
        val earned: Int,
        val spent: Int,
        val net: Int,
        val runningBalance: Int
    )

    fun buckets(entries: List<AstriteEntry>, period: ChartPeriod, count: Int = 14): List<Bucket> {
        if (entries.isEmpty()) return emptyList()
        val today = LocalDate.now()
        return when (period) {
            ChartPeriod.DAILY -> (count - 1 downTo 0).map { offset ->
                val day = today.minusDays(offset.toLong())
                val total = entries.firstOrNull { it.dateIso == day.format(AstriteEntry.ISO) }?.net ?: 0
                Bucket(day.dayOfMonth.toString(), total)
            }
            ChartPeriod.WEEKLY -> (6 downTo 0).map { weeksAgo ->
                val weekStart = today.minusWeeks(weeksAgo.toLong()).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
                val weekEnd = weekStart.plusDays(6)
                val total = entries.filter { it.date in weekStart..weekEnd }.sumOf { it.net }
                Bucket("Wk ${weekStart.dayOfMonth}/${weekStart.monthValue}", total)
            }
            ChartPeriod.MONTHLY -> (5 downTo 0).map { monthsAgo ->
                val month = today.minusMonths(monthsAgo.toLong())
                val total = entries.filter { it.date.year == month.year && it.date.monthValue == month.monthValue }.sumOf { it.net }
                Bucket(month.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, total)
            }
        }
    }

    // --- Net figures: these CAN be negative, and should be ------------------

    fun totalThisWeek(entries: List<AstriteEntry>): Int {
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return entries.filter { it.date >= weekStart && it.date <= today }.sumOf { it.net }
    }

    fun totalThisMonth(entries: List<AstriteEntry>): Int {
        val today = LocalDate.now()
        return entries.filter { it.date.year == today.year && it.date.monthValue == today.monthValue }.sumOf { it.net }
    }

    // --- Earning figures: these can NEVER be negative -----------------------

    fun totalEarned(entries: List<AstriteEntry>): Int = entries.sumOf { it.earned }.coerceAtLeast(0)

    fun totalSpent(entries: List<AstriteEntry>): Int = entries.sumOf { it.spent }.coerceAtLeast(0)

    fun earnedThisWeek(entries: List<AstriteEntry>): Int {
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return entries.filter { it.date >= weekStart && it.date <= today }.sumOf { it.earned }
    }

    fun earnedThisMonth(entries: List<AstriteEntry>): Int {
        val today = LocalDate.now()
        return entries.filter { it.date.year == today.year && it.date.monthValue == today.monthValue }.sumOf { it.earned }
    }

    fun spentThisWeek(entries: List<AstriteEntry>): Int {
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return entries.filter { it.date >= weekStart && it.date <= today }.sumOf { it.spent }
    }

    fun spentThisMonth(entries: List<AstriteEntry>): Int {
        val today = LocalDate.now()
        return entries.filter { it.date.year == today.year && it.date.monthValue == today.monthValue }.sumOf { it.spent }
    }

    /**
     * The spendable balance: everything earned minus everything spent, floored
     * at zero. Every screen that shows "your Astrites" reads this, which is
     * what keeps the Dashboard, the Profile header, the Pull Planner and the
     * Economic Dashboard showing the same number at all times.
     */
    fun balance(entries: List<AstriteEntry>): Int =
        (totalEarned(entries) - totalSpent(entries)).coerceAtLeast(0)

    /** Kept under its original name - callers everywhere mean "spendable balance". */
    fun totalGathered(entries: List<AstriteEntry>): Int = balance(entries)

    /**
     * Average Astrites EARNED per day over the window. Built from earnings
     * only, so a heavy convene session can never drag your daily average into
     * the negative - spending is reported separately.
     */
    fun dailyAverage(entries: List<AstriteEntry>, days: Int = 30): Double {
        if (entries.isEmpty() || days <= 0) return 0.0
        val cutoff = LocalDate.now().minusDays(days.toLong())
        val recent = entries.filter { it.date >= cutoff }
        if (recent.isEmpty()) return 0.0
        return (recent.sumOf { it.earned }.toDouble() / days).coerceAtLeast(0.0)
    }

    /** Average Astrites spent per day over the window - also never negative. */
    fun dailySpendAverage(entries: List<AstriteEntry>, days: Int = 30): Double {
        if (entries.isEmpty() || days <= 0) return 0.0
        val cutoff = LocalDate.now().minusDays(days.toLong())
        val recent = entries.filter { it.date >= cutoff }
        if (recent.isEmpty()) return 0.0
        return (recent.sumOf { it.spent }.toDouble() / days).coerceAtLeast(0.0)
    }

    /**
     * Day-by-day series for the Economic Dashboard's line graph, ending today.
     * [DayPoint.runningBalance] is the balance as it stood at the END of each
     * day, so the line matches the number in the header exactly on its last point.
     */
    fun dailySeries(entries: List<AstriteEntry>, days: Int = 30): List<DayPoint> {
        val today = LocalDate.now()
        val start = today.minusDays((days - 1).coerceAtLeast(0).toLong())
        val before = entries.filter { it.date < start }
        var running = (before.sumOf { it.earned } - before.sumOf { it.spent }).coerceAtLeast(0)
        return (0 until days).map { offset ->
            val day = start.plusDays(offset.toLong())
            val row = entries.firstOrNull { it.dateIso == day.format(AstriteEntry.ISO) }
            val earned = row?.earned ?: 0
            val spent = row?.spent ?: 0
            running = (running + earned - spent).coerceAtLeast(0)
            DayPoint(
                date = day,
                label = "${day.dayOfMonth}/${day.monthValue}",
                earned = earned,
                spent = spent,
                net = earned - spent,
                runningBalance = running
            )
        }
    }
}