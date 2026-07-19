package app.vehplayer.android.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.vehplayer.android.R

/**
 * The always-present trailing "Pin a widget" placeholder slide in the hero
 * pager (HeroPagerAdapter): tap anywhere to open the widget picker. Once a
 * widget is pinned, its slide is inserted before this one and this
 * placeholder stays at the end so another widget can always be added.
 */
class PinWidgetSlideFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_pin_widget_slide, container, false)
        view.setOnClickListener {
            (requireActivity() as CarDashboardActivity).startWidgetPickFlow()
        }
        return view
    }
}
