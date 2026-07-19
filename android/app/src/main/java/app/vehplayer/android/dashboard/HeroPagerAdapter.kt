package app.vehplayer.android.dashboard

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * heroPager's pages (CarDashboardActivity/activity_car_dashboard.xml):
 * Now Playing, Navigate, one slide per pinned widget, and always a trailing
 * empty "Pin a widget" placeholder slide - so pinning is discoverable by
 * swiping (real user feedback, session 8) and a freshly pinned widget gets
 * the whole hero card instead of a small unreadable tile.
 *
 * Stable ids are what lets [setWidgetIds] + notifyDataSetChanged() work on
 * a FragmentStateAdapter: widget slides use the (positive) app-widget id
 * itself, the three fixed pages use negative sentinels.
 */
class HeroPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    companion object {
        const val POSITION_NOW_PLAYING = 0
        const val POSITION_NAVIGATE = 1
        private const val FIXED_LEADING_PAGES = 2

        private const val ID_NOW_PLAYING = -1L
        private const val ID_NAVIGATE = -2L
        private const val ID_PIN_PLACEHOLDER = -3L
    }

    var widgetIds: List<Int> = emptyList()
        private set

    @Suppress("NotifyDataSetChanged") // page set is tiny; stable ids keep the fixed pages alive
    fun setWidgetIds(ids: List<Int>) {
        widgetIds = ids
        notifyDataSetChanged()
    }

    /** Position of the slide showing [widgetId], or -1 if it isn't pinned. */
    fun positionOfWidget(widgetId: Int): Int {
        val index = widgetIds.indexOf(widgetId)
        return if (index == -1) -1 else FIXED_LEADING_PAGES + index
    }

    override fun getItemCount() = FIXED_LEADING_PAGES + widgetIds.size + 1

    override fun createFragment(position: Int): Fragment = when {
        position == POSITION_NOW_PLAYING -> NowPlayingFragment()
        position == POSITION_NAVIGATE -> NavigateMapFragment()
        position < FIXED_LEADING_PAGES + widgetIds.size ->
            WidgetSlideFragment.newInstance(widgetIds[position - FIXED_LEADING_PAGES])
        else -> PinWidgetSlideFragment()
    }

    override fun getItemId(position: Int): Long = when {
        position == POSITION_NOW_PLAYING -> ID_NOW_PLAYING
        position == POSITION_NAVIGATE -> ID_NAVIGATE
        position < FIXED_LEADING_PAGES + widgetIds.size ->
            widgetIds[position - FIXED_LEADING_PAGES].toLong()
        else -> ID_PIN_PLACEHOLDER
    }

    override fun containsItem(itemId: Long): Boolean = when (itemId) {
        ID_NOW_PLAYING, ID_NAVIGATE, ID_PIN_PLACEHOLDER -> true
        else -> itemId in 0..Int.MAX_VALUE && widgetIds.contains(itemId.toInt())
    }
}
