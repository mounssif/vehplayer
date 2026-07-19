package app.vehplayer.android.media

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * The one listener component both Now Playing and Messages ride on (see
 * AndroidManifest.xml's comment on this service). Two independent jobs:
 *
 * 1. Lets [activeMediaController] hand back the phone's current
 *    [MediaController] (Spotify/YouTube Music/whatever), since
 *    MediaSessionManager.getActiveSessions requires naming an enabled
 *    NotificationListenerService component even though media playback
 *    state has nothing to do with notification content.
 * 2. Tracks currently-posted messaging notifications for the Messages
 *    overview - real notification content (sender, preview text) rather
 *    than reading any app's private message store (no public API reads
 *    WhatsApp content at all, and RCS threads aren't in the SMS provider,
 *    see NEXT_SESSION.md), with each entry's own PendingIntent used to
 *    open the real conversation on tap.
 */
class VehplayerNotificationListenerService : NotificationListenerService() {

    data class MessageNotification(
        val key: String,
        val appLabel: String,
        val title: String,
        val text: String,
        val postTime: Long,
        val contentIntent: PendingIntent?,
    )

    companion object {
        var instance: VehplayerNotificationListenerService? = null
            private set

        // A set, not a single nullable lambda: Now Playing and Messages both
        // need to react to the same events independently, one overwriting
        // the other's callback would silently break whichever registered
        // first.
        private val changeListeners = mutableSetOf<() -> Unit>()

        fun addChangeListener(listener: () -> Unit) {
            changeListeners.add(listener)
        }

        fun removeChangeListener(listener: () -> Unit) {
            changeListeners.remove(listener)
        }

        private fun notifyChange() {
            changeListeners.forEach { it() }
        }

        fun activeMediaController(context: android.content.Context): MediaController? {
            val listenerComponent = ComponentName(context, VehplayerNotificationListenerService::class.java)
            val manager = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            return try {
                manager.getActiveSessions(listenerComponent)
                    .firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
                    ?: manager.getActiveSessions(listenerComponent).firstOrNull()
            } catch (e: SecurityException) {
                // Listener access was revoked between the enabled-check and this
                // call (e.g. user just turned it off in Settings) - null is the
                // correct "nothing to show" answer, not a crash.
                null
            }
        }

        fun recentMessages(limit: Int = 20): List<MessageNotification> {
            val service = instance ?: return emptyList()
            return service.activeNotifications
                .filter { it.isMessagingNotification() }
                .sortedByDescending { it.postTime }
                .take(limit)
                .map { it.toMessageNotification(service) }
        }

        private fun StatusBarNotification.isMessagingNotification(): Boolean {
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
            if (notification.category == Notification.CATEGORY_MESSAGE) return true
            // Some messaging apps (older WhatsApp builds, some OEM SMS apps)
            // never set a category - MessagingStyle extras are the fallback
            // signal, present whenever a conversation is actually attached.
            return notification.extras.containsKey(Notification.EXTRA_MESSAGES) ||
                notification.extras.containsKey(Notification.EXTRA_SELF_DISPLAY_NAME)
        }

        private fun StatusBarNotification.toMessageNotification(
            service: VehplayerNotificationListenerService,
        ): MessageNotification {
            val extras = notification.extras
            val appLabel = try {
                val pm = service.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                packageName
            }
            return MessageNotification(
                key = key,
                appLabel = appLabel,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: appLabel,
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                postTime = postTime,
                contentIntent = notification.contentIntent,
            )
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        notifyChange()
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        notifyChange()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        notifyChange()
    }
}
