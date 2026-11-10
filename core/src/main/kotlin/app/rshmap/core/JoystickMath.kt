package app.rshmap.core

import kotlin.math.sqrt

object JoystickMath {
    private const val INV_SQRT2 = 0.70710678f

    /**
     * 8-direction mapping. Writes a vector of length <= 1 into [out] (x right+, y down+);
     * diagonals are normalised so the stick radius is constant. Opposite keys cancel.
     */
    fun axis(up: Boolean, left: Boolean, down: Boolean, right: Boolean, out: FloatArray) {
        val x = (if (right) 1 else 0) - (if (left) 1 else 0)
        val y = (if (down) 1 else 0) - (if (up) 1 else 0)
        if (x != 0 && y != 0) {
            out[0] = x * INV_SQRT2
            out[1] = y * INV_SQRT2
        } else {
            out[0] = x.toFloat()
            out[1] = y.toFloat()
        }
    }
}

/** Smooth virtual-stick position: moves toward the target at 1.0 deflection per [rampMs]. */
class JoystickState(var rampMs: Int) {
    var x = 0f
        private set
    var y = 0f
        private set

    fun update(tx: Float, ty: Float, dtMs: Float) {
        if (rampMs <= 0) { x = tx; y = ty; return }
        val dx = tx - x
        val dy = ty - y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= 1e-6f) { x = tx; y = ty; return }
        val step = dtMs / rampMs
        if (dist <= step) {
            x = tx; y = ty
        } else {
            x += dx / dist * step
            y += dy / dist * step
        }
    }

    fun reset() { x = 0f; y = 0f }
}
