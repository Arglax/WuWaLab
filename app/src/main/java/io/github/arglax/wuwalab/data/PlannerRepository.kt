package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.arglax.wuwalab.gacha.GachaMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.plannerDataStore by preferencesDataStore(name = "wuwa_gacha_planner")

/**
 * Everything the Gacha Pull Planner needs to remember between sessions:
 * per-banner pity counters, the character banner's 50/50 guarantee flag and
 * which banner is currently selected.
 *
 * Deliberately does NOT store its own Astrite balance - the planner reads the
 * global balance from [AstriteRepository] (the same flow the Dashboard and the
 * Profile header observe) and spends through [spendAstrites], which routes the
 * write through [AstriteEconomy] so the daily tracker AND the Economic
 * Dashboard's logbook both record it in the same breath.
 */
data class PlannerState(
    val selectedBanner: GachaMath.Banner = GachaMath.Banner.LIMITED_CHARACTER,
    val characterPity: Int = 0,
    val characterGuaranteed: Boolean = false,
    val weaponPity: Int = 0,
    val standardPity: Int = 0
) {
    fun pityFor(banner: GachaMath.Banner): Int = when (banner) {
        GachaMath.Banner.LIMITED_CHARACTER -> characterPity
        GachaMath.Banner.LIMITED_WEAPON -> weaponPity
        GachaMath.Banner.STANDARD -> standardPity
    }

    fun guaranteedFor(banner: GachaMath.Banner): Boolean =
        banner == GachaMath.Banner.LIMITED_CHARACTER && characterGuaranteed
}

class PlannerRepository(
    private val context: Context,
    private val astriteRepo: AstriteRepository,
    private val economyRepo: EconomyRepository = EconomyRepository(context)
) {

    private val economy = AstriteEconomy(astriteRepo, economyRepo)

    private object Keys {
        val SELECTED_BANNER = stringPreferencesKey("planner_selected_banner")
        val CHARACTER_PITY = intPreferencesKey("planner_character_pity")
        val CHARACTER_GUARANTEED = booleanPreferencesKey("planner_character_guaranteed")
        val WEAPON_PITY = intPreferencesKey("planner_weapon_pity")
        val STANDARD_PITY = intPreferencesKey("planner_standard_pity")
    }

    val stateFlow: Flow<PlannerState> = context.plannerDataStore.data.map { prefs ->
        PlannerState(
            selectedBanner = prefs[Keys.SELECTED_BANNER]
                ?.let { stored -> GachaMath.Banner.entries.firstOrNull { it.name == stored } }
                ?: GachaMath.Banner.LIMITED_CHARACTER,
            characterPity = (prefs[Keys.CHARACTER_PITY] ?: 0).coerceIn(0, GachaMath.HARD_PITY - 1),
            characterGuaranteed = prefs[Keys.CHARACTER_GUARANTEED] ?: false,
            weaponPity = (prefs[Keys.WEAPON_PITY] ?: 0).coerceIn(0, GachaMath.HARD_PITY - 1),
            standardPity = (prefs[Keys.STANDARD_PITY] ?: 0).coerceIn(0, GachaMath.HARD_PITY - 1)
        )
    }

    /** The Pull Planner's own convene history, newest last. */
    val pullLogFlow: Flow<List<LedgerEntry>> = economyRepo.entriesFlow.map { all ->
        all.filter { it.isConvene }
    }

    suspend fun getStateOnce(): PlannerState = stateFlow.first()

    suspend fun setSelectedBanner(banner: GachaMath.Banner) {
        context.plannerDataStore.edit { it[Keys.SELECTED_BANNER] = banner.name }
    }

    suspend fun setPity(banner: GachaMath.Banner, pity: Int) {
        val clamped = pity.coerceIn(0, GachaMath.HARD_PITY - 1)
        context.plannerDataStore.edit { prefs ->
            when (banner) {
                GachaMath.Banner.LIMITED_CHARACTER -> prefs[Keys.CHARACTER_PITY] = clamped
                GachaMath.Banner.LIMITED_WEAPON -> prefs[Keys.WEAPON_PITY] = clamped
                GachaMath.Banner.STANDARD -> prefs[Keys.STANDARD_PITY] = clamped
            }
        }
    }

    suspend fun setCharacterGuaranteed(guaranteed: Boolean) {
        context.plannerDataStore.edit { it[Keys.CHARACTER_GUARANTEED] = guaranteed }
    }

    /**
     * Records a convene spend of [pulls] pulls.
     *
     * Three things happen atomically from the caller's point of view:
     *  1. the day's SPENT column grows (earnings are never touched, so the
     *     lifetime "Astrites Earned" figure can't be dragged negative);
     *  2. a full row lands in the Economic Dashboard's logbook, carrying the
     *     banner, the pull count and the pity/guarantee state at that moment;
     *  3. every screen observing the tracker recomposes with the new balance.
     *
     * The spend is validated against the live balance first. If it would
     * overdraw, nothing at all is written and this returns 0 - the UI disables
     * the button in that case, but this is the last line of defense.
     */
    suspend fun spendAstrites(pulls: Int, bannerLabel: String): Int {
        if (pulls <= 0) return 0
        val cost = GachaMath.astritesForPulls(pulls)
        val state = getStateOnce()
        val banner = GachaMath.Banner.entries.firstOrNull { it.shortLabel == bannerLabel } ?: state.selectedBanner
        val outcome = economy.spend(
            amount = cost,
            category = EconomyCategories.CONVENE,
            note = "$pulls pull(s) on $bannerLabel",
            banner = bannerLabel,
            pulls = pulls,
            pity = state.pityFor(banner),
            guaranteed = state.guaranteedFor(banner)
        )
        return when (outcome) {
            is SpendOutcome.Success -> outcome.spent
            else -> 0
        }
    }

    /** Removes a single convene row from the log. Does not refund the Astrites. */
    suspend fun deletePullLogEntry(id: String) = economyRepo.delete(id)
}