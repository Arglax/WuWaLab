package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary

/**
 * Dashboard-facing card for the Lunite Pass. Collapsed state shows the promo
 * artwork with a call-to-action label; once activated, it's just the
 * artwork with a gold "ACTIVATED" caption underneath. Tapping either state
 * opens [LunitePassDialog].
 */
@Composable
fun LunitePassCard(
    activated: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    GlassCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), accent = AmberGlow) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(R.drawable.ic_lunite_sub),
                    contentDescription = "Lunite Pass",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (activated) "Lunite Pass · ACTIVATED" else "Activate Lunite Pass",
                    color = if (activated) AmberGlow else TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(R.drawable.ic_lunite_sub),
                    contentDescription = "Lunite Pass",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(6.dp))
                if (activated) {
                    Text("ACTIVATED", color = AmberGlow, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelMedium.fontSize)
                } else {
                    Text("Activate Lunite Pass", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                }
            }
        }
    }
}

/**
 * Explains what the Lunite Pass toggle does (this replaces the old Settings
 * toggle - see SettingsDialog.kt) and lets the player switch it on/off.
 *
 * Note: "Edit Initial Astrite" used to live in this dialog - it's now under
 * the Profile header (tap the header -> EditProfileDialog.kt) since it's a
 * profile-level correction, not a Lunite-Pass-specific setting.
 */
@Composable
fun LunitePassDialog(
    activated: Boolean,
    onSetActivated: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lunite Pass", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "If you have the Lunite Pass/Subscription, it grants +90 Astrites daily on login. Turn this on to get reminded (4:00, 12:00, 20:00 Server Time) if you haven't checked in yet that day, and to unlock the check-in button on the Astrite Tracker.",
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (activated) "Lunite Pass Enabled!" else "Have Lunite Pass? Enable",
                        color = if (activated) AmberGlow else TextMuted,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = activated,
                        onCheckedChange = onSetActivated,
                        colors = SwitchDefaults.colors(checkedThumbColor = AmberGlow)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}