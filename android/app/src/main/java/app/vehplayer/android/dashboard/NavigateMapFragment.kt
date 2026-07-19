package app.vehplayer.android.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.vehplayer.android.BuildConfig
import app.vehplayer.android.R
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CoordinateBounds
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Page 2 of heroPager: our own embedded live map, not another app's screen -
 * Android has no API to embed a foreign app's UI the way CarPlay/Android
 * Auto do (see NEXT_SESSION.md, the founder-pushback thread). Destination
 * search + routing are ours too: there's no public API to read what another
 * nav app is currently routing to.
 */
class NavigateMapFragment : Fragment(R.layout.fragment_navigate_map) {

    private lateinit var mapView: MapView
    private lateinit var searchProgress: ProgressBar
    private lateinit var routeInfoPill: TextView
    private lateinit var searchErrorText: TextView
    private var lastKnownOrigin: Point? = null

    // Fragment implements ActivityResultCaller directly (androidx.fragment
    // 1.3+); registering here (not in onViewCreated) is required, it must
    // happen before the fragment reaches STARTED.
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) enableLocationComponent() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.mapView)
        searchProgress = view.findViewById(R.id.searchProgress)
        routeInfoPill = view.findViewById(R.id.routeInfoPill)
        searchErrorText = view.findViewById(R.id.searchErrorText)

        mapView.mapboxMap.loadStyle(Style.DARK) {
            if (hasLocationPermission()) {
                enableLocationComponent()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        val destinationInput = view.findViewById<EditText>(R.id.destinationInput)
        destinationInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = destinationInput.text.toString().trim()
                if (query.isNotEmpty()) search(query)
                true
            } else {
                false
            }
        }
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        requireContext(),
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    private fun enableLocationComponent() {
        mapView.location.updateSettings {
            enabled = true
            pulsingEnabled = true
        }
        mapView.location.addOnIndicatorPositionChangedListener { point ->
            lastKnownOrigin = point
        }
    }

    private fun search(query: String) {
        searchProgress.visibility = View.VISIBLE
        searchErrorText.visibility = View.GONE
        routeInfoPill.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val origin = lastKnownOrigin ?: mapView.mapboxMap.cameraState.center
            val destination = withContext(Dispatchers.IO) {
                runCatching { geocode(query, origin) }.getOrNull()
            }
            if (destination == null) {
                showError("Couldn't find \"$query\"")
                return@launch
            }
            val route = withContext(Dispatchers.IO) { runCatching { fetchRoute(origin, destination) }.getOrNull() }
            if (route == null) {
                showError("Couldn't find a route there")
                return@launch
            }
            drawRoute(route.line, origin, destination)
            routeInfoPill.text = route.label
            routeInfoPill.visibility = View.VISIBLE
            searchProgress.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        searchProgress.visibility = View.GONE
        searchErrorText.text = message
        searchErrorText.visibility = View.VISIBLE
    }

    // Search Box API, not the legacy /geocoding/v5/mapbox.places endpoint:
    // the legacy index has thin POI coverage and ranks pure text-relevance
    // over anything else - "Empire State Building" resolved to a street of
    // that name in Surat, India, ahead of the actual landmark in NYC.
    // Proximity biases among close-relevance matches (doesn't fix a
    // relevance=1 legacy mismatch, but matters here since results are
    // already POI-ranked).
    private fun geocode(query: String, proximity: Point): Point? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL(
            "https://api.mapbox.com/search/searchbox/v1/forward?q=$encoded" +
                "&limit=1&proximity=${proximity.longitude()},${proximity.latitude()}" +
                "&access_token=${BuildConfig.MAPBOX_PUBLIC_TOKEN}",
        )
        val body = fetch(url) ?: return null
        val features = JSONObject(body).optJSONArray("features") ?: return null
        if (features.length() == 0) return null
        val coords = features.getJSONObject(0)
            .getJSONObject("geometry")
            .getJSONArray("coordinates")
        return Point.fromLngLat(coords.getDouble(0), coords.getDouble(1))
    }

    private class Route(val line: LineString, val label: String)

    private fun fetchRoute(origin: Point, destination: Point): Route? {
        val coordsParam = "${origin.longitude()},${origin.latitude()};" +
            "${destination.longitude()},${destination.latitude()}"
        val url = URL(
            "https://api.mapbox.com/directions/v5/mapbox/driving/$coordsParam" +
                "?geometries=geojson&overview=full&access_token=${BuildConfig.MAPBOX_PUBLIC_TOKEN}",
        )
        val body = fetch(url) ?: return null
        val routes = JSONObject(body).optJSONArray("routes") ?: return null
        if (routes.length() == 0) return null
        val routeObj = routes.getJSONObject(0)
        val line = LineString.fromJson(routeObj.getJSONObject("geometry").toString())
        val durationMin = (routeObj.getDouble("duration") / 60).toInt()
        val distanceKm = routeObj.getDouble("distance") / 1000
        return Route(line, "$durationMin min • ${"%.1f".format(distanceKm)} km")
    }

    private fun fetch(url: URL): String? {
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun drawRoute(line: LineString, origin: Point, destination: Point) {
        val lineManager = mapView.annotations.createPolylineAnnotationManager()
        lineManager.deleteAll()
        lineManager.create(
            PolylineAnnotationOptions()
                .withPoints(line.coordinates())
                .withLineColor("#F2A93B")
                .withLineWidth(4.0),
        )

        val bounds = CoordinateBounds(
            Point.fromLngLat(
                minOf(origin.longitude(), destination.longitude()),
                minOf(origin.latitude(), destination.latitude()),
            ),
            Point.fromLngLat(
                maxOf(origin.longitude(), destination.longitude()),
                maxOf(origin.latitude(), destination.latitude()),
            ),
        )
        val camera = mapView.mapboxMap.cameraForCoordinateBounds(
            bounds,
            EdgeInsets(80.0, 60.0, 140.0, 60.0),
        )
        mapView.mapboxMap.flyTo(camera)
    }
}
