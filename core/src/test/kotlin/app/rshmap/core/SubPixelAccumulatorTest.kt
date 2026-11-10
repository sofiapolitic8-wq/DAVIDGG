package app.rshmap.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SubPixelAccumulatorTest {
    @Test fun smallMovesAreNeverLost() {
        val a = SubPixelAccumulator()
        var total = 0
        repeat(10) { total += a.add(0.3f) }
        assertEquals(3, total)
    }

    @Test fun negativeMovesTruncateTowardZero() {
        val a = SubPixelAccumulator()
        var total = 0
        repeat(10) { total += a.add(-0.3f) }
        assertEquals(-3, total)
    }

    @Test fun sumPlusRemainderEqualsInput() {
        val a = SubPixelAccumulator()
        var total = 0
        var input = 0f
        val vals = floatArrayOf(0.7f, -1.9f, 2.4f, 0.05f, -0.6f, 3.3f)
        for (v in vals) { total += a.add(v); input += v }
        assertEquals(input, total + a.remainder, 1e-4f)
    }

    @Test fun resetClearsRemainder() {
        val a = SubPixelAccumulator()
        a.add(0.6f)
        a.reset()
        assertEquals(0f, a.remainder, 0f)
    }
}
