package io.github.arglax.wuwalab.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.arglax.wuwalab.data.TodoItem

/**
 * One exact alarm per to-do task, keyed off the task's own [TodoItem.id] so
 * scheduling/cancelling one task never touches another's. Used by the To-Do
 * Planner's optional per-entry "alarm + notify" toggle.
 */
object TodoReminderScheduler {

    fun schedule(context: Context, item: TodoItem) {
        val triggerAt = item.alarmEpochMs
        cancel(context, item.id) // always clear any previous alarm for this task first
        if (triggerAt == null || !item.notifyEnabled || triggerAt <= System.currentTimeMillis()) return

        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        val pi = pendingIntentFor(context, item)
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // Inexact-but-close fallback if exact-alarm permission isn't granted.
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context, taskId: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, TodoReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, requestCodeFor(taskId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    private fun pendingIntentFor(context: Context, item: TodoItem): PendingIntent {
        val intent = Intent(context, TodoReminderReceiver::class.java).apply {
            putExtra(TodoReminderReceiver.EXTRA_TASK_ID, item.id)
            putExtra(TodoReminderReceiver.EXTRA_TITLE, item.title)
        }
        return PendingIntent.getBroadcast(
            context, requestCodeFor(item.id), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Stable per-task request code so re-scheduling replaces (rather than
    // duplicates) the same task's pending alarm.
    private fun requestCodeFor(taskId: String): Int = 7000 + (taskId.hashCode() and 0x0FFFFFFF)
}