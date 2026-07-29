package io.github.arglax.wuwalab.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.arglax.wuwalab.data.AstriteRepository
import io.github.arglax.wuwalab.data.LuniteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val NOTIF_ID_LUNITE_BASE = 3000

class LuniteReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getIntExtra(EXTRA_SLOT_INDEX, 0)
        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val astriteRepo = AstriteRepository(appContext)
                val luniteRepo = LuniteRepository(appContext, astriteRepo)

                if (luniteRepo.enabledFlow.first() && !luniteRepo.hasCheckedInToday()) {
                    NotificationUtils.ensureChannels(appContext)
                    val (title, body) = messageFor(slot)
                    NotificationUtils.notify(appContext, CHANNEL_ID_LUNITE, NOTIF_ID_LUNITE_BASE + slot, title, body, header = "WuWaLab · Lunite Pass")
                }

                // Re-arm every slot for the next cycle regardless of whether this
                // one fired, so a missed/disabled day doesn't kill the chain.
                LuniteReminderScheduler.rescheduleAll(appContext, luniteRepo)
            } finally {
                pending.finish()
            }
        }
    }

    private fun messageFor(slot: Int): Pair<String, String> = when (slot) {
        0 -> "Lunite Pass: haven't logged in yet?" to
            "It's been 4 hours since reset - log into Wuthering Waves to claim your 90 daily Astrites."
        1 -> "Still no login today" to
            "Halfway through the day and your Lunite Pass Astrites are still unclaimed. Don't lose them!"
        else -> "Last call before reset!" to
            "Only a few hours left - log in now or today's 90 Astrites will be gone for good."
    }

    companion object {
        const val EXTRA_SLOT_INDEX = "slot_index"
    }
}