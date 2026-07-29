package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.redeemDataStore by preferencesDataStore(name = "wuwalab_redeem")

/** What a redeem code actually grants. */
sealed class RedeemReward {
    data class Argstrites(val amount: Int) : RedeemReward()
    data class Title(val shopItemId: String, val titleName: String) : RedeemReward()
    // The Argl4xTh3Best code: grants both a title (with its own permanent
    // Bonus %) AND a flat Argstrite pile in one redemption.
    data class TitleAndArgstrites(val shopItemId: String, val titleName: String, val amount: Int) : RedeemReward()
}

/** How a redemption attempt turned out. */
sealed class RedeemResult {
    data class Success(val reward: RedeemReward) : RedeemResult()
    data object AlreadyRedeemed : RedeemResult()
    data object InvalidCode : RedeemResult()
}

/**
 * A small, fixed catalog of promo codes - each one redeemable exactly once
 * per device. Codes are matched case-insensitively (trimmed of surrounding
 * whitespace) so a stray Shift key doesn't cost someone their reward.
 */
object RedeemCatalog {
    private val codes: Map<String, RedeemReward> = mapOf(
        "ARGLAXGOTWUWA" to RedeemReward.Argstrites(50),
        "CANIHAVESOMEARGS" to RedeemReward.Argstrites(50),
        "HOLYSH_IMATESTER?!" to RedeemReward.Title("title_tester", "Tester"),
        "YOUREALLYDIDSUPPORTMEBROOMFG" to RedeemReward.Title("title_supporter", "WuWaLab Supporter"),
        "ARGL4XTH3BEST" to RedeemReward.TitleAndArgstrites("title_argl4x_best", "Argl4xTh3Best", 100_000)
    )

    fun rewardFor(code: String): RedeemReward? = codes[code.trim().uppercase()]
}

/**
 * Tracks which codes THIS device has already redeemed - every code in
 * [RedeemCatalog] is one-time-only, globally (not per-day or per-account,
 * since WuWaLab has no accounts).
 */
class RedeemRepository(
    private val context: Context,
    private val wuwaRepo: WuwaRepository,
    private val shopRepo: ShopRepository
) {
    private val KEY_REDEEMED = stringSetPreferencesKey("redeemed_codes")

    val redeemedCodesFlow: Flow<Set<String>> = context.redeemDataStore.data.map { it[KEY_REDEEMED] ?: emptySet() }

    suspend fun getRedeemedOnce(): Set<String> = redeemedCodesFlow.first()

    suspend fun redeem(rawCode: String): RedeemResult {
        val normalized = rawCode.trim().uppercase()
        if (normalized.isBlank()) return RedeemResult.InvalidCode

        val reward = RedeemCatalog.rewardFor(normalized) ?: return RedeemResult.InvalidCode
        val already = getRedeemedOnce()
        if (already.contains(normalized)) return RedeemResult.AlreadyRedeemed

        when (reward) {
            is RedeemReward.Argstrites -> wuwaRepo.addRadiantAstrite(reward.amount)
            is RedeemReward.Title -> shopRepo.grantOwnershipSuspend(reward.shopItemId)
            is RedeemReward.TitleAndArgstrites -> {
                // Grant the title (and its Bonus %) first, so the flat
                // Argstrite pile that follows is credited with that title's
                // bonus already cumulative on top of it.
                shopRepo.grantOwnershipSuspend(reward.shopItemId)
                wuwaRepo.addRadiantAstrite(reward.amount)
            }
        }

        context.redeemDataStore.edit { prefs ->
            prefs[KEY_REDEEMED] = (prefs[KEY_REDEEMED] ?: emptySet()) + normalized
        }
        return RedeemResult.Success(reward)
    }
}