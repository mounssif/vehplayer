package app.vehplayer.android

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import app.vehplayer.android.capture.CaptureService
import app.vehplayer.android.input.VehplayerAccessibilityService
import app.vehplayer.android.net.ReachabilityDecision
import app.vehplayer.android.net.ReachabilityLadder
import app.vehplayer.android.server.HttpAssetServer
import app.vehplayer.android.update.UpdateChecker
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The two-minute setup flow (Foundation §3): permission walkthrough, then
 * one tap. This is the real Gate-2/3 flow, replacing the S1-probe dev shell
 * the repo shipped with earlier this session, see NEXT_SESSION.md for what
 * changed and what's still stubbed.
 *
 * Deliberately not using Jetpack Compose: this is a handful of sequential
 * steps behind plain buttons, Compose would be more ceremony than the
 * problem needs at this stage. Revisit once real onboarding UI design lands
 * (Gate 4/5), not a Gate-2 concern.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusView: TextView

    // Boot-time placeholder only: no car has connected yet when capture
    // starts, so there's no real viewport to size the encoder to. Corrected
    // automatically once the first `hello` arrives (control.ts's
    // buildHello), see CaptureService.resize().
    private var carWidth = 1280
    private var carHeight = 800

    private var httpServer: HttpAssetServer? = null
    private lateinit var rootLayout: LinearLayout

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            CaptureService.start(this, result.resultCode, data, carWidth, carHeight, lowLatencyAudio = false)
            val host = localIpAddress() ?: "<phone-hotspot-ip, none found, check the hotspot is on>"
            setStatus(buildInitialStatus() + "\n\nstreaming started.\nOpen this in the car browser:\nhttp://$host:8080/go")
        } else {
            setStatus("screen capture permission denied, cannot start")
        }
    }

    private val vpnConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setStatus("VPN consent granted (tier b/c). VpnReachabilityService still needs its TODO implemented, see net/VpnReachabilityService.kt")
        } else {
            setStatus("VPN consent denied, no reachability tier available, cannot proceed (Foundation §10 kill criterion)")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        rootLayout = root
        statusView = TextView(this).apply { textSize = 15f }
        root.addView(statusView)

        val enableAccessibilityBtn = Button(this).apply {
            text = "1. Enable input control (Accessibility)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(enableAccessibilityBtn)

        val startBtn = Button(this).apply {
            text = "2. Start streaming"
            setOnClickListener { onStartClicked() }
        }
        root.addView(startBtn)

        setContentView(root)
        setStatus(buildInitialStatus())

        httpServer = HttpAssetServer(applicationContext, wsPort = 8787).also {
            try {
                it.start()
            } catch (e: java.io.IOException) {
                setStatus("HttpAssetServer failed to start: ${e.message}\n(expected until webclient/dist/ is bundled into assets/webclient/, see server/HttpAssetServer.kt TODO)")
            }
        }

        checkForUpdate()
    }

    /**
     * Best-effort, silent unless an update is actually found: no banner, no
     * dialog, on failure. Runs once per launch, cheap enough not to need
     * debouncing/caching for how infrequently this app is opened.
     */
    private fun checkForUpdate() {
        CoroutineScope(Dispatchers.IO).launch {
            val update = UpdateChecker.checkBlocking() ?: return@launch
            if (update.versionCode <= BuildConfig.VERSION_CODE) return@launch
            withContext(Dispatchers.Main) { showUpdateBanner(update) }
        }
    }

    private fun showUpdateBanner(update: UpdateChecker.UpdateInfo) {
        val btn = Button(this).apply {
            text = "Update available: ${update.versionName} (tap to download)"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)))
            }
        }
        rootLayout.addView(btn, 0)
    }

    override fun onDestroy() {
        httpServer?.stop()
        httpServer = null
        super.onDestroy()
    }

    private fun onStartClicked() {
        if (!isAccessibilityServiceEnabled()) {
            setStatus("enable input control first (step 1), then tap Start again")
            return
        }

        when (val decision = ReachabilityLadder.decide(this)) {
            is ReachabilityDecision.Tier1NoVpnNeeded -> {
                setStatus("reachability tier (a) IPv6 OK at ${decision.address}, requesting screen capture...")
                requestProjection()
            }
            is ReachabilityDecision.NeedsVpnConsent -> {
                setStatus("tier (a) unavailable, requesting VPN consent for tier (b)/(c)...")
                vpnConsentLauncher.launch(decision.prepareIntent)
            }
            is ReachabilityDecision.VpnAlreadyPrepared -> {
                setStatus("VPN already prepared, requesting screen capture...")
                requestProjection()
            }
        }
    }

    private fun requestProjection() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    /**
     * Two independent signals, both checked: the system settings list (works
     * even if our service process was killed and hasn't run onServiceConnected
     * yet this session) and the live singleton (confirms it's actually bound
     * right now, not just enabled-but-not-yet-connected). TODO(claude-code):
     * confirm this dual check isn't overkill on a real device, may be able to
     * simplify to just the singleton once real timing is observed.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        if (VehplayerAccessibilityService.instance != null) return true
        val expected = ComponentName(this, VehplayerAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    /**
     * No API exposes "the hotspot's own IP" directly; the standard trick is
     * the first non-loopback IPv4 address on any interface, since a phone
     * running a hotspot binds one to the AP interface (commonly 192.168.43.1
     * on stock hotspot implementations, but OEM-dependent, hence read rather
     * than hardcoded). Works the same for tier (a) IPv6-reachable wifi.
     */
    private fun localIpAddress(): String? = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    } catch (e: SocketException) {
        null
    }

    private fun buildInitialStatus(): String = "vehplayer setup\n" +
        "1. Enable input control (one-time)\n" +
        "2. Tap Start, grant screen capture\n" +
        "3. Open the shown URL in the car browser"

    private fun setStatus(s: String) {
        statusView.text = s
    }
}
