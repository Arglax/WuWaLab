package io.github.arglax.wuwalab.ui.redeem

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.data.RedeemRepository
import io.github.arglax.wuwalab.data.RedeemResult
import io.github.arglax.wuwalab.data.RedeemReward
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

/**
 * The Redeem page - a simple textbox + button for one-time promo codes.
 * Every code in [io.github.arglax.wuwalab.data.RedeemCatalog] can only ever
 * be redeemed once per device; redeeming it a second time is rejected with a
 * clear "already redeemed" message rather than silently granting the reward
 * again.
 */
@Composable
fun RedeemScreen(
    redeemRepo: RedeemRepository,
    achievementsRepo: io.github.arglax.wuwalab.data.AchievementsRepository? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val tapFeedback = rememberTapFeedback()

    var codeInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var isRedeeming by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Redeem",
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            HelpButton(
                title = "Redeem Help",
                body = "Enter a promo code to claim its reward - Argstrites, a title, or " +
                    "whatever else it's set up to grant. Every code only works once, ever, " +
                    "so double-check for typos before submitting."
            )
        }
        Spacer(Modifier.height(14.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), accent = VioletGlow) {
            Text(
                "Have a code?",
                color = TextSecondary,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it },
                placeholder = { Text("Enter code") },
                singleLine = true,
                leadingIcon = {
                    Image(painter = painterResource(R.drawable.ic_radiant_astrite), contentDescription = null, modifier = Modifier.size(20.dp))
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    tapFeedback()
                    val submitted = codeInput
                    isRedeeming = true
                    scope.launch {
                        when (val result = redeemRepo.redeem(submitted)) {
                            is RedeemResult.Success -> {
                                messageIsError = false
                                message = when (val reward = result.reward) {
                                    is RedeemReward.Argstrites -> "Code redeemed! +${reward.amount} Argstrites."
                                    is RedeemReward.Title -> "Code redeemed! \"${reward.titleName}\" title unlocked - equip it from your Profile."
                                    is RedeemReward.TitleAndArgstrites -> "Code redeemed! \"${reward.titleName}\" title unlocked with +${reward.amount} Argstrites - equip it from your Profile."
                                }
                                codeInput = ""
                                achievementsRepo?.checkSupporterUnlock()
                            }
                            RedeemResult.AlreadyRedeemed -> {
                                messageIsError = true
                                message = "That code has already been redeemed."
                            }
                            RedeemResult.InvalidCode -> {
                                messageIsError = true
                                message = "That code isn't valid. Double-check for typos."
                            }
                        }
                        isRedeeming = false
                    }
                },
                enabled = codeInput.isNotBlank() && !isRedeeming,
                colors = ButtonDefaults.buttonColors(containerColor = AmberGlow, contentColor = androidx.compose.ui.graphics.Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Redeem")
            }
        }

        message?.let { text ->
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background((if (messageIsError) CoralGlow else EmeraldGlow).copy(alpha = 0.16f))
                    .border(1.dp, (if (messageIsError) CoralGlow else EmeraldGlow).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text,
                    color = if (messageIsError) CoralGlow else EmeraldGlow,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Codes are single-use per device - once redeemed, they can't be claimed again.",
            color = TextMuted,
            fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
    }
}