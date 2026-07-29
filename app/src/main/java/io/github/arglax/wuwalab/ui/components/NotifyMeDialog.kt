package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.data.NotifyPrefs
import io.github.arglax.wuwalab.data.WAVEPLATE_MAX
import io.github.arglax.wuwalab.ui.theme.CyanGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow

/**
 * These three toggles are intentionally independent checkboxes, not a radio
 * group - a player might reasonably want to be pinged both when waveplates
 * cap AND at a custom "I'm about to log off, tell me at 96" threshold at the
 * same time. A radio group would force choosing only one, which doesn't
 * match how the feature is actually used.
 */
@Composable
fun NotifyMeDialog(
    initialPrefs: NotifyPrefs,
    onDismiss: () -> Unit,
    onSave: (NotifyPrefs) -> Unit
) {
    var notifyFull by remember { mutableStateOf(initialPrefs.notifyOnWaveplateFull) }
    var notifyCrystalMax by remember { mutableStateOf(initialPrefs.notifyOnCrystalMax) }
    var customEnabled by remember { mutableStateOf(initialPrefs.customCountEnabled) }
    var customText by remember { mutableStateOf(initialPrefs.customCount.toString()) }

    val customValue = customText.toIntOrNull()
    val customError = customEnabled && (customValue == null || customValue !in 0..WAVEPLATE_MAX)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notify Me", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Choose when WuWaLab should send you a push notification. You can enable any combination of these.",
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
                Spacer(Modifier.height(14.dp))

                NotifyRow(
                    checked = notifyFull,
                    onCheckedChange = { notifyFull = it },
                    accent = CyanGlow,
                    title = "Waveplates full (240/240)",
                    subtitle = "One-time ping the moment your waveplates cap out."
                )
                NotifyRow(
                    checked = notifyCrystalMax,
                    onCheckedChange = { notifyCrystalMax = it },
                    accent = EmeraldGlow,
                    title = "Waveplate Crystals maxed (480)",
                    subtitle = "Ping when banked crystals hit the 480 soft cap."
                )
                NotifyRow(
                    checked = customEnabled,
                    onCheckedChange = { customEnabled = it },
                    accent = VioletGlow,
                    title = "Custom waveplate count",
                    subtitle = "Advanced: get pinged at a specific waveplate count, e.g. 96."
                )
                if (customEnabled) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Waveplate Count (0–240)") },
                        singleLine = true,
                        isError = customError,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 34.dp, top = 4.dp, bottom = 4.dp)
                    )
                    if (customError) {
                        Text(
                            "Enter a number between 0 and $WAVEPLATE_MAX.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            modifier = Modifier.padding(start = 34.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !customError,
                onClick = {
                    onSave(
                        NotifyPrefs(
                            notifyOnWaveplateFull = notifyFull,
                            notifyOnCrystalMax = notifyCrystalMax,
                            customCountEnabled = customEnabled,
                            customCount = customValue ?: WAVEPLATE_MAX
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun NotifyRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = accent)
        )
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
            Text(subtitle, color = TextMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize)
        }
    }
}