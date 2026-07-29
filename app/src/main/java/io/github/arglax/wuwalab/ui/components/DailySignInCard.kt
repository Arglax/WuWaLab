package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlowDeep
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow

/**
 * Dashboard-facing "Daily Sign-In" card, next to the Total Astrites Earned
 * card. Grants a flat +10 Argstrites (WuWaLab's own currency) once per game
 * day (same 4:00 AM Manila reset as the Lunite Pass); when the Lunite Pass
 * is active, claiming here also folds in its +90 real Astrites and marks
 * the Lunite Pass Daily Login tracker as checked-in, so the two don't need
 * to be claimed separately.
 */
@Composable
fun DailySignInCard(
    claimedToday: Boolean,
    luniteActive: Boolean,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier
) {
    val confirmFeedback = io.github.arglax.wuwalab.util.rememberConfirmFeedback()
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !claimedToday, onClick = { confirmFeedback(); onClaim() }),
        accent = if (claimedToday) EmeraldGlow else AmberGlow
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_astrite_enhanced),
                contentDescription = "Argstrite",
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Daily Sign-In",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (claimedToday) "Claimed for today"
            else if (luniteActive) "+10 Argstrites  •  +90 Astrites" else "+10 Argstrites",
            color = if (claimedToday) EmeraldGlowDeep else TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
        if (!claimedToday) {
            Text(
                "Tap to claim",
                color = TextMuted,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
        }
    }
}

/**
 * Purely a confirmation of what got LOGGED in the tracker (mirrors
 * [AstriteCheckInDialog]'s wording) - the app has no way to verify an actual
 * in-game login, so it never claims otherwise.
 */
@Composable
fun DailySignInClaimedDialog(
    argstritesGranted: Int,
    astritesGranted: Int,
    luniteIncluded: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Daily Sign-In Claimed",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize
                )
                Spacer(Modifier.height(14.dp))

                // Both rewards sit side-by-side in one neat row (rather than
                // stacked) so it reads like a single receipt at a glance,
                // instead of two separate call-outs.
                Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RewardPill(
                        iconRes = R.drawable.ic_astrite_enhanced,
                        amount = argstritesGranted,
                        label = "Argstrites",
                        amountColor = VioletGlow
                    )
                    if (luniteIncluded) {
                        RewardPill(
                            iconRes = R.drawable.ic_astrite,
                            amount = astritesGranted,
                            label = "Astrites",
                            amountColor = AmberGlow
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    if (luniteIncluded) "Argstrites added to your profile, Lunite Pass Astrites added to today's Astrite tracker"
                    else "Argstrites added to your profile",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Please make sure to log in to the game to actually receive the Lunite Pass Astrites. " +
                        "This app is just a diary/tracker - it can't claim anything for you in-game.",
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}

/** One reward call-out: icon stacked over a bold "+amount", with its currency name underneath. */
@Composable
private fun RewardPill(
    iconRes: Int,
    amount: Int,
    label: String,
    amountColor: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "+$amount",
            color = amountColor,
            fontWeight = FontWeight.Bold,
            fontSize = MaterialTheme.typography.headlineSmall.fontSize
        )
        Text(
            label,
            color = TextSecondary,
            fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
    }
}