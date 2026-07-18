package app.vehplayer.android.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import app.vehplayer.android.audio.PlaybackAudioCapture
import app.vehplayer.android.protocol.WireProtocol
import app.vehplayer.android.server.LocalMediaServer
import app.vehplayer.android.server.ServerHolder

/**
 * Mirror mode capture (ARCHITECTURE.md §2 "Mirror mode: MediaProjection of
 * the main display... v1 default"). Virtual-display / app-launching mode is
 * Power Mode (Foundation §6b item 2) and is NOT implemented here yet, this
 * service only ever mirrors the whole screen.
 *
 * Started from MainActivity with the MediaProjection permission Intent's
 * resultCode/data already in hand (that permission can only be requested
 * from an Activity, this service just consumes the result).
 */
class CaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_WIDTH = "width"   // boot-time placeholder until a real hello arrives, ARCHITECTURE.md §2 "resolution follows the car viewport"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_LOW_LATENCY_AUDIO = "low_latency_audio" // Route B toggle, Foundation §6b item 3, Pro-gated by MainActivity before this extra is ever set true
        private const val NOTIFICATION_CHANNEL_ID = "vehplayer_capture"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, resultCode: Int, resultData: Intent, carWidth: Int, carHeight: Int, lowLatencyAudio: Boolean = false) {
            val intent = Intent(context, CaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
                putExtra(EXTRA_WIDTH, carWidth)
                putExtra(EXTRA_HEIGHT, carHeight)
                putExtra(EXTRA_LOW_LATENCY_AUDIO, lowLatencyAudio)
            }
            context.startForegroundService(intent)
        }

        @Volatile
        var instance: CaptureService? = null
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: H264Encoder? = null
    private var server: LocalMediaServer? = null
    private var audioCapture: PlaybackAudioCapture? = null
    private var currentWidth = 0
    private var currentHeight = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        // Foreground notification must go up before touching MediaProjection
        // (required by platform policy on recent Android versions). Type is
        // scoped to what's actually granted+used this call: declaring the
        // "microphone" manifest type here unconditionally crashes on API 34+
        // with a SecurityException (RECORD_AUDIO is a dangerous permission
        // that's never runtime-requested anywhere in this app yet, only
        // manifest-declared) even when lowLatencyAudio is false and no
        // microphone capture happens at all. Route B (low-latency audio)
        // isn't wired to any UI control yet (MainActivity always passes
        // false), so this only ever requests the mediaProjection type today;
        // wiring Route B for real needs an actual RECORD_AUDIO runtime
        // permission request added alongside it, not just this type flag.
        val lowLatencyAudio = intent.getBooleanExtra(EXTRA_LOW_LATENCY_AUDIO, false)
        val foregroundType = if (lowLatencyAudio) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        startForeground(NOTIFICATION_ID, buildNotification(), foregroundType)

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = intent.getParcelableExtraCompat<Intent>(EXTRA_RESULT_DATA) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val width = intent.getIntExtra(EXTRA_WIDTH, 1280)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 800)
        currentWidth = width
        currentHeight = height
        instance = this

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        mediaProjection = projection

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                // User revoked capture (screen-share stop bar) or the system
                // tore it down. Tear everything down cleanly rather than limp
                // along with a dead projection.
                stopCaptureInternals()
                stopSelf()
            }
        }, null)

        val localServer = LocalMediaServer(
            port = 8787,
            onInputEvent = { event ->
                app.vehplayer.android.input.VehplayerAccessibilityService.instance?.handleInputEvent(event)
                    ?: android.util.Log.w("CaptureService", "input event dropped, accessibility service not enabled/connected")
            },
            onQualityRequest = { direction -> encoder?.let { adjustBitrateForQualityRequest(it, direction) } },
            onHello = { viewportW, viewportH, dpr -> resize(viewportW, viewportH, dpr) },
        )
        localServer.start()
        server = localServer
        ServerHolder.server = localServer

        startEncoderAndDisplay(projection, width, height)

        if (lowLatencyAudio) {
            val capture = PlaybackAudioCapture(projection) { payload, ptsUs ->
                localServer.broadcastAudioFrame(payload, ptsUs)
            }
            capture.start()
            audioCapture = capture
        }
        // else: Route A (car Bluetooth), zero code path here by design
        // (ARCHITECTURE.md §3, "Route A (default): car Bluetooth... Zero code").

        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        stopCaptureInternals()
        super.onDestroy()
    }

    /**
     * Starts (or restarts, for a resize) the encoder + VirtualDisplay pair at
     * the given pixel dimensions. MediaProjection itself is left running,
     * VirtualDisplay/encoder are the only pieces that need to be recreated
     * to change resolution (MediaCodec doesn't support live resize of a
     * Surface-input encoder).
     */
    private fun startEncoderAndDisplay(projection: MediaProjection, width: Int, height: Int) {
        val localServer = server ?: return

        val videoEncoder = H264Encoder(width, height) { payload, isKeyframe, isConfig, ptsUs ->
            val videoFlags = (if (isKeyframe) WireProtocol.VideoFlag.KEYFRAME else 0) or
                (if (isConfig) WireProtocol.VideoFlag.CONFIG else 0)
            localServer.broadcastVideoFrame(payload, videoFlags, ptsUs)
        }
        encoder = videoEncoder
        val encoderSurface = videoEncoder.start()

        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getMetrics(it)
        }

        virtualDisplay = projection.createVirtualDisplay(
            "vehplayer-mirror",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            encoderSurface,
            null,
            null,
        )
        currentWidth = width
        currentHeight = height
    }

    /**
     * ARCHITECTURE.md §2: "resolution follows the car viewport, not the
     * phone: request the browser's reported canvas size on connect,
     * configure the encoder to match." Called from LocalMediaServer's onHello
     * once a car client's `hello` reports its real viewport (CSS pixels) and
     * device pixel ratio; boot-time capture starts at the EXTRA_WIDTH/HEIGHT
     * placeholder until this fires for the first time.
     */
    private fun resize(viewportW: Int, viewportH: Int, dpr: Double) {
        val projection = mediaProjection ?: return
        if (viewportW <= 0 || viewportH <= 0) return

        // H.264 4:2:0 needs even dimensions; round down so we never exceed
        // the reported viewport.
        val width = (Math.round(viewportW * dpr).toInt()).let { it - (it % 2) }.coerceAtLeast(2)
        val height = (Math.round(viewportH * dpr).toInt()).let { it - (it % 2) }.coerceAtLeast(2)
        if (width == currentWidth && height == currentHeight) return

        virtualDisplay?.release()
        virtualDisplay = null
        encoder?.stop()
        encoder = null

        startEncoderAndDisplay(projection, width, height)
    }

    private fun stopCaptureInternals() {
        virtualDisplay?.release()
        virtualDisplay = null
        encoder?.stop()
        encoder = null
        audioCapture?.stop()
        audioCapture = null
        server?.stop()
        server = null
        ServerHolder.server = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    /**
     * ARCHITECTURE.md §5: "Ladder: drop framerate first (60->30->24), then
     * bitrate steps, then resolution tier." Framerate/resolution steps need
     * encoder reconfiguration (more invasive, causes a visible hiccup);
     * bitrate is the cheap first lever MediaCodec supports live via
     * PARAMETER_KEY_VIDEO_BITRATE, so that's all this does for now.
     * TODO(claude-code): implement the framerate/resolution steps of the
     * ladder once Gate 2 numbers show bitrate-only isn't enough headroom,
     * per ARCHITECTURE.md §5's full ladder and its hysteresis requirement.
     */
    private fun adjustBitrateForQualityRequest(enc: H264Encoder, direction: String) {
        val step = 1_500_000 // 1.5 Mbps per step, unmeasured placeholder
        currentBitrateBps = when (direction) {
            "down" -> (currentBitrateBps - step).coerceAtLeast(2_000_000)
            "up" -> (currentBitrateBps + step).coerceAtMost(12_000_000) // ARCHITECTURE.md §2 ceiling
            else -> currentBitrateBps
        }
        enc.setBitrate(currentBitrateBps)
    }

    private var currentBitrateBps = 8_000_000

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "vehplayer streaming",
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("vehplayer is streaming to your car")
            .setSmallIcon(android.R.drawable.ic_media_play) // TODO(claude-code): real icon at Gate 5 brand pass
            .setOngoing(true)
            .build()
    }
}

// Intent.getParcelableExtra(String) is deprecated in favor of the typed
// overload added in API 33; this shim keeps one call site working across the
// minSdk 29 .. compileSdk 35 range without a deprecation warning at every use.
@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        getParcelableExtra(key) as? T
    }
