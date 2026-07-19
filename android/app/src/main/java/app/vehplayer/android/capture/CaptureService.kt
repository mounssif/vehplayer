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
import app.vehplayer.android.server.HttpAssetServer
import app.vehplayer.android.server.LocalMediaServer
import app.vehplayer.android.server.ServerHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        private const val DEFAULT_HTTP_PORT = 8080

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
    private var currentFrameRate = 30

    // The real, unscaled viewport dims reported by the car's `hello` (or the
    // boot-time placeholder before one arrives) - the resolution-tier lever
    // below scales FROM this, never from currentWidth/currentHeight, so
    // repeated ladder steps don't compound a shrink onto an already-shrunk
    // size.
    private var baseWidth = 1280
    private var baseHeight = 800

    private var httpServer: HttpAssetServer? = null

    /**
     * Null until [startHttpServerWithRetry] resolves a port. Owned here (a
     * foreground Service, survives the user backgrounding/swiping the app
     * away while walking to the car) instead of MainActivity (an Activity,
     * destroyed in exactly that scenario) - the local server used to die
     * with the Activity while capture kept running, so the car's /go
     * request got ERR_CONNECTION_REFUSED even though the hotspot and
     * capture session were both still fine. Public so MainActivity can poll
     * it after calling [start] to build the "open this URL" message.
     */
    var httpServerPort: Int? = null
        private set

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
        baseWidth = width
        baseHeight = height
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
            onQualityRequest = { direction -> adjustQualityForRequest(direction) },
            onHello = { viewportW, viewportH, dpr -> resize(viewportW, viewportH, dpr) },
        )
        localServer.start()
        server = localServer
        ServerHolder.server = localServer

        startHttpServerWithRetry()
        val (initW, initH) = scaledDims()
        startEncoderAndDisplay(projection, initW, initH, frameRateSteps[frameRateStepIndex])

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
    private fun startEncoderAndDisplay(projection: MediaProjection, width: Int, height: Int, frameRate: Int) {
        val localServer = server ?: return

        val videoEncoder = H264Encoder(width, height, bitrateBps = currentBitrateBps, frameRate = frameRate) { payload, isKeyframe, isConfig, ptsUs ->
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
        currentFrameRate = frameRate
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
        if (viewportW <= 0 || viewportH <= 0) return

        // H.264 4:2:0 needs even dimensions; round down so we never exceed
        // the reported viewport. This is the real, unscaled base size - any
        // active resolution-ladder step still applies on top of it (see
        // scaledDims()), not thrown away by a fresh hello.
        val width = (Math.round(viewportW * dpr).toInt()).let { it - (it % 2) }.coerceAtLeast(2)
        val height = (Math.round(viewportH * dpr).toInt()).let { it - (it % 2) }.coerceAtLeast(2)
        if (width == baseWidth && height == baseHeight) return
        baseWidth = width
        baseHeight = height

        reconfigureEncoderForLadder()
    }

    private fun reconfigureEncoderForLadder() {
        val projection = mediaProjection ?: return
        val (width, height) = scaledDims()
        val frameRate = frameRateSteps[frameRateStepIndex]
        if (width == currentWidth && height == currentHeight && frameRate == currentFrameRate) return

        virtualDisplay?.release()
        virtualDisplay = null
        encoder?.stop()
        encoder = null

        startEncoderAndDisplay(projection, width, height, frameRate)
    }

    private fun scaledDims(): Pair<Int, Int> {
        val scale = resolutionScaleSteps[resolutionScaleStepIndex]
        val width = (Math.round(baseWidth * scale).toInt()).let { it - (it % 2) }.coerceAtLeast(2)
        val height = (Math.round(baseHeight * scale).toInt()).let { it - (it % 2) }.coerceAtLeast(2)
        return width to height
    }

    /**
     * Same port-fallback strategy as (formerly) MainActivity: a couple of
     * quick retries on the default port covers a transient release race,
     * then falls through immediately to alternate ports if something else
     * genuinely holds it long-term.
     */
    private fun startHttpServerWithRetry(index: Int = 0) {
        val portAttempts = listOf(DEFAULT_HTTP_PORT, DEFAULT_HTTP_PORT, DEFAULT_HTTP_PORT, 8081, 8082, 8083)
        if (index >= portAttempts.size) {
            android.util.Log.e("CaptureService", "HttpAssetServer failed to start on any of $portAttempts")
            return
        }
        val port = portAttempts[index]
        val server = HttpAssetServer(applicationContext, wsPort = 8787, port = port)
        try {
            server.start()
            httpServer = server
            httpServerPort = port
        } catch (e: java.io.IOException) {
            val isSamePortRetry = index < 2 && portAttempts[index + 1] == port
            CoroutineScope(Dispatchers.Main).launch {
                if (isSamePortRetry) delay(500)
                startHttpServerWithRetry(index + 1)
            }
        }
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
        httpServer?.stop()
        httpServer = null
        httpServerPort = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    // ARCHITECTURE.md §5: "Ladder: drop framerate first (60->30->24), then
    // bitrate steps, then resolution tier. Recover in the same order,
    // reversed, with hysteresis." All three levers below are ASSUMED tiers
    // (not yet MEASURED against real Gate-2 numbers): the doc's own 60->30->24
    // framerate sequence assumes a 60fps starting point, but H264Encoder's
    // real default here is already 30, so this ladder starts one tier "in"
    // from the doc's literal numbers and adds one step further (18) since
    // there was nowhere else to go before falling through to bitrate.
    private val frameRateSteps = listOf(30, 24, 18)
    private val resolutionScaleSteps = listOf(1.0, 0.75, 0.5)
    private var frameRateStepIndex = 0
    private var resolutionScaleStepIndex = 0
    private var currentBitrateBps = 8_000_000 // ARCHITECTURE.md §2 mid default

    private val bitrateStepBps = 1_500_000 // unmeasured placeholder
    private val bitrateFloorBps = 2_000_000
    private val bitrateCeilingBps = 12_000_000 // ARCHITECTURE.md §2 ceiling

    // Hysteresis: a ladder step causes a visible hiccup for the framerate/
    // resolution levers (encoder reconfigure) and shouldn't fire faster than
    // the congestion signal itself can meaningfully change - without this, a
    // bursty signal can thrash the ladder up and down every call.
    // Unmeasured placeholder, same honesty caveat as the step sizes above.
    private val ladderStepCooldownMs = 3_000L
    private var lastLadderStepAtMs = 0L

    private fun adjustQualityForRequest(direction: String) {
        if (android.os.SystemClock.elapsedRealtime() - lastLadderStepAtMs < ladderStepCooldownMs) return
        val stepped = when (direction) {
            "down" -> stepQualityDown()
            "up" -> stepQualityUp()
            else -> false
        }
        if (stepped) lastLadderStepAtMs = android.os.SystemClock.elapsedRealtime()
    }

    /** Worsen quality one lever at a time: framerate, then bitrate, then resolution. */
    private fun stepQualityDown(): Boolean = when {
        frameRateStepIndex < frameRateSteps.lastIndex -> {
            frameRateStepIndex++
            reconfigureEncoderForLadder()
            true
        }
        currentBitrateBps > bitrateFloorBps -> {
            currentBitrateBps = (currentBitrateBps - bitrateStepBps).coerceAtLeast(bitrateFloorBps)
            encoder?.setBitrate(currentBitrateBps)
            true
        }
        resolutionScaleStepIndex < resolutionScaleSteps.lastIndex -> {
            resolutionScaleStepIndex++
            reconfigureEncoderForLadder()
            true
        }
        else -> false // already at the floor on every lever
    }

    /** Recover in the reverse order: resolution (dropped last) first, then bitrate, then framerate. */
    private fun stepQualityUp(): Boolean = when {
        resolutionScaleStepIndex > 0 -> {
            resolutionScaleStepIndex--
            reconfigureEncoderForLadder()
            true
        }
        currentBitrateBps < bitrateCeilingBps -> {
            currentBitrateBps = (currentBitrateBps + bitrateStepBps).coerceAtMost(bitrateCeilingBps)
            encoder?.setBitrate(currentBitrateBps)
            true
        }
        frameRateStepIndex > 0 -> {
            frameRateStepIndex--
            reconfigureEncoderForLadder()
            true
        }
        else -> false // already at the ceiling on every lever
    }

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
