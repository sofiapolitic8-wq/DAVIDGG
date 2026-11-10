package app.rshmap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FiltersTest {
    @Test fun emaConvergesToConstant() {
        val f = EmaFilter(); f.alpha = 0.3f
        var y = 0f
        repeat(60) { y = f.filter(10f) }
        assertEquals(10f, y, 0.01f)
    }

    @Test fun emaAlphaOneIsPassThrough() {
        val f = EmaFilter(); f.alpha = 1f
        assertEquals(4.2f, f.filter(4.2f), 1e-6f)
        assertEquals(-1f, f.filter(-1f), 1e-6f)
    }

    @Test fun oneEuroConvergesAndReducesJitter() {
        val f = OneEuroFilter(minCutoff = 1f, beta = 0f, dCutoff = 1f)
        var maxDev = 0f
        for (i in 0 until 300) {
            val noisy = 5f + (if (i % 2 == 0) 1f else -1f)
            val y = f.filter(noisy, 1f / 60f)
            if (i > 100) maxDev = maxOf(maxDev, abs(y - 5f))
        }
        assertTrue("jitter should be damped, was $maxDev", maxDev < 0.5f)
    }

    @Test fun oneEuroResetClearsState() {
        val f = OneEuroFilter(1f, 0f, 1f)
        repeat(20) { f.filter(8f, 0.016f) }
        f.reset()
        val y = f.filter(0f, 0.016f)
        assertEquals(0f, y, 1e-6f)
    }

    @Test fun rawModeConservesTotalMovement() {
        val p = MouseProcessor()
        p.configure(MouseSettings(smoothing = 0), 1f)
        var total = 0
        var input = 0f
        for (i in 1..200) {
            val v = ((i * 37) % 11 - 5) * 0.37f
            p.process(v, -v, 0.016f, false)
            total += p.outX
            input += v
        }
        assertEquals(input, total.toFloat(), 1.01f)
    }

    @Test fun emaSmoothingKeepsMovementAfterTailDecays() {
        val p = MouseProcessor()
        p.configure(MouseSettings(smoothing = 50, smoothingMode = SmoothingMode.EMA), 1f)
        var total = 0
        repeat(100) { p.process(1f, 0f, 0.016f, false); total += p.outX }
        repeat(100) { p.process(0f, 0f, 0.016f, false); total += p.outX }
        assertTrue("total=$total", total in 98..100)
        assertTrue(p.isIdle)
    }

    @Test fun adsMultiplierAndInvertAndSens() {
        val p = MouseProcessor()
        p.configure(MouseSettings(sensX = 2f, sensY = 1f, adsMultiplier = 0.5f, invertY = true), 1f)
        p.process(10f, 10f, 0.016f, false)
        assertEquals(20, p.outX); assertEquals(-10, p.outY)
        p.process(10f, 10f, 0.016f, true)
        assertEquals(10, p.outX); assertEquals(-5, p.outY)
    }

    @Test fun deadZoneSuppressesTinyMoves() {
        val p = MouseProcessor()
        p.configure(MouseSettings(deadZone = 3f), 1f)
        p.process(1f, 1f, 0.016f, false)
        assertEquals(0, p.outX); assertEquals(0, p.outY)
        p.process(5f, 0f, 0.016f, false)
        assertEquals(5, p.outX)
    }

    @Test fun accelerationOffIsOneToOne() {
        val p = MouseProcessor()
        p.configure(MouseSettings(accelEnabled = false), 1f)
        p.process(7f, 0f, 0.016f, false)
        assertEquals(7, p.outX)
    }

    @Test fun accelerationBoostsFastMoves() {
        val p = MouseProcessor()
        p.configure(MouseSettings(accelEnabled = true, accelGain = 0.5f), 1f)
        p.process(32f, 0f, 0.016f, false)   // speed = 2 counts/ms -> factor 2
        assertTrue("outX=${p.outX}", p.outX in 63..65)
    }
}
