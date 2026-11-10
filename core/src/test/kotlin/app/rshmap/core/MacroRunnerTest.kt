package app.rshmap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroRunnerTest {
    private val steps = listOf(
        MacroStep(StepType.TAP, 0.5f, 0.5f, 30),
        MacroStep(StepType.WAIT, ms = 70),
        MacroStep(StepType.TAP, 0.2f, 0.4f, 30),
    )

    @Test fun singleRunFollowsTiming() {
        val sink = RecordingSink(); val pool = PointerPool()
        val m = MacroRunner(steps, loop = false)
        m.start(1000, 1000f, 500f, sink, pool)
        assertEquals(listOf("D0"), sink.events)
        m.update(1029, sink, pool)
        assertEquals(listOf("D0"), sink.events)             // still pressed
        m.update(1030, sink, pool)
        assertEquals(listOf("D0", "U0"), sink.events)       // released after 30 ms
        m.update(1099, sink, pool)
        assertEquals(2, sink.events.size)                   // waiting
        m.update(1100, sink, pool)
        assertEquals(listOf("D0", "U0", "D0"), sink.events) // second tap after 70 ms wait
        assertEquals(200f, sink.pos(0)!!.first, 0f)
        assertEquals(200f, sink.pos(0)!!.second, 0f)
        m.update(1130, sink, pool)
        assertFalse(m.running)
        assertEquals(0, sink.activeCount)
        assertEquals(0, pool.inUse)
    }

    @Test fun loopsWhileHeldAndFinishesCycleOnRelease() {
        val sink = RecordingSink(); val pool = PointerPool()
        val m = MacroRunner(steps, loop = true)
        m.start(0, 1000f, 500f, sink, pool)
        m.update(130, sink, pool)           // full cycle done -> loops
        assertTrue(m.running)
        val downs = sink.events.count { it.startsWith("D") }
        assertEquals(3, downs)              // tap, tap, tap (restart)
        m.release()
        m.update(260, sink, pool)
        assertFalse(m.running)
        assertEquals(0, sink.activeCount)
    }

    @Test fun stopReleasesEverything() {
        val sink = RecordingSink(); val pool = PointerPool()
        val m = MacroRunner(steps, loop = true)
        m.start(0, 1000f, 500f, sink, pool)
        m.stop(sink, pool)
        assertFalse(m.running)
        assertEquals(0, sink.activeCount)
        assertEquals(0, pool.inUse)
    }

    @Test fun emptyMacroDoesNothing() {
        val sink = RecordingSink(); val pool = PointerPool()
        val m = MacroRunner(emptyList(), loop = true)
        m.start(0, 1f, 1f, sink, pool)
        assertFalse(m.running)
        assertTrue(sink.events.isEmpty())
    }
}
