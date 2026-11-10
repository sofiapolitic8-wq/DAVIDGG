package app.rshmap.core

/**
 * The mapping engine. Pure Kotlin, single-threaded: call every method from ONE thread
 * (the input HandlerThread in the app). Hot-path methods ([onKey], [onMouseDelta], [onFrame])
 * do not allocate.
 *
 * Time is passed in explicitly (milliseconds, any monotonic clock) which keeps it unit-testable.
 */
class MappingEngine(private val sink: TouchSink) {

    private class Rt(val e: Element) {
        var touchId = -1
        var releaseAt = 0L
        var held = false
        var nextBurstAt = 0L
        var burstDown = false
        var cx = 0f
        var cy = 0f
        var radius = 0f
        var ex = 0f
        var ey = 0f
        var lastX = Float.NaN
        var lastY = Float.NaN
        var swipeActive = false
        var swipeStart = 0L
        var js: JoystickState? = null
        var macro: MacroRunner? = null
        var camera: CameraArea? = null
        var carryX = 0f
        var carryY = 0f
        var lastMoveAt = 0L
        val keyIdx = IntArray(4)
        var modIdx = -1
    }

    private val pressed = BooleanArray(Keys.TABLE_SIZE)
    private var bindings = arrayOfNulls<IntArray>(Keys.TABLE_SIZE)
    private var rts: Array<Rt> = emptyArray()
    private var cameraRt: Rt? = null
    private val pool = PointerPool(10)
    private val mouse = MouseProcessor()
    private val axis = FloatArray(2)

    private var profile: Profile? = null
    private var globalMul = 1f
    private var sw = 1920f
    private var sh = 1080f
    private var active = false
    private var lastFrame = 0L
    private var accDx = 0f
    private var accDy = 0f
    private var scoped = false

    val isActive: Boolean get() = active
    val activeTouches: Int get() = pool.inUse

    // ---------------------------------------------------------------- configuration

    fun setScreen(widthPx: Int, heightPx: Int) {
        releaseAll()
        sw = widthPx.coerceAtLeast(1).toFloat()
        sh = heightPx.coerceAtLeast(1).toFloat()
        layout()
    }

    fun setProfile(p: Profile?, globalSensitivity: Float = 1f) {
        releaseAll()
        profile = p
        globalMul = globalSensitivity
        mouse.configure(p?.mouse ?: MouseSettings(), globalSensitivity)
        build()
    }

    fun setActive(a: Boolean) {
        if (!a) releaseAll()
        active = a
        lastFrame = 0L
    }

    private fun build() {
        val list = profile?.elements ?: emptyList()
        rts = Array(list.size) { Rt(list[it]) }
        val tmp = HashMap<Int, ArrayList<Int>>()
        for ((i, rt) in rts.withIndex()) {
            val e = rt.e
            when (e.type) {
                ElementType.JOYSTICK -> {
                    for (k in 0 until 4) rt.keyIdx[k] = if (k < e.keys.size) Keys.index(e.keys[k]) else -1
                    rt.modIdx = Keys.index(e.modifierKey)
                    rt.js = JoystickState(e.rampMs)
                }
                ElementType.MOUSE_LOOK -> {}
                else -> {
                    val idx = Keys.index(e.keys.firstOrNull() ?: 0)
                    if (idx >= 0) tmp.getOrPut(idx) { ArrayList() }.add(i)
                    if (e.type == ElementType.MACRO) rt.macro = MacroRunner(e.steps, e.loop)
                }
            }
        }
        bindings = arrayOfNulls(Keys.TABLE_SIZE)
        for ((k, v) in tmp) bindings[k] = v.toIntArray()
        cameraRt = rts.firstOrNull { it.e.type == ElementType.MOUSE_LOOK }
        layout()
    }

    private fun layout() {
        for (rt in rts) {
            val e = rt.e
            rt.cx = e.x * sw
            rt.cy = e.y * sh
            rt.radius = e.w * sw / 2f
            rt.ex = e.x2 * sw
            rt.ey = e.y2 * sh
            if (e.type == ElementType.MOUSE_LOOK) {
                rt.camera = CameraArea(
                    (e.x - e.w / 2f) * sw, (e.y - e.h / 2f) * sh,
                    (e.x + e.w / 2f) * sw, (e.y + e.h / 2f) * sh,
                    0.18f,
                )
            }
        }
    }

    // ---------------------------------------------------------------- input (hot path)

    /** Keyboard key or pseudo mouse key (see [Keys]). Auto-repeat is filtered here too. */
    fun onKey(code: Int, down: Boolean, now: Long) {
        val i = Keys.index(code)
        if (i < 0) return
        if (down) {
            if (pressed[i]) return
            pressed[i] = true
        } else {
            if (!pressed[i]) return
            pressed[i] = false
        }
        if (!active) return
        val list = bindings[i] ?: return
        for (n in list.indices) handleKey(rts[list[n]], down, now)
    }

    /** Raw relative mouse counts; accumulated until the next frame. */
    fun onMouseDelta(dx: Float, dy: Float) {
        accDx += dx
        accDy += dy
    }

    private fun handleKey(rt: Rt, down: Boolean, now: Long) {
        when (rt.e.type) {
            ElementType.TAP, ElementType.SCROLL -> if (down) {
                press(rt)
                rt.releaseAt = now + rt.e.durationMs
            }
            ElementType.HOLD -> if (down) press(rt) else release(rt)
            ElementType.TOGGLE -> if (down) {
                if (rt.touchId >= 0) release(rt) else press(rt)
            }
            ElementType.FIRE -> if (down) {
                rt.held = true
                press(rt)
                if (rt.e.autoBurst) {
                    rt.burstDown = true
                    rt.nextBurstAt = now + burstDownMs(rt.e)
                }
            } else {
                rt.held = false
                release(rt)
            }
            ElementType.SCOPE -> {
                if (rt.e.toggleMode) {
                    if (down) {
                        rt.held = !rt.held
                        if (rt.held) press(rt) else release(rt)
                    }
                } else if (down) {
                    rt.held = true
                    press(rt)
                } else {
                    rt.held = false
                    release(rt)
                }
                recomputeScoped()
            }
            ElementType.MACRO -> {
                val m = rt.macro ?: return
                if (down) m.start(now, sw, sh, sink, pool) else m.release()
            }
            ElementType.SWIPE -> {
                if (down && !rt.swipeActive) {
                    val id = pool.acquire()
                    if (id >= 0) {
                        rt.touchId = id
                        rt.swipeActive = true
                        rt.swipeStart = now
                        sink.down(id, rt.cx, rt.cy)
                    }
                }
            }
            else -> {}
        }
    }

    private fun burstDownMs(e: Element): Long = minOf(40, e.burstMs / 2).toLong()

    private fun recomputeScoped() {
        var s = false
        for (rt in rts) if (rt.e.type == ElementType.SCOPE && rt.held) s = true
        scoped = s
    }

    private fun press(rt: Rt) {
        if (rt.touchId >= 0) return
        val id = pool.acquire()
        if (id < 0) return
        rt.touchId = id
        sink.down(id, rt.cx, rt.cy)
    }

    private fun release(rt: Rt) {
        if (rt.touchId < 0) return
        sink.up(rt.touchId)
        pool.release(rt.touchId)
        rt.touchId = -1
    }

    // ---------------------------------------------------------------- frame update

    /** Call once per display frame (Choreographer). */
    fun onFrame(now: Long) {
        if (!active) return
        val dt = if (lastFrame == 0L) 16L else (now - lastFrame).coerceIn(1L, 100L)
        lastFrame = now

        val dx = accDx
        val dy = accDy
        accDx = 0f
        accDy = 0f
        val cam = cameraRt
        if (cam != null) {
            mouse.process(dx, dy, dt / 1000f, scoped)
            stepCamera(cam, now, mouse.outX, mouse.outY)
        }

        for (rt in rts) {
            when (rt.e.type) {
                ElementType.TAP, ElementType.SCROLL ->
                    if (rt.touchId >= 0 && now >= rt.releaseAt) release(rt)
                ElementType.FIRE -> if (rt.held && rt.e.autoBurst && now >= rt.nextBurstAt) {
                    if (rt.burstDown) {
                        release(rt)
                        rt.burstDown = false
                        rt.nextBurstAt += (rt.e.burstMs - burstDownMs(rt.e))
                    } else {
                        press(rt)
                        rt.burstDown = true
                        rt.nextBurstAt += burstDownMs(rt.e)
                    }
                }
                ElementType.JOYSTICK -> stepJoystick(rt, dt)
                ElementType.MACRO -> rt.macro?.update(now, sink, pool)
                ElementType.SWIPE -> {
                    if (rt.swipeActive) stepSwipe(rt, now)
                }
                else -> {}
            }
        }
    }

    private fun stepJoystick(rt: Rt, dt: Long) {
        val e = rt.e
        val js = rt.js ?: return
        val k = rt.keyIdx
        JoystickMath.axis(
            k[0] >= 0 && pressed[k[0]], k[1] >= 0 && pressed[k[1]],
            k[2] >= 0 && pressed[k[2]], k[3] >= 0 && pressed[k[3]], axis,
        )
        val scale = if (rt.modIdx >= 0 && pressed[rt.modIdx]) e.modifierScale else e.baseScale
        js.update(axis[0] * scale, axis[1] * scale, dt.toFloat())
        if (js.x != 0f || js.y != 0f) {
            if (rt.touchId < 0) {
                val id = pool.acquire()
                if (id < 0) return
                rt.touchId = id
                sink.down(id, rt.cx, rt.cy)
                rt.lastX = rt.cx
                rt.lastY = rt.cy
            }
            val px = rt.cx + js.x * rt.radius
            val py = rt.cy + js.y * rt.radius
            if (px != rt.lastX || py != rt.lastY) {
                sink.move(rt.touchId, px, py)
                rt.lastX = px
                rt.lastY = py
            }
        } else if (rt.touchId >= 0) {
            release(rt)
        }
    }

    private fun stepSwipe(rt: Rt, now: Long) {
        val t = ((now - rt.swipeStart).toFloat() / rt.e.durationMs).coerceIn(0f, 1f)
        sink.move(rt.touchId, rt.cx + (rt.ex - rt.cx) * t, rt.cy + (rt.ey - rt.cy) * t)
        if (t >= 1f) {
            release(rt)
            rt.swipeActive = false
        }
    }

    private fun stepCamera(rt: Rt, now: Long, mx: Int, my: Int) {
        val cam = rt.camera ?: return
        var rx = mx + rt.carryX
        var ry = my + rt.carryY
        rt.carryX = 0f
        rt.carryY = 0f
        if (rx == 0f && ry == 0f) {
            if (rt.touchId >= 0 && mouse.isIdle && now - rt.lastMoveAt > IDLE_RELEASE_MS) release(rt)
            return
        }
        if (rt.touchId < 0) {
            val id = pool.acquire()
            if (id < 0) return
            rt.touchId = id
            cam.recenter()
            sink.down(id, cam.x, cam.y)
        }
        rt.lastMoveAt = now
        val inside = cam.move(rx, ry)
        sink.move(rt.touchId, cam.x, cam.y)
        if (!inside) {
            // Finger reached the safe border: lift now, re-place at the centre next frame
            // and carry the unspent movement so no mouse distance is lost.
            rx = cam.leftoverX
            ry = cam.leftoverY
            release(rt)
            rt.carryX = rx
            rt.carryY = ry
        }
    }

    // ---------------------------------------------------------------- safety

    /** Lifts every virtual finger and clears all key state. Safe to call at any time. */
    fun releaseAll() {
        for (rt in rts) {
            release(rt)
            rt.held = false
            rt.swipeActive = false
            rt.carryX = 0f
            rt.carryY = 0f
            rt.js?.reset()
            rt.macro?.stop(sink, pool)
        }
        java.util.Arrays.fill(pressed, false)
        pool.reset()
        mouse.reset()
        accDx = 0f
        accDy = 0f
        scoped = false
    }

    private companion object {
        const val IDLE_RELEASE_MS = 250L
    }
}
