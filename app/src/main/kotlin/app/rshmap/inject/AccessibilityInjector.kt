package app.rshmap.inject

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import android.os.Handler
import android.os.SystemClock
import app.rshmap.input.Stats

/**
 * Backend A: AccessibilityService.dispatchGesture with *continued strokes*.
 *
 * Each virtual finger owns one slot (= its pointer id). While a finger is down we keep dispatching
 * short ~[CHUNK_MS] gestures; every chunk continues the previous stroke (willContinue = true), and the
 * next chunk is dispatched the instant the previous one completes, so the finger never lifts. A finger
 * is ended by a final continuation with willContinue = false.
 *
 * Slot phases: IDLE -> START (new stroke needed) -> ACTIVE (stroke in flight) -> END (up requested)
 * An up->down on the same id while ACTIVE becomes END then START in the following gesture.
 *
 * Android allocates a few small objects per dispatched gesture; that is unavoidable with this API
 * and happens at most ~30x/s. The engine/input path before this class is allocation-free.
 */
class AccessibilityInjector(
    private val svc: AccessibilityService,
    private val handler: Handler,
) : TouchInjector {

    override val name = "Accessibility"
    override var onError: ((String) -> Unit)? = null

    private val phase = IntArray(MAX)
    private val downAgain = BooleanArray(MAX)
    private val upReq = BooleanArray(MAX)
    private val cx = FloatArray(MAX)
    private val cy = FloatArray(MAX)
    private val lx = FloatArray(MAX)
    private val ly = FloatArray(MAX)
    private val stroke = arrayOfNulls<StrokeDescription>(MAX)
    private val path = Path()

    private var inFlight = false
    private var dispatchedAt = 0L
    private var maxX = 1f
    private var maxY = 1f
    private var failures = 0

    private val callback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) = onDone(true)
        override fun onCancelled(gestureDescription: GestureDescription?) = onDone(false)
    }

    override fun start(): Boolean {
        Stats.backend = name
        Stats.chunkMs = CHUNK_MS.toFloat()
        failures = 0
        return true
    }

    override fun setScreen(widthPx: Int, heightPx: Int) {
        maxX = (widthPx - 1).coerceAtLeast(1).toFloat()
        maxY = (heightPx - 1).coerceAtLeast(1).toFloat()
    }

    private fun cl(v: Float, max: Float) = if (v < 0f) 0f else if (v > max) max else v

    override fun down(id: Int, x: Float, y: Float) {
        if (id !in 0 until MAX) return
        cx[id] = cl(x, maxX); cy[id] = cl(y, maxY)
        upReq[id] = false
        when (phase[id]) {
            IDLE -> phase[id] = START
            ACTIVE -> { phase[id] = END; downAgain[id] = true }
            END -> downAgain[id] = true
        }
    }

    override fun move(id: Int, x: Float, y: Float) {
        if (id !in 0 until MAX) return
        cx[id] = cl(x, maxX); cy[id] = cl(y, maxY)
    }

    override fun up(id: Int) {
        if (id !in 0 until MAX) return
        when (phase[id]) {
            START -> upReq[id] = true            // tapped and released before dispatch: short tap stroke
            ACTIVE -> { phase[id] = END; downAgain[id] = false }
            END -> downAgain[id] = false
        }
    }

    override fun flush() {
        if (!inFlight && hasWork()) dispatch()
    }

    private fun hasWork(): Boolean {
        for (i in 0 until MAX) if (phase[i] != IDLE) return true
        return false
    }

    private fun dispatch() {
        val b = GestureDescription.Builder()
        val cap = GestureDescription.getMaxStrokeCount().coerceAtMost(MAX)
        var n = 0
        for (i in 0 until MAX) {
            if (n >= cap) break
            var sd: StrokeDescription? = null
            try {
                when (phase[i]) {
                    START -> {
                        path.reset(); path.moveTo(cx[i], cy[i])
                        lx[i] = cx[i]; ly[i] = cy[i]
                        if (upReq[i]) {
                            sd = StrokeDescription(path, 0L, TAP_MS, false)
                            phase[i] = IDLE; upReq[i] = false; stroke[i] = null
                        } else {
                            sd = StrokeDescription(path, 0L, CHUNK_MS, true)
                            stroke[i] = sd; phase[i] = ACTIVE
                        }
                    }
                    ACTIVE -> {
                        path.reset(); path.moveTo(lx[i], ly[i])
                        if (cx[i] != lx[i] || cy[i] != ly[i]) path.lineTo(cx[i], cy[i])
                        sd = stroke[i]!!.continueStroke(path, 0L, CHUNK_MS, true)
                        stroke[i] = sd; lx[i] = cx[i]; ly[i] = cy[i]
                    }
                    END -> {
                        path.reset(); path.moveTo(lx[i], ly[i])
                        if (cx[i] != lx[i] || cy[i] != ly[i]) path.lineTo(cx[i], cy[i])
                        sd = stroke[i]!!.continueStroke(path, 0L, END_MS, false)
                        stroke[i] = null
                        lx[i] = cx[i]; ly[i] = cy[i]
                        phase[i] = if (downAgain[i]) START else IDLE
                        downAgain[i] = false
                    }
                }
            } catch (e: RuntimeException) {
                // Invalid stroke state: reset this slot rather than crash.
                stroke[i] = null; phase[i] = IDLE; upReq[i] = false; downAgain[i] = false
                sd = null
            }
            if (sd != null) { b.addStroke(sd); n++ }
        }
        if (n == 0) return
        val ok = try {
            inFlight = true
            dispatchedAt = SystemClock.uptimeMillis()
            svc.dispatchGesture(b.build(), callback, handler)
        } catch (e: Exception) {
            false
        }
        if (!ok) {
            inFlight = false
            resetAll()
            if (++failures >= 3) onError?.invoke("dispatchGesture rejected")
        }
    }

    private fun onDone(ok: Boolean) {
        inFlight = false
        val spent = (SystemClock.uptimeMillis() - dispatchedAt).toFloat()
        val prev = Stats.injectOverheadMs
        Stats.injectOverheadMs = if (prev == 0f) spent else prev * 0.9f + spent * 0.1f
        if (ok) {
            failures = 0
        } else {
            // Framework dropped the continuing strokes: restart wanted fingers cleanly.
            for (i in 0 until MAX) {
                if (phase[i] == ACTIVE) { phase[i] = START; stroke[i] = null }
                else if (phase[i] == END) { phase[i] = if (downAgain[i]) START else IDLE; downAgain[i] = false; stroke[i] = null }
            }
            if (++failures >= 20) { onError?.invoke("gestures keep getting cancelled"); return }
        }
        if (hasWork()) dispatch()
    }

    private fun resetAll() {
        for (i in 0 until MAX) { phase[i] = IDLE; stroke[i] = null; upReq[i] = false; downAgain[i] = false }
    }

    override fun releaseAll() {
        for (i in 0 until MAX) {
            when (phase[i]) {
                START -> { phase[i] = IDLE; upReq[i] = false }
                ACTIVE -> { phase[i] = END; downAgain[i] = false }
                END -> downAgain[i] = false
            }
        }
        flush()
    }

    override fun stop() {
        releaseAll()
    }

    private companion object {
        const val MAX = 10
        const val CHUNK_MS = 32L
        const val END_MS = 8L
        const val TAP_MS = 40L
        const val IDLE = 0
        const val START = 1
        const val ACTIVE = 2
        const val END = 3
    }
}
