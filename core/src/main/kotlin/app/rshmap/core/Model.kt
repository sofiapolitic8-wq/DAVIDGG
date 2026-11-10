package app.rshmap.core

import kotlinx.serialization.Serializable

@Serializable
enum class ElementType { TAP, HOLD, TOGGLE, JOYSTICK, MOUSE_LOOK, FIRE, SCOPE, SCROLL, MACRO, SWIPE }

@Serializable
enum class StepType { TAP, WAIT }

@Serializable
enum class SmoothingMode { OFF, EMA, ONE_EURO }

/** One macro step. TAP presses (x,y) for [ms]; WAIT just waits [ms]. Coordinates are relative 0..1. */
@Serializable
data class MacroStep(val type: StepType, val x: Float = 0f, val y: Float = 0f, val ms: Int = 50)

@Serializable
data class MouseSettings(
    val sensX: Float = 1.0f,
    val sensY: Float = 1.0f,
    /** Extra multiplier while a SCOPE element is held / toggled on. */
    val adsMultiplier: Float = 0.6f,
    val smoothingMode: SmoothingMode = SmoothingMode.EMA,
    /** 0..100. 0 = raw 1:1 (filter bypassed). */
    val smoothing: Int = 0,
    val accelEnabled: Boolean = false,
    /** Extra gain per (count/ms). Only used when [accelEnabled]. */
    val accelGain: Float = 0.05f,
    val invertY: Boolean = false,
    /** Per-frame movement (in counts) below this magnitude is ignored. 0 = off. */
    val deadZone: Float = 0f,
)

/**
 * A mapped on-screen element. All geometry is relative (0..1) so profiles survive any
 * resolution / orientation.
 *
 * - Circular elements (everything except MOUSE_LOOK): x,y = centre, w = diameter as a fraction of
 *   the screen WIDTH (h is informational only).
 * - MOUSE_LOOK: x,y = centre of the camera area, w/h = its size.
 * - JOYSTICK: keys = [up, left, down, right]. Others: keys[0] is the trigger.
 * - SWIPE: swipes from (x,y) to (x2,y2) over [durationMs].
 */
@Serializable
data class Element(
    val id: String,
    val type: ElementType,
    val label: String = "",
    val x: Float,
    val y: Float,
    val w: Float = 0.06f,
    val h: Float = 0.1f,
    val keys: List<Int> = emptyList(),
    val x2: Float = 0f,
    val y2: Float = 0f,
    /** TAP: press length. SWIPE: swipe length. */
    val durationMs: Int = 60,
    val modifierKey: Int = 0,
    /** Joystick deflection (0..1) when no modifier is held. */
    val baseScale: Float = 1f,
    /** Joystick deflection (0..1) while [modifierKey] is held (walk or sprint). */
    val modifierScale: Float = 0.45f,
    /** Time to reach full deflection / return to centre. 0 = instant. */
    val rampMs: Int = 90,
    val autoBurst: Boolean = false,
    /** Full burst cycle length. */
    val burstMs: Int = 100,
    /** SCOPE: toggle instead of hold. */
    val toggleMode: Boolean = false,
    val steps: List<MacroStep> = emptyList(),
    /** MACRO: loop while the key is held. */
    val loop: Boolean = false,
)

@Serializable
data class Profile(
    val name: String,
    val packageName: String = "",
    val schema: Int = 1,
    val mouse: MouseSettings = MouseSettings(),
    val elements: List<Element> = emptyList(),
)
