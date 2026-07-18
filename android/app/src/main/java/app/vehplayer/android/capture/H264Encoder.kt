package app.vehplayer.android.capture

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.view.Surface
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MediaCodec H.264 hardware encoder, low-latency config (ARCHITECTURE.md §2):
 * KEY_LATENCY=1 where supported, realtime priority, repeat SPS/PPS before
 * every IDR (PREPEND_HEADER_TO_SYNC_FRAMES) so the car client never has to
 * wait for a fresh config frame, and intra-refresh instead of a short GOP so
 * recovery after a socket stall doesn't require a huge keyframe burst.
 *
 * Input is a Surface (from MediaProjection's VirtualDisplay, CaptureService),
 * output is Annex B access units delivered via [onAccessUnit], with the
 * keyframe/config flags the wire protocol needs (WireProtocol.VideoFlag).
 *
 * [start] always tries the full config above first, and falls back to a
 * minimal one (see [buildFormat]) if `configure()` rejects it - confirmed on
 * a real device (Android 16 emulator) that PREPEND_HEADER_TO_SYNC_FRAMES and
 * KEY_INTRA_REFRESH_PERIOD can both be reported as supported by
 * CodecCapabilities and still make `configure()` throw. Encoder capability
 * fragmentation across real devices (MCU2 vs MCU3 phones, ARCHITECTURE.md
 * §5) is exactly why this degrades instead of trusting capability flags
 * alone.
 *
 * TODO(claude-code): bitrate and KEY_INTRA_REFRESH_PERIOD's value are still
 * the ARCHITECTURE.md §2 *default*, not yet MEASURED against real Gate-2
 * numbers even where they're accepted.
 */
class H264Encoder(
    private val width: Int,
    private val height: Int,
    private val bitrateBps: Int = 8_000_000, // ARCHITECTURE.md §2: 6-12 Mbps range, 8 as a mid default until Gate-1/2 tiers are calibrated
    private val frameRate: Int = 30,
    private val onAccessUnit: (payload: ByteArray, isKeyframe: Boolean, isConfig: Boolean, presentationTimeUs: Long) -> Unit,
) {
    companion object {
        private const val TAG = "H264Encoder"
    }

    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var running = false

    /**
     * Call this first; returns the Surface to hand to VirtualDisplay/MediaProjection
     * as the render target.
     *
     * Tries the full-quality format first, and falls back to a minimal one if
     * configure() rejects it. Confirmed on a real device (Android 16 emulator,
     * reproducible every time): this encoder reports FEATURE_IntraRefresh
     * supported via CodecCapabilities, and reports no problem with
     * KEY_PREPEND_HEADER_TO_SYNC_FRAMES either, but MediaCodec.configure()
     * throws a bare IllegalArgumentException (no detail message) for both
     * when actually attempted, crash-looping CaptureService every time
     * streaming starts. Capability *reporting* isn't reliable enough on its
     * own to trust blindly; only configure() actually succeeding is proof,
     * so this always tries for real and degrades gracefully instead.
     */
    fun start(): Surface {
        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            c.configure(buildFormat(preferAdvanced = true), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: IllegalArgumentException) {
            android.util.Log.w(TAG, "encoder rejected full-quality format, retrying minimal", e)
            c.configure(buildFormat(preferAdvanced = false), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        val surface = c.createInputSurface()
        c.start()

        codec = c
        inputSurface = surface
        running = true
        drainLoop()
        return surface
    }

    /**
     * `preferAdvanced = true` is the ARCHITECTURE.md §2 target: baseline
     * profile, repeat SPS/PPS before every sync frame so a reconnecting
     * client never waits for a fresh config frame, and intra-refresh instead
     * of a periodic full IDR to avoid a huge keyframe stalling TCP/WS under
     * loss. `false` is the bare set every encoder that can do Surface-input
     * H.264 at all is expected to accept: color format, bitrate, framerate,
     * and a plain periodic I-frame instead of intra-refresh.
     */
    private fun buildFormat(preferAdvanced: Boolean): MediaFormat =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)

            if (preferAdvanced) {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)
                setInteger(MediaFormat.KEY_LATENCY, 1)
                setInteger(MediaFormat.KEY_PRIORITY, 0) // realtime priority (0 = realtime, per MediaCodec docs)
            }

            // Intra-refresh value is a first guess (macroblocks per frame the
            // encoder should refresh), not yet MEASURED even where it works.
            if (preferAdvanced && deviceSupportsIntraRefresh()) {
                setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, frameRate * 2) // full refresh cycle ~2s
            } else {
                // Short GOP per ARCHITECTURE.md §2 "short GOP (1-2s) OR intra-refresh".
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
        }

    fun stop() {
        running = false
        codec?.let {
            try {
                it.stop()
                it.release()
            } catch (e: IllegalStateException) {
                // already stopped/released, fine during teardown races
            }
        }
        codec = null
        inputSurface = null
    }

    /** Ask the encoder for a fresh IDR now (e.g. after a reconnect or a quality-ladder step). */
    fun requestKeyframe() {
        codec?.let {
            val params = android.os.Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            it.setParameters(params)
        }
    }

    /** Adaptive bitrate step (ARCHITECTURE.md §5), called by the quality ladder controller. */
    fun setBitrate(bps: Int) {
        codec?.let {
            val params = android.os.Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bps)
            }
            it.setParameters(params)
        }
    }

    private fun drainLoop() {
        CoroutineScope(Dispatchers.Default).launch {
            val bufferInfo = MediaCodec.BufferInfo()
            while (isActive && running) {
                val c = codec ?: break
                val outIndex = try {
                    c.dequeueOutputBuffer(bufferInfo, 10_000)
                } catch (e: IllegalStateException) {
                    break // codec was released concurrently during teardown
                }
                if (outIndex >= 0) {
                    val outBuf: ByteBuffer? = c.getOutputBuffer(outIndex)
                    if (outBuf != null && bufferInfo.size > 0) {
                        val data = ByteArray(bufferInfo.size)
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        outBuf.get(data)

                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        val isKeyframe = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                        onAccessUnit(data, isKeyframe, isConfig, bufferInfo.presentationTimeUs)
                    }
                    c.releaseOutputBuffer(outIndex, false)
                }
                // MediaCodec.INFO_OUTPUT_FORMAT_CHANGED / INFO_TRY_AGAIN_LATER: no
                // action needed for a Surface-input encoder emitting Annex B,
                // the format change event doesn't carry data we act on here.
            }
        }
    }

    private fun deviceSupportsIntraRefresh(): Boolean {
        val caps = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .firstOrNull { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }
            }
            ?.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            ?: return false
        return caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_IntraRefresh)
    }
}
