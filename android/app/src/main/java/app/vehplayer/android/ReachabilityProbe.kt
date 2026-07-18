package app.vehplayer.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet6Address
import java.net.NetworkInterface

/**
 * S1 reachability probe (ARCHITECTURE.md §8, Foundation §6b).
 *
 * Job: find out, on THIS phone right now, whether tier (a) of the fallback ladder
 * is available, before falling back to VpnService tiers (b)/(c) which need real
 * VpnService plumbing (Gate 2, not built yet).
 *
 * This class only answers "does a usable address exist locally". It does NOT yet
 * confirm the car's browser can actually reach it (that half of S1 needs the
 * webclient/probe page loaded on the car and a real WS round trip, see
 * webclient/probe/README.md). Treat this as necessary, not sufficient.
 */
data class ReachabilityResult(
    val tier: String,          // "ipv6-gua", "no-ipv6-gua", "no-active-network"
    val interfaceName: String?,
    val address: String?,
    val transport: String?,    // "wifi", "cellular", "unknown"
)

object ReachabilityProbe {

    fun probe(context: Context): ReachabilityResult {
        val transport = activeTransport(context)

        val candidate = findGlobalScopeIpv6()
        return if (candidate != null) {
            ReachabilityResult(
                tier = "ipv6-gua",
                interfaceName = candidate.first,
                address = candidate.second,
                transport = transport,
            )
        } else {
            ReachabilityResult(
                tier = if (transport == null) "no-active-network" else "no-ipv6-gua",
                interfaceName = null,
                address = null,
                transport = transport,
            )
        }
    }

    /**
     * Walks all network interfaces looking for an IPv6 address that is NOT
     * loopback, link-local, or a multicast address, i.e. a real global-scope
     * address a car browser could plausibly reach. Deliberately conservative:
     * false negatives (missing a usable address) are safer here than false
     * positives that make S1's in-car test look better than reality.
     */
    private fun findGlobalScopeIpv6(): Pair<String, String>? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in iface.inetAddresses) {
                if (addr is Inet6Address &&
                    !addr.isLoopbackAddress &&
                    !addr.isLinkLocalAddress &&
                    !addr.isSiteLocalAddress &&
                    !addr.isMulticastAddress
                ) {
                    return iface.displayName to addr.hostAddress.orEmpty()
                }
            }
        }
        return null
    }

    private fun activeTransport(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "unknown"
        }
    }
}
