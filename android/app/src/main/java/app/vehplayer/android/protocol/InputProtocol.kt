package app.vehplayer.android.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Input payload (car -> phone), mirrors webclient/src/inputProtocol.ts:
 *   byte 0        event type (0=down, 1=move, 2=up, 3=scroll)
 *   byte 1        pointer_id
 *   bytes 2-5     x_norm f32 (big-endian)
 *   bytes 6-9     y_norm f32 (big-endian)
 *   bytes 10-13   scroll_delta f32 (big-endian, 0 for non-scroll)
 */
object InputProtocol {
    const val PAYLOAD_BYTES = 14

    enum class EventType(val code: Int) {
        DOWN(0), MOVE(1), UP(2), SCROLL(3);

        companion object {
            fun fromCode(code: Int): EventType = entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("unknown input event type: $code")
        }
    }

    data class InputEvent(
        val type: EventType,
        val pointerId: Int,
        val xNorm: Float,
        val yNorm: Float,
        val scrollDelta: Float = 0f,
    )

    fun decode(payload: ByteArray): InputEvent {
        require(payload.size >= PAYLOAD_BYTES) { "input payload too short: ${payload.size}" }
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val type = EventType.fromCode(buf.get().toInt() and 0xFF)
        val pointerId = buf.get().toInt() and 0xFF
        val x = buf.getFloat(2)
        val y = buf.getFloat(6)
        val scroll = buf.getFloat(10)
        return InputEvent(type, pointerId, x, y, scroll)
    }
}
