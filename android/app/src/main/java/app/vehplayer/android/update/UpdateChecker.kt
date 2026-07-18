package app.vehplayer.android.update

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Sideloaded apps have no Play Store update channel. This checks GitHub
 * Releases directly (public repo, no auth needed) instead: CI-less publishing
 * publishes a debug APK there tagged `build-<versionCode>` with a fixed asset
 * name, this compares that versionCode against what's actually installed.
 * Android still requires an explicit user tap to install (no fully silent
 * update is possible for a non-Play, non-device-owner app), but this removes
 * the "transfer the APK phone<->laptop by hand" step entirely: the download
 * link opens straight in the browser.
 */
object UpdateChecker {
    private const val RELEASES_URL = "https://api.github.com/repos/mounssif/vehplayer/releases/latest"
    private const val APK_ASSET_NAME = "vehplayer-debug.apk"
    private const val TAG = "UpdateChecker"

    data class UpdateInfo(val versionCode: Int, val versionName: String, val downloadUrl: String)

    /** Blocking network call, run this off the main thread. Best-effort: any failure (offline, no releases yet, malformed tag) just means no update is reported, never blocks the core streaming flow. */
    fun checkBlocking(): UpdateInfo? {
        return try {
            val conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val tag = json.optString("tag_name") // "build-<versionCode>"
            val versionCode = tag.substringAfterLast('-').toIntOrNull() ?: return null

            val assets = json.optJSONArray("assets") ?: return null
            val downloadUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == APK_ASSET_NAME }
                ?.optString("browser_download_url")
                ?.takeIf { it.isNotEmpty() }
                ?: return null

            UpdateInfo(versionCode, json.optString("name", tag), downloadUrl)
        } catch (e: Exception) {
            Log.w(TAG, "update check failed, ignoring: ${e.message}")
            null
        }
    }
}
