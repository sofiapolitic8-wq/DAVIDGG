package app.rshmap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraAreaTest {
    // area 0..1000 x 0..500, margin 10% -> safe x 100..900, y 50..450
    private fun area() = CameraArea(0f, 0f, 1000f, 500f, 0.10f)

    @Test fun startsAtCentre() {
        val a = area()
        assertEquals(500f, a.x, 0f); assertEquals(250f, a.y, 0f)
    }

    @Test fun movesInsideSafeZone() {
        val a = area()
        assertTrue(a.move(100f, -50f))
        assertEquals(600f, a.x, 0f); assertEquals(200f, a.y, 0f)
        assertEquals(0f, a.leftoverX, 0f)
    }

    @Test fun hittingTheEdgeClampsAndReportsLeftover() {
        val a = area()
        assertFalse(a.move(500f, 0f))          // wants x=1000, safe max is 900
        assertEquals(900f, a.x, 0f)
        assertEquals(100f, a.leftoverX, 0f)
        assertEquals(0f, a.leftoverY, 0f)
    }

    @Test fun recenterResetsPositionAndLeftover() {
        val a = area()
        a.move(800f, 300f)
        a.recenter()
        assertEquals(500f, a.x, 0f); assertEquals(250f, a.y, 0f)
        assertEquals(0f, a.leftoverX, 0f)
    }

    @Test fun neverLeavesTheArea() {
        val a = area()
        var x = 0
        repeat(1000) {
            if (!a.move(37f, 11f)) a.recenter()
            x++
            assertTrue(a.x in 0f..1000f && a.y in 0f..500f)
        }
        assertEquals(1000, x)
    }
}
