package app.vehplayer.android.server

import app.vehplayer.android.protocol.ControlMessages
import app.vehplayer.android.protocol.InputProtocol
import app.vehplayer.android.protocol.WireProtocol
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer

/**
 * The phone side of the wire protocol (ARCHITECTURE.md §4). One WS endpoint,
 * binary video/audio/input frames + JSON control, per the shared header
 * format in WireProtocol.kt (kept byte-compatible with
 * webclient/src/protocol.ts by hand, there is no schema codegen here, if one
 * side changes the other must change with it, see WireProtocol.kt's header
 * comment).
 *
 * Deliberately supports multiple simultaneous connections (not just one):
 * nothing in the trust model requires exactly one client, and rejecting a
 * second connection outright would make phone-screen-mirror-to-laptop-for-
 * debugging annoying during Gate 2. Only authenticated (post-hello) sockets
 * receive media.
 */
class LocalMediaServer(
    port: Int,
    private val onInputEvent: (InputProtocol.InputEvent) -> Unit = {},
    private val onQualityRequest: (direction: String) -> Unit = {},
    private val onHello: (viewportW: Int, viewportH: Int, dpr: Double) -> Unit = { _, _, _ -> },
) : WebSocketServer(InetSocketAddress(port)) {

    private val authenticated = ConcurrentHashMap<WebSocket, Boolean>()

    override fun onStart() {
        connectionLostTimeout = 15
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        authenticated[conn] = false
        // No media/input is trusted until a valid `hello` arrives (ARCHITECTURE.md
        // §1 trust boundary note). The handshake itself doesn't carry the
        // pairing token, it comes over the control channel as the first message,
        // matching webclient/src/wsClient.ts's connect() -> buildHello() flow.
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        authenticated.remove(conn)
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        System.err.println("[LocalMediaServer] error: ${ex.message}")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        when (val parsed = ControlMessages.parseIncoming(message)) {
            is ControlMessages.Hello -> {
                val ok = PairingToken.isValid(parsed.token)
                if (ok) PairingToken.touch(parsed.token)
                authenticated[conn] = ok
                conn.send(ControlMessages.helloAck(ok, if (ok) null else "invalid or expired token"))
                if (!ok) {
                    conn.close(4001, "invalid token")
                } else {
                    onHello(parsed.viewportW, parsed.viewportH, parsed.dpr)
                }
            }
            is ControlMessages.Ping -> conn.send(ControlMessages.pong(parsed.t))
            is ControlMessages.QualityRequest -> {
                if (authenticated[conn] == true) onQualityRequest(parsed.direction)
            }
            is ControlMessages.Stats -> {
                // Logged, not acted on yet. TODO(claude-code): feed into the
                // same quality-ladder decision as onQualityRequest once
                // ARCHITECTURE.md §5's hysteresis logic lives phone-side too
                // (right now qualityLadder.ts makes the call, this is a
                // second data point for when that logic needs both ends).
            }
            null -> { /* unrecognized control kind, ignore rather than disconnect, forward-compatible */ }
        }
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
        if (authenticated[conn] != true) return // media/input ignored pre-auth
        val raw = ByteArray(message.remaining())
        message.get(raw)
        val frame = try {
            WireProtocol.parseFrame(raw)
        } catch (e: IllegalArgumentException) {
            return // malformed frame, drop silently, this is a local link not an adversarial one worth logging loudly
        }
        if (frame.channel == WireProtocol.Channel.INPUT) {
            try {
                onInputEvent(InputProtocol.decode(frame.payload))
            } catch (e: IllegalArgumentException) {
                // malformed input payload, drop
            }
        }
    }

    fun broadcastVideoFrame(payload: ByteArray, flags: Int, presentationTimeUs: Long) {
        broadcast(WireProtocol.Channel.VIDEO, flags, presentationTimeUs, payload)
    }

    fun broadcastAudioFrame(payload: ByteArray, presentationTimeUs: Long) {
        broadcast(WireProtocol.Channel.AUDIO, 0, presentationTimeUs, payload)
    }

    private fun broadcast(channel: Byte, flags: Int, ptsUs: Long, payload: ByteArray) {
        val frame = WireProtocol.buildFrame(channel, flags, ptsUs, payload)
        val buf = ByteBuffer.wrap(frame)
        for ((conn, isAuth) in authenticated) {
            if (isAuth && conn.isOpen) {
                buf.rewind()
                conn.send(buf)
            }
        }
    }
}
