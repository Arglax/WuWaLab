package io.github.arglax.wuwalab.ui.components

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.arglax.wuwalab.data.AstriteRepository
import io.github.arglax.wuwalab.data.LuniteRepository
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.EmeraldGlow
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** True if POST_NOTIFICATIONS is granted (or not needed pre-Android 13). */
private fun hasNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasExactAlarmPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(AlarmManager::class.java)
    return am?.canScheduleExactAlarms() ?: true
}

/**
 * In-app settings for everything that needs an OS-level permission prompt
 * (notifications, exact alarms) plus the Lunite Pass toggle - this is the
 * logical home for it since enabling it is what turns the reminder alarms
 * and check-in flow on in the first place.
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val astriteRepo = remember { AstriteRepository(context) }
    val luniteRepo = remember { LuniteRepository(context, astriteRepo) }

    var notifGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var exactAlarmGranted by remember { mutableStateOf(hasExactAlarmPermission(context)) }
    var luniteEnabled by remember { mutableStateOf(false) }
    var showPatchNotesFallback by remember { mutableStateOf(false) }
    // 0 = idle, 1 = first "are you sure" confirm, 2 = final irreversible confirm.
    var resetStep by remember { mutableStateOf(0) }

    if (showPatchNotesFallback) {
        AlertDialog(
            onDismissRequest = { showPatchNotesFallback = false },
            title = { Text("Patch Notes") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "No app on this device could open the XML file directly, so here's the raw contents:",
                        color = TextSecondary,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        io.github.arglax.wuwalab.util.PatchNotesUtil.readRawText(context),
                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPatchNotesFallback = false }) { Text("Close") } }
        )
    }

    if (resetStep == 1) {
        AlertDialog(
            onDismissRequest = { resetStep = 0 },
            title = { Text("Full Reset?", fontWeight = FontWeight.Bold, color = CoralGlow) },
            text = {
                Text(
                    "This wipes EVERYTHING - Astrite/Argstrite balances, logs, To-Dos, Pull Planner history, Shop purchases, titles, redeemed codes, your custom profile picture and widget backgrounds, all settings. It's the same as clearing the app's storage from Android Settings.\n\nThis can't be undone.",
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
            },
            confirmButton = {
                TextButton(onClick = { resetStep = 2 }) { Text("Continue", color = CoralGlow, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { resetStep = 0 }) { Text("Cancel") }
            }
        )
    } else if (resetStep == 2) {
        AlertDialog(
            onDismissRequest = { resetStep = 0 },
            title = { Text("Are you REALLY sure?", fontWeight = FontWeight.Bold, color = CoralGlow) },
            text = {
                Text(
                    "Last chance. Tapping \"Erase Everything\" immediately deletes all app data and restarts WuWaLab as if it were freshly installed.",
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    io.github.arglax.wuwalab.util.AppResetUtil.resetAndRestart(context)
                }) { Text("Erase Everything", color = CoralGlow, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { resetStep = 0 }) { Text("Cancel") }
            }
        )
    }

    LaunchedEffect(Unit) { luniteEnabled = luniteRepo.isEnabledOnce() }

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = hasNotificationPermission(context)
                exactAlarmGranted = hasExactAlarmPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> notifGranted = isGranted }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "WuWaLab uses notifications to alert you about full waveplates, maxed crystals, event deadlines, and (if enabled) Lunite Pass check-in reminders.",
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
                Spacer(Modifier.height(14.dp))

                PermissionRow(
                    granted = notifGranted,
                    title = "Notification Permission",
                    grantedText = "Granted - you're all set.",
                    deniedText = "Not granted - notifications won't show."
                )
                if (!notifGranted) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                notifGranted = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Grant Notification Permission") }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Denied Before? Open System Notification Settings") }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    "Lunite Pass",
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )

                if (luniteEnabled) {
                    Spacer(Modifier.height(10.dp))
                    PermissionRow(
                        granted = exactAlarmGranted,
                        title = "Exact Alarm Permission",
                        grantedText = "Granted - reminders will fire on time.",
                        deniedText = "Not granted - reminders may arrive late/batched by the system."
                    )
                    if (!exactAlarmGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Grant Exact Alarm Permission") }
                    }
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                Spacer(Modifier.height(14.dp))
                Text(
                    "About & Support",
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Patch notes, ways to support the project, and a place to leave feedback or request features.",
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
                Spacer(Modifier.height(10.dp))
                AboutLinkRow(
                    icon = { Icon(Icons.Filled.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp)) },
                    label = "Patch Notes",
                    onClick = {
                        val handled = io.github.arglax.wuwalab.util.PatchNotesUtil.openInExternalViewer(context)
                        if (!handled) showPatchNotesFallback = true
                    }
                )
                Spacer(Modifier.height(8.dp))
                AboutLinkRow(
                    icon = { Icon(Icons.Filled.Chat, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp)) },
                    label = "Feedback & Feature Requests",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://discord.gg/renjxYBEZM".toUri()))
                    }
                )
                Spacer(Modifier.height(8.dp))
                AboutLinkRow(
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = CoralGlow, modifier = Modifier.size(20.dp)) },
                    label = "Support the Project",
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://buymeacoffee.com/arglaxaqwv".toUri()))
                    }
                )

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = TextMuted.copy(alpha = 0.15f))
                Spacer(Modifier.height(14.dp))
                Text(
                    "Danger Zone",
                    fontWeight = FontWeight.Bold,
                    color = CoralGlow,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Wipes all local app data - balances, logs, purchases, titles, custom photos, everything - and restarts WuWaLab fresh. Same effect as Android's own \"Clear storage\".",
                    color = TextSecondary,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { resetStep = 1 },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CoralGlow.copy(alpha = 0.85f)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Full Reset") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                exactAlarmGranted = hasExactAlarmPermission(context)
                onDismiss()
            }) { Text("Close") }
        }
    )
}

/** A tappable row for the About & Support section - icon, label, and a chevron affordance. */
@Composable
private fun AboutLinkRow(icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(EmeraldGlow.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyMedium.fontSize, modifier = Modifier.weight(1f))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PermissionRow(granted: Boolean, title: String, grantedText: String, deniedText: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background((if (granted) EmeraldGlow else CoralGlow).copy(alpha = 0.14f))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
            Text(
                if (granted) grantedText else deniedText,
                color = TextMuted,
                fontSize = MaterialTheme.typography.labelSmall.fontSize
            )
        }
    }
}