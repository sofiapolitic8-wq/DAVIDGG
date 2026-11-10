package app.rshmap.core

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class JoystickMathTest {
    private val out = FloatArray(2)

    @Test fun cardinalDirections() {
        JoystickMath.axis(true, false, false, false, out)
        assertEquals(0f, out[0], 1e-6f); assertEquals(-1f, out[1], 1e-6f)
        JoystickMath.axis(false, false, true, false, out)
        assertEquals(0f, out[0], 1e-6f); assertEquals(1f, out[1], 1e-6f)
        JoystickMath.axis(false, true, false, false, out)
        assertEquals(-1f, out[0], 1e-6f); assertEquals(0f, out[1], 1e-6f)
        JoystickMath.axis(false, false, false, true, out)
        assertEquals(1f, out[0], 1e-6f); assertEquals(0f, out[1], 1e-6f)
    }

    @Test fun diagonalsAreNormalised() {
        JoystickMath.axis(true, false, false, true, out)
        val len = sqrt(out[0] * out[0] + out[1] * out[1])
        assertEquals(1f, len, 1e-5f)
        assertEquals(1f, out[0] / -out[1], 1e-5f)
    }

    @Test fun oppositeKeysCancel() {
        JoystickMath.axis(true, false, true, false, out)
        assertEquals(0f, out[0], 0f); assertEquals(0f, out[1], 0f)
        JoystickMath.axis(true, true, true, true, out)
        assertEquals(0f, out[0], 0f); assertEquals(0f, out[1], 0f)
    }

    @Test fun rampUpReachesTargetInRampTime() {
        val js = JoystickState(100)
        js.update(1f, 0f, 50f)
        assertEquals(0.5f, js.x, 1e-5f)
        js.update(1f, 0f, 50f)
        assertEquals(1f, js.x, 1e-5f)
        js.update(1f, 0f, 50f)
        assertEquals(1f, js.x, 1e-5f)
    }

    @Test fun rampDownReturnsToZero() {
        val js = JoystickState(100)
        js.update(1f, 0f, 200f)
        js.update(0f, 0f, 25f)
        assertEquals(0.75f, js.x, 1e-5f)
        js.update(0f, 0f, 100f)
        assertEquals(0f, js.x, 0f)
    }

    @Test fun zeroRampIsInstant() {
        val js = JoystickState(0)
        js.update(0.3f, -0.7f, 1f)
        assertEquals(0.3f, js.x, 0f); assertEquals(-0.7f, js.y, 0f)
    }
}
