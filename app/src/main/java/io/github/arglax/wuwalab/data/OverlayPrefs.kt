package io.github.arglax.wuwalab.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Plain-JSON-file settings store for the floating overlay (bubble position,
 * enabled state). This intentionally does NOT use DataStore like the rest of
 * the app's prefs - it's a flat, human-readable `overlay_prefs.json` in the
 * app's internal storage, so it's easy to inspect/copy by hand if needed.
 *
 * Important honesty check: this file lives in the app's private internal
 * storage (`context.filesDir`), same as everything else the app stores.
 * That means it survives app *updates* (including through Play Store/GitHub
 * release upgrades) but, like all app-private storage on Android, it is
 * wiped by the OS on uninstall or "Clear data" - no app can opt out of that
 * for its own private files. If you want settings that truly survive an
 * uninstall/reinstall, the only real options are: (a) Android's own
 * key/value Auto Backup (opt-in via `android:allowBackup` + backup rules,
 * already partially enabled in this project's manifest, but device/account
 * dependent and not guaranteed), or (b) writing to a *public* shared
 * location the user picks via the Storage Access Framework, which shows a
 * system file picker rather than being invisible/automatic. Astrite log
 * entries you manually log are already safe from this concern since they
 * matter most and are worth revisiting for SAF-backed export/import later
 * (see README §5 for where to hook that in).
 */
data class OverlaySettings(
    val enabled: Boolean = false,
    // Last remembered bubble position, as a fraction of screen width/height
    // (0f..1f) so it survives different screen sizes reasonably. Defaults to
    // the bottom-right area.
    val lastXFraction: Float = 0.88f,
    val lastYFraction: Float = 0.75f
)

class OverlayPrefs(private val context: Context) {

    private val file: File get() = File(context.filesDir, "overlay_prefs.json")

    @Synchronized
    fun load(): OverlaySettings {
        return try {
            if (!file.exists()) return OverlaySettings()
            val json = JSONObject(file.readText())
            OverlaySettings(
                enabled = json.optBoolean("enabled", false),
                lastXFraction = json.optDouble("lastXFraction", 0.88).toFloat(),
                lastYFraction = json.optDouble("lastYFraction", 0.75).toFloat()
            )
        } catch (_: Exception) {
            // Corrupt/missing file - fall back to defaults rather than crash
            // the app or the overlay service over a settings file.
            OverlaySettings()
        }
    }

    @Synchronized
    fun save(settings: OverlaySettings) {
        try {
            val json = JSONObject().apply {
                put("enabled", settings.enabled)
                put("lastXFraction", settings.lastXFraction.toDouble())
                put("lastYFraction", settings.lastYFraction.toDouble())
            }
            file.writeText(json.toString())
        } catch (_: Exception) {
            // Best-effort: if this write fails the app keeps working, the
            // overlay just falls back to defaults next launch.
        }
    }

    fun setEnabled(enabled: Boolean) = save(load().copy(enabled = enabled))

    fun savePosition(xFraction: Float, yFraction: Float) =
        save(load().copy(lastXFraction = xFraction, lastYFraction = yFraction))
}