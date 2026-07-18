package app.vehplayer.android.net

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * Reachability tiers (b) CGNAT-via-VpnService and (c) TeslAA-style
 * public-range VpnService (ARCHITECTURE.md §7, Foundation §6b item 1).
 *
 * NOT implemented yet, deliberately scoped as a follow-up rather than
 * guessed at: the actual address ranges, DNS wiring, and routing rules this
 * needs depend on the S1 spike's MEASURED results (which tier(s) the current
 * Tesla firmware actually blocks), and none of that data exists yet (see
 * validate/S1_reachability.md, still a template). Building the VPN plumbing
 * before knowing whether tier (a) alone already solves it for most
 * firmware versions risks real wasted effort, not just "TODO decoration".
 *
 * What IS decided (Foundation §6b): this stays behind ReachabilityLadder.kt,
 * tried only after ReachabilityProbe.kt's tier-(a) IPv6 check fails, and at
 * most one Android VPN consent dialog is ever shown, once, with honest copy
 * (Foundation §3 principle 2 "Simple").
 *
 * TODO(claude-code):
 *  - implement onStartCommand: establish() a VpnService.Builder session
 *    assigning either a CGNAT (100.64.0.0/10) or TeslAA-style address per
 *    ReachabilityLadder's tier selection
 *  - route only the traffic that needs it (the local hotspot subnet to the
 *    car), NOT a full-tunnel VPN, this is a local address-assignment trick,
 *    not a privacy VPN
 *  - wire a local DNS responder for go.vehplayer.app (or whatever the final
 *    domain is) per ARCHITECTURE.md §7's "DNS dependency rule"
 */
class VpnReachabilityService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO(claude-code): not implemented, see class doc. Returning
        // START_NOT_STICKY rather than silently pretending to succeed.
        android.util.Log.w("VpnReachabilityService", "tier (b)/(c) not implemented yet, see class doc TODO")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
