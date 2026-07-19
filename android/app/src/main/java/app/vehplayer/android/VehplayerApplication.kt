package app.vehplayer.android

import android.app.Application
import com.mapbox.common.MapboxOptions

/**
 * Sets the Mapbox access token before any Activity/Fragment can run - the
 * embedded map layout (dashboard/fragment_navigate_map.xml) inflates a
 * MapView, and Mapbox reads MapboxOptions.accessToken at inflation time.
 * Setting it in a Fragment's onViewCreated is too late: Fragment's
 * onCreateView (which inflates the layout) always runs first.
 */
class VehplayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapboxOptions.accessToken = BuildConfig.MAPBOX_PUBLIC_TOKEN
    }
}
