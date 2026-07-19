package app.vehplayer.android.dashboard

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.vehplayer.android.R

/**
 * Full-screen picker for which app the Navigate tile hands destinations
 * to: vehplayer's own built-in map, or a real installed nav app (Google
 * Maps, Waze, whatever's actually there - see NavAppPreference.kt). Same
 * overlay-at-CarDashboardActivity-level pattern as the other overlays.
 */
class NavAppPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onDismiss: (() -> Unit)? = null
    var onSelectionChanged: (() -> Unit)? = null

    private val closeButton: View
    private val list: LinearLayout

    init {
        LayoutInflater.from(context).inflate(R.layout.view_nav_app_picker, this, true)
        closeButton = findViewById(R.id.navAppPickerCloseButton)
        list = findViewById(R.id.navAppPickerList)
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
        val selected = NavAppPreference.selectedPackage(context)
        val inflater = LayoutInflater.from(context)

        addRow(inflater, "vehplayer map (built-in)", selected == null) {
            NavAppPreference.setSelectedPackage(context, null)
            selectionMade()
        }
        NavAppPreference.installedNavApps(context).forEach { app ->
            addRow(inflater, app.label, selected == app.packageName) {
                NavAppPreference.setSelectedPackage(context, app.packageName)
                selectionMade()
            }
        }
    }

    private fun addRow(inflater: LayoutInflater, label: String, isSelected: Boolean, onClick: () -> Unit) {
        val row = inflater.inflate(R.layout.item_nav_app, list, false)
        row.findViewById<TextView>(R.id.navAppLabel).text = label
        row.findViewById<ImageView>(R.id.navAppCheck).visibility = if (isSelected) View.VISIBLE else View.GONE
        row.setOnClickListener { onClick() }
        list.addView(row)
    }

    private fun selectionMade() {
        render()
        onSelectionChanged?.invoke()
    }
}
