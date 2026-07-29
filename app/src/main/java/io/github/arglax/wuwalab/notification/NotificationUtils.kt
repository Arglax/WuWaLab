package io.github.arglax.wuwalab.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

const val CHANNEL_ID_ALERTS = "wuwa_alerts"
const val CHANNEL_ID_LUNITE = "wuwa_lunite_reminders"

/**
 * Two channels, deliberately split by urgency so the OS/user can tune them
 * separately: waveplate/crystal/event alerts are useful-but-not-urgent
 * (DEFAULT), while Lunite Pass reminders are time-sensitive - miss all three
 * and you lose the day's 90 Astrites for good - so they're HIGH importance
 * with heads-up display.
 */
object NotificationUtils {

    fun ensureChannels(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_ALERTS, "Wuwa Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Waveplate, crystal, and event deadline alerts."
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_LUNITE, "Lunite Pass Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders to log in and claim your daily Lunite Pass Astrites before they're lost."
            }
        )
    }

    /**
     * Posts a notification with a small category "header" (shown above the
     * title by the system, via [NotificationCompat.Builder.setSubText]) plus
     * the title and a short context line underneath.
     *
     * [header] is the category, e.g. "WuWaLab · Overlay" or "WuWaLab · Waveplates".
     * [title] is the bold headline, e.g. "Astrites Logged".
     * [text] is the short context/explanation underneath.
     */
    fun notify(
        context: Context,
        channelId: String,
        id: Int,
        title: String,
        text: String,
        header: String = "WuWaLab"
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val smallIcon = context.resources.getIdentifier("ic_astrite", "drawable", context.packageName)
            .takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setSubText(header)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text).setBigContentTitle(title))
            .setAutoCancel(true)
            .setPriority(
                if (channelId == CHANNEL_ID_LUNITE) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}