package io.github.arglax.wuwalab.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.arglax.wuwalab.data.LuniteRepository
import java.util.concurrent.TimeUnit

/**
 * Three reminder slots, timed as offsets from the 4:00 AM Manila reset:
 *   Slot 0: +4h  -> 8:00 AM Manila
 *   Slot 1: +12h -> 4:00 PM Manila
 *   Slot 2: +20h -> 12:00 AM Manila (midnight, 4h before the next reset)
 * Each alarm re-arms itself for the next day when it fires (see
 * LuniteReminderReceiver), so this only needs to be called on: app launch,
 * the Lunite Pass toggle being turned on, and device boot.
 */
object LuniteReminderScheduler {

    private val OFFSET_HOURS = listOf(4L, 12L, 20L)
    private const val REQUEST_CODE_BASE = 5100

    fun rescheduleAll(context: Context, luniteRepo: LuniteRepository) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val lastReset = luniteRepo.lastResetEpochMs()
        val now = System.currentTimeMillis()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

        OFFSET_HOURS.forEachIndexed { index, hours ->
            var triggerAt = lastReset + TimeUnit.HOURS.toMillis(hours)
            if (triggerAt <= now) triggerAt += TimeUnit.DAYS.toMillis(1)
            val pi = pendingIntentFor(context, index)
            am.cancel(pi)
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // Falls back to an inexact-but-close alarm if exact-alarm
                // permission isn't granted - still fires, just not to the minute.
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        OFFSET_HOURS.indices.forEach { index -> am.cancel(pendingIntentFor(context, index)) }
    }

    private fun pendingIntentFor(context: Context, slotIndex: Int): PendingIntent {
        val intent = Intent(context, LuniteReminderReceiver::class.java).apply {
            putExtra(LuniteReminderReceiver.EXTRA_SLOT_INDEX, slotIndex)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE_BASE + slotIndex, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}