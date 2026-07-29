package io.github.arglax.wuwalab.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "WidgetTickReceiver"
private const val TICK_INTERVAL_MS = 10_000L
private const val REQUEST_CODE = 9100

/**
 * Keeps the home-screen widget's live values (waveplate/crystal regen,
 * "full in Xh Ym" countdown, etc.) fresh every 10 seconds - but ONLY while
 * the widget is actually pinned to a home screen. There's no OS callback for
 * "the widget is currently visible" (home screens aren't observable that
 * way), so "pinned at all" is the closest available signal and the one used
 * here: as soon as the last instance is removed the chain stops rescheduling
 * itself, so it never runs (and never drains battery) for someone who isn't
 * using the widget. [WuwaWidgetReceiver.onEnabled]/[onDisabled] start and
 * stop this loop; [io.github.arglax.wuwalab.notification.LuniteBootReceiver]
 * also nudges it awake after a reboot in case a widget was already pinned.
 *
 * A true 10s cadence is far below Android's recommended
 * `updatePeriodMillis` floor (30 min), so this is deliberately done as a
 * self-rescheduling exact alarm rather than the widget's built-in periodic
 * update - which the OS enforces a hard minimum on and would silently
 * ignore anything this frequent.
 */
class WidgetTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pinned = runCatching {
                    GlanceAppWidgetManager(appContext).getGlanceIds(WuwaWidget::class.java).isNotEmpty()
                }.getOrDefault(false)

                if (!pinned) return@launch // nothing pinned -> let the chain die out

                WuwaWidget.updateAll(appContext)
                scheduleNext(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Widget tick failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, WidgetTickReceiver::class.java)
            return PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun scheduleNext(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val triggerAt = System.currentTimeMillis() + TICK_INTERVAL_MS
            val pi = pendingIntent(context)
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // No exact-alarm permission: falls back to inexact timing
                // (the OS may batch/delay this), still far better than no
                // refresh at all while the widget is pinned.
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }

        /** Starts the loop - safe to call repeatedly, e.g. every time a new instance is pinned. */
        fun start(context: Context) {
            scheduleNext(context)
        }

        /** Stops the loop - call once the last widget instance is removed. */
        fun stop(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context, WidgetTickReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }

        /** Resumes the loop after a reboot, but only if a widget instance is already pinned. */
        suspend fun resumeIfPinned(context: Context) {
            val pinned = runCatching {
                GlanceAppWidgetManager(context).getGlanceIds(WuwaWidget::class.java).isNotEmpty()
            }.getOrDefault(false)
            if (pinned) scheduleNext(context)
        }
    }
}