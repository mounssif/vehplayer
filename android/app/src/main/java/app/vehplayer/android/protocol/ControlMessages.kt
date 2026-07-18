package app.vehplayer.android.protocol

import org.json.JSONObject

/**
 * Control channel (ARCHITECTURE.md §4), mirrors webclient/src/control.ts.
 * Uses org.json (built into the Android framework, no extra dependency)
 * rather than kotlinx.serialization, this channel is low-rate and the
 * message set is small and stable, a full serialization library is more
 * machinery than the problem needs.
 */
object ControlMessages {
    const val HELLO_VERSION = 1

    fun helloAck(ok: Boolean, reason: String? = null): String =
        JSONObject().apply {
            put("kind", "hello_ack")
            put("ok", ok)
            reason?.let { put("reason", it) }
            put("serverVersion", HELLO_VERSION)
        }.toString()

    fun pong(echoedT: Double): String =
        JSONObject().apply {
            put("kind", "pong")
            put("t", System.currentTimeMillis().toDouble())
            put("echoedT", echoedT)
        }.toString()

    fun thermal(level: String): String =
        JSONObject().apply {
            put("kind", "thermal")
            put("level", level) // "nominal" | "fair" | "serious" | "critical", matches PowerManager.getThermalStatus() buckets mapped in ThermalMonitor.kt
        }.toString()

    data class Hello(val version: Int, val token: String, val viewportW: Int, val viewportH: Int, val dpr: Double)

    /** Parses whatever the car client sent; returns null for message kinds this side doesn't act on. */
    fun parseIncoming(raw: String): Any? {
        val json = JSONObject(raw)
        return when (json.optString("kind")) {
            "hello" -> Hello(
                version = json.optInt("version"),
                token = json.optString("token"),
                viewportW = json.optInt("viewportW"),
                viewportH = json.optInt("viewportH"),
                dpr = json.optDouble("dpr", 1.0),
            )
            "ping" -> Ping(json.optDouble("t"))
            "quality_request" -> QualityRequest(json.optString("direction"))
            "stats" -> Stats(
                fps = json.optDouble("fps"),
                decodeMs = json.optDouble("decodeMs"),
                bufferedAmount = json.optLong("bufferedAmount"),
                framesDropped = json.optInt("framesDropped"),
            )
            else -> null
        }
    }

    data class Ping(val t: Double)
    data class QualityRequest(val direction: String) // "up" | "down"
    data class Stats(val fps: Double, val decodeMs: Double, val bufferedAmount: Long, val framesDropped: Int)
}
