package io.github.arglax.wuwalab.ui.earn

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.data.EARN_QUIZ_REWARD_TABLE
import io.github.arglax.wuwalab.data.EarnDailyState
import io.github.arglax.wuwalab.data.EarnRepository
import io.github.arglax.wuwalab.data.nextResetEpochMs
import io.github.arglax.wuwalab.ui.components.GlassCard
import io.github.arglax.wuwalab.ui.components.HelpButton
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow
import io.github.arglax.wuwalab.util.rememberTapFeedback
import kotlinx.coroutines.launch

/** Live "resets in Hh Mm" countdown to the next 4:00 AM Manila reset. */
@Composable
private fun rememberResetCountdown(): String {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000)
        }
    }
    val remainingMs = (nextResetEpochMs(now) - now).coerceAtLeast(0)
    val hours = remainingMs / (60 * 60 * 1000)
    val minutes = (remainingMs / (60 * 1000)) % 60
    return "${hours}h ${minutes}m"
}

/**
 * The Earn page: 5 randomly generated PEMDAS-flavored arithmetic questions,
 * one attempt per day. Correct answers map to a fixed Argstrite payout - see
 * [EARN_QUIZ_REWARD_TABLE].
 */
@Composable
fun EarnScreen(
    earnRepo: EarnRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val tapFeedback = rememberTapFeedback()
    val resetCountdown = rememberResetCountdown()

    var state by remember { mutableStateOf<EarnDailyState?>(null) }
    var answers by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = earnRepo.getTodayStateOnce()
        state = loaded
        answers = List(loaded.questions.size) { "" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Earn",
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            HelpButton(
                title = "Earn Help",
                body = "5 random arithmetic questions - basic operations up through order-of-operations " +
                    "(PEMDAS) chains, no exponents or roots. Answer as many correctly as you can: " +
                    "1 correct = 5 Argstrites, 2 = 10, 3 = 20, 4 = 30, all 5 = 50. " +
                    "One attempt per day, resetting at the same 4:00 AM reset as everything else."
            )
        }
        Spacer(Modifier.height(14.dp))

        val current = state
        if (current == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VioletGlow)
            }
            return@Column
        }

        if (current.completed) {
            GlassCard(modifier = Modifier.fillMaxWidth(), accent = if (current.rewardGranted > 0) EmeraldGlow else CoralGlow) {
                Text(
                    "Today's Quiz Complete",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${current.correctCount} / ${current.questions.size} correct",
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(R.drawable.ic_radiant_astrite), contentDescription = null, modifier = Modifier.height(18.dp).width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "+${current.rewardGranted} Argstrites earned",
                        color = if (current.rewardGranted > 0) EmeraldGlow else TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Come back after reset for another attempt - resets in $resetCountdown.",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
            return@Column
        }

        Text(
            "Answer all 5 - you only get one attempt per day.",
            color = TextSecondary,
            fontSize = MaterialTheme.typography.bodySmall.fontSize
        )
        Spacer(Modifier.height(14.dp))

        current.questions.forEachIndexed { index, question ->
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), accent = VioletGlow) {
                Text(
                    "Q${index + 1}.  ${question.prompt} = ?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.titleSmall.fontSize
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = answers.getOrElse(index) { "" },
                    onValueChange = { newVal ->
                        val cleaned = newVal.filter { it.isDigit() || it == '-' }
                        answers = answers.toMutableList().also { if (index < it.size) it[index] = cleaned }
                    },
                    placeholder = { Text("Your answer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        val allAnswered = answers.size == current.questions.size && answers.all { it.isNotBlank() }
        Button(
            onClick = {
                tapFeedback()
                isSubmitting = true
                scope.launch {
                    val parsed = answers.map { it.toIntOrNull() }
                    val result = earnRepo.submit(parsed)
                    state = result
                    isSubmitting = false
                }
            },
            enabled = allAnswered && !isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = AmberGlow, contentColor = androidx.compose.ui.graphics.Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSubmitting) "Grading..." else "Submit Answers")
        }
    }
}