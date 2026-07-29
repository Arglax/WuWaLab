package io.github.arglax.wuwalab.data

import kotlin.math.ceil

/**
 * The single place the Argstrite bonus pipeline is defined. Everything that
 * pays out Argstrites - sign-in, quiz, redeem, feature rewards, Bank interest -
 * runs through here so no two screens can disagree about what a number is.
 *
 * The one rule that is easy to get wrong: MULTIPLIERS ARE ADDITIVE, never
 * compounding. Holding a x2 and a x5 gives x7, not x10. Each multiplier source
 * contributes its own face value to a shared pool rather than scaling whatever
 * came before it, which keeps the ceiling predictable no matter how many
 * multiplier titles and app events land on the same day.
 */
object BonusMath {

    /**
     * Sums the face value of every ACTIVE multiplier term (anything above x1),
     * falling back to x1 when nothing is active.
     *
     *   nothing active        -> x1
     *   x2 event only         -> x2
     *   x10 title + x2 event  -> x12
     *   x2 title + x5 title   -> x7   (NOT x10)
     */
    fun combineMultipliers(terms: List<Float>): Float {
        val active = terms.filter { it > 1f }
        return if (active.isEmpty()) 1f else active.sum()
    }

    fun combineMultipliers(vararg terms: Float): Float = combineMultipliers(terms.toList())

    /**
     * Scales a positive earning by the bonus percent pool and then by the
     * combined additive multiplier. Deductions (amount <= 0) pass through
     * untouched. Always rounds UP - if the scaled result isn't whole, the
     * player gets the benefit of the doubt rather than losing a fraction to
     * truncation.
     */
    fun apply(amount: Int, bonusPercent: Float, multiplier: Float): Int {
        if (amount <= 0) return amount
        if (bonusPercent <= 0f && multiplier <= 1f) return amount
        return ceil(amount * (1.0 + bonusPercent / 100.0) * multiplier).toInt()
    }
}
