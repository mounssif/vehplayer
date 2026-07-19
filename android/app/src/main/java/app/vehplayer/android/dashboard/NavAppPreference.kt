package app.vehplayer.android.dashboard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.mapbox.geojson.Point

/**
 * v1 prep, per real user feedback: not everyone wants vehplayer's own
 * Mapbox rendering - some want the Navigate tile to just launch a real
 * nav app (Google Maps, Waze) they already trust, the same way the
 * original three-phase CarDashboardActivity plan scoped a "nav app
 * picker" for Phase 3. Detects installed apps that can actually handle a
 * `geo:` intent (not a hardcoded Google Maps/Waze allowlist - whatever's
 * really installed) rather than assuming which ones exist.
 */
object NavAppPreference {

    data class NavApp(val packageName: String, val label: String)

    private const val PREFS = "nav_app_prefs"
    private const val KEY_SELECTED_PACKAGE = "selected_package"

    /** Null means "use the built-in map" - the default. */
    fun selectedPackage(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SELECTED_PACKAGE, null)

    fun setSelectedPackage(context: Context, packageName: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SELECTED_PACKAGE, packageName)
            .apply()
    }

    /** Real installed apps that resolve a `geo:` intent, not a fixed allowlist. */
    fun installedNavApps(context: Context): List<NavApp> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=0,0"))
        return pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                // The car dashboard's own webclient/HttpAssetServer is not a
                // real nav app even though nothing forces it out of this
                // query result set; there's no realistic case where it would
                // show up here, no explicit filter needed beyond the geo:
                // intent match itself.
                val label = resolveInfo.loadLabel(pm)?.toString() ?: packageName
                NavApp(packageName, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Launches the chosen external app at the given destination, car-dial-style (no in-app route drawing). */
    fun launchExternal(context: Context, packageName: String, destination: Point, label: String) {
        val uri = Uri.parse(
            "geo:${destination.latitude()},${destination.longitude()}?q=${destination.latitude()},${destination.longitude()}($label)",
        )
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)
        runCatching { context.startActivity(intent) }
    }
}
