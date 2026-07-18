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
 * TODO(claude-code): every constant here (bitrate, KEY_INTRA_REFRESH_PERIOD)
 * is the ARCHITECTURE.md §2 *default*, not yet MEASURED. Calibrate against
 * real Gate-2 numbers, this compiles and should run, but the actual
 * quality/latency tradeoff is unverified (no device access in the authoring
 * session, see NEXT_SESSION.md).
 */
class H264Encoder(
    private val width: Int,
    private val height: Int,
    private val bitrateBps: Int = 8_000_000, // ARCHITECTURE.md §2: 6-12 Mbps range, 8 as a mid default until Gate-1/2 tiers are calibrated
    private val frameRate: Int = 30,
    private val onAccessUnit: (payload: ByteArray, isKeyframe: Boolean, isConfig: Boolean, presentationTimeUs: Long) -> Unit,
) {
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var running = false

    /** Call this first; returns the Surface to hand to VirtualDisplay/MediaProjection as the render target. */
    fun start(): Surface {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)

            // Repeat SPS/PPS before every sync frame, so a client that just
            // (re)connected or dropped a config frame recovers on the next IDR.
            setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)

            // Intra-refresh instead of a periodic full IDR: avoids the "huge
            // keyframe stalls TCP/WS under loss" failure mode called out in
            // ARCHITECTURE.md §2. Value is a first guess (macroblocks per
            // frame the encoder should refresh), not yet MEASURED.
            if (deviceSupportsIntraRefresh()) {
                setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, frameRate * 2) // full refresh cycle ~2s
            } else {
                // Fallback: short GOP per ARCHITECTURE.md §2 "short GOP (1-2s) OR intra-refresh"
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }

            // KEY_LATENCY is hidden-API-adjacent on some OEM builds; guarded
            // with try/catch at the setInteger call site below rather than
            // here, since MediaFormat.setInteger doesn't throw for unknown
            // keys, the codec just ignores what it doesn't understand.
            setInteger(MediaFormat.KEY_LATENCY, 1)
            setInteger(MediaFormat.KEY_PRIORITY, 0) // realtime priority (0 = realtime, per MediaCodec docs)
        }

        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = c.createInputSurface()
        c.start()

        codec = c
        inputSurface = surface
        running = true
        drainLoop()
        return surface
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
