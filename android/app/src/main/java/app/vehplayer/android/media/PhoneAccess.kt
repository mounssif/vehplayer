package app.vehplayer.android.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Runtime dangerous permissions, not a special-access settings toggle like
 * [NotificationAccess] - the actual grant flow lives in CarDashboardActivity
 * (registerForActivityResult needs an Activity), this just centralizes the
 * "are we allowed" check both PhoneOverlayView and the Activity need.
 */
object PhoneAccess {

    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)

    fun isEnabled(context: Context): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
