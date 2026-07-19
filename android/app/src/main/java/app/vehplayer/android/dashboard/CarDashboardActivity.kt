package app.vehplayer.android.dashboard

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import app.vehplayer.android.R
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
            // ACTION_DIAL (not ACTION_CALL): opens the dialer pre-filled,
            // needs no CALL_PHONE permission, the user still taps call.
            startActivity(Intent(Intent.ACTION_DIAL))
        }
        setUpTile(R.id.tileMessages, R.drawable.ic_message, "Messages") {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            )
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

    private fun setUpTile(includeId: Int, iconRes: Int, label: String, onClick: () -> Unit) {
        val tile = findViewById<View>(includeId)
        tile.findViewById<ImageView>(R.id.tileIconBadge).setImageResource(iconRes)
        tile.findViewById<TextView>(R.id.tileLabel).text = label
        tile.setOnClickListener { onClick() }
    }
}
