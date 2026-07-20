package app.vehplayer.android.net

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Firewall-bypass diagnostic (docs/NEXT_SESSION.md session 9). The leading
 * suspect for "the car can reach the internet through the hotspot but cannot
 * reach the phone's own hotspot IP" is the carrier/OEM tethering firewall,
 * which DROPs inbound to the AP host on the Settings-tethering code path.
 *
 * `WifiManager.startLocalOnlyHotspot` brings up a normal, joinable WPA2 AP on
 * a DIFFERENT code path (the app-to-app "local only" one), which frequently
 * does NOT carry those DROP rules. The Tesla joins it exactly like any
 * hotspot (normal SSID + password), so unlike Bluetooth PAN / Wi-Fi Direct /
 * DNS-tunnel ideas this one is actually reachable by the locked in-car
 * browser. It has no internet, but the mirror data plane is fully local
 * (HttpAssetServer serves the bundle from assets), so that is fine.
 *
 * Caveat kept honest: the LOH address is still RFC1918 (typically
 * 192.168.x.1). If the block turns out to be Tesla's own RFC1918 filter
 * rather than the phone firewall, this will NOT help and IPv6 (tier a) is the
 * only path. Which of the two is settled by the /diag reachability test.
 *
 * Requires CHANGE_WIFI_STATE + (FINE_LOCATION pre-33 / NEARBY_WIFI_DEVICES
 * 33+) and location services ON. Only one LocalOnlyHotspot can exist at a
 * time and it usually cannot run alongside the Settings hotspot.
 */
class LocalOnlyHotspotController(private val appContext: Context) {

    data class Info(val ssid: String?, val passphrase: String?, val band: String?)

    private val wifiManager =
        appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Volatile
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    @Volatile
    var lastInfo: Info? = null
        private set

    val isRunning: Boolean get() = reservation != null

    /**
     * Starts the local-only hotspot. [onResult] is called on the main thread
     * with either the credentials to type into the car, or a human-readable
     * error. Safe to call when already running (re-delivers current info).
     */
    fun start(onResult: (Info?, String?) -> Unit) {
        reservation?.let { onResult(lastInfo, null); return }
        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = res
                    lastInfo = extractInfo(res)
                    Log.i(TAG, "local-only hotspot started: ${lastInfo?.ssid}")
                    onResult(lastInfo, null)
                }

                override fun onFailed(reason: Int) {
                    reservation = null
                    val msg = when (reason) {
                        ERROR_NO_CHANNEL -> "no free Wi-Fi channel (turn Wi-Fi off/on, or disable the normal hotspot first)"
                        ERROR_GENERIC -> "generic failure (some OEMs block LocalOnlyHotspot while the SIM hotspot is on)"
                        ERROR_INCOMPATIBLE_MODE -> "incompatible mode (Wi-Fi is in a state that can't host it right now)"
                        ERROR_TETHERING_DISALLOWED -> "tethering disallowed by policy/carrier on this device"
                        else -> "unknown error $reason"
                    }
                    Log.w(TAG, "local-only hotspot failed: $msg")
                    onResult(null, msg)
                }

                override fun onStopped() {
                    Log.i(TAG, "local-only hotspot stopped")
                    reservation = null
                }
            }, null)
        } catch (e: SecurityException) {
            onResult(null, "permission/location denied: ${e.message} (grant location + turn location services ON)")
        } catch (e: IllegalStateException) {
            onResult(null, "can't start now: ${e.message}")
        }
    }

    fun stop() {
        reservation?.close()
        reservation = null
    }

    @Suppress("DEPRECATION")
    private fun extractInfo(res: WifiManager.LocalOnlyHotspotReservation): Info =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val c = res.softApConfiguration
            Info(
                ssid = c.ssid,
                passphrase = c.passphrase,
                band = null,
            )
        } else {
            val c = res.wifiConfiguration
            Info(ssid = c?.SSID, passphrase = c?.preSharedKey, band = null)
        }

    companion object {
        private const val TAG = "LocalOnlyHotspot"

        /**
         * Best-effort address the car should target: the RFC1918 IPv4 on an
         * up, non-loopback, non-point-to-point interface (LocalOnlyHotspot
         * typically lands on 192.168.x.1). Enumerated live because no API
         * exposes the LOH interface address directly.
         */
        fun apAddress(): String? = try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback && !it.isPointToPoint }
                .flatMap { iface -> Collections.list(iface.inetAddresses).filterIsInstance<Inet4Address>() }
                .firstOrNull { a ->
                    val b = a.address
                    val b0 = b[0].toInt() and 0xFF
                    val b1 = b[1].toInt() and 0xFF
                    b0 == 192 && b1 == 168
                }?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
