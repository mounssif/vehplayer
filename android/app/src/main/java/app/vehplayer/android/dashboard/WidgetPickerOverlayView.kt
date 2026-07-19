package app.vehplayer.android.dashboard

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.vehplayer.android.R

/**
 * Full-screen picker over every installed home-screen widget provider, our
 * own UI instead of ACTION_APPWIDGET_PICK's system Activity. Two reasons
 * (session 8): the system picker's contents vary per OEM and demonstrably
 * omitted providers on a real device (Google Maps' widget was missing from
 * it while the phone's own launcher picker showed it - MEASURED, founder's
 * Galaxy S23), and its phone-styled UI clashes with the car screen this
 * dashboard is mirrored to. AppWidgetManager.installedProviders is the
 * same source launchers build their own pickers from.
 *
 * Same overlay-at-CarDashboardActivity-level pattern as the other overlays.
 */
class WidgetPickerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onDismiss: (() -> Unit)? = null
    var onWidgetChosen: ((AppWidgetProviderInfo) -> Unit)? = null

    private val closeButton: View
    private val list: LinearLayout

    init {
        LayoutInflater.from(context).inflate(R.layout.view_widget_picker, this, true)
        closeButton = findViewById(R.id.widgetPickerCloseButton)
        list = findViewById(R.id.widgetPickerList)
        closeButton.setOnClickListener { onDismiss?.invoke() }
    }

    fun open() {
        visibility = View.VISIBLE
        render()
    }

    fun close() {
        visibility = View.GONE
    }

    private fun render() {
        list.removeAllViews()
        val inflater = LayoutInflater.from(context)
        val pm = context.packageManager

        val byApp = AppWidgetManager.getInstance(context).installedProviders
            .groupBy { info ->
                runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                }.getOrDefault(info.provider.packageName)
            }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)

        byApp.forEach { (appLabel, providers) ->
            val header = inflater.inflate(R.layout.item_widget_app_header, list, false)
            (header as TextView).text = appLabel
            list.addView(header)

            providers.sortedBy { it.loadLabel(pm) }.forEach { info ->
                val row = inflater.inflate(R.layout.item_widget_provider, list, false)
                row.findViewById<TextView>(R.id.widgetProviderLabel).text = info.loadLabel(pm)
                val iconView = row.findViewById<ImageView>(R.id.widgetProviderIcon)
                val icon = runCatching { info.loadIcon(context, 0) }.getOrNull()
                if (icon != null) iconView.setImageDrawable(icon) else iconView.visibility = View.INVISIBLE
                row.setOnClickListener { onWidgetChosen?.invoke(info) }
                list.addView(row)
            }
        }

        if (byApp.isEmpty()) {
            val header = inflater.inflate(R.layout.item_widget_app_header, list, false)
            (header as TextView).text = "No widgets available on this phone"
            list.addView(header)
        }
    }
}
