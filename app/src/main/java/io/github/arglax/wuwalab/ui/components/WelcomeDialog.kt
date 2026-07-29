package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.ui.theme.AmberGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow

/**
 * Shown once at startup so first-time (and returning) users understand what
 * WuWaLab is and isn't before they start entering data. Covers, in order:
 * privacy/no-login, cost/no-ads, and support being optional. Dismissing
 * without checking the box just closes it for this session - checking
 * "Don't show this again" persists the choice via [onHideForever] so it
 * never appears again on this device.
 */
@Composable
fun WelcomeDialog(
    onDismiss: () -> Unit,
    onHideForever: () -> Unit
) {
    var hideForever by remember { mutableStateOf(false) }
    val tapFeedback = io.github.arglax.wuwalab.util.rememberTapFeedback()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Welcome to WuWaLab", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "A quick heads-up before you dive in:",
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
                Spacer(Modifier.height(14.dp))

                WelcomePoint(
                    icon = Icons.Filled.Lock,
                    iconTint = EmeraldGlow,
                    title = "Your account stays yours",
                    body = "WuWaLab never logs into your Wuthering Waves account, reads your game files, or talks to Kuro Games' servers. Every number you see here - Waveplates, Astrites, and everything else - is only what you manually enter, stored privately on your own device."
                )
                Spacer(Modifier.height(14.dp))

                WelcomePoint(
                    icon = Icons.Filled.VerifiedUser,
                    iconTint = VioletGlow,
                    title = "Respecting the game's Terms of Service",
                    body = "Because nothing here connects to your account or automates gameplay, using WuWaLab alongside Wuthering Waves doesn't put your account at risk."
                )
                Spacer(Modifier.height(14.dp))

                WelcomePoint(
                    icon = Icons.Filled.Public,
                    iconTint = AmberGlow,
                    title = "Everything is free, forever",
                    body = "Every feature in this app is fully unlocked - no paywalls, no ads, no premium tier hiding functionality behind it."
                )
                Spacer(Modifier.height(14.dp))

                WelcomePoint(
                    icon = Icons.Filled.Favorite,
                    iconTint = AmberGlow,
                    title = "Supporting the dev is optional",
                    body = "If you'd like to support development, that's always appreciated but entirely voluntary - it never unlocks anything you wouldn't already have."
                )

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hideForever,
                        onCheckedChange = { tapFeedback(); hideForever = it },
                        colors = CheckboxDefaults.colors(checkedColor = VioletGlow)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Don't show this again",
                        color = TextMuted,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tapFeedback()
                if (hideForever) onHideForever() else onDismiss()
            }) {
                Text("Got it", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun WelcomePoint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    body: String
) {
    Row {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
            Spacer(Modifier.height(2.dp))
            Text(body, color = TextSecondary, fontSize = MaterialTheme.typography.labelMedium.fontSize)
        }
    }
}