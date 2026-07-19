package app.vehplayer.android.media

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Same permission-grant pattern as the accessibility flow
 * (MainActivity.isAccessibilityServiceEnabled / ACTION_ACCESSIBILITY_SETTINGS):
 * a sensitive one-time system settings grant, checked defensively rather
 * than assumed, with a plain settings Intent to send the user to when it's
 * missing. Covers both Now Playing (MediaSessionManager.getActiveSessions
 * requires an enabled listener component even though it doesn't read
 * notification content) and Messages (which does read notification content).
 */
object NotificationAccess {

    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
