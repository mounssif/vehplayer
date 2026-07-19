package app.vehplayer.android.dashboard

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import app.vehplayer.android.R
import app.vehplayer.android.media.NotificationAccess
import app.vehplayer.android.media.VehplayerNotificationListenerService

/**
 * Full-screen recent-messages overview (see the layout's doc comment for
 * why it lives at the CarDashboardActivity level, not inside the Messages
 * tile). Built on notification metadata, not any per-app message store:
 * there's no public API to read WhatsApp content at all, and RCS threads
 * aren't in the SMS provider, so notifications (sender, preview, an
 * app-provided tap intent that opens the real conversation) are the only
 * data source that generically works across SMS/WhatsApp/Telegram/etc -
 * see VehplayerNotificationListenerService's doc comment.
 */
class MessagesOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onDismiss: (() -> Unit)? = null

    private val closeButton: View
    private val openAppButton: View
    private val emptyState: View
    private val emptyText: TextView
    private val emptySubtext: TextView
    private val scroll: NestedScrollView
    private val list: LinearLayout

    private val onNotificationChange = { if (visibility == View.VISIBLE) refresh() }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_messages_overlay, this, true)
        closeButton = findViewById(R.id.messagesCloseButton)
        openAppButton = findViewById(R.id.messagesOpenAppButton)
        emptyState = findViewById(R.id.messagesEmptyState)
        emptyText = findViewById(R.id.messagesEmptyText)
        emptySubtext = findViewById(R.id.messagesEmptySubtext)
        scroll = findViewById(R.id.messagesScroll)
        list = findViewById(R.id.messagesList)

        closeButton.setOnClickListener { onDismiss?.invoke() }
        openAppButton.setOnClickListener {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            )
        }

        VehplayerNotificationListenerService.addChangeListener(onNotificationChange)
    }

    override fun onDetachedFromWindow() {
        VehplayerNotificationListenerService.removeChangeListener(onNotificationChange)
        super.onDetachedFromWindow()
    }

    fun open() {
        visibility = View.VISIBLE
        refresh()
    }

    fun close() {
        visibility = View.GONE
    }

    private fun refresh() {
        if (!NotificationAccess.isEnabled(context)) {
            showEmpty(
                "Tap to enable Messages",
                enablePrompt = true,
                subtext = null,
            )
            return
        }
        val messages = VehplayerNotificationListenerService.recentMessages()
        if (messages.isEmpty()) {
            // Real-device feedback: this can show even with a large
            // "unread" count inside the Messages app itself - that count
            // is the app's own inbox state, not what's currently posted as
            // a system notification. Once a notification's been seen or
            // cleared on the phone, there's nothing left for a
            // NotificationListenerService to read (no API reads the
            // app's actual inbox generically, see this class's doc
            // comment) - explain that rather than let it read as broken.
            showEmpty(
                "No recent messages",
                enablePrompt = false,
                subtext = "Shows new messages as they arrive, not your full inbox",
            )
            return
        }
        emptyState.visibility = View.GONE
        scroll.visibility = View.VISIBLE
        list.removeAllViews()
        val inflater = LayoutInflater.from(context)
        messages.forEach { message ->
            val row = inflater.inflate(R.layout.item_message_notification, list, false)
            row.findViewById<TextView>(R.id.messageSender).text = message.title
            row.findViewById<TextView>(R.id.messageApp).text = message.appLabel
            row.findViewById<TextView>(R.id.messageText).text = message.text
            row.contentDescription = "${message.appLabel}, ${message.title}: ${message.text}"
            row.setOnClickListener { openConversation(message.contentIntent) }
            list.addView(row)
        }
    }

    private fun openConversation(contentIntent: PendingIntent?) {
        if (contentIntent == null) return
        runCatching { contentIntent.send() }
        onDismiss?.invoke()
    }

    private fun showEmpty(message: String, enablePrompt: Boolean, subtext: String?) {
        scroll.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        emptyText.text = message
        emptySubtext.text = subtext
        emptySubtext.visibility = if (subtext != null) View.VISIBLE else View.GONE
        emptyState.setOnClickListener {
            if (enablePrompt) context.startActivity(NotificationAccess.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
