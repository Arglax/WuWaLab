package io.github.arglax.wuwalab.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * EDIT THIS: your repo's owner/name, used to hit the GitHub REST API for the
 * latest release (tags formatted like "v1.0", "v1.1", "v2.0", ...).
 */
const val GITHUB_OWNER = "Arglax"
const val GITHUB_REPO = "WuWaLab"
private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

data class UpdateInfo(
    val tagName: String,          // e.g. "v1.1"
    val htmlUrl: String,          // release page, for "View on GitHub"
    val apkDownloadUrl: String?,  // direct .apk asset URL, if the release has one
    val notes: String
)

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object UpdateChecker {

    /**
     * Compares GitHub's latest release tag against [currentVersion] (e.g.
     * "v1.0", matching BuildConfig.VERSION_NAME prefixed with "v"). Tags are
     * split on '.' and compared numerically component-by-component, so
     * v1.10 correctly beats v1.9 (a plain string compare would get that
     * backwards).
     */
    suspend fun check(currentVersion: String): UpdateCheckResult {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10.seconds.toJavaDuration())
                .readTimeout(10.seconds.toJavaDuration())
                .build()
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return UpdateCheckResult.Error("GitHub returned HTTP ${response.code}")
                }
                val body = response.body?.string() ?: return UpdateCheckResult.Error("Empty response")
                val json = JSONObject(body)
                val tag = json.getString("tag_name")
                val htmlUrl = json.optString("html_url", "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases")
                val notes = json.optString("body", "")
                val apkUrl = findApkAssetUrl(json)

                if (isNewer(tag, currentVersion)) {
                    UpdateCheckResult.Available(UpdateInfo(tag, htmlUrl, apkUrl, notes))
                } else {
                    UpdateCheckResult.UpToDate
                }
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Network error")
        }
    }

    private fun findApkAssetUrl(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                return asset.optString("browser_download_url", null)
            }
        }
        return null
    }

    /** True if [remoteTag] represents a strictly newer version than [localTag]. */
    fun isNewer(remoteTag: String, localTag: String): Boolean {
        val remote = parseVersion(remoteTag)
        val local = parseVersion(localTag)
        for (i in 0 until maxOf(remote.size, local.size)) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    private fun parseVersion(tag: String): List<Int> =
        tag.trim()
            .removePrefix("v").removePrefix("V")
            .split(".", "-", "+")
            .mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }

    /**
     * Downloads the release APK to the app's cache dir and fires the system
     * package installer via a FileProvider content:// URI - same pattern as
     * WuWa Config Patcher's ExportUtils.downloadAndInstallApk(). Requires the
     * REQUEST_INSTALL_PACKAGES permission (declared in the manifest) and, on
     * Android 8+, the user granting "install unknown apps" for this app the
     * first time - the system prompts for that automatically when the
     * install intent fires if it isn't already granted.
     */
    suspend fun downloadAndInstall(context: Context, apkUrl: String, onProgress: (Float) -> Unit = {}) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15.seconds.toJavaDuration())
            .readTimeout(60.seconds.toJavaDuration())
            .build()
        val request = Request.Builder().url(apkUrl).build()
        val outFile = File(context.cacheDir, "wuwalab_update.apk")

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Download failed: HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("Empty download body")
            val total = body.contentLength().takeIf { it > 0 }
            var written = 0L

            body.byteStream().use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total != null) onProgress(written / total.toFloat())
                    }
                }
            }
        }

        val apkUri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", outFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    fun openReleasePage(context: Context, htmlUrl: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, htmlUrl.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}