package app.vehplayer.android.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

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
    private const val POLL_INTERVAL_MS = 400L

    /** REQUEST_INSTALL_PACKAGES is a manifest-declared permission, but installing from a
     * specific source still needs this per-app runtime toggle (same shape as the
     * accessibility "restricted settings" gate the user already had to clear once). */
    fun hasInstallPermission(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun installPermissionSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Downloads via DownloadManager (handles the actual transfer natively),
     * polling it for progress since DownloadManager's own notification is
     * easy to miss and gives no in-app feedback. [onStatus] is called on the
     * main thread with a short human-readable line for each state change;
     * on success this also launches the installer. Caller should have
     * already checked [hasInstallPermission].
     */
    fun downloadAndInstall(context: Context, url: String, onStatus: (String) -> Unit = {}) {
        val appContext = context.applicationContext
        val destFile = File(appContext.getExternalFilesDir(null), FILE_NAME)
        destFile.delete() // drop any stale previous download

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("vehplayer update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, null, FILE_NAME)

        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)
        val handler = Handler(Looper.getMainLooper())

        // Progress polling and the completion broadcast can both fire the
        // final state; guard against reporting it (and launching the
        // installer) twice.
        var finished = false

        fun finish(success: Boolean, message: String) {
            if (finished) return
            finished = true
            onStatus(message)
            if (success) promptInstall(appContext, destFile, onStatus)
        }

        val poll = object : Runnable {
            override fun run() {
                if (finished) return
                val info = queryStatus(manager, downloadId)
                when (info?.status) {
                    DownloadManager.STATUS_RUNNING -> {
                        onStatus(
                            if (info.totalBytes > 0) {
                                "Downloading update... ${(info.bytesSoFar * 100 / info.totalBytes)}%"
                            } else {
                                "Downloading update..."
                            },
                        )
                        handler.postDelayed(this, POLL_INTERVAL_MS)
                    }
                    DownloadManager.STATUS_PENDING -> {
                        onStatus("Download queued...")
                        handler.postDelayed(this, POLL_INTERVAL_MS)
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> finish(true, "Download complete, opening installer...")
                    DownloadManager.STATUS_FAILED -> {
                        Log.w(TAG, "update download failed, reason=${info.reason}")
                        finish(false, "Download failed. Check your connection and try again.")
                    }
                    else -> handler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }
        handler.post(poll)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                appContext.unregisterReceiver(this)
                val info = queryStatus(manager, downloadId)
                if (info?.status == DownloadManager.STATUS_SUCCESSFUL) {
                    finish(true, "Download complete, opening installer...")
                } else {
                    Log.w(TAG, "update download finished but not successful, reason=${info?.reason}")
                    finish(false, "Download failed. Check your connection and try again.")
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, filter)
        }
    }

    private data class DownloadInfo(val status: Int, val bytesSoFar: Long, val totalBytes: Long, val reason: Int)

    private fun queryStatus(manager: DownloadManager, downloadId: Long): DownloadInfo? {
        val cursor: Cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
        cursor.use {
            if (!it.moveToFirst()) return null
            return DownloadInfo(
                status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                bytesSoFar = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                totalBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            )
        }
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
