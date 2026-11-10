package app.rshmap.core

import kotlin.math.PI
import kotlin.math.abs

/** Exponential moving average. alpha 1 = no smoothing, small alpha = heavy smoothing. */
class EmaFilter {
    var alpha = 1f
    private var y = 0f

    fun filter(x: Float): Float {
        y += alpha * (x - y)
        return y
    }

    fun reset() {
        y = 0f
    }
}

/** 1-Euro filter (Casiez et al.): low lag on fast motion, strong smoothing on slow jitter. */
class OneEuroFilter(var minCutoff: Float = 1f, var beta: Float = 0f, var dCutoff: Float = 1f) {
    private var prevX = 0f
    private var prevDx = 0f

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    /** @param dt seconds since the previous sample (must be > 0). */
    fun filter(x: Float, dt: Float): Float {
        val d = if (dt <= 1e-5f) 1e-5f else dt
        val dx = (x - prevX) / d
        val dxHat = prevDx + alpha(dCutoff, d) * (dx - prevDx)
        val cutoff = minCutoff + beta * abs(dxHat)
        val xHat = prevX + alpha(cutoff, d) * (x - prevX)
        prevX = xHat
        prevDx = dxHat
        return xHat
    }

    fun reset() {
        prevX = 0f
        prevDx = 0f
    }
}
