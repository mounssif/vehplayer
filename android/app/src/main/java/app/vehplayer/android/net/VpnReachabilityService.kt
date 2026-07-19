package app.vehplayer.android.net

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * Reachability tier (c): TeslAA-style non-RFC1918 address assignment
 * (ARCHITECTURE.md §7, Foundation §6b item 1).
 *
 * Address range validated this session against a real shipping competitor
 * (TeslaMirror, a currently-selling app whose own documentation describes
 * binding its embedded server to `100.99.9.9`) plus independent Tesla
 * Motors Club forum reports of hobbyists solving the identical
 * "Tesla's browser refuses RFC1918 addresses" problem the same way - not a
 * guess at an untested range. `100.64.0.0/10` is RFC 6598 Shared Address
 * Space (reserved for carrier-grade NAT), deliberately NOT one of the
 * RFC1918 ranges (10/8, 172.16/12, 192.168/16) that block applies to.
 *
 * No packet-forwarding loop reading [vpnInterface]'s file descriptor: this
 * is a LAN-side address-assignment trick, not a traffic-tunneling VPN.
 * Standard kernel IP routing delivers a packet arriving on any physical
 * interface (the hotspot AP link, in this case) to the local socket stack
 * whenever its destination address is configured on ANY local interface -
 * including a VpnService-assigned tun address - regardless of which wire
 * it physically arrived on. `HttpAssetServer`/`LocalMediaServer` already
 * bind wildcard (`NanoHTTPD(port)`, no explicit host), so once Android has
 * assigned [VIRTUAL_ADDRESS] here, a hotspot-connected car's connection to
 * it reaches that already-listening socket with no extra plumbing.
 *
 * **Not yet verified on a real Tesla** - the address range is evidence-
 * backed (see above), but this specific implementation has only run
 * against the emulator's own loopback-like networking, which cannot
 * reproduce a real phone-hotspot-to-car link. Confirm end-to-end on real
 * hardware before trusting this tier in the field.
 */
class VpnReachabilityService : VpnService() {

    companion object {
        const val VIRTUAL_ADDRESS = "100.99.9.1"

        /** Set once establish() succeeds; null if not running or it failed. */
        @Volatile
        var activeAddress: String? = null
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val established = runCatching {
            Builder()
                .addAddress(VIRTUAL_ADDRESS, 32)
                .addRoute(VIRTUAL_ADDRESS, 32)
                .setSession("vehplayer")
                .setMtu(1500)
                .establish()
        }.getOrNull()

        if (established == null) {
            android.util.Log.w("VpnReachabilityService", "establish() failed or was null")
            activeAddress = null
            return START_NOT_STICKY
        }
        vpnInterface = established
        activeAddress = VIRTUAL_ADDRESS
        return START_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        activeAddress = null
        super.onDestroy()
    }
}
