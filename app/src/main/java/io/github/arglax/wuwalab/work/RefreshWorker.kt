package io.github.arglax.wuwalab.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.arglax.wuwalab.data.WuwaEvent
import io.github.arglax.wuwalab.data.WuwaRepository
import io.github.arglax.wuwalab.notification.CHANNEL_ID_ALERTS
import io.github.arglax.wuwalab.notification.NotificationUtils
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val NOTIF_ID_WAVEPLATES = 1001
private const val NOTIF_ID_CRYSTALS = 1002
private const val NOTIF_ID_CUSTOM = 1003
private const val EVENT_NOTIF_ID_BASE = 2000

/**
 * Periodic worker (run every ~30 min via WorkManager) that:
 *  1. Refreshes the events cache from GitHub.
 *  2. Checks if waveplates are full -> notifies once.
 *  3. Checks event windows -> notifies at 3-day and 1-day-left thresholds.
 * Lunite Pass check-in reminders are handled separately by
 * LuniteReminderScheduler/LuniteReminderReceiver (exact alarms, not this
 * periodic worker), since those need to fire at specific times of day rather
 * than "whenever this next happens to run".
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = WuwaRepository(applicationContext)
        NotificationUtils.ensureChannels(applicationContext)

        // 1. Waveplates / Crystals - respect the user's Notify Me preferences.
        val prefs = repo.notifyPrefsFlow.first()
        val state = repo.getWaveplateStateOnce()
        val nowCount = state.computeCurrent()
        val crystalCount = state.computeCrystals()

        repo.clearNotifiedFullIfBelowMax(nowCount)
        if (prefs.notifyOnWaveplateFull && nowCount >= 240 && repo.shouldNotifyFull()) {
            notify(
                NOTIF_ID_WAVEPLATES,
                "Waveplates full!",
                "You're at 240/240. Time to spend them before they cap.",
                header = "WuWaLab · Waveplates"
            )
            repo.markNotifiedFull()
        }

        repo.clearNotifiedCrystalMaxIfBelow(crystalCount)
        if (prefs.notifyOnCrystalMax && crystalCount >= 480 && repo.shouldNotifyCrystalMax()) {
            notify(
                NOTIF_ID_CRYSTALS,
                "Waveplate Crystals maxed!",
                "You've hit the 480 soft cap. Consider converting or spending them.",
                header = "WuWaLab · Crystals"
            )
            repo.markNotifiedCrystalMax()
        }

        if (prefs.customCountEnabled) {
            repo.clearNotifiedCustomIfBelow(nowCount, prefs.customCount)
            if (nowCount >= prefs.customCount && repo.shouldNotifyCustom()) {
                notify(
                    NOTIF_ID_CUSTOM,
                    "Waveplates at ${prefs.customCount}+",
                    "You've hit your custom threshold of ${prefs.customCount} waveplates.",
                    header = "WuWaLab · Waveplates"
                )
                repo.markNotifiedCustom()
            }
        }

        // 2. Events
        val events = try {
            repo.refreshEventsFromGitHub()
        } catch (_: Exception) {
            repo.getCachedEvents()
        }
        val now = System.currentTimeMillis()
        val oneDayMs = TimeUnit.DAYS.toMillis(1)
        val threeDaysMs = TimeUnit.DAYS.toMillis(3)

        events.forEach { event ->
            if (event.status(now) != WuwaEvent.Status.LIVE) return@forEach
            val msLeft = event.endEpochMs - now

            if (msLeft in 0..threeDaysMs && !repo.hasNotified(repo.notified3DayKey(), event.id)) {
                notify(
                    EVENT_NOTIF_ID_BASE + event.id.hashCode(),
                    "${event.name} ends in 3 days",
                    "Don't forget to finish it before it expires.",
                    header = "WuWaLab · Events"
                )
                repo.markNotified(repo.notified3DayKey(), event.id)
            }
            if (msLeft in 0..oneDayMs && !repo.hasNotified(repo.notified1DayKey(), event.id)) {
                notify(
                    EVENT_NOTIF_ID_BASE + event.id.hashCode() + 1,
                    "${event.name} ends in 1 day",
                    "Last call — expires soon!",
                    header = "WuWaLab · Events"
                )
                repo.markNotified(repo.notified1DayKey(), event.id)
            }
        }

        // 3. Nudge the widget to redraw with fresh numbers.
        io.github.arglax.wuwalab.widget.WuwaWidget.updateAll(applicationContext)

        return Result.success()
    }

    private fun notify(id: Int, title: String, text: String, header: String = "WuWaLab") {
        NotificationUtils.notify(applicationContext, CHANNEL_ID_ALERTS, id, title, text, header = header)
    }
}