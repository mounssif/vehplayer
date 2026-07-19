package app.vehplayer.android.dashboard

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.SharedPreferences

/**
 * Thin wrapper around AppWidgetHost for the dashboard's generic "pin any
 * widget" tile (NEXT_SESSION.md session 6: the right fit for things that
 * will never get bespoke integration - Home Assistant, a manufacturer's own
 * widget, weather - unlike Messages/Phone, which read real notification/
 * call data directly instead of embedding a foreign-styled widget). No
 * special permission needed: ACTION_APPWIDGET_PICK's own system Activity
 * handles the BIND_APPWIDGET consent internally, the same mechanism every
 * third-party launcher (Nova, etc.) uses. One pinned widget at a time,
 * persisted across restarts by its AppWidgetManager id.
 */
class PinnedWidgetHost(private val context: Context) {
    companion object {
        private const val HOST_ID = 4277 // arbitrary, just needs to be stable + unique within this app
        private const val PREFS_NAME = "vehplayer_widget"
        private const val KEY_WIDGET_ID = "pinned_widget_id"
    }

    val appWidgetHost = AppWidgetHost(context, HOST_ID)
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun allocateWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    /**
     * Null if nothing is pinned, or the persisted id is no longer valid (the
     * widget's own app was uninstalled since). Clears the stale pref as a
     * side effect in the latter case so it isn't checked again every launch.
     */
    fun pinnedWidgetId(): Int? {
        val id = prefs.getInt(KEY_WIDGET_ID, -1)
        if (id == -1) return null
        if (appWidgetManager.getAppWidgetInfo(id) != null) return id
        clearPinned()
        return null
    }

    fun persistPinned(widgetId: Int) {
        prefs.edit().putInt(KEY_WIDGET_ID, widgetId).apply()
    }

    /** Drops the persisted id and releases it back to AppWidgetHost; safe to call with nothing pinned. */
    fun clearPinned() {
        val id = prefs.getInt(KEY_WIDGET_ID, -1)
        prefs.edit().remove(KEY_WIDGET_ID).apply()
        if (id != -1) appWidgetHost.deleteAppWidgetId(id)
    }

    fun createHostView(widgetId: Int): AppWidgetHostView? {
        val info = appWidgetManager.getAppWidgetInfo(widgetId) ?: return null
        return appWidgetHost.createView(context, widgetId, info)
    }
}
