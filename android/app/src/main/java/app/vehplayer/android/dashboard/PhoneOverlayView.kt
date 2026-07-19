package app.vehplayer.android.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import app.vehplayer.android.R
import app.vehplayer.android.media.PhoneAccess
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Full-screen Phone overview: recent calls + contacts w/ an A-Z scrubber,
 * same owned-at-CarDashboardActivity-level reasoning as the other
 * overlays. Unlike those, the permissions here (READ_CALL_LOG,
 * READ_CONTACTS) are ordinary runtime dangerous permissions, not a
 * special-access settings toggle - the actual request has to go through
 * an Activity's ActivityResultLauncher, so this view just asks its host
 * (via [onRequestPermissions]) rather than requesting directly.
 */
class PhoneOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onDismiss: (() -> Unit)? = null
    var onRequestPermissions: (() -> Unit)? = null

    private val closeButton: View
    private val dialButton: View
    private val tabRecent: TextView
    private val tabContacts: TextView
    private val emptyState: View
    private val emptyText: TextView
    private val recentScroll: NestedScrollView
    private val recentList: LinearLayout
    private val contactsContainer: View
    private val contactsScroll: NestedScrollView
    private val contactsList: LinearLayout
    private val azIndex: LinearLayout

    private var showingRecent = true
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val contactRowsByLetter = mutableMapOf<Char, View>()

    init {
        LayoutInflater.from(context).inflate(R.layout.view_phone_overlay, this, true)
        closeButton = findViewById(R.id.phoneCloseButton)
        dialButton = findViewById(R.id.phoneDialButton)
        tabRecent = findViewById(R.id.phoneTabRecent)
        tabContacts = findViewById(R.id.phoneTabContacts)
        emptyState = findViewById(R.id.phoneEmptyState)
        emptyText = findViewById(R.id.phoneEmptyText)
        recentScroll = findViewById(R.id.recentScroll)
        recentList = findViewById(R.id.recentList)
        contactsContainer = findViewById(R.id.contactsContainer)
        contactsScroll = findViewById(R.id.contactsScroll)
        contactsList = findViewById(R.id.contactsList)
        azIndex = findViewById(R.id.azIndex)

        closeButton.setOnClickListener { onDismiss?.invoke() }
        dialButton.setOnClickListener { context.startActivity(Intent(Intent.ACTION_DIAL)) }
        tabRecent.setOnClickListener { switchTab(recent = true) }
        tabContacts.setOnClickListener { switchTab(recent = false) }
        azIndex.setOnTouchListener { _, event -> handleIndexTouch(event); true }
    }

    fun open() {
        visibility = View.VISIBLE
        refresh()
    }

    fun close() {
        visibility = View.GONE
    }

    /** Called by the host after the permission dialog result comes back. */
    fun refresh() {
        if (!PhoneAccess.isEnabled(context)) {
            showEmpty("Tap to enable Phone", enablePrompt = true)
            return
        }
        emptyState.visibility = View.GONE
        loadRecentCalls()
        loadContacts()
        switchTab(showingRecent)
    }

    private fun switchTab(recent: Boolean) {
        showingRecent = recent
        recentScroll.visibility = if (recent) View.VISIBLE else View.GONE
        contactsContainer.visibility = if (recent) View.GONE else View.VISIBLE
        tabRecent.setBackgroundResource(if (recent) R.drawable.bg_keyboard_key_accent else 0)
        tabRecent.setTextColor(context.getColor(if (recent) R.color.dash_bg else R.color.dash_text_muted))
        tabContacts.setBackgroundResource(if (!recent) R.drawable.bg_keyboard_key_accent else 0)
        tabContacts.setTextColor(context.getColor(if (!recent) R.color.dash_bg else R.color.dash_text_muted))
    }

    private data class CallEntry(val name: String, val number: String, val type: Int, val date: Long)

    private fun loadRecentCalls() {
        recentList.removeAllViews()
        val entries = mutableListOf<CallEntry>()
        val projection = arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
        )
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                // Modern CallLog rejects a "LIMIT" clause tacked onto
                // sortOrder (IllegalArgumentException, strict grammar
                // validation since API 30) - cap client-side instead.
                while (cursor.moveToNext() && entries.size < 30) {
                    entries.add(
                        CallEntry(
                            name = cursor.getString(0).orEmpty(),
                            number = cursor.getString(1).orEmpty(),
                            type = cursor.getInt(2),
                            date = cursor.getLong(3),
                        ),
                    )
                }
            }
        }.onFailure { android.util.Log.w("PhoneOverlayView", "call log query failed", it) }
        if (entries.isEmpty()) {
            showEmpty("No recent calls", enablePrompt = false)
            return
        }
        val inflater = LayoutInflater.from(context)
        entries.forEach { entry ->
            val row = inflater.inflate(R.layout.item_call_log, recentList, false)
            row.findViewById<TextView>(R.id.callName).text = entry.name.ifEmpty { entry.number }
            row.findViewById<TextView>(R.id.callTime).text =
                "${callTypeLabel(entry.type)} • ${timeFormat.format(entry.date)}"
            row.findViewById<ImageView>(R.id.callTypeIcon).setColorFilter(
                context.getColor(if (entry.type == CallLog.Calls.MISSED_TYPE) R.color.dash_accent_amber else R.color.dash_text_muted),
            )
            row.setOnClickListener {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entry.number}")))
            }
            recentList.addView(row)
        }
    }

    private fun callTypeLabel(type: Int) = when (type) {
        CallLog.Calls.MISSED_TYPE -> "Missed"
        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
        CallLog.Calls.INCOMING_TYPE -> "Incoming"
        else -> "Call"
    }

    private data class Contact(val id: Long, val name: String)

    private fun loadContacts() {
        contactsList.removeAllViews()
        contactRowsByLetter.clear()
        azIndex.removeAllViews()

        val contacts = mutableListOf<Contact>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1",
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    contacts.add(Contact(id = cursor.getLong(0), name = name))
                }
            }
        }.onFailure { android.util.Log.w("PhoneOverlayView", "contacts query failed", it) }

        val inflater = LayoutInflater.from(context)
        contacts.forEach { contact ->
            val row = inflater.inflate(R.layout.item_contact, contactsList, false)
            row.findViewById<TextView>(R.id.contactInitial).text = contact.name.take(1).uppercase()
            row.findViewById<TextView>(R.id.contactName).text = contact.name
            row.setOnClickListener {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id.toString()),
                    ),
                )
            }
            contactsList.addView(row)

            val letter = contact.name.first().uppercaseChar().let { if (it in 'A'..'Z') it else '#' }
            contactRowsByLetter.getOrPut(letter) { row }
        }

        contactRowsByLetter.keys.sorted().forEach { letter ->
            azIndex.addView(
                TextView(context).apply {
                    text = letter.toString()
                    textSize = 10f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(context.getColor(R.color.dash_text_muted))
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
                },
            )
        }
    }

    private fun handleIndexTouch(event: MotionEvent) {
        if (azIndex.childCount == 0) return
        val index = (event.y / azIndex.height.coerceAtLeast(1) * azIndex.childCount)
            .toInt()
            .coerceIn(0, azIndex.childCount - 1)
        val letter = (azIndex.getChildAt(index) as TextView).text.first()
        contactRowsByLetter[letter]?.let { row -> contactsScroll.scrollTo(0, row.top) }
    }

    private fun showEmpty(message: String, enablePrompt: Boolean) {
        recentScroll.visibility = View.GONE
        contactsContainer.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        emptyText.text = message
        emptyState.setOnClickListener {
            if (enablePrompt) onRequestPermissions?.invoke()
        }
    }
}
