package app.vehplayer.android.dashboard

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import app.vehplayer.android.BuildConfig
import app.vehplayer.android.R
import com.mapbox.geojson.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Full-screen destination search: text field + live suggestions + the
 * custom [CarKeyboardView], all owned at the CarDashboardActivity level
 * (see that XML's comment) rather than inside NavigateMapFragment's hero
 * card, which is too short on-screen to fit all three. NavigateMapFragment
 * just calls [open] and gets a callback with a resolved point once the
 * driver picks (or types and submits) a destination.
 */
class DestinationSearchOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onDismiss: (() -> Unit)? = null
    var onDestinationChosen: ((Point, String) -> Unit)? = null

    private val closeButton: View
    private val destinationInput: EditText
    private val searchProgress: ProgressBar
    private val searchErrorText: TextView
    private val suggestionsScroll: NestedScrollView
    private val suggestionsList: LinearLayout
    private val keyboard: CarKeyboardView

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var suggestJob: Job? = null
    private var searchSessionToken: String = UUID.randomUUID().toString()
    private var origin: Point = Point.fromLngLat(0.0, 0.0)

    init {
        LayoutInflater.from(context).inflate(R.layout.view_destination_search_overlay, this, true)
        closeButton = findViewById(R.id.overlayCloseButton)
        destinationInput = findViewById(R.id.overlayDestinationInput)
        searchProgress = findViewById(R.id.overlaySearchProgress)
        searchErrorText = findViewById(R.id.overlaySearchErrorText)
        suggestionsScroll = findViewById(R.id.overlaySuggestionsScroll)
        suggestionsList = findViewById(R.id.overlaySuggestionsList)
        keyboard = findViewById(R.id.overlayKeyboard)

        closeButton.setOnClickListener { onDismiss?.invoke() }

        destinationInput.showSoftInputOnFocus = false
        destinationInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitTypedQuery()
                true
            } else {
                false
            }
        }
        destinationInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = onQueryChanged(s?.toString().orEmpty())
        })

        keyboard.onKey = { text -> insertAtCursor(text) }
        keyboard.onSpace = { insertAtCursor(" ") }
        keyboard.onBackspace = {
            val start = destinationInput.selectionStart.coerceAtLeast(1)
            destinationInput.text?.delete(start - 1, start)
        }
        keyboard.onSearch = { submitTypedQuery() }
    }

    /** Reset to a blank search and show, focused, ready to type. */
    fun open(origin: Point) {
        this.origin = origin
        destinationInput.setText("")
        hideError()
        hideSuggestions()
        searchSessionToken = UUID.randomUUID().toString()
        keyboard.resetMode()
        visibility = View.VISIBLE
        destinationInput.requestFocus()
    }

    fun close() {
        suggestJob?.cancel()
        visibility = View.GONE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.coroutineContext[Job]?.cancel()
    }

    private fun insertAtCursor(text: String) {
        val start = destinationInput.selectionStart.coerceAtLeast(0)
        destinationInput.text?.insert(start, text)
    }

    private fun submitTypedQuery() {
        val query = destinationInput.text.toString().trim()
        if (query.isEmpty()) return
        hideSuggestions()
        hideError()
        searchProgress.visibility = View.VISIBLE
        scope.launch {
            val destination = withContext(Dispatchers.IO) {
                runCatching { geocode(query, origin) }.getOrNull()
            }
            searchSessionToken = UUID.randomUUID().toString()
            searchProgress.visibility = View.GONE
            if (destination == null) {
                showError("Couldn't find \"$query\"")
                return@launch
            }
            onDestinationChosen?.invoke(destination, query)
        }
    }

    // Debounced (300ms) so every keystroke doesn't fire a network call -
    // fine on a phone hotspot, wasteful and laggy on one.
    private fun onQueryChanged(query: String) {
        suggestJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 3) {
            hideSuggestions()
            return
        }
        suggestJob = scope.launch {
            delay(300)
            val suggestions = withContext(Dispatchers.IO) {
                runCatching { fetchSuggestions(trimmed, origin) }.getOrNull()
            }
            renderSuggestions(suggestions.orEmpty())
        }
    }

    private data class Suggestion(val mapboxId: String, val name: String, val address: String)

    // Search Box API, not the legacy /geocoding/v5/mapbox.places endpoint:
    // the legacy index has thin POI coverage and ranks pure text-relevance
    // over anything else - "Empire State Building" resolved to a street of
    // that name in Surat, India, ahead of the actual landmark in NYC.
    private fun fetchSuggestions(query: String, proximity: Point): List<Suggestion> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL(
            "https://api.mapbox.com/search/searchbox/v1/suggest?q=$encoded" +
                "&limit=5&proximity=${proximity.longitude()},${proximity.latitude()}" +
                "&session_token=$searchSessionToken&access_token=${BuildConfig.MAPBOX_PUBLIC_TOKEN}",
        )
        val body = fetch(url) ?: return emptyList()
        val results = JSONObject(body).optJSONArray("suggestions") ?: JSONArray()
        return (0 until results.length()).mapNotNull { i ->
            val item = results.getJSONObject(i)
            val id = item.optString("mapbox_id").ifEmpty { return@mapNotNull null }
            Suggestion(
                mapboxId = id,
                name = item.optString("name"),
                address = item.optString("place_formatted"),
            )
        }
    }

    private fun retrieveSuggestion(mapboxId: String): Point? {
        val url = URL(
            "https://api.mapbox.com/search/searchbox/v1/retrieve/$mapboxId" +
                "?session_token=$searchSessionToken&access_token=${BuildConfig.MAPBOX_PUBLIC_TOKEN}",
        )
        val body = fetch(url) ?: return null
        val features = JSONObject(body).optJSONArray("features") ?: return null
        if (features.length() == 0) return null
        val coords = features.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        return Point.fromLngLat(coords.getDouble(0), coords.getDouble(1))
    }

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
        val coords = features.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        return Point.fromLngLat(coords.getDouble(0), coords.getDouble(1))
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

    private fun renderSuggestions(suggestions: List<Suggestion>) {
        suggestionsList.removeAllViews()
        if (suggestions.isEmpty()) {
            hideSuggestions()
            return
        }
        val inflater = LayoutInflater.from(context)
        suggestions.forEach { suggestion ->
            val row = inflater.inflate(R.layout.item_suggestion, suggestionsList, false)
            row.findViewById<TextView>(R.id.suggestionName).text = suggestion.name
            row.findViewById<TextView>(R.id.suggestionAddress).text = suggestion.address
            row.contentDescription = "${suggestion.name}, ${suggestion.address}"
            row.setOnClickListener { selectSuggestion(suggestion) }
            suggestionsList.addView(
                row,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        suggestionsScroll.visibility = View.VISIBLE
        // TalkBack: announce that results appeared, so a driver relying on
        // audio feedback knows without having to glance down at the list.
        suggestionsScroll.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
    }

    private fun hideSuggestions() {
        suggestionsScroll.visibility = View.GONE
        suggestionsList.removeAllViews()
    }

    private fun selectSuggestion(suggestion: Suggestion) {
        hideSuggestions()
        searchProgress.visibility = View.VISIBLE
        scope.launch {
            val destination = withContext(Dispatchers.IO) {
                runCatching { retrieveSuggestion(suggestion.mapboxId) }.getOrNull()
            }
            searchSessionToken = UUID.randomUUID().toString()
            searchProgress.visibility = View.GONE
            if (destination == null) {
                showError("Couldn't look up \"${suggestion.name}\"")
                return@launch
            }
            onDestinationChosen?.invoke(destination, suggestion.name)
        }
    }

    private fun showError(message: String) {
        searchErrorText.text = message
        searchErrorText.visibility = View.VISIBLE
    }

    private fun hideError() {
        searchErrorText.visibility = View.GONE
    }
}
