package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.achievementsDataStore by preferencesDataStore(name = "wuwa_achievements")

/** Which running counter an [Achievement] is measured against. See the call sites noted on each counter for where it's incremented. */
enum class AchievementCounter(val id: String) {
    // Incremented in MainActivity right after a successful DailySignInRepository.claim().
    SIGNIN_CLAIMS("signin_claims"),
    // Incremented in GachaPlannerScreen right after PlannerRepository.spendAstrites() (a "pull log").
    PULLS_LOGGED("pulls_logged"),
    // Same call site as PULLS_LOGGED - every pull log in this app carries an
    // auto-generated note ("N pull(s) on <banner>"), so in practice this
    // tracks in lockstep with PULLS_LOGGED. Kept as its own counter (rather
    // than reusing PULLS_LOGGED) so a future "notes become optional for
    // pulls" change wouldn't require restructuring the achievement.
    PULLS_LOGGED_WITH_NOTES("pulls_logged_with_notes"),
    // Incremented in TodoScreen when a task's checkbox flips false -> true.
    TODOS_COMPLETED("todos_completed"),
    // Same call site as TODOS_COMPLETED, only when item.description is non-blank.
    TODOS_COMPLETED_WITH_NOTES("todos_completed_with_notes"),
    // Incremented in EisenhowerMatrixScreen whenever an item is dropped onto
    // a real quadrant (i.e. TodoRepository.setQuadrant() to anything other
    // than UNASSIGNED) - a proxy for "actively organizing the Matrix".
    MATRIX_ASSIGNMENTS("matrix_assignments")
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val counter: AchievementCounter,
    val goal: Int,
    val rewardArgstrites: Int,
    // Every regular achievement stacks a flat +3% onto the cumulative Bonus %.
    val bonusPercent: Float = 3f
)

/** The one hard-coded special case - see class doc on [AchievementsRepository.evaluateUnlocks]. */
private val SUPPORTER_ACHIEVEMENT = Achievement(
    id = "supporter_title_bonus",
    title = "WuWaLab Supporter",
    description = "Redeem the YOUREALLYDIDSUPPORTMEBROOMFG code.",
    counter = AchievementCounter.SIGNIN_CLAIMS, // unused for this one - see isSupporterUnlocked()
    goal = Int.MAX_VALUE,
    rewardArgstrites = 500,
    bonusPercent = 20f
)

/** The fixed catalog of count-based achievements. Thresholds are a reasonable default - easy to retune later. */
val ACHIEVEMENT_CATALOG: List<Achievement> = listOf(
    Achievement(
        id = "consistent_farmer",
        title = "Consistent Farmer",
        description = "Claim the Daily Sign-In 7 times.",
        counter = AchievementCounter.SIGNIN_CLAIMS,
        goal = 7,
        rewardArgstrites = 200
    ),
    Achievement(
        id = "pull_historian",
        title = "Pull Historian",
        description = "Log 10 pulls in the Pull Planner.",
        counter = AchievementCounter.PULLS_LOGGED,
        goal = 10,
        rewardArgstrites = 25
    ),
    Achievement(
        id = "detailed_puller",
        title = "Detailed Puller",
        description = "Log 10 pulls with notes attached.",
        counter = AchievementCounter.PULLS_LOGGED_WITH_NOTES,
        goal = 10,
        rewardArgstrites = 25
    ),
    Achievement(
        id = "task_finisher",
        title = "Task Finisher",
        description = "Complete 20 To-Dos.",
        counter = AchievementCounter.TODOS_COMPLETED,
        goal = 20,
        rewardArgstrites = 50
    ),
    Achievement(
        id = "thorough_planner",
        title = "Thorough Planner",
        description = "Complete 20 To-Dos with notes attached.",
        counter = AchievementCounter.TODOS_COMPLETED_WITH_NOTES,
        goal = 20,
        rewardArgstrites = 50
    ),
    Achievement(
        id = "matrix_custodian",
        title = "Matrix Custodian",
        description = "Organize 15 items onto the Eisenhower Matrix.",
        counter = AchievementCounter.MATRIX_ASSIGNMENTS,
        goal = 15,
        rewardArgstrites = 50
    )
)

data class AchievementUiState(
    val achievement: Achievement,
    val progress: Int,
    val unlocked: Boolean
)

/**
 * Tracks count-based Achievements and the cumulative Argstrite "Bonus %"
 * they permanently stack into. Every unlock grants a one-time flat Argstrite
 * reward AND a permanent +N% added to [WuwaRepository.bonusPercentFlow] -
 * the single number [WuwaRepository.addRadiantAstrite] / [WuwaRepository.addPendingArgstrite]
 * actually multiply every future earning by, so this is never just cosmetic.
 *
 * The `YOUREALLYDIDSUPPORTMEBROOMFG` Redeem code is a special case: the
 * moment [RedeemRepository] shows that code as redeemed, an extra
 * "WuWaLab Supporter" achievement auto-unlocks on top of the regular six,
 * worth +200 Argstrites flat and +20% - stacked, not substituted.
 */
class AchievementsRepository(
    private val context: Context,
    private val wuwaRepo: WuwaRepository,
    private val redeemRepo: RedeemRepository
) {
    private val KEY_PROGRESS = stringPreferencesKey("achievement_progress_json") // counterId -> count
    private val KEY_UNLOCKED = stringPreferencesKey("achievement_unlocked_ids_json") // JSON array of achievement ids

    private val progressFlow: Flow<Map<String, Int>> = context.achievementsDataStore.data.map { prefs ->
        parseProgress(prefs[KEY_PROGRESS])
    }
    private val unlockedIdsFlow: Flow<Set<String>> = context.achievementsDataStore.data.map { prefs ->
        parseUnlocked(prefs[KEY_UNLOCKED])
    }

    /** Every catalog achievement plus its live progress/unlocked state, for the Profile summary's Achievements section. */
    val achievementsFlow: Flow<List<AchievementUiState>> = combine(progressFlow, unlockedIdsFlow) { progress, unlocked ->
        ACHIEVEMENT_CATALOG.map { a ->
            AchievementUiState(a, progress[a.counter.id] ?: 0, unlocked.contains(a.id))
        }
    }

    /** Whether the Supporter bonus is currently unlocked - drives its own card in the Achievements section. */
    val supporterUnlockedFlow: Flow<Boolean> = redeemRepo.redeemedCodesFlow.map { it.contains("YOUREALLYDIDSUPPORTMEBROOMFG") }

    /** The Bonus % shown in the profile summary - always the live sum, never a snapshot. Mirrors [WuwaRepository.bonusPercentFlow] 1:1. */
    val bonusPercentFlow: Flow<Float> = wuwaRepo.bonusPercentFlow

    /** The cumulative title bonus MULTIPLIER (e.g. x10) - kept separate from [bonusPercentFlow] since it must never be folded into a plain percent. Mirrors [WuwaRepository.bonusMultiplierFlow] 1:1. */
    val bonusMultiplierFlow: Flow<Float> = wuwaRepo.bonusMultiplierFlow

    suspend fun recordSignInClaim() = incrementAndEvaluate(AchievementCounter.SIGNIN_CLAIMS)

    suspend fun recordPullLogged(hadNote: Boolean) {
        incrementAndEvaluate(AchievementCounter.PULLS_LOGGED)
        if (hadNote) incrementAndEvaluate(AchievementCounter.PULLS_LOGGED_WITH_NOTES)
    }

    suspend fun recordTodoCompleted(hadNote: Boolean) {
        incrementAndEvaluate(AchievementCounter.TODOS_COMPLETED)
        if (hadNote) incrementAndEvaluate(AchievementCounter.TODOS_COMPLETED_WITH_NOTES)
    }

    suspend fun recordMatrixAssignment() = incrementAndEvaluate(AchievementCounter.MATRIX_ASSIGNMENTS)

    private suspend fun incrementAndEvaluate(counter: AchievementCounter) {
        val current = progressFlow.first().toMutableMap()
        current[counter.id] = (current[counter.id] ?: 0) + 1
        persistProgress(current)
        evaluateUnlocks(current)
    }

    /**
     * Unlocks any not-yet-unlocked achievement whose counter has reached its
     * goal, grants its flat reward, then recomputes the FULL cumulative
     * Bonus % (every unlocked achievement's +1%, plus +20% if the Supporter
     * code has been redeemed) and pushes that total into [WuwaRepository] so
     * it's immediately reflected in every subsequent earning - not just
     * displayed in the UI.
     */
    private suspend fun evaluateUnlocks(progress: Map<String, Int>) {
        val unlocked = unlockedIdsFlow.first().toMutableSet()
        var changed = false
        ACHIEVEMENT_CATALOG.forEach { a ->
            if (!unlocked.contains(a.id) && (progress[a.counter.id] ?: 0) >= a.goal) {
                unlocked.add(a.id)
                changed = true
                wuwaRepo.addRadiantAstrite(a.rewardArgstrites)
            }
        }
        if (changed) persistUnlocked(unlocked)
        recomputeBonusPercent(unlocked)
    }

    /**
     * Call this after every Redeem attempt (or on app start) - separate from
     * [evaluateUnlocks] because the Supporter unlock isn't counter-driven, it
     * flips the instant that specific code gets redeemed.
     */
    suspend fun checkSupporterUnlock() {
        val redeemed = redeemRepo.getRedeemedOnce().contains("YOUREALLYDIDSUPPORTMEBROOMFG")
        val unlocked = unlockedIdsFlow.first()
        val alreadyGranted = unlocked.contains(SUPPORTER_ACHIEVEMENT.id)
        if (redeemed && !alreadyGranted) {
            val next = unlocked + SUPPORTER_ACHIEVEMENT.id
            persistUnlocked(next)
            wuwaRepo.addRadiantAstrite(SUPPORTER_ACHIEVEMENT.rewardArgstrites)
            recomputeBonusPercent(next)
        }
    }

    private suspend fun recomputeBonusPercent(unlockedIds: Set<String>) {
        val fromAchievements = ACHIEVEMENT_CATALOG.filter { unlockedIds.contains(it.id) }.sumOf { it.bonusPercent.toDouble() }.toFloat()
        val fromSupporter = if (unlockedIds.contains(SUPPORTER_ACHIEVEMENT.id)) SUPPORTER_ACHIEVEMENT.bonusPercent else 0f
        wuwaRepo.setBonusPercent(fromAchievements + fromSupporter)
    }

    private suspend fun persistProgress(progress: Map<String, Int>) {
        context.achievementsDataStore.edit { it[KEY_PROGRESS] = JSONObject(progress as Map<*, *>).toString() }
    }

    private suspend fun persistUnlocked(ids: Set<String>) {
        context.achievementsDataStore.edit { it[KEY_UNLOCKED] = org.json.JSONArray(ids.toList()).toString() }
    }

    private fun parseProgress(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.optInt(it, 0) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseUnlocked(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}