package app.vehplayer.android.net

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.nio.ByteBuffer

/**
 * Minimal STUN Binding responder (RFC 5389 subset) for the WebRTC in-car
 * probe (docs/NEXT_SESSION.md session 7 wrap-up, webclient probe-webrtc
 * page). Purpose: a one-photo answer to the single question no public
 * source settles - does Tesla's in-car browser send UDP to an RFC1918
 * address on the hotspot LAN, or does its private-range block cover UDP
 * too, not just TCP/HTTP?
 *
 * The probe page points RTCPeerConnection at `stun:<phone-ip>:3478`. If the
 * browser's ICE gathering produces a srflx candidate, a full UDP round trip
 * to this socket happened - WebRTC media/data channels over the hotspot
 * link are viable and the whole WebRTC transport direction is GO. Binding
 * requests are the only STUN message this needs to understand.
 *
 * Deliberately bound on the wildcard address: the kernel delivers packets
 * arriving on the AP interface to it directly (strong-host model is
 * satisfied because the destination is the AP interface's own address -
 * unlike the tier (c) VPN address, which Android 14+'s BPF ingress-discard
 * drops for exactly this cross-interface case, see NEXT_SESSION.md).
 */
class ProbeStunServer(private val port: Int = DEFAULT_PORT) {

    companion object {
        const val DEFAULT_PORT = 3478
        private const val TAG = "ProbeStunServer"
        private const val MAGIC_COOKIE = 0x2112A442
        private const val BINDING_REQUEST: Short = 0x0001
        private const val BINDING_SUCCESS: Short = 0x0101.toShort()
        private const val ATTR_XOR_MAPPED_ADDRESS: Short = 0x0020
    }

    @Volatile
    private var socket: DatagramSocket? = null

    /**
     * Zero-adb counter (same idea as HttpAssetServer.requestCount): >0 after
     * a car probe run means UDP genuinely traverses car -> AP -> this socket,
     * without needing `adb logcat -s ProbeStunServer` at the car.
     */
    @Volatile
    var answeredCount = 0
        private set

    fun start() {
        if (socket != null) return
        val s = DatagramSocket(port)
        socket = s
        Thread({ receiveLoop(s) }, TAG).apply {
            isDaemon = true
            start()
        }
        Log.i(TAG, "probe STUN responder listening on udp/$port")
    }

    fun stop() {
        socket?.close()
        socket = null
    }

    private fun receiveLoop(s: DatagramSocket) {
        val buf = ByteArray(1500)
        while (!s.isClosed) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                s.receive(packet)
                val reply = buildBindingResponse(packet) ?: continue
                s.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                answeredCount++
                Log.i(TAG, "answered STUN binding from ${packet.address}:${packet.port}")
            } catch (e: Exception) {
                if (!s.isClosed) Log.w(TAG, "receive loop error: ${e.message}")
            }
        }
    }

    /** Null for anything that isn't a well-formed STUN Binding Request. */
    private fun buildBindingResponse(packet: DatagramPacket): ByteArray? {
        if (packet.length < 20) return null
        val header = ByteBuffer.wrap(packet.data, 0, 20)
        val type = header.short
        header.short // message length, unused - attributes are ignored
        val cookie = header.int
        if (type != BINDING_REQUEST || cookie != MAGIC_COOKIE) return null
        val transactionId = ByteArray(12).also { header.get(it) }

        val addrBytes = packet.address.address
        val family: Byte = if (packet.address is Inet6Address) 0x02 else 0x01
        val valueLen = 4 + addrBytes.size
        val messageLen = 4 + valueLen

        val out = ByteBuffer.allocate(20 + messageLen)
        out.putShort(BINDING_SUCCESS)
        out.putShort(messageLen.toShort())
        out.putInt(MAGIC_COOKIE)
        out.put(transactionId)
        out.putShort(ATTR_XOR_MAPPED_ADDRESS)
        out.putShort(valueLen.toShort())
        out.put(0)
        out.put(family)
        out.putShort((packet.port xor (MAGIC_COOKIE ushr 16)).toShort())
        // RFC 5389 §15.2: IPv4 XORs with the magic cookie; IPv6 with
        // cookie + transaction id (16 bytes total).
        val xorMask = ByteBuffer.allocate(16).putInt(MAGIC_COOKIE).put(transactionId).array()
        for (i in addrBytes.indices) {
            out.put((addrBytes[i].toInt() xor xorMask[i].toInt()).toByte())
        }
        return out.array()
    }
}
