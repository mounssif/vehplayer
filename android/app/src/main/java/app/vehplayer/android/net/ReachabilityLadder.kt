package app.vehplayer.android.net

import android.content.Context
import android.content.Intent
import app.vehplayer.android.ReachabilityProbe

/**
 * Foundation §6b: "the fallback ladder itself is the shipped architecture."
 * This is the single call site MainActivity uses; it does not itself
 * implement tiers (b)/(c) (VpnReachabilityService.kt is still a stub), it
 * only decides *whether* they're needed and requests VPN consent if so.
 */
sealed interface ReachabilityDecision {
    data class Tier1NoVpnNeeded(val address: String) : ReachabilityDecision
    data class NeedsVpnConsent(val prepareIntent: Intent) : ReachabilityDecision
    data object VpnAlreadyPrepared : ReachabilityDecision
}

object ReachabilityLadder {

    /**
     * Call from MainActivity. If this returns [ReachabilityDecision.NeedsVpnConsent],
     * launch that Intent for a result; on success, tiers (b)/(c) still need
     * VpnReachabilityService's TODO implemented before they actually do
     * anything (Foundation §6b's ladder is architecturally ready, tier (b)/(c)
     * bytes are not written yet, tracked in that file, not hidden here).
     */
    fun decide(context: Context): ReachabilityDecision {
        val probe = ReachabilityProbe.probe(context)
        if (probe.tier == "ipv6-gua" && probe.address != null) {
            return ReachabilityDecision.Tier1NoVpnNeeded(probe.address)
        }

        val prepareIntent = android.net.VpnService.prepare(context)
        return if (prepareIntent != null) {
            ReachabilityDecision.NeedsVpnConsent(prepareIntent)
        } else {
            ReachabilityDecision.VpnAlreadyPrepared
        }
    }
}
