package io.github.arglax.wuwalab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private val Context.earnDataStore by preferencesDataStore(name = "wuwa_earn")

/** One arithmetic question - [prompt] is display text, [answer] is the expected integer result. */
data class EarnQuestion(val prompt: String, val answer: Int)

/** Reward table: index = number of correct answers (0..5), value = Argstrites granted. */
val EARN_QUIZ_REWARD_TABLE = mapOf(0 to 0, 1 to 5, 2 to 10, 3 to 20, 4 to 30, 5 to 50)

data class EarnDailyState(
    // ISO date (Manila reset-aligned "game day") this state belongs to - a
    // stored state from a different day is stale and gets regenerated.
    val day: String = "",
    val questions: List<EarnQuestion> = emptyList(),
    val completed: Boolean = false,
    val correctCount: Int = 0,
    val rewardGranted: Int = 0
)

/**
 * Generates 5 "tricky but easy-moderate" arithmetic questions - everything
 * from a single operation up through order-of-operations (PEMDAS) chains,
 * deliberately no exponents or roots. All division is guaranteed to land on
 * a whole number so there's never an ambiguous "round which way?" answer.
 */
private object EarnQuestionGenerator {

    fun generate(seed: Long): List<EarnQuestion> {
        val rng = Random(seed)
        // One of each "shape" so the 5 questions ramp up in trickiness rather
        // than being 5 independent coin flips that might all land easy.
        val shapes = listOf(
            ::simpleAddSub, ::simpleMulDiv, ::chainNoParens, ::chainWithParens, ::fourTermPemdas
        ).shuffled(rng)
        return shapes.map { it(rng) }
    }

    private fun simpleAddSub(rng: Random): EarnQuestion {
        val a = rng.nextInt(12, 90)
        val b = rng.nextInt(5, 50)
        return if (rng.nextBoolean()) {
            EarnQuestion("$a + $b", a + b)
        } else {
            val (big, small) = if (a >= b) a to b else b to a
            EarnQuestion("$big - $small", big - small)
        }
    }

    private fun simpleMulDiv(rng: Random): EarnQuestion {
        return if (rng.nextBoolean()) {
            val a = rng.nextInt(3, 12)
            val b = rng.nextInt(3, 12)
            EarnQuestion("$a x $b", a * b)
        } else {
            val b = rng.nextInt(2, 12)
            val result = rng.nextInt(2, 12)
            val a = b * result
            EarnQuestion("$a / $b", result)
        }
    }

    // No parentheses - correct only if you apply * and / before + and -.
    private fun chainNoParens(rng: Random): EarnQuestion {
        val a = rng.nextInt(2, 15)
        val b = rng.nextInt(2, 9)
        val c = rng.nextInt(2, 9)
        return if (rng.nextBoolean()) {
            // a + b * c
            EarnQuestion("$a + $b x $c", a + b * c)
        } else {
            // a x b - c
            EarnQuestion("$a x $b - $c", a * b - c)
        }
    }

    // Parentheses force a different order than the "obvious" left-to-right read.
    private fun chainWithParens(rng: Random): EarnQuestion {
        val a = rng.nextInt(2, 15)
        val b = rng.nextInt(2, 12)
        val c = rng.nextInt(2, 9)
        return if (rng.nextBoolean()) {
            // (a + b) x c
            EarnQuestion("($a + $b) x $c", (a + b) * c)
        } else {
            // a x (b + c)
            EarnQuestion("$a x ($b + $c)", a * (b + c))
        }
    }

    // A full 4-term PEMDAS chain - the "hardest" of the 5.
    private fun fourTermPemdas(rng: Random): EarnQuestion {
        val a = rng.nextInt(10, 30)
        val b = rng.nextInt(2, 9)
        val c = rng.nextInt(2, 9)
        val d = rng.nextInt(2, 20)
        return if (rng.nextBoolean()) {
            // a + b * c - d
            EarnQuestion("$a + $b x $c - $d", a + b * c - d)
        } else {
            // (a - d) + b * c
            EarnQuestion("($a - $d) + $b x $c", (a - d) + b * c)
        }
    }
}

/**
 * The "Earn" daily quiz: 5 randomly generated arithmetic questions, one
 * attempt per Manila game-day (same 4:00 AM reset boundary as everything
 * else daily in the app). Correct-answer counts map to a fixed Argstrite
 * payout via [EARN_QUIZ_REWARD_TABLE], granted immediately on submission.
 */
class EarnRepository(private val context: Context, private val wuwaRepo: WuwaRepository) {

    private val KEY_STATE = stringPreferencesKey("earn_daily_state_json")

    private val rawStateFlow: Flow<EarnDailyState> = context.earnDataStore.data.map { prefs ->
        parse(prefs[KEY_STATE])
    }

    /** Today's quiz state, regenerating a fresh question set if the stored one is from a prior game-day. */
    val todayStateFlow: Flow<EarnDailyState> = rawStateFlow.map { stored ->
        val today = gameDayFor().toString()
        if (stored.day == today) stored else EarnDailyState(day = today, questions = EarnQuestionGenerator.generate(seedFor(today)))
    }

    suspend fun getTodayStateOnce(): EarnDailyState {
        val state = todayStateFlow.first()
        // Persist a freshly-generated set immediately so re-opening the
        // screen mid-attempt (before submitting) shows the SAME 5 questions,
        // not a new random set every recomposition/reopen.
        if (rawStateFlow.first().day != state.day) persist(state)
        return state
    }

    /** Grades [answers] (same order as the stored questions), grants the reward once, and persists completion. */
    suspend fun submit(answers: List<Int?>): EarnDailyState {
        val state = getTodayStateOnce()
        if (state.completed) return state // already attempted today - no double-dipping.

        val correctCount = state.questions.indices.count { i -> answers.getOrNull(i) == state.questions[i].answer }
        val baseReward = EARN_QUIZ_REWARD_TABLE[correctCount] ?: 0
        // addRadiantAstrite applies the current Argstrite Bonus % (from
        // Achievements/Supporter) - store the ACTUAL credited amount here so
        // "Today's Quiz Complete" shows what was really earned, not the
        // pre-bonus table value.
        val reward = if (baseReward > 0) wuwaRepo.addRadiantAstrite(baseReward) else 0

        val finished = state.copy(completed = true, correctCount = correctCount, rewardGranted = reward)
        persist(finished)
        return finished
    }

    private fun seedFor(day: String): Long = day.hashCode().toLong()

    private suspend fun persist(state: EarnDailyState) {
        context.earnDataStore.edit { it[KEY_STATE] = serialize(state) }
    }

    private fun serialize(state: EarnDailyState): String {
        val json = JSONObject()
        json.put("day", state.day)
        json.put("completed", state.completed)
        json.put("correctCount", state.correctCount)
        json.put("rewardGranted", state.rewardGranted)
        val arr = JSONArray()
        state.questions.forEach { q ->
            arr.put(JSONObject().apply { put("prompt", q.prompt); put("answer", q.answer) })
        }
        json.put("questions", arr)
        return json.toString()
    }

    private fun parse(raw: String?): EarnDailyState {
        if (raw.isNullOrBlank()) return EarnDailyState()
        return try {
            val json = JSONObject(raw)
            val arr = json.optJSONArray("questions") ?: JSONArray()
            val questions = (0 until arr.length()).map { i ->
                val q = arr.getJSONObject(i)
                EarnQuestion(q.getString("prompt"), q.getInt("answer"))
            }
            EarnDailyState(
                day = json.optString("day", ""),
                questions = questions,
                completed = json.optBoolean("completed", false),
                correctCount = json.optInt("correctCount", 0),
                rewardGranted = json.optInt("rewardGranted", 0)
            )
        } catch (_: Exception) {
            EarnDailyState()
        }
    }
}