package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

// ---- Regen constants -------------------------------------------------------
// Waveplates: +1 every 6 minutes while below the 240 soft cap - passive regen
// never pushes past 240, but the player can manually enter a value above it
// (up to the 2400 ultra-max) to reflect an in-game overflow/banked amount;
// that state shows as OVERLOADED until spent back below the cap.
const val WAVEPLATE_REGEN_INTERVAL_MINUTES = 6L
const val WAVEPLATE_MAX = 240
const val WAVEPLATE_ULTRA_MAX = 2400

// Waveplate Crystals only start accruing once Waveplates are already sitting
// at 240/240 (that's how the in-game overflow works). +1 every 12 minutes,
// hard-capped at 480 - unlike waveplates, this cap can never be exceeded,
// manually or otherwise.
const val CRYSTAL_REGEN_INTERVAL_MINUTES = 12L
const val CRYSTAL_SOFT_CAP = 480

// EDIT THIS: point at your GitHub-hosted authoritative events JSON.
// Example schema (array of objects):
// [
//   { "id": "convene_x", "name": "Convene: Something", "startEpochMs": 1234567890000, "endEpochMs": 1234567890000 }
// ]
const val EVENTS_JSON_URL =
    "https://raw.githubusercontent.com/Arglax/Mobile-WuWa-Config/main/data/events.json"

private val Context.dataStore by preferencesDataStore(name = "wuwa_state")

enum class ResourceStatus { DEPLETED, REGENERATING, FULL, OVERLOADED }

data class WaveplateState(
    val current: Int,               // 0..240 as of lastUpdatedEpochMs
    val fragments: Int,              // Waveplate Crystals, 0..2400 as of lastUpdatedEpochMs
    val lastUpdatedEpochMs: Long
) {
    /**
     * Live-computed waveplate count based on elapsed time since last manual
     * update. Passive regen only ever climbs up to the 240 soft cap - if the
     * stored baseline is already at or above 240 (including a manual entry
     * above it, e.g. 300), it's returned as-is with no further passive regen.
     */
    fun computeCurrent(nowEpochMs: Long = System.currentTimeMillis()): Int {
        if (current >= WAVEPLATE_MAX) return current
        val minutesElapsed = (nowEpochMs - lastUpdatedEpochMs) / 60000L
        val regenerated = (minutesElapsed / WAVEPLATE_REGEN_INTERVAL_MINUTES).toInt()
        return (current + regenerated).coerceAtMost(WAVEPLATE_MAX)
    }

    /**
     * Live-computed crystal count. Crystals ONLY regenerate passively while
     * waveplates have been sitting at the 240 cap - if the stored waveplate
     * baseline was below 240, no crystal regen happens until (in real time)
     * waveplates would have hit the cap. 480 is a hard cap here: crystals can
     * never exceed it, passively or via manual entry.
     */
    fun computeCrystals(nowEpochMs: Long = System.currentTimeMillis()): Int {
        if (fragments >= CRYSTAL_SOFT_CAP) return CRYSTAL_SOFT_CAP

        val minutesElapsedTotal = (nowEpochMs - lastUpdatedEpochMs) / 60000L
        val minutesToWaveplateCap = if (current >= WAVEPLATE_MAX) {
            0L
        } else {
            val ticksNeeded = WAVEPLATE_MAX - current
            ticksNeeded.toLong() * WAVEPLATE_REGEN_INTERVAL_MINUTES
        }
        val minutesOfCrystalRegen = (minutesElapsedTotal - minutesToWaveplateCap).coerceAtLeast(0)
        val crystalsGained = (minutesOfCrystalRegen / CRYSTAL_REGEN_INTERVAL_MINUTES).toInt()
        return (fragments + crystalsGained).coerceAtMost(CRYSTAL_SOFT_CAP)
    }

    fun waveplateStatus(nowEpochMs: Long = System.currentTimeMillis()): ResourceStatus {
        val wp = computeCurrent(nowEpochMs)
        return when {
            wp <= 0 -> ResourceStatus.DEPLETED
            wp > WAVEPLATE_MAX -> ResourceStatus.OVERLOADED // manual override past the 240 soft cap
            wp == WAVEPLATE_MAX -> ResourceStatus.FULL
            else -> ResourceStatus.REGENERATING
        }
    }

    fun crystalStatus(nowEpochMs: Long = System.currentTimeMillis()): ResourceStatus {
        val c = computeCrystals(nowEpochMs)
        return when {
            c <= 0 -> ResourceStatus.DEPLETED
            c >= CRYSTAL_SOFT_CAP -> ResourceStatus.FULL // 480 is a hard cap, never overloaded
            else -> ResourceStatus.REGENERATING
        }
    }

    /** Millis until waveplates are full, or 0 if already full. */
    fun millisUntilWaveplatesFull(nowEpochMs: Long = System.currentTimeMillis()): Long {
        val nowCount = computeCurrent(nowEpochMs)
        if (nowCount >= WAVEPLATE_MAX) return 0L
        val needed = WAVEPLATE_MAX - nowCount
        val minutesToNextTick = WAVEPLATE_REGEN_INTERVAL_MINUTES -
                ((nowEpochMs - lastUpdatedEpochMs) / 60000L) % WAVEPLATE_REGEN_INTERVAL_MINUTES
        val totalMinutes = minutesToNextTick + (needed - 1) * WAVEPLATE_REGEN_INTERVAL_MINUTES
        return totalMinutes * 60000L
    }

    /** Millis until waveplates hit a specific target count (e.g. a custom notify threshold). */
    fun millisUntilWaveplateCount(target: Int, nowEpochMs: Long = System.currentTimeMillis()): Long? {
        val nowCount = computeCurrent(nowEpochMs)
        if (target <= nowCount) return null // already reached/passed
        if (target > WAVEPLATE_MAX) return null // unreachable via passive regen
        val needed = target - nowCount
        val minutesToNextTick = WAVEPLATE_REGEN_INTERVAL_MINUTES -
                ((nowEpochMs - lastUpdatedEpochMs) / 60000L) % WAVEPLATE_REGEN_INTERVAL_MINUTES
        val totalMinutes = minutesToNextTick + (needed - 1) * WAVEPLATE_REGEN_INTERVAL_MINUTES
        return totalMinutes * 60000L
    }

    /** Millis until crystals hit the 480 soft cap, or null if unreachable/already there. */
    fun millisUntilCrystalsFull(nowEpochMs: Long = System.currentTimeMillis()): Long? {
        if (fragments >= CRYSTAL_SOFT_CAP) return null
        val minutesToWaveplateCap = if (current >= WAVEPLATE_MAX) {
            0L
        } else {
            (WAVEPLATE_MAX - current).toLong() * WAVEPLATE_REGEN_INTERVAL_MINUTES -
                    ((nowEpochMs - lastUpdatedEpochMs) / 60000L)
        }.coerceAtLeast(0)
        val crystalsNeeded = CRYSTAL_SOFT_CAP - fragments
        val minutesOfCrystalRegen = crystalsNeeded.toLong() * CRYSTAL_REGEN_INTERVAL_MINUTES
        return (minutesToWaveplateCap + minutesOfCrystalRegen) * 60000L
    }
}

data class WuwaEvent(
    val id: String,
    val name: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    // Name of a drawable resource (no extension, no path) to use as this
    // event's banner background, e.g. "convene_changli_bg" for a file at
    // res/drawable/convene_changli_bg.png. Null/missing/not-found all fall
    // back to a plain accent-colored card - see EventBanner in MainActivity.kt.
    val bannerImage: String? = null,
    // Free-form category shown as a small pill in both the banner and the
    // detail popup, e.g. "farming", "convene", "combat", "leisure". Null/blank
    // means "no type" (a plain featured event) and the pill is simply omitted.
    val eventType: String? = null,
    // Bullet-point detail lines shown in the tap-to-expand popup. Kept as a
    // simple list of strings rather than a rich structure so events.json stays
    // easy to hand-edit - each string is just rendered as its own bullet.
    val details: List<String> = emptyList()
) {
    enum class Status { UPCOMING, LIVE, EXPIRED }

    fun status(nowEpochMs: Long = System.currentTimeMillis()): Status = when {
        nowEpochMs < startEpochMs -> Status.UPCOMING
        nowEpochMs in startEpochMs..endEpochMs -> Status.LIVE
        else -> Status.EXPIRED
    }
}

/** User-configurable "Notify Me" preferences for waveplates/crystals. */
data class NotifyPrefs(
    val notifyOnWaveplateFull: Boolean = true,
    val notifyOnCrystalMax: Boolean = false,
    val customCountEnabled: Boolean = false,
    val customCount: Int = WAVEPLATE_MAX
)

class WuwaRepository(val context: Context) {

    private val KEY_CURRENT = intPreferencesKey("waveplate_current")
    private val KEY_FRAGMENTS = intPreferencesKey("waveplate_fragments")
    private val KEY_UPDATED = longPreferencesKey("waveplate_last_updated")
    private val KEY_EVENTS_CACHE = stringPreferencesKey("events_cache_json")
    private val KEY_NOTIFIED_FULL = longPreferencesKey("notified_full_at") // debounce
    private val KEY_NOTIFIED_CRYSTAL_MAX = longPreferencesKey("notified_crystal_max_at")
    private val KEY_NOTIFIED_CUSTOM = longPreferencesKey("notified_custom_at")
    private val KEY_NOTIFIED_3D = stringPreferencesKey("notified_3day_ids")
    private val KEY_NOTIFIED_1D = stringPreferencesKey("notified_1day_ids")

    private val KEY_NOTIFY_FULL_ENABLED = booleanPreferencesKey("notify_full_enabled")
    private val KEY_NOTIFY_CRYSTAL_MAX_ENABLED = booleanPreferencesKey("notify_crystal_max_enabled")
    private val KEY_NOTIFY_CUSTOM_ENABLED = booleanPreferencesKey("notify_custom_enabled")
    private val KEY_NOTIFY_CUSTOM_COUNT = intPreferencesKey("notify_custom_count")
    private val KEY_SKIPPED_UPDATE_TAG = stringPreferencesKey("skipped_update_tag")
    private val KEY_RADIANT_ASTRITE = intPreferencesKey("radiant_astrite_count")
    private val KEY_PENDING_ARGSTRITE = intPreferencesKey("pending_argstrite_count")
    private val KEY_WELCOME_DIALOG_HIDDEN = booleanPreferencesKey("welcome_dialog_hidden_forever")
    private val KEY_SHOWN_ARGSTRITE_HINT = booleanPreferencesKey("shown_argstrite_hidden_method_hint")
    private val KEY_SHOWN_BONUS_ARGSTRITE_HINT = booleanPreferencesKey("shown_bonus_argstrite_hidden_method_hint")
    // Cumulative, permanent % bonus applied to every Argstrite earning - the
    // sum of every unlocked Achievement's +1% plus the Supporter title's
    // +20%. Owned/recomputed by AchievementsRepository; stored here so the
    // two places Argstrites actually get created (below) can apply it
    // atomically without a circular dependency on AchievementsRepository.
    // Stored as a float so future fractional bonuses (e.g. a +0.5% perk)
    // are possible without a migration.
    private val KEY_BONUS_PERCENT = floatPreferencesKey("argstrite_bonus_percent")
    // Cumulative % from every OWNED Shop title's titleBonusPercent (see
    // ShopRepository.recomputeTitleBonus) - kept as a SEPARATE key from
    // KEY_BONUS_PERCENT (achievements) so the two systems never clobber
    // each other; the two are simply summed together in [bonusPercentFlow].
    private val KEY_TITLE_BONUS_PERCENT = floatPreferencesKey("argstrite_title_bonus_percent")
    // Cumulative MULTIPLIER from titles like "???" (x10) - applied on top
    // of the summed percent bonus. 1f = no effect.
    private val KEY_TITLE_BONUS_MULTIPLIER = floatPreferencesKey("argstrite_title_bonus_multiplier")

    suspend fun getSkippedUpdateTag(): String? = context.dataStore.data.first()[KEY_SKIPPED_UPDATE_TAG]

    suspend fun setSkippedUpdateTag(tag: String) {
        context.dataStore.edit { it[KEY_SKIPPED_UPDATE_TAG] = tag }
    }

    // --- "How WuWaLab Works" welcome dialog ---
    // Shown once at startup (first launch, and every subsequent launch)
    // until the user explicitly checks "Don't show this again" - this is a
    // one-way flip, there's no in-app toggle to bring it back other than
    // clearing app data, by design (it's a one-time orientation notice, not
    // a setting anyone needs to revisit).

    suspend fun isWelcomeDialogHidden(): Boolean =
        context.dataStore.data.first()[KEY_WELCOME_DIALOG_HIDDEN] ?: false

    suspend fun setWelcomeDialogHiddenForever() {
        context.dataStore.edit { it[KEY_WELCOME_DIALOG_HIDDEN] = true }
    }

    /**
     * The "hidden method" reveal for the Argstrite side-currency: the very
     * first time ANY real log (Astrite log, spend log, pull log, To-Do log,
     * or a manual Waveplate update) earns a pending Argstrite, the award
     * popup should explain the mechanic once - and never again. If that log
     * also carried an optional note/description (earning the bonus +1), a
     * SECOND, separate one-time popup explains the bonus the first time it's
     * ever triggered. Returns null once both have already been shown, so the
     * caller falls back to the plain "+N Argstrites" popup with no hint text.
     */
    suspend fun consumeArgstriteHint(hadNote: Boolean): String? {
        val baseShown = context.dataStore.data.first()[KEY_SHOWN_ARGSTRITE_HINT] ?: false
        if (!baseShown) {
            context.dataStore.edit { it[KEY_SHOWN_ARGSTRITE_HINT] = true }
            return "Great! You found a hidden method to earn Argstrites."
        }
        if (hadNote) {
            val bonusShown = context.dataStore.data.first()[KEY_SHOWN_BONUS_ARGSTRITE_HINT] ?: false
            if (!bonusShown) {
                context.dataStore.edit { it[KEY_SHOWN_BONUS_ARGSTRITE_HINT] = true }
                return "Amazing! You've found a hidden method to earn bonus Argstrites!"
            }
        }
        return null
    }

    val waveplateStateFlow: Flow<WaveplateState> = context.dataStore.data.map { prefs ->
        WaveplateState(
            current = prefs[KEY_CURRENT] ?: 0,
            fragments = prefs[KEY_FRAGMENTS] ?: 0,
            lastUpdatedEpochMs = prefs[KEY_UPDATED] ?: System.currentTimeMillis()
        )
    }

    suspend fun setWaveplates(current: Int, fragments: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENT] = current.coerceIn(0, WAVEPLATE_ULTRA_MAX)
            prefs[KEY_FRAGMENTS] = fragments.coerceIn(0, CRYSTAL_SOFT_CAP)
            prefs[KEY_UPDATED] = System.currentTimeMillis()
        }
    }

    suspend fun getWaveplateStateOnce(): WaveplateState = waveplateStateFlow.first()

    // --- Radiant Astrite (custom currency) ---

    val radiantAstriteFlow: Flow<Int> = context.dataStore.data.map { it[KEY_RADIANT_ASTRITE] ?: 0 }

    suspend fun getRadiantAstriteOnce(): Int = radiantAstriteFlow.first()

    suspend fun setRadiantAstrite(amount: Int) {
        context.dataStore.edit { it[KEY_RADIANT_ASTRITE] = amount.coerceAtLeast(0) }
    }

    /**
     * Atomically deducts [amount] Argstrites, but ONLY if the balance covers
     * it. Returns false and writes nothing otherwise. This is the guard that
     * makes an overdrawn shop purchase impossible even if two taps race each
     * other - the check and the deduction happen inside the same edit block.
     */
    suspend fun trySpendRadiantAstrite(amount: Int): Boolean {
        if (amount <= 0) return false
        var succeeded = false
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_RADIANT_ASTRITE] ?: 0
            if (current >= amount) {
                prefs[KEY_RADIANT_ASTRITE] = current - amount
                succeeded = true
            }
        }
        return succeeded
    }

    // --- Argstrite Bonus % (see Achievements) ---

    /** Achievements-only bonus % (kept for anything that still wants just this component). */
    private val achievementBonusPercentFlow: Flow<Float> = context.dataStore.data.map { it[KEY_BONUS_PERCENT] ?: 0f }

    /** Titles-only cumulative bonus %, see [KEY_TITLE_BONUS_PERCENT]. */
    private val titleBonusPercentFlow: Flow<Float> = context.dataStore.data.map { it[KEY_TITLE_BONUS_PERCENT] ?: 0f }

    /**
     * Titles-only cumulative multiplier (e.g. the "???" title's x10), see
     * [KEY_TITLE_BONUS_MULTIPLIER]. Public (not just [getBonusMultiplierOnce])
     * so the Profile summary can show this LIVE next to the Bonus % instead
     * of silently folding it into a single percent figure - a x10 is not the
     * same thing as a +10% and must never be displayed as if it were.
     */
    val bonusMultiplierFlow: Flow<Float> = context.dataStore.data.map { it[KEY_TITLE_BONUS_MULTIPLIER] ?: 1f }

    /** The TOTAL Argstrite Bonus % shown across the app - achievements' + owned titles' bonuses, summed. Does not include the title multiplier (see [applyBonus]). */
    val bonusPercentFlow: Flow<Float> = kotlinx.coroutines.flow.combine(achievementBonusPercentFlow, titleBonusPercentFlow) { a, t -> a + t }

    suspend fun getBonusPercentOnce(): Float = bonusPercentFlow.first()

    suspend fun getBonusMultiplierOnce(): Float = bonusMultiplierFlow.first()

    /** AchievementsRepository calls this with its freshly recomputed total whenever an achievement unlocks. */
    suspend fun setBonusPercent(percent: Float) {
        context.dataStore.edit { it[KEY_BONUS_PERCENT] = percent.coerceAtLeast(0f) }
    }

    /** ShopRepository.recomputeTitleBonus calls this whenever a title is bought/redeemed/lost. */
    suspend fun setTitleBonus(percent: Float, multiplier: Float) {
        context.dataStore.edit {
            it[KEY_TITLE_BONUS_PERCENT] = percent.coerceAtLeast(0f)
            it[KEY_TITLE_BONUS_MULTIPLIER] = multiplier.coerceAtLeast(1f)
        }
    }

    /**
     * Scales a positive earning by the current Argstrite Bonus % (achievements
     * + titles, summed), then by the title bonus multiplier (e.g. the "???"
     * title's x10) - deductions (amount <= 0) pass through untouched. Always
     * rounds UP: if the scaled result isn't a whole number, the player gets
     * the benefit of the doubt rather than losing a fractional Argstrite to
     * truncation.
     */
    /**
     * The combined multiplier actually in force right now: every owned title's
     * multiplier plus whichever local app event is live (Midweek Jump x2 on
     * Wednesdays, Weekend Rush x3 at weekends), read straight off the device
     * clock - no server, no stored flag to drift out of date.
     *
     * ADDITIVE, not compounding: a x10 title on a Wednesday is x12, not x20.
     * See [BonusMath.combineMultipliers].
     */
    suspend fun getTotalMultiplierOnce(): Float =
        BonusMath.combineMultipliers(getBonusMultiplierOnce(), AppEventCalendar.multiplier())

    /** Live version of [getTotalMultiplierOnce], for UI that must react the moment a title is bought. */
    val totalMultiplierFlow: Flow<Float> = bonusMultiplierFlow.map { titleMultiplier ->
        BonusMath.combineMultipliers(titleMultiplier, AppEventCalendar.multiplier())
    }

    /**
     * What [amount] WOULD become if credited right now - no write, no side
     * effect. The Bank quotes payouts with this, and it is the same code path
     * the real credit takes, so a preview can never drift from the payout.
     */
    suspend fun previewBonus(amount: Int): Int = applyBonus(amount)

    private suspend fun applyBonus(amount: Int): Int {
        if (amount <= 0) return amount
        return BonusMath.apply(amount, getBonusPercentOnce(), getTotalMultiplierOnce())
    }

    /**
     * Credits [delta] Argstrites, scaled by the current Bonus %. Returns the
     * actual amount credited (post-bonus) so callers that show a "+N
     * Argstrites earned" message can display the real figure rather than the
     * pre-bonus base amount.
     */
    suspend fun addRadiantAstrite(delta: Int): Int {
        val bonused = applyBonus(delta)
        context.dataStore.edit {
            val current = it[KEY_RADIANT_ASTRITE] ?: 0
            it[KEY_RADIANT_ASTRITE] = (current + bonused).coerceAtLeast(0)
        }
        return bonused
    }

    /**
     * Credits back exactly [amount] Argstrites with NO bonus scaling.
     *
     * Use this (never [addRadiantAstrite]) to refund a spend that failed
     * after the deduction already happened (e.g. a render failing after
     * [trySpendRadiantAstrite] succeeded). [trySpendRadiantAstrite] never
     * applies the bonus when it takes Argstrites away, so reversing that
     * exact deduction must not apply the bonus either - otherwise a player
     * with an active Bonus % would net extra Argstrites every time a paid
     * action happened to fail and refund.
     */
    suspend fun refundRadiantAstrite(amount: Int) {
        if (amount <= 0) return
        context.dataStore.edit {
            val current = it[KEY_RADIANT_ASTRITE] ?: 0
            it[KEY_RADIANT_ASTRITE] = current + amount
        }
    }

    // --- Pending Argstrites (earned-but-unclaimed reward for using real features) ---

    /**
     * Argstrites earned by actually using the app (logging an Astrite entry,
     * logging a spend, etc.) sit here first instead of landing straight in the
     * spendable balance - so a burst of quick actions doesn't fire a popup for
     * every single one. The Dashboard's "Claim" button sweeps this into
     * [radiantAstriteFlow] in one go.
     */
    val pendingArgstriteFlow: Flow<Int> = context.dataStore.data.map { it[KEY_PENDING_ARGSTRITE] ?: 0 }

    suspend fun getPendingArgstriteOnce(): Int = pendingArgstriteFlow.first()

    /**
     * Called right after a real, functional action - adds to the unclaimed pile.
     * Returns the actual amount added (post-bonus) so callers that show a "+N
     * Argstrites" popup can display the real figure rather than the pre-bonus
     * base amount (same reasoning as [addRadiantAstrite]).
     */
    suspend fun addPendingArgstrite(amount: Int): Int {
        if (amount <= 0) return 0
        val bonused = applyBonus(amount)
        context.dataStore.edit {
            val current = it[KEY_PENDING_ARGSTRITE] ?: 0
            it[KEY_PENDING_ARGSTRITE] = current + bonused
        }
        return bonused
    }

    /** Sweeps everything pending into the spendable balance and returns how much was claimed. */
    suspend fun claimPendingArgstrite(): Int {
        var claimed = 0
        context.dataStore.edit { prefs ->
            claimed = prefs[KEY_PENDING_ARGSTRITE] ?: 0
            if (claimed > 0) {
                prefs[KEY_PENDING_ARGSTRITE] = 0
                prefs[KEY_RADIANT_ASTRITE] = (prefs[KEY_RADIANT_ASTRITE] ?: 0) + claimed
            }
        }
        return claimed
    }

    // --- Notify Me preferences ---

    val notifyPrefsFlow: Flow<NotifyPrefs> = context.dataStore.data.map { prefs ->
        NotifyPrefs(
            notifyOnWaveplateFull = prefs[KEY_NOTIFY_FULL_ENABLED] ?: true,
            notifyOnCrystalMax = prefs[KEY_NOTIFY_CRYSTAL_MAX_ENABLED] ?: false,
            customCountEnabled = prefs[KEY_NOTIFY_CUSTOM_ENABLED] ?: false,
            customCount = prefs[KEY_NOTIFY_CUSTOM_COUNT] ?: WAVEPLATE_MAX
        )
    }

    suspend fun setNotifyPrefs(prefs: NotifyPrefs) {
        context.dataStore.edit {
            it[KEY_NOTIFY_FULL_ENABLED] = prefs.notifyOnWaveplateFull
            it[KEY_NOTIFY_CRYSTAL_MAX_ENABLED] = prefs.notifyOnCrystalMax
            it[KEY_NOTIFY_CUSTOM_ENABLED] = prefs.customCountEnabled
            it[KEY_NOTIFY_CUSTOM_COUNT] = prefs.customCount.coerceIn(0, WAVEPLATE_MAX)
        }
    }

    // --- Notification debounce bookkeeping ---

    suspend fun shouldNotifyFull(): Boolean {
        val last = context.dataStore.data.first()[KEY_NOTIFIED_FULL] ?: 0L
        return last == 0L
    }

    suspend fun markNotifiedFull() {
        context.dataStore.edit { it[KEY_NOTIFIED_FULL] = System.currentTimeMillis() }
    }

    suspend fun clearNotifiedFullIfBelowMax(currentCount: Int) {
        if (currentCount < WAVEPLATE_MAX) {
            context.dataStore.edit { it[KEY_NOTIFIED_FULL] = 0L }
        }
    }

    suspend fun shouldNotifyCrystalMax(): Boolean {
        val last = context.dataStore.data.first()[KEY_NOTIFIED_CRYSTAL_MAX] ?: 0L
        return last == 0L
    }

    suspend fun markNotifiedCrystalMax() {
        context.dataStore.edit { it[KEY_NOTIFIED_CRYSTAL_MAX] = System.currentTimeMillis() }
    }

    suspend fun clearNotifiedCrystalMaxIfBelow(crystalCount: Int) {
        if (crystalCount < CRYSTAL_SOFT_CAP) {
            context.dataStore.edit { it[KEY_NOTIFIED_CRYSTAL_MAX] = 0L }
        }
    }

    suspend fun shouldNotifyCustom(): Boolean {
        val last = context.dataStore.data.first()[KEY_NOTIFIED_CUSTOM] ?: 0L
        return last == 0L
    }

    suspend fun markNotifiedCustom() {
        context.dataStore.edit { it[KEY_NOTIFIED_CUSTOM] = System.currentTimeMillis() }
    }

    suspend fun clearNotifiedCustomIfBelow(count: Int, threshold: Int) {
        if (count < threshold) {
            context.dataStore.edit { it[KEY_NOTIFIED_CUSTOM] = 0L }
        }
    }

    suspend fun hasNotified(bucketKey: androidx.datastore.preferences.core.Preferences.Key<String>, eventId: String): Boolean {
        val raw = context.dataStore.data.first()[bucketKey] ?: ""
        return raw.split(",").contains(eventId)
    }

    suspend fun markNotified(bucketKey: androidx.datastore.preferences.core.Preferences.Key<String>, eventId: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[bucketKey] ?: ""
            val set = existing.split(",").filter { it.isNotBlank() }.toMutableSet()
            set.add(eventId)
            prefs[bucketKey] = set.joinToString(",")
        }
    }

    fun notified3DayKey() = KEY_NOTIFIED_3D
    fun notified1DayKey() = KEY_NOTIFIED_1D

    // --- Events: fetch from GitHub, fall back to bundled assets/events.json, cache locally ---

    suspend fun refreshEventsFromGitHub(): List<WuwaEvent> {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10.seconds.toJavaDuration())
                .readTimeout(10.seconds.toJavaDuration())
                .build()
            val request = Request.Builder().url(EVENTS_JSON_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return loadBundledEvents()
                val body = response.body?.string() ?: return loadBundledEvents()
                context.dataStore.edit { it[KEY_EVENTS_CACHE] = body }
                parseEvents(body)
            }
        } catch (_: Exception) {
            loadBundledEvents()
        }
    }

    suspend fun getCachedEvents(): List<WuwaEvent> {
        val raw = context.dataStore.data.first()[KEY_EVENTS_CACHE]
        if (raw != null) return parseEvents(raw)
        return loadBundledEvents()
    }

    private fun loadBundledEvents(): List<WuwaEvent> {
        return try {
            val json = context.assets.open("events.json").bufferedReader().use { it.readText() }
            parseEvents(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseEvents(json: String): List<WuwaEvent> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WuwaEvent(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    startEpochMs = o.getLong("startEpochMs"),
                    endEpochMs = o.getLong("endEpochMs"),
                    bannerImage = if (o.has("bannerImage") && !o.isNull("bannerImage")) o.getString("bannerImage") else null,
                    eventType = if (o.has("eventType") && !o.isNull("eventType")) o.getString("eventType") else null,
                    details = if (o.has("details") && !o.isNull("details")) {
                        val d = o.getJSONArray("details")
                        (0 until d.length()).map { j -> d.getString(j) }
                    } else emptyList()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
