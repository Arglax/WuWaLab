package io.github.arglax.wuwalab.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.arglax.wuwalab.update.UpdateInfo
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.TextSecondary

@Composable
fun UpdateAvailableDialog(
    info: UpdateInfo,
    currentVersion: String,
    downloading: Boolean,
    downloadProgress: Float,
    onDismiss: () -> Unit,
    onSkipVersion: () -> Unit,
    onUpdateNow: () -> Unit,
    onViewOnGitHub: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("Update Available", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "${info.tagName} is available (you're on $currentVersion).",
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        info.notes.take(400),
                        color = TextSecondary,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        modifier = Modifier.heightIn(max = 140.dp)
                    )
                }
                if (downloading) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        color = EmeraldGlow,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Downloading update... ${(downloadProgress * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                } else if (info.apkDownloadUrl == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No APK attached to this release - opening the GitHub release page instead.",
                        color = TextSecondary,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
            }
        },
        confirmButton = {
            if (info.apkDownloadUrl != null) {
                TextButton(enabled = !downloading, onClick = onUpdateNow) { Text("Update Now") }
            } else {
                TextButton(onClick = onViewOnGitHub) { Text("View on GitHub") }
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                TextButton(enabled = !downloading, onClick = onSkipVersion) { Text("Skip This Version") }
                TextButton(enabled = !downloading, onClick = onDismiss) { Text("Later") }
            }
        }
    )
}