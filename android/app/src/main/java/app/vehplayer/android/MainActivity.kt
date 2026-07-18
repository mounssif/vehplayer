package app.vehplayer.android

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.media.projection.MediaProjectionManager
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
import app.vehplayer.android.update.ApkInstaller
import app.vehplayer.android.update.UpdateChecker
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    private lateinit var rootLayout: LinearLayout

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            CaptureService.start(this, result.resultCode, data, carWidth, carHeight, lowLatencyAudio = false)
            setStatus("Streaming started! Preparing the local address...")
            awaitHttpServerAndShowUrl()
        } else {
            setStatus("Screen recording permission was declined — streaming can't start without it. Tap Start to try again.")
        }
    }

    /**
     * CaptureService.start() only fires the service Intent; the local HTTP
     * server (owned there now, not here - see CaptureService.httpServerPort's
     * doc comment for why) resolves its port asynchronously as part of that
     * service's own startup. Poll briefly rather than guessing a port.
     */
    private fun awaitHttpServerAndShowUrl(attempt: Int = 0) {
        val port = CaptureService.instance?.httpServerPort
        if (port != null) {
            val host = localIpAddress()
            if (host != null) {
                setStatus("Streaming started! Open this address in your car's browser:\n\nhttp://$host:$port/go")
            } else {
                setStatus("Streaming started, but couldn't detect your hotspot's address. Turn on your phone's hotspot and tap Start again.")
            }
            return
        }
        if (attempt >= 20) { // ~4s at 200ms apart
            setStatus("Streaming started, but the local server didn't come up yet. Check the vehplayer notification is still there, then reopen this screen.")
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            delay(200)
            awaitHttpServerAndShowUrl(attempt + 1)
        }
    }

    private val vpnConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // VpnReachabilityService (tiers b/c) is a real, tracked TODO
            // (net/VpnReachabilityService.kt), intentionally not surfaced to
            // the user, that's an internal implementation detail.
            setStatus("VPN permission granted, but this connection path isn't finished yet. Use a direct connection for now.")
        } else {
            setStatus("VPN permission was declined. Without it, this phone has no way to reach your car — streaming can't start.")
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

        val versionFooter = TextView(this).apply {
            text = "vehplayer ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
            textSize = 11f
            alpha = 0.5f
            setPadding(0, 32, 0, 0)
        }
        root.addView(versionFooter)

        setContentView(root)
        setStatus(buildInitialStatus())

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
            text = "Update available - tap to install"
            setOnClickListener { startUpdate(update) }
        }
        rootLayout.addView(btn, 0)
    }

    /**
     * Downloads and hands the APK straight to the system installer
     * (ApkInstaller.kt) instead of opening the browser. Same one-time
     * permission-grant pattern as the accessibility flow above: if
     * "install unknown apps" isn't granted for this app yet, send the user
     * to the settings screen for it and ask them to tap Update again,
     * rather than silently falling back to a worse flow.
     */
    private fun startUpdate(update: UpdateChecker.UpdateInfo) {
        if (!ApkInstaller.hasInstallPermission(this)) {
            setStatus("One more permission is needed to install updates. Opening settings now — after allowing it, come back and tap Update again.")
            startActivity(ApkInstaller.installPermissionSettingsIntent(this))
            return
        }
        setStatus("Downloading update...")
        ApkInstaller.downloadAndInstall(this, update.downloadUrl) { message -> setStatus(message) }
    }

    private fun onStartClicked() {
        if (!isAccessibilityServiceEnabled()) {
            setStatus("Enable input control first (step 1), then tap Start again.")
            return
        }

        when (val decision = ReachabilityLadder.decide(this)) {
            is ReachabilityDecision.Tier1NoVpnNeeded -> {
                android.util.Log.i("MainActivity", "reachability tier (a) OK at ${decision.address}")
                setStatus("Connection looks good. Requesting screen access...")
                requestProjection()
            }
            is ReachabilityDecision.NeedsVpnConsent -> {
                setStatus("Direct connection isn't available. Trying an alternate path, this needs one extra permission...")
                vpnConsentLauncher.launch(decision.prepareIntent)
            }
            is ReachabilityDecision.VpnAlreadyPrepared -> {
                setStatus("Requesting screen access...")
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
