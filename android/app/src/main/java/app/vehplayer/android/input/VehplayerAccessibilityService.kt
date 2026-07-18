package app.vehplayer.android.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import app.vehplayer.android.protocol.InputProtocol

/**
 * Free-tier default input injection (Foundation §6b item 2): AccessibilityService
 * `dispatchGesture` on the mirrored main display. One settings toggle, no
 * Shizuku, no developer options. Power Mode (Shizuku, virtual-display
 * targeting) is a separate, not-yet-implemented path, see the TODO at the
 * bottom of this file.
 *
 * Coordinates arrive normalized [0,1] to the video frame (ARCHITECTURE.md
 * §4); this class denormalizes against the real display size before handing
 * them to dispatchGesture, which wants real pixel coordinates.
 *
 * Companion-held singleton reference so LocalMediaServer's input callback
 * (owned by CaptureService, a different Android component) can reach this
 * service without a bound-service IPC dance for what's a same-process call.
 */
class VehplayerAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: VehplayerAccessibilityService? = null
            private set

        // continueStroke's startTime is cumulative from the stroke's own
        // start (not wall-clock, not the enclosing gesture), so each segment
        // duration is clamped into a sane range: long enough that jittery
        // network delivery doesn't produce a near-zero duration (rejected by
        // dispatchGesture), short enough that a slow finger doesn't stall
        // the continuation past when the next MOVE actually arrives.
        private const val MIN_SEGMENT_DURATION_MS = 8L
        private const val MAX_SEGMENT_DURATION_MS = 100L
        private const val INITIAL_SEGMENT_DURATION_MS = 20L
    }

    private var screenWidth = 0
    private var screenHeight = 0

    // Tracks in-flight strokes per pointer id via the StrokeDescription
    // continuation API (API 26+): each MOVE extends the previous stroke with
    // continueStroke() instead of buffering points for a single gesture
    // fired on UP, so drags are dispatched live rather than "committed all
    // at once".
    private class StrokeState(
        var stroke: GestureDescription.StrokeDescription,
        var elapsedMs: Long,
        var lastX: Float,
        var lastY: Float,
        var lastEventUptimeMs: Long,
    )

    private val activeStrokes = HashMap<Int, StrokeState>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This service doesn't need to react to accessibility events, only to
        // emit gestures; the empty override is required by the base class.
    }

    override fun onInterrupt() {}

    fun handleInputEvent(event: InputProtocol.InputEvent) {
        val x = event.xNorm * screenWidth
        val y = event.yNorm * screenHeight

        when (event.type) {
            InputProtocol.EventType.DOWN -> {
                val path = Path().apply { moveTo(x, y) }
                val stroke = GestureDescription.StrokeDescription(
                    path, 0L, INITIAL_SEGMENT_DURATION_MS, true,
                )
                activeStrokes[event.pointerId] = StrokeState(
                    stroke = stroke,
                    elapsedMs = INITIAL_SEGMENT_DURATION_MS,
                    lastX = x,
                    lastY = y,
                    lastEventUptimeMs = SystemClock.uptimeMillis(),
                )
                dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
            }
            InputProtocol.EventType.MOVE -> {
                val state = activeStrokes[event.pointerId] ?: return
                extendStroke(state, x, y, willContinue = true)
            }
            InputProtocol.EventType.UP -> {
                val state = activeStrokes.remove(event.pointerId) ?: return
                extendStroke(state, x, y, willContinue = false)
            }
            InputProtocol.EventType.SCROLL -> {
                // TODO(claude-code): AccessibilityService has no direct
                // "scroll wheel" gesture primitive; the usual trick is a
                // short synthetic swipe at (x,y) sized/directioned by
                // scrollDelta. Not implemented, media apps' touch scroll
                // works via the DOWN/MOVE/UP path above regardless.
            }
        }
    }

    /**
     * Extends an in-flight stroke to (x, y) via continueStroke() and
     * dispatches it immediately, so drags render live instead of being
     * replayed all at once on UP. Segment duration is derived from real
     * wall-clock spacing between events (clamped), since the wire protocol
     * carries no per-event timestamp of its own.
     */
    private fun extendStroke(state: StrokeState, x: Float, y: Float, willContinue: Boolean) {
        val now = SystemClock.uptimeMillis()
        val segmentDuration = (now - state.lastEventUptimeMs)
            .coerceIn(MIN_SEGMENT_DURATION_MS, MAX_SEGMENT_DURATION_MS)

        val path = Path().apply {
            moveTo(state.lastX, state.lastY)
            lineTo(x, y)
        }
        val continued = state.stroke.continueStroke(path, state.elapsedMs, segmentDuration, willContinue)

        state.stroke = continued
        state.elapsedMs += segmentDuration
        state.lastX = x
        state.lastY = y
        state.lastEventUptimeMs = now

        dispatchGesture(GestureDescription.Builder().addStroke(continued).build(), null, null)
    }
}
