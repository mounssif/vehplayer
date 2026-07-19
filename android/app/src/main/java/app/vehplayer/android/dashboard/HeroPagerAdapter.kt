package app.vehplayer.android.dashboard

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/** heroPager's two pages (CarDashboardActivity/activity_car_dashboard.xml): Now Playing, Navigate. */
class HeroPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 2

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> NowPlayingFragment()
        else -> NavigateMapFragment()
    }
}
