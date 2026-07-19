package app.vehplayer.android.dashboard

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import app.vehplayer.android.R
import app.vehplayer.android.media.PhoneAccess
import com.mapbox.geojson.Point
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The car-facing home screen (Phase 1 of the dashboard, see NEXT_SESSION.md):
 * what mirror mode actually shows once streaming starts, instead of this
 * app's own plain setup screen. Mirror mode captures whatever is in the
 * foreground on the phone - if that's a purpose-built dashboard instead of
 * MainActivity's settings list, that is what the car sees, with zero wire
 * protocol or capture-pipeline changes.
 *
 * Now Playing, Messages and Phone are all wired to real data (session 6).
 * Navigate defaults to the built-in Mapbox map but respects
 * NavAppPreference - a user can pick a real installed nav app (Google
 * Maps, Waze, whatever's actually there) instead, per real user feedback
 * that not everyone wants in-app rendering.
 */
class CarDashboardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONNECTION_URL = "connection_url"
        const val EXTRA_HOTSPOT_IP = "hotspot_ip"
        const val EXTRA_HOTSPOT_IFACE = "hotspot_iface"
        const val EXTRA_PROBE_URL = "probe_url"
    }

    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val clockHandler = Handler(Looper.getMainLooper())
    private lateinit var clockText: TextView
    private lateinit var heroPager: ViewPager2
    private lateinit var heroPagerAdapter: HeroPagerAdapter
    private lateinit var heroPageDots: LinearLayout
    private lateinit var destinationSearchOverlay: DestinationSearchOverlayView
    private lateinit var messagesOverlay: MessagesOverlayView
    private lateinit var phoneOverlay: PhoneOverlayView
    private lateinit var navAppPicker: NavAppPickerView
    private lateinit var widgetPicker: WidgetPickerOverlayView

    internal lateinit var pinnedWidgetHost: PinnedWidgetHost
        private set

    // Set right before launching the picker/configure intents and read back
    // in their result handlers - more robust across OEM widget-picker
    // implementations than trusting the id echoed back in the result Intent,
    // which isn't consistently populated on every device.
    private var pendingWidgetId: Int? = null

    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { phoneOverlay.refresh() }

    private val widgetBindLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { handleWidgetBindResult() }

    private val widgetConfigureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> handleWidgetConfigureResult(result) }

    private val clockTick = object : Runnable {
        override fun run() {
            clockText.text = clockFormat.format(System.currentTimeMillis())
            clockHandler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car_dashboard)
        goEdgeToEdgeImmersive()

        clockText = findViewById(R.id.clockText)
        heroPageDots = findViewById(R.id.heroPageDots)
        destinationSearchOverlay = findViewById(R.id.destinationSearchOverlay)
        destinationSearchOverlay.onDismiss = { destinationSearchOverlay.close() }
        messagesOverlay = findViewById(R.id.messagesOverlay)
        messagesOverlay.onDismiss = { messagesOverlay.close() }
        phoneOverlay = findViewById(R.id.phoneOverlay)
        phoneOverlay.onDismiss = { phoneOverlay.close() }
        phoneOverlay.onRequestPermissions = { phonePermissionLauncher.launch(PhoneAccess.PERMISSIONS) }
        navAppPicker = findViewById(R.id.navAppPicker)
        navAppPicker.onDismiss = { navAppPicker.close() }

        pinnedWidgetHost = PinnedWidgetHost(this)
        widgetPicker = findViewById(R.id.widgetPicker)
        widgetPicker.onDismiss = { widgetPicker.close() }
        widgetPicker.onWidgetChosen = { info -> handleWidgetChosen(info) }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        destinationSearchOverlay.visibility == View.VISIBLE -> destinationSearchOverlay.close()
                        messagesOverlay.visibility == View.VISIBLE -> messagesOverlay.close()
                        phoneOverlay.visibility == View.VISIBLE -> phoneOverlay.close()
                        navAppPicker.visibility == View.VISIBLE -> navAppPicker.close()
                        widgetPicker.visibility == View.VISIBLE -> widgetPicker.close()
                        findViewById<View>(R.id.probeQrOverlay).visibility == View.VISIBLE ->
                            findViewById<View>(R.id.probeQrOverlay).visibility = View.GONE
                        else -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            },
        )

        heroPagerAdapter = HeroPagerAdapter(this).apply {
            setWidgetIds(pinnedWidgetHost.pinnedWidgetIds())
        }
        heroPager = findViewById<ViewPager2>(R.id.heroPager).apply {
            adapter = heroPagerAdapter
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    refreshPageDots(position)
                }
            })
        }
        refreshPageDots(heroPager.currentItem)

        intent.getStringExtra(EXTRA_CONNECTION_URL)?.let { url ->
            val hotspotIp = intent.getStringExtra(EXTRA_HOTSPOT_IP)
            val hotspotIface = intent.getStringExtra(EXTRA_HOTSPOT_IFACE)
            val probeUrl = intent.getStringExtra(EXTRA_PROBE_URL)
            findViewById<TextView>(R.id.connectionUrlText).apply {
                // Compact chip instead of the raw address dump: the header
                // stays one short line no matter how much connection detail
                // exists (founder ask, session 9 - the two address lines were
                // creeping toward the tiles). Tap opens the overlay with the
                // /go URL, hotspot ip (iface) and the scan-to-probe QR.
                text = "connect info"
                visibility = View.VISIBLE
                setOnClickListener { showConnectionInfo(url, hotspotIp, hotspotIface, probeUrl) }
            }
        }

        setUpTile(R.id.tileNavigation, R.drawable.ic_navigation, "Navigate") {
            // Swipes the hero card to the embedded map page (heroPager,
            // HeroPagerAdapter) instead of launching an external app - the
            // "slide" the founder asked for, see NEXT_SESSION.md.
            heroPager.setCurrentItem(1, true)
        }
        setUpTile(R.id.tilePhone, R.drawable.ic_phone, "Phone") {
            phoneOverlay.open()
        }
        setUpTile(R.id.tileMessages, R.drawable.ic_message, "Messages") {
            messagesOverlay.open()
        }
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockTick)
    }

    override fun onPause() {
        clockHandler.removeCallbacks(clockTick)
        super.onPause()
    }

    // AppWidgetHost's own required lifecycle pairing (its docs: start/stop
    // in onStart/onStop, not onResume/onPause) - this is what makes a pinned
    // widget actually receive RemoteViews updates while visible.
    override fun onStart() {
        super.onStart()
        pinnedWidgetHost.appWidgetHost.startListening()
    }

    override fun onStop() {
        pinnedWidgetHost.appWidgetHost.stopListening()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goEdgeToEdgeImmersive()
    }

    /** Status/nav bars add nothing on a car screen; reclaim the space. */
    private fun goEdgeToEdgeImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    /** Called by NavigateMapFragment when its "Where to?" pill is tapped. */
    fun openDestinationSearch(origin: Point, onChosen: (Point, String) -> Unit) {
        destinationSearchOverlay.onDestinationChosen = { point, label ->
            destinationSearchOverlay.close()
            onChosen(point, label)
        }
        destinationSearchOverlay.open(origin)
    }

    /** Called by NavigateMapFragment's settings icon. */
    fun openNavAppPicker() {
        navAppPicker.open()
    }

    /** Called by PinWidgetSlideFragment's placeholder slide. */
    internal fun startWidgetPickFlow() {
        widgetPicker.open()
    }

    /**
     * Our own picker (WidgetPickerOverlayView, see its doc for why the
     * system ACTION_APPWIDGET_PICK Activity was dropped) only chooses a
     * provider - binding still needs real consent.
     * [AppWidgetManager.bindAppWidgetIdIfAllowed] is tried first (silently
     * succeeds for providers this app is already trusted for); if it
     * returns false, [AppWidgetManager.ACTION_APPWIDGET_BIND] shows the
     * one-time consent dialog, the same mechanism every third-party
     * launcher uses.
     */
    private fun handleWidgetChosen(info: android.appwidget.AppWidgetProviderInfo) {
        widgetPicker.close()
        val widgetId = pinnedWidgetHost.allocateWidgetId()
        if (pinnedWidgetHost.appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, info.provider)) {
            proceedPastBind(widgetId, info)
            return
        }
        pendingWidgetId = widgetId
        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        }
        try {
            widgetBindLauncher.launch(bindIntent)
        } catch (e: ActivityNotFoundException) {
            pendingWidgetId = null
            pinnedWidgetHost.appWidgetHost.deleteAppWidgetId(widgetId)
            android.util.Log.w("CarDashboardActivity", "no widget bind consent activity available on this device", e)
        }
    }

    /**
     * NOT gated on [result]'s resultCode - verified live on the emulator
     * that `AllowBindAppWidgetActivity` returns RESULT_CANCELED even when
     * the user taps "Create" and the bind genuinely succeeds (confirmed via
     * [AppWidgetManager.getAppWidgetInfo] becoming non-null right after,
     * and the widget rendering pipeline proceeding normally from there).
     * Trusting resultCode here silently deleted every successfully-bound
     * widget; checking the real post-bind state instead is what every
     * production launcher actually relies on.
     */
    private fun handleWidgetBindResult() {
        val widgetId = pendingWidgetId
        pendingWidgetId = null
        if (widgetId == null) return
        val info = pinnedWidgetHost.appWidgetManager.getAppWidgetInfo(widgetId)
        if (info == null) {
            pinnedWidgetHost.appWidgetHost.deleteAppWidgetId(widgetId)
            return
        }
        proceedPastBind(widgetId, info)
    }

    /**
     * Some providers need their own configure Activity to run before the
     * widget has real content (e.g. picking a location for a weather
     * widget) - only finalize once that's done, or immediately if the
     * provider doesn't declare one.
     */
    private fun proceedPastBind(widgetId: Int, info: android.appwidget.AppWidgetProviderInfo) {
        val configureComponent = info.configure
        if (configureComponent != null) {
            pendingWidgetId = widgetId
            val configureIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configureComponent
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            try {
                widgetConfigureLauncher.launch(configureIntent)
            } catch (e: ActivityNotFoundException) {
                pendingWidgetId = null
                finalizePinnedWidget(widgetId)
            }
        } else {
            finalizePinnedWidget(widgetId)
        }
    }

    private fun handleWidgetConfigureResult(result: ActivityResult) {
        val widgetId = pendingWidgetId
        pendingWidgetId = null
        if (widgetId == null) return
        if (result.resultCode == Activity.RESULT_OK) {
            finalizePinnedWidget(widgetId)
        } else {
            pinnedWidgetHost.appWidgetHost.deleteAppWidgetId(widgetId)
        }
    }

    private fun finalizePinnedWidget(widgetId: Int) {
        pinnedWidgetHost.addPinned(widgetId)
        refreshWidgetSlides()
        // Land on the freshly pinned widget's slide so the result of the
        // whole pick/bind/configure flow is immediately visible.
        heroPagerAdapter.positionOfWidget(widgetId)
            .takeIf { it >= 0 }
            ?.let { heroPager.setCurrentItem(it, true) }
    }

    /** Called by WidgetSlideFragment's unpin button. */
    internal fun unpinWidget(widgetId: Int) {
        pinnedWidgetHost.removePinned(widgetId)
        refreshWidgetSlides()
    }

    private fun refreshWidgetSlides() {
        heroPagerAdapter.setWidgetIds(pinnedWidgetHost.pinnedWidgetIds())
        refreshPageDots(heroPager.currentItem)
    }

    /**
     * Rebuilds the dot strip - page count varies with pinned widgets. Each
     * dot is tappable (with a touch target much larger than the 6dp visual):
     * the Navigate page's embedded MapView consumes horizontal drags for map
     * panning (verified live), so swiping is not a reliable way to get PAST
     * that page - tapping a dot is the guaranteed path to the widget slides.
     */
    private fun refreshPageDots(selected: Int) {
        // Posted, not run inline: ViewPager2 fires onPageSelected during its
        // own initial layout pass, where the freshly-added dots' requestLayout
        // is silently dropped and they stay unmeasured at 0x0 (found live via
        // `dumpsys activity top` showing the children dirty at 0,36-0,36).
        heroPageDots.post {
            heroPageDots.removeAllViews()
            val density = resources.displayMetrics.density
            val dotSize = (6 * density).toInt()
            val touchSize = (28 * density).toInt()
            for (i in 0 until heroPagerAdapter.itemCount) {
                val dot = View(this).apply {
                    layoutParams = FrameLayout.LayoutParams(dotSize, dotSize, android.view.Gravity.CENTER)
                    setBackgroundResource(
                        if (i == selected) R.drawable.dash_dot_active else R.drawable.dash_dot_inactive,
                    )
                }
                val touchTarget = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(touchSize, touchSize)
                    addView(dot)
                    setOnClickListener { heroPager.setCurrentItem(i, true) }
                }
                heroPageDots.addView(touchTarget)
            }
        }
    }

    /**
     * Full-screen connection overlay behind the "connect info" chip: the /go
     * URL to type in the car, the hotspot AP address + interface name (the
     * iface makes a wrong-interface pick visible from a car-screen photo
     * alone - swlan0/ap0 = real AP, wlan0 = client radio, rmnet* = cellular,
     * i.e. hotspot probably off), and the scan-to-probe QR (?ip=&port= +
     * autorun - scan with any second device on the hotspot and the whole
     * connectivity probe runs with zero typing).
     */
    private fun showConnectionInfo(url: String, hotspotIp: String?, hotspotIface: String?, probeUrl: String?) {
        val overlay = findViewById<FrameLayout>(R.id.probeQrOverlay)
        val image = findViewById<ImageView>(R.id.probeQrImage)
        if (probeUrl != null && image.drawable == null) {
            runCatching { image.setImageBitmap(qrBitmap(probeUrl, 720)) }
                .onFailure { android.util.Log.w("CarDashboardActivity", "QR render failed", it) }
        }
        image.visibility = if (image.drawable != null) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.probeQrCaption).text = buildString {
            append(url)
            if (hotspotIp != null) {
                append("\nhotspot ").append(hotspotIp)
                if (hotspotIface != null) append(" (").append(hotspotIface).append(')')
            }
            // Live reachability counters (zero-adb): reopen this card after a
            // car-side attempt - any nonzero proves packets DO arrive here.
            app.vehplayer.android.capture.CaptureService.instance?.let { svc ->
                val (httpHits, stunHits) = svc.probeHitCounts
                append("\n\nreached from network: HTTP ").append(httpHits)
                    .append("x / STUN ").append(stunHits)
                    .append("x (reopen after a car attempt; nonzero = packets arrive)")
                svc.lastDiagReport?.let { append("\n\nlast in-car diag: ").append(summarizeDiag(it)) }
            }
            if (probeUrl != null) {
                append("\n\nIn the car type this URL (or scan it with a phone/laptop on the hotspot): ")
                append(probeUrl)
                append("\nIt runs the full test itself and reports the result back here.")
            }
        }
        overlay.visibility = View.VISIBLE
        overlay.setOnClickListener { overlay.visibility = View.GONE }
    }

    /**
     * One-line summary of the JSON report the /diag page POSTs back, so the
     * founder reads the in-car result on the phone without a photo. Best
     * effort: any parse trouble just shows the raw head.
     */
    private fun summarizeDiag(json: String): String = try {
        val o = org.json.JSONObject(json)
        val results = o.optJSONObject("results")
        val parts = listOf("self", "ping", "ws", "stun", "rtc").mapNotNull { k ->
            results?.optJSONObject(k)?.optString("status")?.let { "$k=$it" }
        }
        val metrics = o.optJSONObject("metrics")
        val chromium = metrics?.optString("chromium")?.takeIf { it.isNotEmpty() }
        (parts.joinToString(" ") + (chromium?.let { "  [Chromium $it]" } ?: "")).ifEmpty { "(empty report)" }
    } catch (e: Exception) {
        json.take(80)
    }

    private fun qrBitmap(content: String, sizePx: Int): android.graphics.Bitmap {
        val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            content,
            com.google.zxing.BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 1),
        )
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                pixels[y * sizePx + x] =
                    if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        return android.graphics.Bitmap.createBitmap(pixels, sizePx, sizePx, android.graphics.Bitmap.Config.RGB_565)
    }

    private fun setUpTile(includeId: Int, iconRes: Int, label: String, onClick: () -> Unit) {
        val tile = findViewById<View>(includeId)
        tile.findViewById<ImageView>(R.id.tileIconBadge).setImageResource(iconRes)
        tile.findViewById<TextView>(R.id.tileLabel).text = label
        tile.setOnClickListener { onClick() }
    }
}
