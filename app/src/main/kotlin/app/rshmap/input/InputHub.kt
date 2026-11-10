package app.rshmap.input

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import app.rshmap.core.MappingEngine
import app.rshmap.core.TouchSink
import app.rshmap.inject.TouchInjector
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bridges the UI thread (where Android delivers keyboard/mouse events) to the dedicated input thread.
 *
 * Producer (main thread) writes primitives into a fixed ring buffer - no objects, no boxing.
 * Consumer (input thread) drains it into [MappingEngine]. A Choreographer callback on the same thread
 * drives [MappingEngine.onFrame] at the display refresh rate so mouse deltas are batched per frame.
 *
 * Everything that touches [engine] or [injector] runs on [handler]'s thread.
 */
class InputHub {
    private val thread = HandlerThread("rshmap-input", Process.THREAD_PRIORITY_URGENT_DISPLAY).also { it.start() }
    val handler = Handler(thread.looper)

    /** Only touched on the input thread. */
    var injector: TouchInjector? = null

    /** Optional visual trace (test mode). Only touched on the input thread. */
    var trace: TouchSink? = null

    private val sink = object : TouchSink {
        override fun down(id: Int, x: Float, y: Float) { injector?.down(id, x, y); trace?.down(id, x, y) }
        override fun move(id: Int, x: Float, y: Float) { injector?.move(id, x, y); trace?.move(id, x, y) }
        override fun up(id: Int) { injector?.up(id); trace?.up(id) }
    }

    val engine = MappingEngine(sink)

    // ---- lock-free single-producer / single-consumer ring ----
    private val types = IntArray(CAP)
    private val codes = IntArray(CAP)
    private val fa = FloatArray(CAP)
    private val fb = FloatArray(CAP)
    private val head = AtomicInteger(0)
    @Volatile private var tail = 0
    private val scheduled = AtomicBoolean(false)

    private val drainRunnable = Runnable { drain() }

    private var choreographer: Choreographer? = null
    @Volatile private var running = false
    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos -> onFrame(frameTimeNanos) }

    // ---------------------------------------------------------------- producer side (main thread)

    fun key(code: Int, down: Boolean) = push(TYPE_KEY, code, if (down) 1f else 0f, 0f)

    fun mouse(dx: Float, dy: Float) = push(TYPE_MOUSE, 0, dx, dy)

    private fun push(type: Int, code: Int, a: Float, b: Float) {
        val h = head.get()
        if (h - tail >= CAP) return // overflow: drop (never happens at sane rates)
        val s = h and MASK
        types[s] = type
        codes[s] = code
        fa[s] = a
        fb[s] = b
        head.set(h + 1)
        if (scheduled.compareAndSet(false, true)) handler.post(drainRunnable)
    }

    // ---------------------------------------------------------------- consumer side (input thread)

    private fun drain() {
        scheduled.set(false)
        var t = tail
        val h = head.get()
        if (t == h) return
        val now = SystemClock.uptimeMillis()
        while (t != h) {
            val s = t and MASK
            if (types[s] == TYPE_KEY) engine.onKey(codes[s], fa[s] > 0.5f, now) else engine.onMouseDelta(fa[s], fb[s])
            t++
        }
        tail = t
        injector?.flush()
    }

    private fun onFrame(frameTimeNanos: Long) {
        if (!running) return
        engine.onFrame(frameTimeNanos / 1_000_000L)
        injector?.flush()
        Stats.activeTouches = engine.activeTouches
        choreographer?.postFrameCallback(frameCallback)
    }

    fun startFrames() {
        handler.post {
            if (running) return@post
            running = true
            val c = choreographer ?: Choreographer.getInstance().also { choreographer = it }
            c.postFrameCallback(frameCallback)
        }
    }

    fun stopFrames() {
        handler.post {
            running = false
            choreographer?.removeFrameCallback(frameCallback)
        }
    }

    fun post(r: Runnable) {
        handler.post(r)
    }

    /** Releases everything and stops the thread after queued work has finished. */
    fun shutdown() {
        handler.post {
            running = false
            choreographer?.removeFrameCallback(frameCallback)
            engine.setActive(false)
            injector?.releaseAll()
        }
        handler.postDelayed({ thread.quitSafely() }, 400)
    }

    private companion object {
        const val CAP = 1024
        const val MASK = CAP - 1
        const val TYPE_KEY = 1
        const val TYPE_MOUSE = 2
    }
}
