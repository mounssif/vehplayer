package app.vehplayer.android.audio

import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ARCHITECTURE.md §3 Route B: AudioPlaybackCapture -> AAC -> WS audio
 * channel. Needs the live [MediaProjection] object (not just the
 * resultCode/data pair), which is why this lives inside CaptureService
 * rather than a separate Service, see the manifest comment on
 * `foregroundServiceType="mediaProjection|microphone"`.
 *
 * Route A (default, car Bluetooth) never constructs this class at all, it's
 * purely the Pro low-latency-audio toggle's implementation.
 *
 * TODO(claude-code): ARCHITECTURE.md §3 flags a REPORTED edge case ("some
 * DRM apps produce silence"), not handled here, would show up as a silent
 * AAC stream rather than an error, worth a real-device check at Gate 2.
 */
class PlaybackAudioCapture(
    private val projection: MediaProjection,
    private val onAacAccessUnit: (payload: ByteArray, presentationTimeUs: Long) -> Unit,
) {
    private var audioRecord: AudioRecord? = null
    private var encoder: AacEncoder? = null
    private var job: Job? = null

    private val sampleRate = 48_000
    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @Suppress("MissingPermission") // RECORD_AUDIO declared in the manifest; caller (CaptureService) only constructs this after the user has completed the MediaProjection consent flow, which implies the app is already in a foreground/trusted state
    fun start() {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
            .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
            .build()

        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val record = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(audioFormat)
                    .build(),
            )
            .setBufferSizeInBytes(minBufSize * 2)
            .setAudioPlaybackCaptureConfig(config)
            .build()
        audioRecord = record

        val aac = AacEncoder(sampleRate = sampleRate, channelCount = 2, onAccessUnit = onAacAccessUnit)
        aac.start()
        encoder = aac

        record.startRecording()
        job = CoroutineScope(Dispatchers.Default).launch {
            val buf = ByteArray(minBufSize)
            var ptsUs = 0L
            val bytesPerFrame = 4 // 16-bit stereo = 2 bytes * 2 channels
            while (isActive) {
                val read = record.read(buf, 0, buf.size)
                if (read > 0) {
                    aac.feedPcm(buf, read, ptsUs)
                    // Advance the timeline by exactly what we captured, not by
                    // wall-clock time, keeps encoder timestamps consistent
                    // even if this loop gets briefly starved by the scheduler.
                    val frames = read / bytesPerFrame
                    ptsUs += (frames.toLong() * 1_000_000L) / sampleRate
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        audioRecord?.let {
            try {
                it.stop()
                it.release()
            } catch (e: IllegalStateException) {
                // already stopped, fine during teardown races
            }
        }
        audioRecord = null
        encoder?.stop()
        encoder = null
    }
}
