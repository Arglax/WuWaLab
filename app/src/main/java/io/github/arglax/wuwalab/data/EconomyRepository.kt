package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/**
 * ---------------------------------------------------------------------------
 * Economic Dashboard - transaction ledger ("logbook")
 * ---------------------------------------------------------------------------
 *
 * The Astrite Tracker ([AstriteRepository]) stores ONE aggregated row per day
 * (how much was earned that day, how much was spent that day). That's perfect
 * for the Dashboard chart and the balance in the Profile header, but it can't
 * answer "where did those 1,600 Astrites actually go?".
 *
 * This ledger is the answer: every individual earn/spend transaction, with a
 * timestamp, a category, an optional note and - for convene spends logged from
 * the Pull Planner - the banner, the pull count and the pity/guarantee state at
 * the moment of the spend. It is a strictly ADDITIVE detail layer:
 *
 *   [AstriteRepository]  = the authoritative balance (single source of truth)
 *   [EconomyRepository]  = the human-readable story behind that balance
 *
 * Both are always written through [AstriteEconomy] in the same suspending call,
 * so the Economic Dashboard, the Astrite Tracker, the Dashboard cards, the
 * Pull Planner and the Profile header can never disagree with each other.
 */
enum class LedgerType { EARN, SPEND }

data class LedgerEntry(
    val id: String,
    val epochMs: Long,
    val dateIso: String,
    val type: LedgerType,
    /** Always a positive magnitude - the [type] carries the direction. */
    val amount: Int,
    val category: String,
    val note: String = "",
    // --- Convene-specific columns (only populated for Pull Planner spends) ---
    val banner: String = "",
    val pulls: Int = 0,
    val pity: Int = -1,
    val guaranteed: Boolean = false
) {
    val signedAmount: Int get() = if (type == LedgerType.EARN) amount else -amount
    val isConvene: Boolean get() = pulls > 0
    val date: LocalDate get() = LocalDate.parse(dateIso)
}

/** The category chips the logbook offers. Free text is still allowed. */
object EconomyCategories {
    const val CONVENE = "Convene"
    const val DAILY_LOGIN = "Daily Login"
    const val LUNITE_PASS = "Lunite Pass"
    const val EVENT = "Event"
    const val QUESTS = "Quests"
    const val EXPLORATION = "Exploration"
    const val MAIL = "Mail / Compensation"
    const val SHOP = "Shop"
    const val MANUAL = "Manual Entry"
    const val OTHER = "Other"

    val earnOptions = listOf(DAILY_LOGIN, LUNITE_PASS, EVENT, QUESTS, EXPLORATION, MAIL, MANUAL, OTHER)
    val spendOptions = listOf(CONVENE, SHOP, MANUAL, OTHER)
}

private val Context.economyDataStore by preferencesDataStore(name = "wuwa_economy")

class EconomyRepository(private val context: Context) {

    private val KEY_LEDGER = stringPreferencesKey("economy_ledger_json")
    private val KEY_ADVANCED = booleanPreferencesKey("economy_advanced_mode")

    /**
     * false = the classic, lightweight Astrite Tracker (unchanged).
     * true  = the full Economic Dashboard (line graph + logbook + breakdowns).
     *
     * Off by default on purpose: nobody gets a more complicated app than the
     * one they installed unless they ask for it.
     */
    val advancedModeFlow: Flow<Boolean> = context.economyDataStore.data.map { it[KEY_ADVANCED] ?: false }

    suspend fun setAdvancedMode(enabled: Boolean) {
        context.economyDataStore.edit { it[KEY_ADVANCED] = enabled }
    }

    val entriesFlow: Flow<List<LedgerEntry>> = context.economyDataStore.data.map { prefs ->
        parse(prefs[KEY_LEDGER] ?: "[]")
    }

    suspend fun getEntriesOnce(): List<LedgerEntry> = entriesFlow.first()

    suspend fun append(entry: LedgerEntry) {
        val all = getEntriesOnce() + entry
        // Keep the newest 1,000 transactions. That's years of daily play, and
        // it stops a runaway log from bloating the preferences file.
        save(all.sortedBy { it.epochMs }.takeLast(1000))
    }

    suspend fun delete(id: String) {
        save(getEntriesOnce().filterNot { it.id == id })
    }

    suspend fun clearAll() {
        save(emptyList())
    }

    private suspend fun save(entries: List<LedgerEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("epochMs", e.epochMs)
                    put("date", e.dateIso)
                    put("type", e.type.name)
                    put("amount", e.amount)
                    put("category", e.category)
                    put("note", e.note)
                    put("banner", e.banner)
                    put("pulls", e.pulls)
                    put("pity", e.pity)
                    put("guaranteed", e.guaranteed)
                }
            )
        }
        context.economyDataStore.edit { it[KEY_LEDGER] = arr.toString() }
    }

    private fun parse(json: String): List<LedgerEntry> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LedgerEntry(
                id = o.optString("id", UUID.randomUUID().toString()),
                epochMs = o.optLong("epochMs", System.currentTimeMillis()),
                dateIso = o.optString("date", LocalDate.now().toString()),
                type = if (o.optString("type", "EARN") == "SPEND") LedgerType.SPEND else LedgerType.EARN,
                amount = o.optInt("amount", 0).coerceAtLeast(0),
                category = o.optString("category", EconomyCategories.OTHER),
                note = o.optString("note", ""),
                banner = o.optString("banner", ""),
                pulls = o.optInt("pulls", 0),
                pity = o.optInt("pity", -1),
                guaranteed = o.optBoolean("guaranteed", false)
            )
        }.sortedBy { it.epochMs }
    } catch (_: Exception) {
        emptyList()
    }
}

/** What a spend attempt did. Never throws - the UI renders the reason instead. */
sealed class SpendOutcome {
    data class Success(val spent: Int, val newBalance: Int) : SpendOutcome()
    data class InsufficientFunds(val requested: Int, val available: Int) : SpendOutcome()
    data object InvalidAmount : SpendOutcome()
}

/**
 * The ONE door every Astrite movement goes through.
 *
 * Writing an earn or a spend anywhere in the app should call this rather than
 * poking [AstriteRepository] directly, because this is what keeps the daily
 * aggregate and the transaction logbook in lockstep. Spends are validated here
 * against the live balance, so no code path - planner, shop, overlay, manual
 * entry - can ever push the player into the negative.
 */
class AstriteEconomy(
    private val astriteRepo: AstriteRepository,
    private val economyRepo: EconomyRepository
) {

    suspend fun currentBalance(): Int = AstriteStats.balance(astriteRepo.getEntriesOnce())

    /** Adds Astrites. Negative/zero amounts are ignored rather than silently flipping into a spend. */
    suspend fun earn(
        amount: Int,
        category: String,
        note: String = "",
        dateIso: String = LocalDate.now().format(AstriteEntry.ISO)
    ): Int {
        val safe = amount.coerceAtLeast(0)
        if (safe == 0) return 0
        astriteRepo.addEarnToDate(dateIso, safe, label(category, note))
        economyRepo.append(
            LedgerEntry(
                id = UUID.randomUUID().toString(),
                epochMs = System.currentTimeMillis(),
                dateIso = dateIso,
                type = LedgerType.EARN,
                amount = safe,
                category = category,
                note = note
            )
        )
        return safe
    }

    /**
     * Removes Astrites, but only if the player actually has them. The lifetime
     * "Astrites Earned" figure is never touched by this - only the spendable
     * balance moves - which is why earnings can never read as negative.
     */
    suspend fun spend(
        amount: Int,
        category: String,
        note: String = "",
        banner: String = "",
        pulls: Int = 0,
        pity: Int = -1,
        guaranteed: Boolean = false,
        dateIso: String = LocalDate.now().format(AstriteEntry.ISO)
    ): SpendOutcome {
        if (amount <= 0) return SpendOutcome.InvalidAmount
        val available = currentBalance()
        if (amount > available) return SpendOutcome.InsufficientFunds(amount, available)

        astriteRepo.addSpendToDate(dateIso, amount, label(category, note))
        economyRepo.append(
            LedgerEntry(
                id = UUID.randomUUID().toString(),
                epochMs = System.currentTimeMillis(),
                dateIso = dateIso,
                type = LedgerType.SPEND,
                amount = amount,
                category = category,
                note = note,
                banner = banner,
                pulls = pulls,
                pity = pity,
                guaranteed = guaranteed
            )
        )
        return SpendOutcome.Success(amount, available - amount)
    }

    private fun label(category: String, note: String) =
        if (note.isBlank()) category else "$category ($note)"
}