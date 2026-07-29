package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Flat +10 Argstrites (WuWaLab's own currency) for tapping the Daily Sign-In card, once per game day. */
const val DAILY_SIGNIN_BASE_ARGSTRITES = 10

private val Context.dailySignInDataStore by preferencesDataStore(name = "wuwa_daily_signin")

/** What a [DailySignInRepository.claim] actually granted, split by currency so the UI can render each with its own icon. */
data class DailySignInClaimResult(
    val argstritesGranted: Int,
    val astritesGranted: Int,
    val luniteIncluded: Boolean
)

/**
 * Backs the "Daily Sign-In" card on the Dashboard. Reuses the exact same
 * 4:00 AM Manila reset boundary as the Lunite Pass check-in ([gameDayFor])
 * so both features roll over at the same instant.
 *
 * BUGFIX: the base reward is a flat +10 Argstrites - WuWaLab's own in-app
 * currency - and now correctly credits [WuwaRepository]'s radiant-astrite
 * pool (the same one the Profile header's "Argstrites" counter reads from).
 * Previously this was being logged into the real Astrite tracker instead
 * (mislabeled "Daily Sign-In (Argstrite)"), so it never actually showed up
 * as Argstrites anywhere.
 *
 * If the Lunite Pass is active, claiming here ALSO grants its +90 real
 * Astrites (logged normally on the Astrite tracker, same as any other entry)
 * and marks the Lunite Pass Daily Login card as checked-in for the day, so
 * the player doesn't have to tap both buttons separately.
 */
class DailySignInRepository(
    private val context: Context,
    private val astriteRepo: AstriteRepository,
    private val luniteRepo: LuniteRepository,
    private val wuwaRepo: WuwaRepository
) {
    private val KEY_LAST_CLAIM_GAME_DAY = stringPreferencesKey("daily_signin_last_claim_day")

    val lastClaimDayFlow: Flow<String?> = context.dailySignInDataStore.data.map { it[KEY_LAST_CLAIM_GAME_DAY] }

    suspend fun hasClaimedToday(nowUtcMs: Long = System.currentTimeMillis()): Boolean {
        val last = lastClaimDayFlow.first()
        return last == gameDayFor(nowUtcMs).toString()
    }

    /**
     * Claims today's reward. Returns a [DailySignInClaimResult] describing
     * what was granted (split by currency), or null if today was already
     * claimed.
     */
    suspend fun claim(nowUtcMs: Long = System.currentTimeMillis()): DailySignInClaimResult? {
        if (hasClaimedToday(nowUtcMs)) return null

        val dayIso = gameDayFor(nowUtcMs).toString()
        val luniteActive = luniteRepo.isEnabledOnce()

        // Argstrites -> the app's own currency pool, NOT the Astrite log.
        // addRadiantAstrite applies the current Bonus % - use its return
        // value so the claim result reflects what was actually credited.
        val argstritesGranted = wuwaRepo.addRadiantAstrite(DAILY_SIGNIN_BASE_ARGSTRITES)

        var astritesGranted = 0
        if (luniteActive) {
            astritesGranted = LUNITE_DAILY_ASTRITES
            AstriteEconomy(astriteRepo, EconomyRepository(context))
                .earn(LUNITE_DAILY_ASTRITES, EconomyCategories.LUNITE_PASS, "Daily Sign-In", dayIso)
            // Reflect the same check-in on the Lunite Pass Daily Login tracker
            // (Astrite screen) without adding its bonus a second time.
            luniteRepo.markCheckedIn(dayIso)
        }

        context.dailySignInDataStore.edit { it[KEY_LAST_CLAIM_GAME_DAY] = dayIso }
        return DailySignInClaimResult(
            argstritesGranted = argstritesGranted,
            astritesGranted = astritesGranted,
            luniteIncluded = luniteActive
        )
    }
}