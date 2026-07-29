package io.github.arglax.wuwalab.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.arglax.wuwalab.data.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val NOTIF_ID_TODO_BASE = 6000

/**
 * Fires the reminder for a single To-Do task at its scheduled alarm time.
 * Unlike the Lunite reminders (which re-arm daily), this is a one-shot -
 * a to-do alarm doesn't repeat unless the player edits the task and picks
 * a new time.
 */
class TodoReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "To-Do Reminder"
        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val todoRepo = TodoRepository(appContext)
                // Skip the notification if the task was completed or deleted
                // before the alarm fired.
                val item = todoRepo.getItemsOnce().firstOrNull { it.id == taskId }
                if (item != null && !item.done && item.notifyEnabled) {
                    NotificationUtils.ensureChannels(appContext)
                    NotificationUtils.notify(
                        appContext,
                        CHANNEL_ID_ALERTS,
                        NOTIF_ID_TODO_BASE + taskId.hashCode(),
                        "To-Do Reminder",
                        title,
                        header = "WuWaLab · To-Do"
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "task_title"
    }
}