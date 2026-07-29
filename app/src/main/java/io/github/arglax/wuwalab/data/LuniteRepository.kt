package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

const val LUNITE_DAILY_ASTRITES = 90

// The game's daily reset is 4:00 AM Philippines time, fixed regardless of the
// player's device timezone (this is the server's reset moment, not a local
// clock time) - Philippines is UTC+8 year-round (no DST), so this is a plain
// fixed offset rather than a named zone that could shift.
val MANILA_OFFSET: ZoneOffset = ZoneOffset.ofHours(8)
const val RESET_HOUR_MANILA = 4

private val Context.luniteDataStore by preferencesDataStore(name = "wuwa_lunite")

/**
 * Which "game day" (as a reset-aligned date) a given instant belongs to.
 * Top-level (not tied to [LuniteRepository]) so any other daily-reset
 * feature - e.g. the Daily Sign-In / Argstrite claim - can share the exact
 * same 4:00 AM Manila boundary without duplicating the math.
 *
 * Note for the curious: 4:00 AM Manila (UTC+8) is 20:00 UTC the *previous*
 * calendar day, not 00:00 or 08:00 UTC.
 */
fun gameDayFor(nowUtcMs: Long = System.currentTimeMillis()): LocalDate {
    val manilaNow = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowUtcMs), MANILA_OFFSET)
    return if (manilaNow.hour < RESET_HOUR_MANILA) manilaNow.toLocalDate().minusDays(1) else manilaNow.toLocalDate()
}

/** Next 4:00 AM Manila reset at or after [fromUtcMs]. */
fun nextResetEpochMs(fromUtcMs: Long = System.currentTimeMillis()): Long {
    val manilaNow = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromUtcMs), MANILA_OFFSET)
    var reset = manilaNow.toLocalDate().atTime(RESET_HOUR_MANILA, 0).atZone(MANILA_OFFSET)
    if (!reset.isAfter(manilaNow)) reset = reset.plusDays(1)
    return reset.toInstant().toEpochMilli()
}

/** Most recent 4:00 AM Manila reset at or before [fromUtcMs]. */
fun lastResetEpochMs(fromUtcMs: Long = System.currentTimeMillis()): Long =
    nextResetEpochMs(fromUtcMs) - 24L * 60 * 60 * 1000

class LuniteRepository(private val context: Context, private val astriteRepo: AstriteRepository) {

    private val KEY_ENABLED = booleanPreferencesKey("lunite_enabled")
    private val KEY_LAST_CHECKIN_GAME_DAY = stringPreferencesKey("lunite_last_checkin_day")

    val enabledFlow: Flow<Boolean> = context.luniteDataStore.data.map { it[KEY_ENABLED] ?: false }

    suspend fun isEnabledOnce(): Boolean = enabledFlow.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.luniteDataStore.edit { it[KEY_ENABLED] = enabled }
    }

    /** Which "game day" (as a reset-aligned date) a given instant belongs to. */
    fun gameDayFor(nowUtcMs: Long = System.currentTimeMillis()): LocalDate = io.github.arglax.wuwalab.data.gameDayFor(nowUtcMs)

    suspend fun hasCheckedInToday(nowUtcMs: Long = System.currentTimeMillis()): Boolean {
        val last = context.luniteDataStore.data.first()[KEY_LAST_CHECKIN_GAME_DAY]
        return last == gameDayFor(nowUtcMs).toString()
    }

    /**
     * Records today's check-in and adds +90 Astrites to that day's tracker
     * entry - "adding" here means increment-on-top-of-whatever-is-already-
     * logged for that date, not overwrite, so it stacks cleanly with any
     * manual entries for the same day rather than clobbering them.
     */
    suspend fun checkIn(nowUtcMs: Long = System.currentTimeMillis()) {
        val dayIso = gameDayFor(nowUtcMs).toString()
        // Routed through AstriteEconomy so the day's tracker row AND the
        // Economic Dashboard logbook both record this in one go.
        AstriteEconomy(astriteRepo, EconomyRepository(context))
            .earn(LUNITE_DAILY_ASTRITES, EconomyCategories.LUNITE_PASS, "Daily check-in", dayIso)
        markCheckedIn(dayIso)
    }

    /**
     * Marks today as checked-in WITHOUT touching the Astrite tracker - used
     * when another flow (the Daily Sign-In / Argstrite button) has already
     * added the Lunite Pass's +90 Astrites itself, so this just silences the
     * reminder alarms and flips the check-in card to "done" without double
     * counting the bonus.
     */
    suspend fun markCheckedIn(dayIso: String) {
        context.luniteDataStore.edit { it[KEY_LAST_CHECKIN_GAME_DAY] = dayIso }
    }

    /** Next 4:00 AM Manila reset at or after [fromUtcMs]. */
    fun nextResetEpochMs(fromUtcMs: Long = System.currentTimeMillis()): Long = io.github.arglax.wuwalab.data.nextResetEpochMs(fromUtcMs)

    /** Most recent 4:00 AM Manila reset at or before [fromUtcMs]. */
    fun lastResetEpochMs(fromUtcMs: Long = System.currentTimeMillis()): Long = io.github.arglax.wuwalab.data.lastResetEpochMs(fromUtcMs)
}