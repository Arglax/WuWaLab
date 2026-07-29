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

class LuniteBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val astriteRepo = AstriteRepository(appContext)
                val luniteRepo = LuniteRepository(appContext, astriteRepo)
                if (luniteRepo.enabledFlow.first()) {
                    LuniteReminderScheduler.rescheduleAll(appContext, luniteRepo)
                }
                io.github.arglax.wuwalab.widget.WidgetTickReceiver.resumeIfPinned(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}