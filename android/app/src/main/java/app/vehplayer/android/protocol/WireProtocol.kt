package app.vehplayer.android.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wire protocol (ARCHITECTURE.md §4). Mirrors webclient/src/protocol.ts
 * byte-for-byte. If either side changes, bump HELLO_VERSION in both and in
 * ControlMessages.kt / control.ts.
 *
 *   byte 0      channel   (0x01 video, 0x02 audio, 0x03 input, 0x04 control)
 *   byte 1      flags     (video: bit0 = keyframe/IDR, bit1 = config (SPS/PPS))
 *   bytes 2-9   u64 timestamp_us (sender monotonic, big-endian)
 *   bytes 10..  payload
 */
object WireProtocol {
    const val HEADER_BYTES = 10

    object Channel {
        const val VIDEO: Byte = 0x01
        const val AUDIO: Byte = 0x02
        const val INPUT: Byte = 0x03
        const val CONTROL: Byte = 0x04
    }

    object VideoFlag {
        const val KEYFRAME = 0b0000_0001
        const val CONFIG = 0b0000_0010
    }

    data class Frame(
        val channel: Byte,
        val flags: Int,
        val timestampUs: Long,
        val payload: ByteArray,
    )

    /** Builds one frame ready to hand to a WebSocket send() call. */
    fun buildFrame(channel: Byte, flags: Int, timestampUs: Long, payload: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_BYTES + payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(channel)
        buf.put(flags.toByte())
        buf.putLong(timestampUs)
        buf.put(payload)
        return buf.array()
    }

    /** Parses a frame received on the INPUT channel (car -> phone). */
    fun parseFrame(raw: ByteArray): Frame {
        require(raw.size >= HEADER_BYTES) { "frame too short: ${raw.size} bytes" }
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
        val channel = buf.get()
        val flags = buf.get().toInt() and 0xFF
        val timestampUs = buf.long
        val payload = raw.copyOfRange(HEADER_BYTES, raw.size)
        return Frame(channel, flags, timestampUs, payload)
    }

    fun nowMonotonicUs(): Long = System.nanoTime() / 1000
}
