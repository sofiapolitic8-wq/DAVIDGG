package app.rshmap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingEngineTest {
    private fun engine(sink: TouchSink, vararg els: Element, mouse: MouseSettings = MouseSettings()): MappingEngine {
        val e = MappingEngine(sink)
        e.setScreen(1000, 500)
        e.setProfile(Profile("t", elements = els.toList(), mouse = mouse))
        e.setActive(true)
        return e
    }

    private val stick = Element(
        "j", ElementType.JOYSTICK, x = 0.2f, y = 0.7f, w = 0.2f,
        keys = listOf(Keys.W, Keys.A, Keys.S, Keys.D), rampMs = 0,
    )

    @Test fun joystickPressesLiftsAndKeepsRadius() {
        val s = RecordingSink(); val e = engine(s, stick)
        e.onKey(Keys.W, true, 0); e.onFrame(16)
        assertEquals(listOf("D0", "M0"), s.events)
        val (x, y) = s.pos(0)!!
        assertEquals(200f, x, 0.01f)            // centre x = 0.2*1000
        assertEquals(350f - 100f, y, 0.01f)     // up by radius (0.2*1000/2)
        e.onKey(Keys.W, false, 20); e.onFrame(32)
        assertEquals(0, s.activeCount)
    }

    @Test fun joystickModifierScalesDeflection() {
        val s = RecordingSink()
        val e = engine(s, stick.copy(modifierKey = Keys.SHIFT_LEFT, modifierScale = 0.5f))
        e.onKey(Keys.SHIFT_LEFT, true, 0)
        e.onKey(Keys.D, true, 0); e.onFrame(16)
        assertEquals(250f, s.pos(0)!!.first, 0.01f)   // 200 + 0.5*100
    }

    @Test fun joystickRampsSmoothly() {
        val s = RecordingSink(); val e = engine(s, stick.copy(rampMs = 100))
        e.onKey(Keys.D, true, 0)
        e.onFrame(16); val x1 = s.pos(0)!!.first
        e.onFrame(32); val x2 = s.pos(0)!!.first
        assertTrue(x1 > 200f && x2 > x1 && x2 < 300f)
    }

    @Test fun tapHoldsForDuration() {
        val s = RecordingSink()
        val e = engine(s, Element("t", ElementType.TAP, x = 0.5f, y = 0.5f, keys = listOf(Keys.SPACE), durationMs = 50))
        e.onKey(Keys.SPACE, true, 0); e.onFrame(0)
        e.onKey(Keys.SPACE, false, 10); e.onFrame(30)
        assertEquals(1, s.activeCount)
        e.onFrame(60)
        assertEquals(0, s.activeCount)
    }

    @Test fun repeatedKeyDownIsIgnored() {
        val s = RecordingSink()
        val e = engine(s, Element("t", ElementType.HOLD, x = 0.5f, y = 0.5f, keys = listOf(Keys.F)))
        e.onKey(Keys.F, true, 0); e.onKey(Keys.F, true, 5); e.onKey(Keys.F, true, 10)
        assertEquals(listOf("D0"), s.events)
        e.onKey(Keys.F, false, 20)
        assertEquals(listOf("D0", "U0"), s.events)
    }

    @Test fun toggleFlips() {
        val s = RecordingSink()
        val e = engine(s, Element("t", ElementType.TOGGLE, x = 0.5f, y = 0.5f, keys = listOf(Keys.C)))
        e.onKey(Keys.C, true, 0); e.onKey(Keys.C, false, 1)
        assertEquals(1, s.activeCount)
        e.onKey(Keys.C, true, 2)
        assertEquals(0, s.activeCount)
    }

    @Test fun fireAutoBurstPulses() {
        val s = RecordingSink()
        val e = engine(s, Element("f", ElementType.FIRE, x = 0.9f, y = 0.5f, keys = listOf(Keys.MOUSE_LEFT), autoBurst = true, burstMs = 100))
        e.onKey(Keys.MOUSE_LEFT, true, 0)
        e.onFrame(0); e.onFrame(50)       // down phase is min(40, 50) = 40 ms -> released at 40
        assertEquals(0, s.activeCount)
        e.onFrame(100)                    // next cycle starts at 100
        assertEquals(1, s.activeCount)
        e.onKey(Keys.MOUSE_LEFT, false, 105)
        assertEquals(0, s.activeCount)
    }

    @Test fun mouseLookUsesOwnPointerAndMoves() {
        val s = RecordingSink()
        val look = Element("l", ElementType.MOUSE_LOOK, x = 0.75f, y = 0.4f, w = 0.5f, h = 0.8f)
        val e = engine(s, stick, look)
        e.onKey(Keys.W, true, 0)
        e.onMouseDelta(10f, 5f); e.onFrame(16)
        assertEquals(setOf("D0", "M0", "D1", "M1"), s.events.toSet())
        assertNotNull(s.pos(1))
        // The camera is stepped before other elements, so it owns pointer 0; the stick gets pointer 1.
        assertEquals(750f + 10f, s.pos(0)!!.first, 0.01f)
        assertEquals(200f + 5f, s.pos(0)!!.second, 0.01f)
        assertNotNull(s.pos(1))
    }

    @Test fun mouseLookRecentersInsteadOfRunningOut() {
        val s = RecordingSink()
        val look = Element("l", ElementType.MOUSE_LOOK, x = 0.75f, y = 0.4f, w = 0.5f, h = 0.8f)
        val e = engine(s, look)
        var lifts = 0
        var t = 0L
        repeat(60) {
            e.onMouseDelta(40f, 0f); t += 16; e.onFrame(t)
            s.pos(0)?.let { assertTrue("x=${it.first}", it.first in 500f..1000f) }
        }
        lifts = s.events.count { it == "U0" }
        assertTrue("should have re-centred at least once, lifts=$lifts", lifts >= 1)
    }

    @Test fun cameraTouchReleasedWhenIdle() {
        val s = RecordingSink()
        val e = engine(s, Element("l", ElementType.MOUSE_LOOK, x = 0.75f, y = 0.4f, w = 0.5f, h = 0.8f))
        e.onMouseDelta(5f, 0f); e.onFrame(16)
        assertEquals(1, s.activeCount)
        var t = 16L
        repeat(30) { t += 16; e.onFrame(t) }
        assertEquals(0, s.activeCount)
    }

    @Test fun scopeSwitchesToAdsSensitivity() {
        val s = RecordingSink()
        val scope = Element("s", ElementType.SCOPE, x = 0.8f, y = 0.4f, keys = listOf(Keys.MOUSE_RIGHT))
        val look = Element("l", ElementType.MOUSE_LOOK, x = 0.75f, y = 0.4f, w = 0.5f, h = 0.8f)
        val e = engine(s, scope, look, mouse = MouseSettings(adsMultiplier = 0.5f))
        e.onMouseDelta(20f, 0f); e.onFrame(16)
        val base = s.pos(0)!!.first - 750f
        assertEquals(20f, base, 0.01f)
        e.onKey(Keys.MOUSE_RIGHT, true, 20)
        val before = s.pos(0)!!.first
        e.onMouseDelta(20f, 0f); e.onFrame(32)
        assertEquals(10f, s.pos(0)!!.first - before, 0.01f)
    }

    @Test fun releaseAllLeavesNothingDown() {
        val s = RecordingSink()
        val look = Element("l", ElementType.MOUSE_LOOK, x = 0.75f, y = 0.4f, w = 0.5f, h = 0.8f)
        val hold = Element("h", ElementType.HOLD, x = 0.5f, y = 0.5f, keys = listOf(Keys.F))
        val macro = Element("m", ElementType.MACRO, x = 0.5f, y = 0.5f, keys = listOf(Keys.G), loop = true, steps = listOf(MacroStep(StepType.TAP, 0.3f, 0.3f, 500)))
        val e = engine(s, stick, look, hold, macro)
        e.onKey(Keys.W, true, 0); e.onKey(Keys.F, true, 0); e.onKey(Keys.G, true, 0)
        e.onMouseDelta(3f, 3f); e.onFrame(16)
        assertTrue(s.activeCount >= 3)
        e.releaseAll()
        assertEquals(0, s.activeCount)
        assertEquals(0, e.activeTouches)
        e.setActive(false)
        assertEquals(0, s.activeCount)
    }

    @Test fun swipeMovesFromAToBThenLifts() {
        val s = RecordingSink()
        val sw = Element("s", ElementType.SWIPE, x = 0.2f, y = 0.5f, x2 = 0.8f, y2 = 0.5f, keys = listOf(Keys.X), durationMs = 100)
        val e = engine(s, sw)
        e.onKey(Keys.X, true, 0)
        e.onFrame(0); e.onFrame(50)
        assertEquals(500f, s.pos(0)!!.first, 0.01f)
        e.onFrame(100)
        assertEquals(0, s.activeCount)
    }

    @Test fun wheelScrollTapsTarget() {
        val s = RecordingSink()
        val e = engine(s, Element("w", ElementType.SCROLL, x = 0.5f, y = 0.9f, keys = listOf(Keys.WHEEL_UP), durationMs = 30))
        e.onKey(Keys.WHEEL_UP, true, 0); e.onKey(Keys.WHEEL_UP, false, 0)
        assertEquals(1, s.activeCount)
        e.onFrame(40)
        assertEquals(0, s.activeCount)
    }

    @Test fun inactiveEngineIgnoresKeys() {
        val s = RecordingSink(); val e = engine(s, Element("t", ElementType.HOLD, x = 0.5f, y = 0.5f, keys = listOf(Keys.F)))
        e.setActive(false)
        e.onKey(Keys.F, true, 0)
        assertTrue(s.events.isEmpty())
    }
}
