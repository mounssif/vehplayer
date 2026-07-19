package app.vehplayer.android.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import app.vehplayer.android.R

/**
 * One hero-card slide showing one pinned AppWidget at full hero size (see
 * HeroPagerAdapter). The AppWidgetHost itself (and its start/stopListening
 * lifecycle) stays owned by CarDashboardActivity - this fragment only
 * creates/attaches the host view for its own widget id.
 */
class WidgetSlideFragment : Fragment() {

    companion object {
        private const val ARG_WIDGET_ID = "widget_id"

        fun newInstance(widgetId: Int) = WidgetSlideFragment().apply {
            arguments = Bundle().apply { putInt(ARG_WIDGET_ID, widgetId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_widget_slide, container, false)
        val widgetId = requireArguments().getInt(ARG_WIDGET_ID)
        val dashboard = requireActivity() as CarDashboardActivity
        val frame = view.findViewById<FrameLayout>(R.id.widgetSlideContainer)

        val hostView = dashboard.pinnedWidgetHost.createHostView(widgetId)
        if (hostView != null) {
            frame.addView(
                hostView,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
            // Tell the provider its real rendered size - many widgets pick a
            // denser layout for larger cells; without this they render at
            // phone-homescreen default proportions inside the big hero card.
            frame.post {
                if (!isAdded) return@post
                val density = resources.displayMetrics.density
                val widthDp = (frame.width / density).toInt()
                val heightDp = (frame.height / density).toInt()
                if (widthDp > 0 && heightDp > 0) {
                    @Suppress("DEPRECATION")
                    hostView.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
                }
            }
        }

        view.findViewById<View>(R.id.widgetSlideUnpinButton).setOnClickListener {
            dashboard.unpinWidget(widgetId)
        }
        return view
    }
}
