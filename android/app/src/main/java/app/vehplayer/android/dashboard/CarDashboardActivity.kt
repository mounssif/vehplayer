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
    }

    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val clockHandler = Handler(Looper.getMainLooper())
    private lateinit var clockText: TextView
    private lateinit var heroPager: ViewPager2
    private lateinit var dotNowPlaying: View
    private lateinit var dotNavigate: View
    private lateinit var destinationSearchOverlay: DestinationSearchOverlayView
    private lateinit var messagesOverlay: MessagesOverlayView
    private lateinit var phoneOverlay: PhoneOverlayView
    private lateinit var navAppPicker: NavAppPickerView

    private lateinit var pinnedWidgetHost: PinnedWidgetHost
    private lateinit var widgetPlaceholder: View
    private lateinit var widgetHostContainer: FrameLayout
    private lateinit var widgetUnpinButton: View
    private var currentWidgetView: android.appwidget.AppWidgetHostView? = null

    // Set right before launching the picker/configure intents and read back
    // in their result handlers - more robust across OEM widget-picker
    // implementations than trusting the id echoed back in the result Intent,
    // which isn't consistently populated on every device.
    private var pendingWidgetId: Int? = null

    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { phoneOverlay.refresh() }

    private val widgetPickLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> handleWidgetPickResult(result) }

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
        dotNowPlaying = findViewById(R.id.dotNowPlaying)
        dotNavigate = findViewById(R.id.dotNavigate)
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
        widgetPlaceholder = findViewById(R.id.widgetPlaceholder)
        widgetHostContainer = findViewById(R.id.widgetHostContainer)
        widgetUnpinButton = findViewById(R.id.widgetUnpinButton)
        widgetPlaceholder.setOnClickListener { startWidgetPickFlow() }
        widgetUnpinButton.setOnClickListener { unpinWidget() }
        pinnedWidgetHost.pinnedWidgetId()?.let { renderPinnedWidget(it) }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        destinationSearchOverlay.visibility == View.VISIBLE -> destinationSearchOverlay.close()
                        messagesOverlay.visibility == View.VISIBLE -> messagesOverlay.close()
                        phoneOverlay.visibility == View.VISIBLE -> phoneOverlay.close()
                        navAppPicker.visibility == View.VISIBLE -> navAppPicker.close()
                        else -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            },
        )

        heroPager = findViewById<ViewPager2>(R.id.heroPager).apply {
            adapter = HeroPagerAdapter(this@CarDashboardActivity)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    dotNowPlaying.setBackgroundResource(
                        if (position == 0) R.drawable.dash_dot_active else R.drawable.dash_dot_inactive,
                    )
                    dotNavigate.setBackgroundResource(
                        if (position == 1) R.drawable.dash_dot_active else R.drawable.dash_dot_inactive,
                    )
                }
            })
        }

        intent.getStringExtra(EXTRA_CONNECTION_URL)?.let { url ->
            findViewById<TextView>(R.id.connectionUrlText).apply {
                text = url
                visibility = View.VISIBLE
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

    /**
     * ACTION_APPWIDGET_PICK just lets the user choose a provider - it does
     * NOT reliably grant BIND_APPWIDGET on its own (verified live on the
     * emulator: skipping the explicit bind step below rendered the
     * framework's own "Couldn't add widget." fallback content instead of
     * the real widget, even though the pick+configure round trip completed
     * normally). [AppWidgetManager.bindAppWidgetIdIfAllowed] is tried first
     * (silently succeeds for providers this app is already trusted for);
     * if it returns false, [AppWidgetManager.ACTION_APPWIDGET_BIND] shows
     * the real one-time consent dialog, the same mechanism every
     * third-party launcher uses.
     */
    private fun startWidgetPickFlow() {
        val widgetId = pinnedWidgetHost.allocateWidgetId()
        pendingWidgetId = widgetId
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        try {
            widgetPickLauncher.launch(pickIntent)
        } catch (e: ActivityNotFoundException) {
            pendingWidgetId = null
            pinnedWidgetHost.appWidgetHost.deleteAppWidgetId(widgetId)
            android.util.Log.w("CarDashboardActivity", "no widget picker available on this device", e)
        }
    }

    private fun handleWidgetPickResult(result: ActivityResult) {
        val widgetId = pendingWidgetId
        pendingWidgetId = null
        if (widgetId == null || result.resultCode != Activity.RESULT_OK) {
            widgetId?.let { pinnedWidgetHost.appWidgetHost.deleteAppWidgetId(it) }
            return
        }
        val info = pinnedWidgetHost.appWidgetManager.getAppWidgetInfo(widgetId)
        if (info == null) {
            pinnedWidgetHost.appWidgetHost.deleteAppWidgetId(widgetId)
            return
        }
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
        pinnedWidgetHost.persistPinned(widgetId)
        renderPinnedWidget(widgetId)
    }

    private fun renderPinnedWidget(widgetId: Int) {
        val hostView = pinnedWidgetHost.createHostView(widgetId) ?: return
        currentWidgetView?.let { widgetHostContainer.removeView(it) }
        widgetHostContainer.addView(
            hostView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        currentWidgetView = hostView
        widgetPlaceholder.visibility = View.GONE
        widgetHostContainer.visibility = View.VISIBLE
        widgetUnpinButton.visibility = View.VISIBLE
    }

    private fun unpinWidget() {
        currentWidgetView?.let { widgetHostContainer.removeView(it) }
        currentWidgetView = null
        pinnedWidgetHost.clearPinned()
        widgetHostContainer.visibility = View.GONE
        widgetUnpinButton.visibility = View.GONE
        widgetPlaceholder.visibility = View.VISIBLE
    }

    private fun setUpTile(includeId: Int, iconRes: Int, label: String, onClick: () -> Unit) {
        val tile = findViewById<View>(includeId)
        tile.findViewById<ImageView>(R.id.tileIconBadge).setImageResource(iconRes)
        tile.findViewById<TextView>(R.id.tileLabel).text = label
        tile.setOnClickListener { onClick() }
    }
}
