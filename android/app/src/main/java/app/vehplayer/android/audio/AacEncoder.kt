package app.vehplayer.android.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AAC-LC hardware encoder, buffer-input (raw PCM in, AAC access units out).
 * Route B only (Foundation §6b item 3, Pro toggle); Route A (default, car
 * Bluetooth) never touches this class at all.
 *
 * TODO(claude-code): sampleRate/channelCount here must match whatever
 * AudioPlaybackCapture actually negotiates on a real device (AudioCaptureService.kt),
 * hardcoded to 48kHz stereo as the common case, unverified against real output.
 */
class AacEncoder(
    private val sampleRate: Int = 48_000,
    private val channelCount: Int = 2,
    private val bitrateBps: Int = 128_000,
    private val onAccessUnit: (payload: ByteArray, presentationTimeUs: Long) -> Unit,
) {
    private var codec: MediaCodec? = null
    private var running = false

    fun start() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
        }
        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        c.start()
        codec = c
        running = true
        drainLoop()
    }

    fun stop() {
        running = false
        codec?.let {
            try {
                it.stop()
                it.release()
            } catch (e: IllegalStateException) {
                // already torn down, fine
            }
        }
        codec = null
    }

    /** Feed one chunk of raw 16-bit PCM (interleaved if stereo) captured from AudioPlaybackCapture. */
    fun feedPcm(pcm: ByteArray, size: Int, presentationTimeUs: Long) {
        val c = codec ?: return
        val inIndex = c.dequeueInputBuffer(10_000)
        if (inIndex < 0) return // encoder busy, drop this chunk rather than block the capture thread (ARCHITECTURE.md §3 latency target is tight, buffering here would defeat the point)
        val inBuf = c.getInputBuffer(inIndex) ?: return
        inBuf.clear()
        inBuf.put(pcm, 0, size)
        c.queueInputBuffer(inIndex, 0, size, presentationTimeUs, 0)
    }

    private fun drainLoop() {
        CoroutineScope(Dispatchers.Default).launch {
            val info = MediaCodec.BufferInfo()
            while (isActive && running) {
                val c = codec ?: break
                val outIndex = try {
                    c.dequeueOutputBuffer(info, 10_000)
                } catch (e: IllegalStateException) {
                    break
                }
                if (outIndex >= 0) {
                    val outBuf: ByteBuffer? = c.getOutputBuffer(outIndex)
                    if (outBuf != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        val data = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.get(data)
                        onAccessUnit(data, info.presentationTimeUs)
                    }
                    c.releaseOutputBuffer(outIndex, false)
                }
            }
        }
    }
}
