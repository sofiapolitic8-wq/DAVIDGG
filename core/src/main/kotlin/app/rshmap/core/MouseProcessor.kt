package app.rshmap.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Converts raw per-frame mouse counts into whole-pixel camera steps:
 * dead-zone -> invert -> acceleration -> sensitivity (X/Y, ADS) -> smoothing -> sub-pixel accumulator.
 * Allocation-free after construction; results are in [outX]/[outY].
 */
class MouseProcessor {
    private var s = MouseSettings()
    private var globalMul = 1f
    private val accX = SubPixelAccumulator()
    private val accY = SubPixelAccumulator()
    private val emaX = EmaFilter()
    private val emaY = EmaFilter()
    private val oneX = OneEuroFilter()
    private val oneY = OneEuroFilter()

    var outX = 0
        private set
    var outY = 0
        private set

    /** True when there is no raw input and the smoothing tail has fully decayed. */
    var isIdle = true
        private set

    fun configure(settings: MouseSettings, globalMultiplier: Float) {
        s = settings
        globalMul = globalMultiplier
        val k = settings.smoothing.coerceIn(0, 100) / 100f
        val alpha = 1f - 0.9f * k
        emaX.alpha = alpha
        emaY.alpha = alpha
        val minCut = 0.8f + 12f * (1f - k)
        for (f in arrayOf(oneX, oneY)) {
            f.minCutoff = minCut
            f.beta = 0.004f
            f.dCutoff = 1f
        }
        reset()
    }

    fun reset() {
        accX.reset(); accY.reset()
        emaX.reset(); emaY.reset()
        oneX.reset(); oneY.reset()
        outX = 0; outY = 0
        isIdle = true
    }

    fun process(rawDx: Float, rawDy: Float, dtSec: Float, ads: Boolean) {
        var dx = rawDx
        var dy = rawDy
        val hasInput = dx != 0f || dy != 0f

        if (s.deadZone > 0f && hasInput) {
            if (sqrt(dx * dx + dy * dy) < s.deadZone) {
                dx = 0f; dy = 0f
            }
        }
        if (s.invertY) dy = -dy
        if (s.accelEnabled && (dx != 0f || dy != 0f)) {
            val speed = sqrt(dx * dx + dy * dy) / (dtSec * 1000f).coerceAtLeast(1f)
            val f = 1f + s.accelGain * speed
            dx *= f; dy *= f
        }
        val adsMul = if (ads) s.adsMultiplier else 1f
        dx *= s.sensX * globalMul * adsMul
        dy *= s.sensY * globalMul * adsMul

        if (s.smoothing > 0) {
            when (s.smoothingMode) {
                SmoothingMode.EMA -> { dx = emaX.filter(dx); dy = emaY.filter(dy) }
                SmoothingMode.ONE_EURO -> { dx = oneX.filter(dx, dtSec); dy = oneY.filter(dy, dtSec) }
                SmoothingMode.OFF -> {}
            }
            if (!hasInput && abs(dx) < 0.05f && abs(dy) < 0.05f) {
                emaX.reset(); emaY.reset(); oneX.reset(); oneY.reset()
                dx = 0f; dy = 0f
            }
        }
        isIdle = !hasInput && dx == 0f && dy == 0f
        outX = accX.add(dx)
        outY = accY.add(dy)
    }
}
