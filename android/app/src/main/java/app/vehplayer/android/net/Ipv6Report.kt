package app.vehplayer.android.net

import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Reports global (routable) IPv6 addresses per interface (session 9). After
 * the RFC1918 block was confirmed to kill the car->phone path on every IPv4
 * private address, tier (a) IPv6 is plan A: a public-range IPv6 (GUA, the
 * 2000::/3 block) is NOT inside Tesla's RFC1918 filter. The gating unknown
 * is whether the SIM/APN even hands out IPv6, and whether the hotspot
 * interface exposes one to clients.
 *
 * This surfaces both without an external site: a GUA on the cellular
 * interface (rmnet, ccmni) means the SIM has IPv6; a GUA on the AP
 * interface (ap, swlan, wlan) means the hotspot exposes it to the car.
 * Link-local (fe80::) and unique-local (fc00::/7) addresses are excluded -
 * neither is routable for this purpose.
 */
object Ipv6Report {

    data class Entry(val iface: String, val address: String, val cellular: Boolean, val hotspot: Boolean)

    fun globalV6(): List<Entry> = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface ->
                Collections.list(iface.inetAddresses)
                    .filterIsInstance<Inet6Address>()
                    .filter { isGlobalUnicast(it) }
                    .map { Entry(iface.name, it.hostAddress?.substringBefore('%') ?: "?",
                        isCellular(iface.name), isHotspot(iface.name)) }
            }
    } catch (e: Exception) {
        emptyList()
    }

    /** One-line summary for the connect-info overlay. */
    fun summary(): String {
        val all = globalV6()
        if (all.isEmpty()) return "IPv6: none (SIM/APN likely IPv4-only -> tier (a) not available)"
        val cell = all.filter { it.cellular }
        val ap = all.filter { it.hotspot }
        return buildString {
            append("IPv6 global: ")
            append(all.joinToString("; ") { "${it.iface} ${it.address}" })
            append(cell.takeIf { it.isNotEmpty() }?.let { "  [SIM has IPv6]" } ?: "  [no cellular IPv6]")
            if (ap.isNotEmpty()) append("  [hotspot exposes IPv6]")
        }
    }

    private fun isGlobalUnicast(a: Inet6Address): Boolean {
        if (a.isLinkLocalAddress || a.isLoopbackAddress || a.isMulticastAddress || a.isAnyLocalAddress) return false
        val first = a.address[0].toInt() and 0xFF
        if (first and 0xFE == 0xFC) return false // fc00::/7 unique-local
        return first and 0xE0 == 0x20 // 2000::/3 global unicast
    }

    private fun isCellular(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("pdp") ||
            n.startsWith("rmnet_data") || n.startsWith("v4-rmnet")
    }

    private fun isHotspot(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("ap") || n.startsWith("swlan") || n.startsWith("softap") || n.startsWith("wlan")
    }
}
