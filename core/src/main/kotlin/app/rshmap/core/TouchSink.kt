package app.rshmap.core

/** Receives virtual touches in screen pixels. Pointer ids are stable for the life of a touch. */
interface TouchSink {
    fun down(id: Int, x: Float, y: Float)
    fun move(id: Int, x: Float, y: Float)
    fun up(id: Int)
}

/** Hands out the lowest free pointer id so concurrent virtual fingers never collide. */
class PointerPool(private val max: Int = 10) {
    private val used = BooleanArray(max)

    fun acquire(): Int {
        for (i in 0 until max) if (!used[i]) { used[i] = true; return i }
        return -1
    }

    fun release(id: Int) {
        if (id in 0 until max) used[id] = false
    }

    fun reset() = java.util.Arrays.fill(used, false)

    val inUse: Int get() { var n = 0; for (b in used) if (b) n++; return n }
}
