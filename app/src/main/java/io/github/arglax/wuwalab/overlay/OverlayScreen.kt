package io.github.arglax.wuwalab.overlay

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.arglax.wuwalab.R
import io.github.arglax.wuwalab.data.OverlayPrefs
import io.github.arglax.wuwalab.ui.components.GlassCard
import io.github.arglax.wuwalab.ui.components.HelpButton
import io.github.arglax.wuwalab.ui.theme.CoralGlow
import io.github.arglax.wuwalab.ui.theme.TextMuted
import io.github.arglax.wuwalab.ui.theme.TextPrimary
import io.github.arglax.wuwalab.ui.theme.TextSecondary
import io.github.arglax.wuwalab.ui.theme.VioletGlow
import io.github.arglax.wuwalab.widget.WuwaWidgetReceiver

private fun canDrawOverlays(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || Settings.canDrawOverlays(context)

/**
 * Requests the launcher pin the Waveplates widget directly, skipping the
 * "long-press home screen -> Widgets -> find WuWaLab" flow. Requires API 26+
 * (Settings.ACTION_APPWIDGET_PIN doesn't exist below O) and a launcher that
 * supports pinning (most stock/modern launchers do) - the UI above falls
 * back to pointing the user at the manual flow when unsupported.
 */
private fun requestPinWuwaWidget(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val appWidgetManager = context.getSystemService(AppWidgetManager::class.java) ?: return
    val provider = ComponentName(context, WuwaWidgetReceiver::class.java)
    if (!appWidgetManager.isRequestPinAppWidgetSupported) return
    val successCallback = PendingIntent.getBroadcast(
        context,
        0,
        Intent(),
        PendingIntent.FLAG_IMMUTABLE
    )
    appWidgetManager.requestPinAppWidget(provider, null, successCallback)
}

@Composable
fun OverlayScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val overlayPrefs = remember { OverlayPrefs(context) }

    var permissionGranted by remember { mutableStateOf(canDrawOverlays(context)) }
    var enabled by remember { mutableStateOf(overlayPrefs.load().enabled) }
    val tapFeedback = io.github.arglax.wuwalab.util.rememberTapFeedback()

    // Overlay permission is granted through a system settings screen, not an
    // in-app dialog, so re-check it whenever the user comes back to the app
    // (same pattern SettingsDialog.kt uses for notification/exact-alarm perms).
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Floating Overlay",
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            HelpButton(
                title = "Floating Overlay Help",
                body = "Turn on \"Show Overlay Bubble\" to get a small draggable bubble that " +
                    "floats over other apps, including the game itself.\n\n" +
                    "Tap the bubble to pop open a quick Log Astrites menu.\n" +
                    "Press and drag to move it anywhere on screen.\n" +
                    "Drag it to the bottom-center of the screen - it glows red with a trash " +
                    "icon - and release to remove the overlay entirely.\n\n" +
                    "Needs the \"Display over other apps\" system permission the first time."
            )
        }
        Text(
            "A small draggable bubble that floats over other apps (including the game itself) for quick Astrite logging without switching back to WuWaLab.",
            color = TextSecondary
        )

        GlassCard(modifier = Modifier.fillMaxWidth(), accent = VioletGlow) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(R.drawable.ic_astrite), contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show Overlay Bubble", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        if (!permissionGranted) "Needs the \"Display over other apps\" permission first."
                        else if (enabled) "Active - drag the bubble to the bottom-center to remove it."
                        else "Off",
                        color = TextSecondary,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
                Switch(
                    checked = enabled && permissionGranted,
                    onCheckedChange = { checked ->
                        if (checked && !permissionGranted) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                "package:${context.packageName}".toUri()
                            )
                            context.startActivity(intent)
                            return@Switch
                        }
                        enabled = checked
                        overlayPrefs.setEnabled(checked)
                        if (checked) OverlayService.start(context) else OverlayService.stop(context)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = VioletGlow)
                )
            }

            if (!permissionGranted) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Grant Display-Over-Other-Apps Permission") }
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth(), accent = VioletGlow) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(R.drawable.ic_waveplate), contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Add Widget", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        "Pin the Waveplates home-screen widget without leaving the app.",
                        color = TextSecondary,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { tapFeedback(); requestPinWuwaWidget(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add Widget to Home Screen") }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your launcher doesn't support one-tap pinning on this Android version - " +
                        "long-press your home screen instead and add \"WuWaLab\" from the widget picker.",
                    color = TextMuted,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth(), accent = CoralGlow) {
            Text("How It Works", fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            BulletLine("Tap the bubble to pop open Log Astrites right on top of whatever app you're in.")
            BulletLine("Press and drag the bubble to move it anywhere on screen.")
            BulletLine("Drag it into the bottom-center of the screen - it'll glow red with a trash icon - and release to remove the overlay.")
            BulletLine("A small \"Log Added\" or \"Log Not Added\" notification confirms what happened, then the popup closes on its own.")
            BulletLine("Anything you log through the bubble is saved the same way as entries logged in-app, and shows up immediately in the Astrite Tracker tab.")
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("•  ", color = TextSecondary)
        Text(text, color = TextSecondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
    }
}