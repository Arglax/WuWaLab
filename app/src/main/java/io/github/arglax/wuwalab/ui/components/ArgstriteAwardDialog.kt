package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.ui.theme.AmberGlow

/**
 * Awards Argstrites for actually using a real feature (logging an Astrite
 * entry, logging a spend, etc). The reward is +1 normally, or +2 when the
 * action carried its own custom/optional note - rewarding a little extra
 * effort. The amount is only added to the PENDING pile (see
 * [io.github.arglax.wuwalab.data.WuwaRepository.addPendingArgstrite]) - it
 * isn't spendable until claimed from the Dashboard's "Claim" button, so quick
 * back-to-back actions don't spam a popup for every single one unless the
 * caller chooses to show it.
 */
fun argstriteRewardFor(note: String): Int = if (note.isNotBlank()) 2 else 1

/**
 * The Dashboard's "Claim" button - sweeps up every Argstrite banked from real
 * actions (Astrite logs, spend logs, etc.) in one tap instead of popping a
 * dialog every single time one is earned. Only rendered while there's
 * something to claim.
 */
@Composable
fun ClaimArgstritesButton(amount: Int, onClick: () -> Unit, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    androidx.compose.material3.Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = AmberGlow,
            contentColor = androidx.compose.ui.graphics.Color.Black
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 12.dp),
        modifier = modifier
    ) {
        Image(painter = painterResource(R.drawable.ic_radiant_astrite), contentDescription = null, modifier = androidx.compose.ui.Modifier.size(16.dp))
        Spacer(androidx.compose.ui.Modifier.width(6.dp))
        Text("Claim  +$amount", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelSmall.fontSize)
    }
}

/**
 * [hintMessage], when non-null, is the one-time "hidden method" explainer
 * (see [io.github.arglax.wuwalab.data.WuwaRepository.consumeArgstriteHint]) -
 * shown alongside the usual "+N Argstrites" line exactly once for the base
 * discovery and once more for the bonus-note discovery, then never again.
 */
@Composable
fun ArgstriteAwardDialog(amount: Int, hintMessage: String? = null, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Argstrites earned!", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(R.drawable.ic_radiant_astrite), contentDescription = null, modifier = androidx.compose.ui.Modifier.size(22.dp))
                    Spacer(androidx.compose.ui.Modifier.width(8.dp))
                    Text(
                        "+$amount Argstrite" + (if (amount == 1) "" else "s"),
                        color = AmberGlow,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize
                    )
                }
                if (hintMessage != null) {
                    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painter = painterResource(R.drawable.ic_radiant_astrite), contentDescription = null, modifier = androidx.compose.ui.Modifier.size(16.dp))
                        Spacer(androidx.compose.ui.Modifier.width(6.dp))
                        Text(
                            hintMessage,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                            fontSize = MaterialTheme.typography.bodySmall.fontSize
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Nice") } }
    )
}