package app.rshmap.core

/**
 * Time-driven macro player. Drive it with [update] every frame; it never allocates after construction.
 * With [loop] the sequence repeats while the trigger key is held; releasing finishes the current cycle.
 */
class MacroRunner(private val steps: List<MacroStep>, private val loop: Boolean) {
    var running = false
        private set
    private var idx = 0
    private var until = 0L
    private var touchId = -1
    private var sw = 1f
    private var sh = 1f
    private var keyHeld = false

    fun start(now: Long, screenW: Float, screenH: Float, sink: TouchSink, pool: PointerPool) {
        if (running || steps.isEmpty()) return
        sw = screenW; sh = screenH
        running = true
        keyHeld = true
        idx = 0
        enter(now, sink, pool)
    }

    /** Trigger released: a looping macro finishes its current cycle and stops. */
    fun release() {
        keyHeld = false
    }

    fun stop(sink: TouchSink, pool: PointerPool) {
        leave(sink, pool)
        running = false
        keyHeld = false
    }

    fun update(now: Long, sink: TouchSink, pool: PointerPool) {
        var guard = 0
        while (running && now >= until && guard++ < 256) {
            val nominal = until
            leave(sink, pool)
            idx++
            if (idx >= steps.size) {
                if (loop && keyHeld) idx = 0 else { running = false; return }
            }
            enter(if (now - nominal > 100L) now else nominal, sink, pool)
        }
    }

    private fun enter(t: Long, sink: TouchSink, pool: PointerPool) {
        val s = steps[idx]
        until = t + s.ms
        if (s.type == StepType.TAP) {
            val id = pool.acquire()
            if (id >= 0) {
                touchId = id
                sink.down(id, s.x * sw, s.y * sh)
            }
        }
    }

    private fun leave(sink: TouchSink, pool: PointerPool) {
        if (touchId >= 0) {
            sink.up(touchId)
            pool.release(touchId)
            touchId = -1
        }
    }
}
