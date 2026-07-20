package app.vehplayer.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release APK in-app and hands it straight to the system
 * installer, instead of routing through the browser (open Chrome, find the
 * download notification, tap it). Android still requires one explicit tap
 * on the installer's own confirmation screen for a non-Play app - that part
 * can't be skipped without being a device-owner/system app - this only
 * removes the browser hop in front of it.
 */
object ApkInstaller {
    private const val TAG = "ApkInstaller"
    private const val FILE_NAME = "vehplayer-update.apk"

    /** REQUEST_INSTALL_PACKAGES is a manifest-declared permission, but installing from a
     * specific source still needs this per-app runtime toggle (same shape as the
     * accessibility "restricted settings" gate the user already had to clear once). */
    fun hasInstallPermission(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun installPermissionSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Downloads the APK IN-APP (not via the system DownloadManager) and hands
     * it to the installer. This is deliberate, and it fixes a real bug: while
     * the tier (c) reachability VpnService is up (the status-bar "key"/VPN
     * icon), DownloadManager - a separate system process - has its traffic
     * captured by the VPN tun, which routes only the /32 virtual address, so
     * the download silently stalls. The app's own process is excluded from
     * the VPN (`addDisallowedApplication(packageName)` in
     * VpnReachabilityService), so an in-app HttpURLConnection bypasses the
     * tun and downloads fine WITHOUT tearing the VPN or the stream down. (The
     * earlier "stop the stream to update" fix targeted the wrong cause -
     * MediaProjection - and failed because the real blocker was the VPN.)
     *
     * [onStatus] is called on the main thread with a short human-readable
     * line per state change; on success this launches the installer. Caller
     * should have already checked [hasInstallPermission].
     */
    fun downloadAndInstall(context: Context, url: String, onStatus: (String) -> Unit = {}) {
        val appContext = context.applicationContext
        val destFile = File(appContext.getExternalFilesDir(null), FILE_NAME)
        val handler = Handler(Looper.getMainLooper())
        fun post(msg: String) = handler.post { onStatus(msg) }

        Thread({
            destFile.delete() // drop any stale previous download
            var conn: HttpURLConnection? = null
            try {
                // Follow cross-scheme redirects manually: GitHub's release
                // asset URL 302s to a signed objects.githubusercontent.com
                // URL, and HttpURLConnection won't auto-follow https->https
                // across hosts if the scheme/host policy trips - do it by hand
                // so a redirect never looks like a failure.
                var current = url
                var redirects = 0
                while (true) {
                    conn = (URL(current).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 15000
                        readTimeout = 30000
                        requestMethod = "GET"
                    }
                    val code = conn!!.responseCode
                    if (code in 300..399 && redirects < 5) {
                        val loc = conn!!.getHeaderField("Location") ?: break
                        conn!!.disconnect()
                        current = loc
                        redirects++
                        continue
                    }
                    if (code != HttpURLConnection.HTTP_OK) {
                        Log.w(TAG, "update download HTTP $code")
                        post("Download failed (HTTP $code). Try again.")
                        return@Thread
                    }
                    break
                }

                val total = conn!!.contentLengthLong
                conn!!.inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = (read * 100 / total).toInt()
                                if (pct != lastPct) { lastPct = pct; post("Downloading update... $pct%") }
                            } else {
                                post("Downloading update... ${read / 1024}KB")
                            }
                        }
                    }
                }
                handler.post {
                    onStatus("Download complete, opening installer...")
                    promptInstall(appContext, destFile, onStatus)
                }
            } catch (e: Exception) {
                Log.w(TAG, "in-app update download failed", e)
                post("Download failed: ${e.message ?: "unknown"}. Try again.")
            } finally {
                conn?.disconnect()
            }
        }, "apk-download").start()
    }

    private fun promptInstall(context: Context, apkFile: File, onStatus: (String) -> Unit) {
        if (!apkFile.exists()) {
            Log.w(TAG, "download reported success but file missing at ${apkFile.path}")
            onStatus("Download finished but the file is missing. Try again.")
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
