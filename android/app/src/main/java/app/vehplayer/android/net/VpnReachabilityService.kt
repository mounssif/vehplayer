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
 *
 * **MEASURED, session 7, real hardware (Galaxy S23 / Android 16 + real
 * Model 3): this tier DOES NOT WORK on modern Android.** The original
 * assumption ("standard kernel routing delivers any packet addressed to a
 * locally-configured address regardless of arrival interface") is disproven
 * by Android's BPF ingress-discard hardening: `dumpsys connectivity
 * trafficcontroller` shows a literal `sIngressDiscardMap` entry pinning
 * [VIRTUAL_ADDRESS] to tun0 as the only allowed ingress interface, so
 * TCP/UDP from a hotspot/USB-tethered peer (the car) is dropped in BPF
 * before ever reaching the TCP stack (confirmed: zero SYN-RECV during a
 * live connection attempt; ICMP passes because the BPF check covers only
 * TCP/UDP, which made ping a misleading success signal). This is
 * anti-spoofing hardening for VPN addresses, not a bug, and there is no
 * app-side workaround without root. On older Android versions that predate
 * this hardening the trick may still work, which is why this tier stays in
 * the ladder rather than being deleted - but tier (a) IPv6 is the only
 * viable path on current Android. The emulator "verification" of this tier
 * in session 6 was a false positive: a single virtual device can never
 * exercise the cross-interface ingress path that the BPF check guards.
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
                // Exclude this app itself from its own VPN. Without this, our
                // HTTP/WS server sockets carry the VPN's fwmark (a VpnService
                // applies to the owning app too by default), and Android's
                // policy routing then forces their *reply* packets into the
                // VPN routing table - which contains only VIRTUAL_ADDRESS/32
                // and no route back to the hotspot client's subnet, so every
                // SYN-ACK toward the car is silently dropped and the browser
                // times out. Confirmed on a real phone (session 7): inbound
                // SYNs reached the local stack fine (rule-0 local table),
                // ICMP echo replies (kernel-generated, unmarked) made it back
                // out, but TCP replies died. Excluding the app leaves our
                // sockets unmarked so replies route via the tether table
                // (hotspot subnet -> AP interface) like any normal service.
                .addDisallowedApplication(packageName)
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
