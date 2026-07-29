package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.ceil

private val Context.bankDataStore by preferencesDataStore(name = "wuwa_bank")

/** Smallest deposit worth opening - below this, rounding does more work than the rate. */
const val BANK_MIN_DEPOSIT = 100

/** How many deposits can be open at once. Keeps the list readable and stops spam-opening. */
const val BANK_MAX_OPEN_DEPOSITS = 6

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L

/** No penalty and no interest inside this window - a free "changed my mind". */
const val BANK_GRACE_HOURS = 24L

/** Between the grace window and this mark, breaking a deposit costs [BANK_EARLY_PENALTY_PERCENT]. */
const val BANK_PENALTY_END_HOURS = 72L

const val BANK_EARLY_PENALTY_PERCENT = 10

/**
 * The fixed-term products.
 *
 * The rate curve is deliberately CONCAVE: total payout climbs steeply with
 * term length (2.5% -> 36%), while payout PER DAY falls (2.50%/day ->
 * 1.20%/day). That shape is the whole design - it makes short, repeated
 * deposits the strongest play. Ten 3-day terms across a month return 60%
 * against the 36% a single 1-month term pays, so the rewarded behaviour is
 * showing up every day or three rather than parking Argstrites once and
 * forgetting the app exists.
 *
 * The curve follows roughly rate(days) = 2.5% x days^0.79. The table below is
 * the authority; the formula only explains its shape.
 */
enum class DepositTerm(
    val id: String,
    val label: String,
    val shortLabel: String,
    val durationMs: Long,
    val ratePercent: Float
) {
    ONE_DAY("1d", "1 Day", "1D", DAY_MS, 2.5f),
    THREE_DAYS("3d", "3 Days", "3D", 3 * DAY_MS, 6f),
    ONE_WEEK("1w", "1 Week", "1W", 7 * DAY_MS, 12f),
    TWO_WEEKS("2w", "2 Weeks", "2W", 14 * DAY_MS, 20f),
    ONE_MONTH("1m", "1 Month", "1M", 30 * DAY_MS, 36f);

    val days: Int get() = (durationMs / DAY_MS).toInt()

    /** Effective yield per day - the number that makes the short-term advantage obvious. */
    val ratePerDay: Float get() = ratePercent / days

    companion object {
        fun fromId(value: String?): DepositTerm = entries.firstOrNull { it.id == value } ?: ONE_DAY
    }
}

/** Which early-exit rule a still-locked deposit currently falls under. */
enum class EarlyExitBand(val label: String) {
    GRACE("Free window"),
    PENALTY("Penalty window"),
    FREE("No penalty")
}

enum class BankOutcome(val label: String) {
    MATURED("Matured"),
    WITHDRAWN_FREE("Withdrawn early"),
    WITHDRAWN_PENALTY("Withdrawn - penalty")
}

data class Deposit(
    val id: String = UUID.randomUUID().toString(),
    val principal: Int,
    val term: DepositTerm,
    val openedAtEpochMs: Long,
    val closedAtEpochMs: Long? = null,
    val outcome: BankOutcome? = null,
    /** What actually landed back in the balance, filled in on close. */
    val returnedAmount: Int = 0,
    /** Bonused interest paid at maturity - always 0 for an early exit. */
    val interestPaid: Int = 0,
    /** Principal lost to the early-exit penalty - 0 unless the penalty band applied. */
    val penaltyPaid: Int = 0
) {
    val isOpen: Boolean get() = closedAtEpochMs == null
    val maturesAtEpochMs: Long get() = openedAtEpochMs + term.durationMs

    fun isMatured(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs >= maturesAtEpochMs

    fun millisRemaining(nowMs: Long = System.currentTimeMillis()): Long =
        (maturesAtEpochMs - nowMs).coerceAtLeast(0L)

    fun millisHeld(nowMs: Long = System.currentTimeMillis()): Long =
        (nowMs - openedAtEpochMs).coerceAtLeast(0L)

    /** Interest BEFORE the bonus pipeline - what the term's rate alone pays. */
    val baseInterest: Int get() = ceil(principal * (term.ratePercent / 100.0)).toInt()
}

/**
 * A full, human-readable breakdown of a payout. Every step is shown rather
 * than collapsed into one number, because the bonus percent and the multiplier
 * are routinely the largest part of the payout - hiding them would make the
 * Bank look broken rather than generous.
 */
data class DepositQuote(
    val principal: Int,
    val term: DepositTerm,
    val baseInterest: Int,
    val bonusPercent: Float,
    val multiplier: Float,
    val interestAfterPercent: Int,
    val finalInterest: Int
) {
    val totalPayout: Int get() = principal + finalInterest
}

object BankMath {

    /**
     * The payout a deposit would produce at TODAY's bonuses. Bonuses apply at
     * CLAIM time, not deposit time, so this is an estimate that moves with the
     * player's titles and whichever app event is live - which the screen says
     * out loud rather than hiding.
     */
    fun quote(principal: Int, term: DepositTerm, bonusPercent: Float, multiplier: Float): DepositQuote {
        val base = ceil(principal * (term.ratePercent / 100.0)).toInt()
        return DepositQuote(
            principal = principal,
            term = term,
            baseInterest = base,
            bonusPercent = bonusPercent,
            multiplier = multiplier,
            interestAfterPercent = ceil(base * (1.0 + bonusPercent / 100.0)).toInt(),
            // One single ceil, exactly as BonusMath.apply does it, so the
            // preview can never be a coin off what actually gets credited.
            finalInterest = BonusMath.apply(base, bonusPercent, multiplier)
        )
    }

    /**
     * Which rule breaking this deposit early falls under right now.
     *
     *   under 24h  -> GRACE   : principal back in full, no interest
     *   24h to 72h -> PENALTY : 10% of principal kept, no interest
     *   over 72h   -> FREE    : principal back in full, no interest
     *
     * A 1-day term matures at the 24h mark, so it can never reach the penalty
     * band - it is either inside the free window or already claimable.
     */
    fun earlyExitBand(heldMs: Long): EarlyExitBand = when {
        heldMs < BANK_GRACE_HOURS * HOUR_MS -> EarlyExitBand.GRACE
        heldMs < BANK_PENALTY_END_HOURS * HOUR_MS -> EarlyExitBand.PENALTY
        else -> EarlyExitBand.FREE
    }

    fun earlyPenalty(principal: Int, band: EarlyExitBand): Int =
        if (band == EarlyExitBand.PENALTY) ceil(principal * (BANK_EARLY_PENALTY_PERCENT / 100.0)).toInt() else 0

    /** Drives the "safe to break for another Nh" countdown. */
    fun millisUntilPenaltyStarts(heldMs: Long): Long = (BANK_GRACE_HOURS * HOUR_MS - heldMs).coerceAtLeast(0L)

    /** Drives the "penalty-free again in Nh" countdown. */
    fun millisUntilPenaltyEnds(heldMs: Long): Long = (BANK_PENALTY_END_HOURS * HOUR_MS - heldMs).coerceAtLeast(0L)
}

/** What a close() actually did, so the screen can report it honestly. */
data class BankCloseResult(
    val deposit: Deposit,
    val outcome: BankOutcome,
    val principalReturned: Int,
    val interestPaid: Int,
    val penaltyPaid: Int
) {
    val totalReturned: Int get() = principalReturned + interestPaid
}

sealed interface BankOpenResult {
    data class Success(val deposit: Deposit) : BankOpenResult
    data class BelowMinimum(val minimum: Int) : BankOpenResult
    data class NotEnoughArgstrites(val needed: Int, val balance: Int) : BankOpenResult
    data class TooManyOpen(val limit: Int) : BankOpenResult
}

/**
 * The Investment Center's store and rules.
 *
 * Argstrites move through [WuwaRepository] and nowhere else. Opening a deposit
 * spends through the same atomic guard the Shop uses, so a double-tap can
 * never overdraw. Returned capital comes back through the NON-bonused refund
 * path - it is the player's own money, not an earning - while only the
 * interest goes through the bonused credit path. That split is what stops the
 * bonus percent and multiplier from silently inflating the principal, which
 * would otherwise turn deposit-and-instantly-break into an infinite money bug.
 */
class BankRepository(
    private val context: Context,
    private val wuwaRepo: WuwaRepository
) {

    private val KEY_DEPOSITS = stringPreferencesKey("bank_deposits_json")
    private val KEY_LIFETIME_INTEREST = intPreferencesKey("bank_lifetime_interest")

    private val allDepositsFlow: Flow<List<Deposit>> = context.bankDataStore.data.map { prefs ->
        parse(prefs[KEY_DEPOSITS] ?: "[]")
    }

    val openDepositsFlow: Flow<List<Deposit>> =
        allDepositsFlow.map { list -> list.filter { it.isOpen }.sortedBy { it.maturesAtEpochMs } }

    val closedDepositsFlow: Flow<List<Deposit>> =
        allDepositsFlow.map { list -> list.filterNot { it.isOpen }.sortedByDescending { it.closedAtEpochMs ?: 0L } }

    val lockedTotalFlow: Flow<Int> = openDepositsFlow.map { list -> list.sumOf { it.principal } }

    val lifetimeInterestFlow: Flow<Int> = context.bankDataStore.data.map { it[KEY_LIFETIME_INTEREST] ?: 0 }

    suspend fun open(principal: Int, term: DepositTerm, nowMs: Long = System.currentTimeMillis()): BankOpenResult {
        if (principal < BANK_MIN_DEPOSIT) return BankOpenResult.BelowMinimum(BANK_MIN_DEPOSIT)

        val all = allDepositsFlow.first()
        if (all.count { it.isOpen } >= BANK_MAX_OPEN_DEPOSITS) return BankOpenResult.TooManyOpen(BANK_MAX_OPEN_DEPOSITS)

        if (!wuwaRepo.trySpendRadiantAstrite(principal)) {
            return BankOpenResult.NotEnoughArgstrites(principal, wuwaRepo.getRadiantAstriteOnce())
        }

        val deposit = Deposit(principal = principal, term = term, openedAtEpochMs = nowMs)
        save(all + deposit)
        return BankOpenResult.Success(deposit)
    }

    /**
     * Closes a deposit - claiming it if it has matured, breaking it early if it
     * hasn't. The caller doesn't get to choose which: the elapsed time decides,
     * so there is no path where an early break can be mistaken for a claim.
     */
    suspend fun close(depositId: String, nowMs: Long = System.currentTimeMillis()): BankCloseResult? {
        val all = allDepositsFlow.first()
        val deposit = all.firstOrNull { it.id == depositId && it.isOpen } ?: return null

        val result: BankCloseResult
        if (deposit.isMatured(nowMs)) {
            wuwaRepo.refundRadiantAstrite(deposit.principal)
            val interest = wuwaRepo.addRadiantAstrite(deposit.baseInterest)
            result = BankCloseResult(deposit, BankOutcome.MATURED, deposit.principal, interest, 0)
            bumpLifetimeInterest(interest)
        } else {
            val band = BankMath.earlyExitBand(deposit.millisHeld(nowMs))
            val penalty = BankMath.earlyPenalty(deposit.principal, band)
            val returned = (deposit.principal - penalty).coerceAtLeast(0)
            wuwaRepo.refundRadiantAstrite(returned)
            result = BankCloseResult(
                deposit = deposit,
                outcome = if (penalty > 0) BankOutcome.WITHDRAWN_PENALTY else BankOutcome.WITHDRAWN_FREE,
                principalReturned = returned,
                interestPaid = 0,
                penaltyPaid = penalty
            )
        }

        val closed = deposit.copy(
            closedAtEpochMs = nowMs,
            outcome = result.outcome,
            returnedAmount = result.totalReturned,
            interestPaid = result.interestPaid,
            penaltyPaid = result.penaltyPaid
        )
        val remaining = all.filterNot { it.id == deposit.id }
        // Keep a short history so the screen can show what happened, without
        // letting it grow without bound.
        val done = (remaining.filterNot { it.isOpen } + closed)
            .sortedByDescending { it.closedAtEpochMs ?: 0L }
            .take(30)
        save(remaining.filter { it.isOpen } + done)
        return result
    }

    private suspend fun bumpLifetimeInterest(delta: Int) {
        if (delta <= 0) return
        context.bankDataStore.edit { it[KEY_LIFETIME_INTEREST] = (it[KEY_LIFETIME_INTEREST] ?: 0) + delta }
    }

    suspend fun clearAll() {
        context.bankDataStore.edit {
            it[KEY_DEPOSITS] = "[]"
            it[KEY_LIFETIME_INTEREST] = 0
        }
    }

    private suspend fun save(deposits: List<Deposit>) {
        val arr = JSONArray()
        deposits.forEach { d ->
            arr.put(
                JSONObject().apply {
                    put("id", d.id)
                    put("principal", d.principal)
                    put("term", d.term.id)
                    put("openedAt", d.openedAtEpochMs)
                    if (d.closedAtEpochMs != null) put("closedAt", d.closedAtEpochMs)
                    if (d.outcome != null) put("outcome", d.outcome.name)
                    put("returned", d.returnedAmount)
                    put("interest", d.interestPaid)
                    put("penalty", d.penaltyPaid)
                }
            )
        }
        context.bankDataStore.edit { it[KEY_DEPOSITS] = arr.toString() }
    }

    private fun parse(json: String): List<Deposit> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Deposit(
                id = o.optString("id", UUID.randomUUID().toString()),
                principal = o.optInt("principal", 0),
                term = DepositTerm.fromId(o.optString("term")),
                openedAtEpochMs = o.optLong("openedAt", System.currentTimeMillis()),
                closedAtEpochMs = if (o.has("closedAt") && !o.isNull("closedAt")) o.optLong("closedAt") else null,
                outcome = runCatching { BankOutcome.valueOf(o.optString("outcome")) }.getOrNull(),
                returnedAmount = o.optInt("returned", 0),
                interestPaid = o.optInt("interest", 0),
                penaltyPaid = o.optInt("penalty", 0)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
