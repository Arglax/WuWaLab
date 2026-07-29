package io.github.arglax.wuwalab.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import io.github.arglax.wuwalab.MainActivity
import java.io.File

/**
 * A true "full reset" - equivalent to what Android's own Settings > Apps >
 * WuWaLab > Storage > "Clear storage" does, but reachable from inside the
 * app itself. Wipes every file WuWaLab has ever written (all DataStore
 * preferences, every JSON state file, every saved/custom image) and then
 * relaunches the app from a completely clean process, since in-memory
 * singletons (repositories, cached Flows) would otherwise keep serving stale
 * data even after the files underneath them are gone.
 */
object AppResetUtil {

    /** Deletes every file this app owns on internal storage. */
    fun wipeAllAppData(context: Context) {
        // DataStore Preferences files (astrite log, waveplates, redeem codes,
        // shop state, etc.) all live under filesDir/datastore/*.preferences_pb -
        // deleting filesDir wholesale catches those plus every plain JSON file
        // (profile_studio.json, widget_studio.json, custom avatar/background
        // PNGs...) without having to individually track every path ever
        // written across the app's history.
        runCatching { context.filesDir?.deleteRecursively() }
        runCatching { context.cacheDir?.deleteRecursively() }
        runCatching { context.getExternalFilesDir(null)?.deleteRecursively() }
        runCatching { context.externalCacheDir?.deleteRecursively() }
        // Pre-DataStore SharedPreferences, if any ever existed on this device.
        runCatching {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (prefsDir.exists()) prefsDir.listFiles()?.forEach { it.delete() }
        }
        // Cancel anything scheduled (waveplate/event/todo reminders) so a
        // wiped install doesn't still get notifications about data that no
        // longer exists.
        runCatching {
            val am = context.getSystemService(AlarmManager::class.java)
            am?.cancel(PendingIntent.getBroadcast(context, 0, Intent(), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        }
    }

    /**
     * Wipes all app data, then kills and relaunches the app fresh - as close
     * to a real "reinstall" as is possible without actually reinstalling.
     */
    fun resetAndRestart(context: Context) {
        wipeAllAppData(context)

        val restartIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(AlarmManager::class.java)
        am?.set(AlarmManager.RTC, System.currentTimeMillis() + 300, pendingIntent)
        Process.killProcess(Process.myPid())
    }
}