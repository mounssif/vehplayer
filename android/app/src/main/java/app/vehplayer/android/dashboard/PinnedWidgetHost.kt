package app.vehplayer.android.dashboard

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.SharedPreferences

/**
 * Thin wrapper around AppWidgetHost for the dashboard's generic "pin any
 * widget" feature (NEXT_SESSION.md session 6: the right fit for things that
 * will never get bespoke integration - Home Assistant, a manufacturer's own
 * widget, weather - unlike Messages/Phone, which read real notification/
 * call data directly instead of embedding a foreign-styled widget). No
 * special permission needed: ACTION_APPWIDGET_BIND's consent dialog is the
 * same mechanism every third-party launcher (Nova, etc.) uses.
 *
 * Session 8: widgets moved from a single small tile into hero-card slides
 * (real user feedback: a phone-homescreen widget squeezed into the tile was
 * unreadable), so this now persists an ordered LIST of pinned widget ids -
 * one slide each - instead of a single id.
 */
class PinnedWidgetHost(private val context: Context) {
    companion object {
        private const val HOST_ID = 4277 // arbitrary, just needs to be stable + unique within this app
        private const val PREFS_NAME = "vehplayer_widget"
        private const val KEY_WIDGET_ID = "pinned_widget_id" // legacy single-widget key, migrated below
        private const val KEY_WIDGET_IDS = "pinned_widget_ids" // ordered, comma-joined
    }

    val appWidgetHost = AppWidgetHost(context, HOST_ID)
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    /**
     * Ordered list of pinned widget ids. Ids whose provider is no longer
     * valid (the widget's own app was uninstalled since) are dropped and
     * released as a side effect so they aren't re-checked every launch.
     */
    fun pinnedWidgetIds(): List<Int> {
        migrateLegacySingleId()
        val stored = prefs.getString(KEY_WIDGET_IDS, null).orEmpty()
        if (stored.isEmpty()) return emptyList()
        val ids = stored.split(',').mapNotNull { it.toIntOrNull() }
        val (valid, stale) = ids.partition { appWidgetManager.getAppWidgetInfo(it) != null }
        if (stale.isNotEmpty()) {
            stale.forEach { appWidgetHost.deleteAppWidgetId(it) }
            persist(valid)
        }
        return valid
    }

    fun addPinned(widgetId: Int) {
        persist(pinnedWidgetIds() + widgetId)
    }

    /** Drops one pinned widget and releases its id back to AppWidgetHost. */
    fun removePinned(widgetId: Int) {
        persist(pinnedWidgetIds() - widgetId)
        appWidgetHost.deleteAppWidgetId(widgetId)
    }

    fun createHostView(widgetId: Int): AppWidgetHostView? {
        val info = appWidgetManager.getAppWidgetInfo(widgetId) ?: return null
        return appWidgetHost.createView(context, widgetId, info)
    }

    private fun persist(ids: List<Int>) {
        prefs.edit().putString(KEY_WIDGET_IDS, ids.joinToString(",")).apply()
    }

    /** A widget pinned under the pre-slides single-id scheme keeps working after the update. */
    private fun migrateLegacySingleId() {
        val legacy = prefs.getInt(KEY_WIDGET_ID, -1)
        if (legacy == -1) return
        prefs.edit().remove(KEY_WIDGET_ID).apply()
        val stored = prefs.getString(KEY_WIDGET_IDS, null).orEmpty()
        val ids = stored.split(',').mapNotNull { it.toIntOrNull() }
        if (!ids.contains(legacy)) persist(ids + legacy)
    }
}
