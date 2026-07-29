package io.github.arglax.wuwalab.gacha

/**
 * Pure, UI-free Wuthering Waves convene math. Everything the planner shows is
 * derived from here so the numbers stay consistent between the summary cards
 * and the projection chart.
 *
 * Model (per the app's spec):
 *  - 160 Astrites = 1 pull (Radiant/Forging/Lustrous Tide equivalent).
 *  - Hard pity: the 80th pull since the last 5★ is a guaranteed 5★.
 *  - Base 5★ rate: 0.8% per pull for pulls 1..65.
 *  - Soft pity: starting at pull 66 the per-pull rate ramps up linearly
 *    (+6% per pull) until hard pity at 80 forces 100%.
 *  - Limited Character banner (Radiant Tide): winning the 5★ is a 50/50
 *    between the featured character and a standard one; losing the 50/50
 *    guarantees the NEXT 5★ is the featured one.
 *  - Limited Weapon banner (Forging Tide): the featured weapon is 100%
 *    guaranteed on any 5★ (no 50/50 in WuWa, unlike some other gachas).
 *  - Standard banner (Lustrous Tide): 80 hard pity, no rate-up concept -
 *    "success" simply means any 5★.
 */
object GachaMath {

    const val ASTRITES_PER_PULL = 160
    const val HARD_PITY = 80
    const val SOFT_PITY_START = 66
    const val BASE_RATE = 0.008
    const val SOFT_PITY_RAMP_PER_PULL = 0.06

    enum class Banner(
        val label: String,
        val shortLabel: String,
        val hasFiftyFifty: Boolean
    ) {
        LIMITED_CHARACTER(label = "Radiant Tide (Limited Character)", shortLabel = "Character", hasFiftyFifty = true),
        LIMITED_WEAPON(label = "Forging Tide (Limited Weapon)", shortLabel = "Weapon", hasFiftyFifty = false),
        STANDARD(label = "Lustrous Tide (Standard)", shortLabel = "Standard", hasFiftyFifty = false)
    }

    fun pullsFromAstrites(astrites: Int): Int =
        (astrites / ASTRITES_PER_PULL).coerceAtLeast(0)

    fun astritesForPulls(pulls: Int): Int = pulls * ASTRITES_PER_PULL

    /**
     * Per-pull 5★ rate given the current pity counter. [pity] is the number
     * of consecutive non-5★ pulls already made, so the pull about to happen
     * is pull number (pity + 1) of the cycle.
     */
    fun rateAtPity(pity: Int): Double {
        val pullNumber = pity + 1
        return when {
            pullNumber >= HARD_PITY -> 1.0
            pullNumber < SOFT_PITY_START -> BASE_RATE
            else -> (BASE_RATE + (pullNumber - (SOFT_PITY_START - 1)) * SOFT_PITY_RAMP_PER_PULL)
                .coerceAtMost(1.0)
        }
    }

    /**
     * Cumulative probability of pulling AT LEAST ONE 5★ (any 5★) within the
     * next [pulls] pulls, starting from [currentPity]. Index i of the result
     * is the probability within (i + 1) pulls; the list has [pulls] entries.
     */
    fun anyFiveStarCurve(currentPity: Int, pulls: Int): List<Double> {
        if (pulls <= 0) return emptyList()
        val out = ArrayList<Double>(pulls)
        var probNo5StarYet = 1.0
        var pity = currentPity.coerceIn(0, HARD_PITY - 1)
        repeat(pulls) {
            val r = rateAtPity(pity)
            probNo5StarYet *= (1.0 - r)
            out.add(1.0 - probNo5StarYet)
            // If a 5★ had dropped, that branch is already absorbed into the
            // cumulative probability; the surviving "no 5★ yet" branch keeps
            // climbing pity. Hard pity zeroes the surviving branch anyway.
            pity = if (r >= 1.0) 0 else pity + 1
        }
        return out
    }

    /**
     * Cumulative probability of obtaining the FEATURED unit within the next
     * [pulls] pulls. Exact dynamic program over (pity, guaranteedNext5Star):
     *
     *  - each pull hits a 5★ with rateAtPity(pity);
     *  - on a 5★: featured immediately if the banner has no 50/50 or the
     *    guarantee is active; otherwise 50% featured (success) / 50% standard
     *    (pity resets, guarantee turns on);
     *  - on a non-5★: pity += 1.
     *
     * For [Banner.STANDARD] and [Banner.LIMITED_WEAPON] with no active
     * guarantee semantics this degrades gracefully (weapon = every 5★ is
     * featured; standard = success means any 5★).
     */
    fun featuredCurve(
        banner: Banner,
        currentPity: Int,
        guaranteed: Boolean,
        pulls: Int
    ): List<Double> {
        if (pulls <= 0) return emptyList()
        if (!banner.hasFiftyFifty) return anyFiveStarCurve(currentPity, pulls)

        // State: probability mass still WITHOUT the featured unit, indexed by
        // [guaranteeFlag][pity]. pity is capped at HARD_PITY - 1 because the
        // 80th pull always resolves.
        val size = HARD_PITY
        var noWin = Array(2) { DoubleArray(size) }
        val startPity = currentPity.coerceIn(0, size - 1)
        noWin[if (guaranteed) 1 else 0][startPity] = 1.0

        var success = 0.0
        val out = ArrayList<Double>(pulls)

        repeat(pulls) {
            val next = Array(2) { DoubleArray(size) }
            for (g in 0..1) {
                val row = noWin[g]
                for (p in 0 until size) {
                    val mass = row[p]
                    if (mass == 0.0) continue
                    val r = rateAtPity(p)
                    val hit = mass * r
                    val miss = mass * (1.0 - r)
                    if (hit > 0.0) {
                        if (g == 1) {
                            success += hit // guarantee active: featured for sure
                        } else {
                            success += hit * 0.5                 // won the 50/50
                            next[1][0] += hit * 0.5              // lost it: pity 0, guarantee on
                        }
                    }
                    if (miss > 0.0) {
                        val np = (p + 1).coerceAtMost(size - 1)
                        next[g][np] += miss
                    }
                }
            }
            noWin = next
            out.add(success.coerceIn(0.0, 1.0))
        }
        return out
    }

    /**
     * Non-cumulative version of [featuredCurve]: index i is the probability
     * that the featured unit is obtained on EXACTLY pull (i + 1) - i.e. not
     * obtained in the first i pulls, then obtained on the very next one -
     * rather than the "at least once by pull i+1" figure the cumulative
     * curve shows. Simple first-difference of the cumulative curve, since
     * P(exactly at pull k) = P(cumulative by k) - P(cumulative by k-1).
     */
    fun featuredCurveNonCumulative(
        banner: Banner,
        currentPity: Int,
        guaranteed: Boolean,
        pulls: Int
    ): List<Double> {
        val cumulative = featuredCurve(banner, currentPity, guaranteed, pulls)
        if (cumulative.isEmpty()) return emptyList()
        val out = ArrayList<Double>(cumulative.size)
        var previous = 0.0
        cumulative.forEach { cum ->
            out.add((cum - previous).coerceAtLeast(0.0))
            previous = cum
        }
        return out
    }

    /** Non-cumulative counterpart of [anyFiveStarCurve] - see [featuredCurveNonCumulative]. */
    fun anyFiveStarCurveNonCumulative(currentPity: Int, pulls: Int): List<Double> {
        val cumulative = anyFiveStarCurve(currentPity, pulls)
        if (cumulative.isEmpty()) return emptyList()
        val out = ArrayList<Double>(cumulative.size)
        var previous = 0.0
        cumulative.forEach { cum ->
            out.add((cum - previous).coerceAtLeast(0.0))
            previous = cum
        }
        return out
    }

    /** Probability of the featured unit with exactly the pulls the player can afford right now. */
    fun chanceWithBudget(
        banner: Banner,
        currentPity: Int,
        guaranteed: Boolean,
        astrites: Int
    ): Double {
        val pulls = pullsFromAstrites(astrites)
        if (pulls <= 0) return 0.0
        return featuredCurve(banner, currentPity, guaranteed, pulls).last()
    }

    /**
     * Worst-case pulls still needed to force the featured unit:
     * character banner without guarantee = two full pities (lose 50/50 at
     * hard pity, then hit hard pity again); everything else = one pity.
     */
    fun worstCasePullsToFeatured(banner: Banner, currentPity: Int, guaranteed: Boolean): Int {
        val toHardPity = (HARD_PITY - currentPity).coerceAtLeast(1)
        return if (banner.hasFiftyFifty && !guaranteed) toHardPity + HARD_PITY else toHardPity
    }
}