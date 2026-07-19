package app.vehplayer.android

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import app.vehplayer.android.capture.CaptureService
import app.vehplayer.android.dashboard.CarDashboardActivity
import app.vehplayer.android.input.VehplayerAccessibilityService
import app.vehplayer.android.net.ReachabilityDecision
import app.vehplayer.android.net.ReachabilityLadder
import app.vehplayer.android.net.VpnReachabilityService
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

    private companion object {
        const val UPDATE_POLL_INTERVAL_MS = 5 * 60_000L
    }

    private lateinit var statusView: TextView

    // Boot-time placeholder only: no car has connected yet when capture
    // starts, so there's no real viewport to size the encoder to. Corrected
    // automatically once the first `hello` arrives (control.ts's
    // buildHello), see CaptureService.resize().
    private var carWidth = 1280
    private var carHeight = 800

    private lateinit var updateBannerContainer: LinearLayout
    private var updatePollJob: kotlinx.coroutines.Job? = null
    private var shownUpdateVersionCode: Int? = null

    // Set by onStartClicked() when ReachabilityLadder finds a real global
    // IPv6 address (tier (a)) - awaitHttpServerAndShowUrl() must use THIS,
    // not always fall back to localIpAddress()'s IPv4 guess: a real hotspot
    // IPv4 (192.168.x.x etc.) is exactly the RFC1918 range Tesla's in-car
    // browser is confirmed to refuse to connect to at all (NEXT_SESSION.md),
    // so silently ignoring a known-good tier (a) address here would defeat
    // the whole point of having the ladder pick tier (a) in the first place.
    private var reachableTier1Address: String? = null

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
     *
     * Once the URL is known, hands off to CarDashboardActivity - mirror mode
     * casts whatever's in the foreground, so that dashboard (not this
     * settings screen) is deliberately what the car ends up showing.
     */
    private fun awaitHttpServerAndShowUrl(attempt: Int = 0) {
        val port = CaptureService.instance?.httpServerPort
        if (port != null) {
            val host = reachableTier1Address ?: localIpAddress()
            val url = if (host != null) "http://${formatHostForUrl(host)}:$port/go" else null
            if (url != null) {
                startActivity(
                    Intent(this, CarDashboardActivity::class.java)
                        .putExtra(CarDashboardActivity.EXTRA_CONNECTION_URL, url),
                )
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
            startVpnTierAndProceed()
        } else {
            setStatus("VPN permission was declined. Without it, this phone has no way to reach your car — streaming can't start.")
        }
    }

    /**
     * Starts VpnReachabilityService (tier (c), see that class) and waits
     * briefly for its address assignment to resolve - establish() happens
     * asynchronously inside the service's onStartCommand, same async-port
     * shape as CaptureService.httpServerPort below, not something this call
     * can read synchronously right after starting the service.
     */
    private fun startVpnTierAndProceed() {
        startService(Intent(this, VpnReachabilityService::class.java))
        awaitVpnAddressAndProceed()
    }

    private fun awaitVpnAddressAndProceed(attempt: Int = 0) {
        val address = VpnReachabilityService.activeAddress
        if (address != null) {
            reachableTier1Address = address
            setStatus("Connection looks good. Requesting screen access...")
            requestProjection()
            return
        }
        if (attempt >= 20) { // ~4s at 200ms apart
            setStatus("Couldn't set up the alternate connection path. Streaming can't start without a way for your car to reach this phone.")
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            delay(200)
            awaitVpnAddressAndProceed(attempt + 1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.statusText)
        updateBannerContainer = findViewById(R.id.updateBannerContainer)

        findViewById<View>(R.id.enableAccessibilityBtn).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<View>(R.id.startBtn).setOnClickListener { onStartClicked() }
        findViewById<TextView>(R.id.versionFooter).text =
            "vehplayer ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"

        setStatus(buildInitialStatus())
    }

    /**
     * Best-effort, silent unless an update is actually found: no banner, no
     * dialog, on failure. Polls while this screen is visible (not just once
     * per onCreate) - a real user kept the app open across a release and the
     * banner never appeared until a force-close, because the process (and
     * this Activity instance) had survived the whole time.
     */
    override fun onResume() {
        super.onResume()
        updatePollJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val update = UpdateChecker.checkBlocking()
                if (update != null && update.versionCode > BuildConfig.VERSION_CODE) {
                    withContext(Dispatchers.Main) { showUpdateBanner(update) }
                }
                delay(UPDATE_POLL_INTERVAL_MS)
            }
        }
    }

    override fun onPause() {
        updatePollJob?.cancel()
        updatePollJob = null
        super.onPause()
    }

    private fun showUpdateBanner(update: UpdateChecker.UpdateInfo) {
        // Poll loop can find the same (or a newer) release repeatedly; keep
        // exactly one banner, replacing it only when the version changes.
        if (update.versionCode == shownUpdateVersionCode) return
        shownUpdateVersionCode = update.versionCode
        updateBannerContainer.removeAllViews()
        val banner = LayoutInflater.from(this).inflate(R.layout.item_update_banner, updateBannerContainer, false)
        banner.findViewById<View>(R.id.updateBannerButton).setOnClickListener { startUpdate(update) }
        updateBannerContainer.addView(banner)
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
                reachableTier1Address = decision.address
                setStatus("Connection looks good. Requesting screen access...")
                requestProjection()
            }
            is ReachabilityDecision.NeedsVpnConsent -> {
                reachableTier1Address = null
                setStatus("Direct connection isn't available. Trying an alternate path, this needs one extra permission...")
                vpnConsentLauncher.launch(decision.prepareIntent)
            }
            is ReachabilityDecision.VpnAlreadyPrepared -> {
                setStatus("Connection ready. Requesting screen access...")
                startVpnTierAndProceed()
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
     * No API exposes "the hotspot's own IP" directly. A cellular data
     * interface (rmnet*, ccmni*, etc.) can be up at the same time as the
     * hotspot AP interface and would otherwise win by enumeration order
     * alone - not what "the hotspot's own IP" means, and a real latent gap
     * (NEXT_SESSION.md) even though it wasn't the cause of either bug found
     * so far. Prefer an interface whose name suggests AP/hotspot mode AND
     * whose address is RFC1918 private range; fall back to any RFC1918
     * address; fall back to the first non-loopback address as a last resort.
     */
    private fun localIpAddress(): String? = try {
        val candidates = Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface ->
                Collections.list(iface.inetAddresses)
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress }
                    .map { iface to it }
            }
        candidates.firstOrNull { (iface, addr) -> isLikelyHotspotInterface(iface.name) && isPrivateRange(addr) }
            ?.second?.hostAddress
            ?: candidates.firstOrNull { (_, addr) -> isPrivateRange(addr) }?.second?.hostAddress
            ?: candidates.firstOrNull()?.second?.hostAddress
    } catch (e: SocketException) {
        null
    }

    private fun isLikelyHotspotInterface(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("ap") || n.startsWith("wlan") || n.startsWith("swlan") || n.startsWith("softap")
    }

    private fun isPrivateRange(addr: Inet4Address): Boolean {
        val b = addr.address
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        return b0 == 10 || (b0 == 172 && b1 in 16..31) || (b0 == 192 && b1 == 168)
    }

    /** IPv6 literals need brackets in a URL authority (`[2001:db8::1]:8080`); IPv4 doesn't. */
    private fun formatHostForUrl(host: String): String = if (host.contains(':')) "[$host]" else host

    private fun buildInitialStatus(): String = "vehplayer setup\n" +
        "1. Enable input control (one-time)\n" +
        "2. Tap Start, grant screen capture\n" +
        "3. Open the shown URL in the car browser"

    private fun setStatus(s: String) {
        statusView.text = s
    }
}
