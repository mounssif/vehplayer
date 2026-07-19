package app.vehplayer.android.dashboard

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
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
 * Now-playing is still a static "Nothing playing" placeholder here
 * (Phase 2 wires MediaSessionManager + Notification Listener access, a
 * sensitive permission worth its own confirmation step, not bundled into
 * this pass). Tiles fire plain launcher intents; a nav-app picker (Phase 3)
 * replaces the generic `geo:` intent with a chosen app later.
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

    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { phoneOverlay.refresh() }

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

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        destinationSearchOverlay.visibility == View.VISIBLE -> destinationSearchOverlay.close()
                        messagesOverlay.visibility == View.VISIBLE -> messagesOverlay.close()
                        phoneOverlay.visibility == View.VISIBLE -> phoneOverlay.close()
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

    private fun setUpTile(includeId: Int, iconRes: Int, label: String, onClick: () -> Unit) {
        val tile = findViewById<View>(includeId)
        tile.findViewById<ImageView>(R.id.tileIconBadge).setImageResource(iconRes)
        tile.findViewById<TextView>(R.id.tileLabel).text = label
        tile.setOnClickListener { onClick() }
    }
}
