package app.rshmap.core

/** Turns fractional movement into whole-pixel steps without ever losing the remainder. */
class SubPixelAccumulator {
    private var rem = 0f

    /** Adds [v] and returns the whole number of pixels (truncated toward zero) that can be emitted now. */
    fun add(v: Float): Int {
        val t = v + rem
        val i = t.toInt()
        rem = t - i
        return i
    }

    val remainder: Float get() = rem

    fun reset() {
        rem = 0f
    }
}
