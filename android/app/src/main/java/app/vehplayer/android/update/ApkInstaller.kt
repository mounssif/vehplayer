package app.vehplayer.android.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
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

    /** REQUEST_INSTALL_PACKAGES is a manifest-declared permission, but installing from a
     * specific source still needs this per-app runtime toggle (same shape as the
     * accessibility "restricted settings" gate the user already had to clear once). */
    fun hasInstallPermission(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun installPermissionSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Fire-and-forget: downloads via DownloadManager (handles retry/progress
     * natively), then launches the installer once the download actually
     * succeeded. Caller should have already checked [hasInstallPermission].
     */
    fun downloadAndInstall(context: Context, url: String) {
        val appContext = context.applicationContext
        File(appContext.getExternalFilesDir(null), FILE_NAME).delete() // drop any stale previous download

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("vehplayer update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, null, FILE_NAME)

        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                appContext.unregisterReceiver(this)
                if (downloadSucceeded(manager, downloadId)) {
                    promptInstall(appContext, File(appContext.getExternalFilesDir(null), FILE_NAME))
                } else {
                    Log.w(TAG, "update download failed, downloadId=$downloadId")
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

    private fun downloadSucceeded(manager: DownloadManager, downloadId: Long): Boolean {
        val cursor: Cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
        cursor.use {
            if (!it.moveToFirst()) return false
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return status == DownloadManager.STATUS_SUCCESSFUL
        }
    }

    private fun promptInstall(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Log.w(TAG, "download reported success but file missing at ${apkFile.path}")
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
