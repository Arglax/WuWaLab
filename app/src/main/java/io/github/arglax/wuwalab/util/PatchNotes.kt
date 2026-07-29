package io.github.arglax.wuwalab.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Patch Notes lives locally in the app now (`assets/patchnotes.xml`) rather
 * than pointing at a GitHub releases page, so it works fully offline.
 *
 * Opening a raw XML asset directly isn't possible - assets aren't file://
 * accessible under scoped storage - so this copies it into the app's cache
 * dir (which the existing FileProvider `update_cache` path already covers)
 * and hands out a content:// Uri for an external viewer/browser to open.
 * Most devices have something that can render XML (a browser, at minimum);
 * if genuinely nothing can, [readRawText] backs an in-app fallback dialog.
 */
object PatchNotesUtil {

    private const val ASSET_NAME = "patchnotes.xml"
    private const val CACHE_FILE_NAME = "patchnotes.xml"

    /** Attempts to open patchnotes.xml in an external viewer. Returns true if something handled it. */
    fun openInExternalViewer(context: Context): Boolean {
        return try {
            val file = copyAssetToCache(context)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/xml")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open Patch Notes"))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /** Raw text of the bundled patch notes asset, for the in-app fallback dialog. */
    fun readRawText(context: Context): String = try {
        context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        "Patch notes are unavailable right now."
    }

    private fun copyAssetToCache(context: Context): File {
        val outFile = File(context.cacheDir, CACHE_FILE_NAME)
        context.assets.open(ASSET_NAME).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outFile
    }
}