package app.rshmap.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import app.rshmap.core.Element
import app.rshmap.core.ElementType
import app.rshmap.core.Keys
import app.rshmap.core.MacroStep
import app.rshmap.core.Profile
import app.rshmap.core.StepType
import app.rshmap.util.KeyNames
import app.rshmap.util.Settings
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Live layout editor drawn over the running game. Everything (toolbar, menus) is drawn by this one view
 * so it works from a Service context without dialogs. Drag to move, drag the handle to resize, press
 * "Bind" and hit a key / mouse button to assign. Touch anywhere else while binding to cancel.
 */
class EditorView(
    ctx: Context,
    initial: Profile,
    private val settings: Settings,
    private val labels: Labels,
    private val cb: Callback,
) : View(ctx) {

    interface Callback {
        fun onSave(p: Profile)
        fun onTest(p: Profile)
        fun onExit()
    }

    /** Localised strings, supplied by the service so the view stays resource-free. */
    class Labels(
        val add: String, val bind: String, val opts: String, val del: String, val undo: String, val redo: String,
        val grid: String, val opMinus: String, val opPlus: String, val test: String, val save: String, val exit: String,
        val pressKey: String, val pressKeyFor: String, val tapStep: String, val up: String, val left: String,
        val down: String, val right: String, val modifier: String,
    )

    private var profile = initial
    private var els = ArrayList(initial.elements)
    private val undo = ArrayDeque<List<Element>>()
    private val redo = ArrayDeque<List<Element>>()
    private var sel = -1
    private var grid = settings.editorGrid
    private var opacity = settings.editorOpacity
    private var nextId = els.size + 1

    private val dp = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 12f * dp }

    private class Btn(val label: String, val act: () -> Unit) { val r = RectF() }
    private var buttons = listOf<Btn>()

    private enum class Mode { IDLE, MOVE, RESIZE, END2, BIND, PICK_STEP }
    private var mode = Mode.IDLE
    private var dragDx = 0f
    private var dragDy = 0f
    private var pushed = false

    private var bindSlot = 0
    private var bindModifier = false
    private val bindKeys = IntArray(4)
    private var menu: List<Pair<String, () -> Unit>>? = null
    private val menuRect = RectF()
    private val itemH get() = 44f * dp

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        buttons = listOf(
            Btn(labels.add) { showAddMenu() },
            Btn(labels.bind) { if (sel >= 0) startBind(false) },
            Btn(labels.opts) { showOptions() },
            Btn(labels.del) { delete() },
            Btn(labels.undo) { doUndo() },
            Btn(labels.redo) { doRedo() },
            Btn(labels.grid) { grid = !grid; settings.editorGrid = grid; invalidate() },
            Btn(labels.opMinus) { opacity = max(0.2f, opacity - 0.15f); settings.editorOpacity = opacity; invalidate() },
            Btn(labels.opPlus) { opacity = min(1f, opacity + 0.15f); settings.editorOpacity = opacity; invalidate() },
            Btn(labels.test) { cb.onTest(build()) },
            Btn(labels.save) { cb.onSave(build()) },
            Btn(labels.exit) { cb.onExit() },
        )
    }

    private fun build() = profile.copy(elements = els.toList())

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
    }

    // ------------------------------------------------------------------ undo / redo

    private fun snapshot() {
        undo.addLast(els.toList())
        if (undo.size > 50) undo.removeFirst()
        redo.clear()
    }

    private fun doUndo() {
        val s = undo.removeLastOrNull() ?: return
        redo.addLast(els.toList())
        els = ArrayList(s); sel = min(sel, els.size - 1); invalidate()
    }

    private fun doRedo() {
        val s = redo.removeLastOrNull() ?: return
        undo.addLast(els.toList())
        els = ArrayList(s); sel = min(sel, els.size - 1); invalidate()
    }

    private fun delete() {
        if (sel < 0) return
        snapshot(); els.removeAt(sel); sel = -1; invalidate()
    }

    private fun replace(e: Element) {
        if (sel in els.indices) els[sel] = e
        invalidate()
    }

    // ------------------------------------------------------------------ menus

    private fun showAddMenu() {
        val items = ArrayList<Pair<String, () -> Unit>>()
        fun add(label: String, type: ElementType, key: Int = 0) = items.add(label to { addElement(type, key) })
        add("Tap", ElementType.TAP); add("Hold", ElementType.HOLD); add("Toggle", ElementType.TOGGLE)
        add("Joystick", ElementType.JOYSTICK); add("Mouse look", ElementType.MOUSE_LOOK)
        add("Fire", ElementType.FIRE); add("Scope / ADS", ElementType.SCOPE)
        add("Wheel ↑", ElementType.SCROLL, Keys.WHEEL_UP); add("Wheel ↓", ElementType.SCROLL, Keys.WHEEL_DOWN)
        add("Macro", ElementType.MACRO); add("Swipe", ElementType.SWIPE)
        menu = items; invalidate()
    }

    private fun showOptions() {
        if (sel < 0) return
        val e = els[sel]
        val items = ArrayList<Pair<String, () -> Unit>>()
        items.add("Duplicate" to {
            snapshot(); els.add(e.copy(id = "e${nextId++}", x = min(0.95f, e.x + 0.05f), y = min(0.95f, e.y + 0.05f))); sel = els.size - 1; invalidate()
        })
        when (e.type) {
            ElementType.FIRE -> items.add("Auto-burst: ${if (e.autoBurst) "ON" else "OFF"}" to { snapshot(); replace(e.copy(autoBurst = !e.autoBurst)) })
            ElementType.SCOPE -> items.add("Mode: ${if (e.toggleMode) "toggle" else "hold"}" to { snapshot(); replace(e.copy(toggleMode = !e.toggleMode)) })
            ElementType.TAP, ElementType.SCROLL -> items.add("Press time: ${e.durationMs} ms" to {
                snapshot(); replace(e.copy(durationMs = cycle(e.durationMs, intArrayOf(30, 60, 120, 250))))
            })
            ElementType.JOYSTICK -> {
                items.add("${labels.modifier} (${KeyNames.of(e.modifierKey)})" to { startBind(true) })
                items.add("Ramp: ${e.rampMs} ms" to { snapshot(); replace(e.copy(rampMs = cycle(e.rampMs, intArrayOf(0, 60, 90, 150, 250)))) })
                items.add("Modifier deflection: ${(e.modifierScale * 100).roundToInt()}%" to {
                    snapshot(); replace(e.copy(modifierScale = cycleF(e.modifierScale, floatArrayOf(0.3f, 0.45f, 0.6f, 1f))))
                })
                items.add("Base deflection: ${(e.baseScale * 100).roundToInt()}%" to {
                    snapshot(); replace(e.copy(baseScale = cycleF(e.baseScale, floatArrayOf(0.5f, 0.6f, 0.8f, 1f))))
                })
            }
            ElementType.SWIPE -> items.add("Swipe time: ${e.durationMs} ms" to {
                snapshot(); replace(e.copy(durationMs = cycle(e.durationMs, intArrayOf(100, 200, 350, 600))))
            })
            ElementType.MACRO -> {
                items.add(labels.tapStep to { mode = Mode.PICK_STEP; invalidate() })
                for (ms in intArrayOf(50, 100, 250, 500)) items.add("Add wait $ms ms" to {
                    snapshot(); replace(e.copy(steps = e.steps + MacroStep(StepType.WAIT, ms = ms)))
                })
                items.add("Loop while held: ${if (e.loop) "ON" else "OFF"}" to { snapshot(); replace(e.copy(loop = !e.loop)) })
                items.add("Clear steps (${e.steps.size})" to { snapshot(); replace(e.copy(steps = emptyList())) })
            }
            else -> {}
        }
        menu = items; invalidate()
    }

    private fun cycle(v: Int, opts: IntArray): Int = opts.firstOrNull { it > v } ?: opts[0]
    private fun cycleF(v: Float, opts: FloatArray): Float {
        val i = opts.indexOfFirst { kotlin.math.abs(it - v) < 0.01f }
        return opts[(i + 1) % opts.size]
    }

    private fun addElement(type: ElementType, key: Int) {
        snapshot()
        val id = "e${nextId++}"
        val e = when (type) {
            ElementType.MOUSE_LOOK -> Element(id, type, "Look", 0.75f, 0.4f, 0.5f, 0.8f)
            ElementType.JOYSTICK -> Element(id, type, "Move", 0.17f, 0.7f, 0.13f, 0.23f, keys = listOf(Keys.W, Keys.A, Keys.S, Keys.D), modifierKey = Keys.SHIFT_LEFT)
            ElementType.SWIPE -> Element(id, type, "Swipe", 0.4f, 0.5f, 0.06f, 0.1f, x2 = 0.6f, y2 = 0.5f, durationMs = 200)
            else -> Element(id, type, type.name.lowercase().replaceFirstChar { it.uppercase() }, 0.5f, 0.5f, 0.06f, 0.1f, keys = if (key != 0) listOf(key) else emptyList())
        }
        els.add(e); sel = els.size - 1
        menu = null
        if (key == 0 && type != ElementType.MOUSE_LOOK) startBind(false)
        invalidate()
    }

    // ------------------------------------------------------------------ binding

    private fun startBind(modifier: Boolean) {
        if (sel < 0) return
        menu = null
        snapshot()
        bindModifier = modifier
        bindSlot = 0
        mode = Mode.BIND
        invalidate()
    }

    private fun assign(code: Int) {
        if (sel !in els.indices) { mode = Mode.IDLE; return }
        val e = els[sel]
        when {
            bindModifier -> { replace(e.copy(modifierKey = code)); mode = Mode.IDLE }
            e.type == ElementType.JOYSTICK -> {
                bindKeys[bindSlot++] = code
                if (bindSlot >= 4) { replace(e.copy(keys = bindKeys.toList())); mode = Mode.IDLE }
            }
            else -> { replace(e.copy(keys = listOf(code))); mode = Mode.IDLE }
        }
        invalidate()
    }

    private fun bindPrompt(): String {
        val e = els.getOrNull(sel) ?: return labels.pressKey
        if (bindModifier) return "${labels.pressKeyFor} ${labels.modifier}"
        if (e.type == ElementType.JOYSTICK) {
            return "${labels.pressKeyFor} " + listOf(labels.up, labels.left, labels.down, labels.right)[bindSlot]
        }
        return labels.pressKey
    }

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        if (mode == Mode.BIND) {
            if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0 && ev.keyCode != KeyEvent.KEYCODE_BACK) assign(ev.keyCode)
            return ev.keyCode != KeyEvent.KEYCODE_BACK
        }
        if (ev.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(ev)
        return true
    }

    override fun onGenericMotionEvent(ev: MotionEvent): Boolean {
        if (mode == Mode.BIND && ev.actionMasked == MotionEvent.ACTION_SCROLL) {
            val v = ev.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (v != 0f) assign(if (v > 0) Keys.WHEEL_UP else Keys.WHEEL_DOWN)
            return true
        }
        return super.onGenericMotionEvent(ev)
    }

    // ------------------------------------------------------------------ geometry

    private fun isRect(e: Element) = e.type == ElementType.MOUSE_LOOK
    private fun cxOf(e: Element) = e.x * width
    private fun cyOf(e: Element) = e.y * height
    private fun radius(e: Element) = max(e.w * width / 2f, 20f * dp)

    private fun hit(e: Element, x: Float, y: Float): Boolean =
        if (isRect(e)) x in (e.x - e.w / 2) * width..(e.x + e.w / 2) * width && y in (e.y - e.h / 2) * height..(e.y + e.h / 2) * height
        else hypot(x - cxOf(e), y - cyOf(e)) <= radius(e)

    private fun handleOf(e: Element): Pair<Float, Float> =
        if (isRect(e)) (e.x + e.w / 2) * width to (e.y + e.h / 2) * height
        else cxOf(e) + radius(e) * 0.707f to cyOf(e) + radius(e) * 0.707f

    private fun snap(v: Float, total: Int): Float {
        if (!grid) return v.coerceIn(0f, 1f)
        val g = 24f * dp
        return ((v * total / g).roundToInt() * g / total).coerceIn(0f, 1f)
    }

    // ------------------------------------------------------------------ touch

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val m = menu
            if (m != null) {
                if (menuRect.contains(x, y)) {
                    val i = ((y - menuRect.top) / itemH).toInt()
                    menu = null
                    m.getOrNull(i)?.second?.invoke()
                } else menu = null
                invalidate(); return true
            }
            if (mode == Mode.BIND) {
                if (ev.isFromSource(InputDevice.SOURCE_MOUSE) && ev.buttonState != 0) {
                    val bs = ev.buttonState
                    assign(
                        when {
                            bs and MotionEvent.BUTTON_SECONDARY != 0 -> Keys.MOUSE_RIGHT
                            bs and MotionEvent.BUTTON_TERTIARY != 0 -> Keys.MOUSE_MIDDLE
                            bs and MotionEvent.BUTTON_BACK != 0 -> Keys.MOUSE_BACK
                            bs and MotionEvent.BUTTON_FORWARD != 0 -> Keys.MOUSE_FORWARD
                            else -> Keys.MOUSE_LEFT
                        },
                    )
                } else { mode = Mode.IDLE }
                invalidate(); return true
            }
            if (mode == Mode.PICK_STEP) {
                val e = els.getOrNull(sel)
                if (e != null && y > toolbarBottom) {
                    snapshot(); replace(e.copy(steps = e.steps + MacroStep(StepType.TAP, (x / width).coerceIn(0f, 1f), (y / height).coerceIn(0f, 1f), 40)))
                    return true
                }
                mode = Mode.IDLE
            }
            for (b in buttons) if (b.r.contains(x, y)) { b.act(); invalidate(); return true }

            val s = els.getOrNull(sel)
            if (s != null) {
                val h = handleOf(s)
                if (hypot(x - h.first, y - h.second) < 22f * dp) { snapshot(); mode = Mode.RESIZE; return true }
                if (s.type == ElementType.SWIPE && hypot(x - s.x2 * width, y - s.y2 * height) < 24f * dp) { snapshot(); mode = Mode.END2; return true }
            }
            var found = -1
            for (i in els.indices.reversed()) if (hit(els[i], x, y)) { found = i; break }
            // prefer smaller shapes on top of the big camera area
            if (found >= 0 && isRect(els[found])) {
                for (i in els.indices.reversed()) if (!isRect(els[i]) && hit(els[i], x, y)) { found = i; break }
            }
            if (found >= 0) {
                val e = els[found]
                val wasSel = found == sel
                sel = found
                dragDx = e.x * width - x; dragDy = e.y * height - y
                pushed = false
                mode = Mode.MOVE
                if (wasSel && ev.eventTime - lastTap < 350) startBind(false)
                lastTap = ev.eventTime
            } else { sel = -1; mode = Mode.IDLE }
            invalidate()
            return true
        }
        if (ev.actionMasked == MotionEvent.ACTION_MOVE) {
            val e = els.getOrNull(sel) ?: return true
            when (mode) {
                Mode.MOVE -> {
                    if (!pushed) { snapshot(); pushed = true }
                    replace(e.copy(x = snap((x + dragDx) / width, width), y = snap((y + dragDy) / height, height)))
                }
                Mode.RESIZE -> if (isRect(e)) {
                    val l = e.x - e.w / 2; val tp = e.y - e.h / 2
                    val nw = max(0.08f, x / width - l); val nh = max(0.08f, y / height - tp)
                    replace(e.copy(x = (l + nw / 2).coerceIn(0f, 1f), y = (tp + nh / 2).coerceIn(0f, 1f), w = min(1f, nw), h = min(1f, nh)))
                } else {
                    val r = hypot(x - cxOf(e), y - cyOf(e)) / 0.707f
                    val w = (2f * r / width).coerceIn(0.03f, 0.5f)
                    replace(e.copy(w = w, h = w * width / height))
                }
                Mode.END2 -> replace(e.copy(x2 = snap(x / width, width), y2 = snap(y / height, height)))
                else -> {}
            }
            invalidate()
        }
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (mode == Mode.MOVE || mode == Mode.RESIZE || mode == Mode.END2) mode = Mode.IDLE
        }
        return true
    }

    private var lastTap = 0L
    private var toolbarBottom = 0f

    // ------------------------------------------------------------------ drawing

    private fun colorFor(e: Element) = when (e.type) {
        ElementType.JOYSTICK -> 0xFF42A5F5.toInt()
        ElementType.MOUSE_LOOK -> 0xFFAB47BC.toInt()
        ElementType.FIRE -> 0xFFEF5350.toInt()
        ElementType.SCOPE -> 0xFFFFA726.toInt()
        ElementType.MACRO -> 0xFF26C6DA.toInt()
        ElementType.SWIPE -> 0xFF9CCC65.toInt()
        else -> 0xFF78909C.toInt()
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0x66000000)
        if (grid) {
            p.color = 0x22FFFFFF; p.strokeWidth = 1f; p.style = Paint.Style.STROKE
            val g = 24f * dp
            var gx = 0f
            while (gx < width) { c.drawLine(gx, 0f, gx, height.toFloat(), p); gx += g }
            var gy = 0f
            while (gy < height) { c.drawLine(0f, gy, width.toFloat(), gy, p); gy += g }
        }
        val a = (opacity * 255).toInt()
        for ((i, e) in els.withIndex()) {
            val col = colorFor(e)
            p.style = Paint.Style.FILL
            p.color = (col and 0x00FFFFFF) or ((a * 0.45f).toInt() shl 24)
            if (isRect(e)) {
                val r = RectF((e.x - e.w / 2) * width, (e.y - e.h / 2) * height, (e.x + e.w / 2) * width, (e.y + e.h / 2) * height)
                c.drawRoundRect(r, 12f * dp, 12f * dp, p)
            } else c.drawCircle(cxOf(e), cyOf(e), radius(e), p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = (if (i == sel) 3f else 1.5f) * dp
            p.color = if (i == sel) Color.WHITE else ((col and 0x00FFFFFF) or (a shl 24))
            if (isRect(e)) c.drawRoundRect(RectF((e.x - e.w / 2) * width, (e.y - e.h / 2) * height, (e.x + e.w / 2) * width, (e.y + e.h / 2) * height), 12f * dp, 12f * dp, p)
            else c.drawCircle(cxOf(e), cyOf(e), radius(e), p)

            t.alpha = max(a, 120)
            if (e.type == ElementType.JOYSTICK && e.keys.size >= 4) {
                val r = radius(e) * 0.65f
                c.drawText(KeyNames.of(e.keys[0]), cxOf(e), cyOf(e) - r + 4f * dp, t)
                c.drawText(KeyNames.of(e.keys[1]), cxOf(e) - r, cyOf(e) + 4f * dp, t)
                c.drawText(KeyNames.of(e.keys[2]), cxOf(e), cyOf(e) + r + 4f * dp, t)
                c.drawText(KeyNames.of(e.keys[3]), cxOf(e) + r, cyOf(e) + 4f * dp, t)
            } else {
                c.drawText(KeyNames.joined(e.keys).ifEmpty { "?" }, cxOf(e), cyOf(e) + 4f * dp, t)
            }
            c.drawText(e.label, cxOf(e), (if (isRect(e)) (e.y - e.h / 2) * height + 16f * dp else cyOf(e) + radius(e) + 12f * dp), t)

            if (e.type == ElementType.SWIPE) {
                p.style = Paint.Style.STROKE; p.color = col
                c.drawLine(cxOf(e), cyOf(e), e.x2 * width, e.y2 * height, p)
                p.style = Paint.Style.FILL
                c.drawCircle(e.x2 * width, e.y2 * height, 10f * dp, p)
            }
            if (i == sel) {
                if (e.type == ElementType.MACRO) for ((n, s) in e.steps.withIndex()) if (s.type == StepType.TAP) {
                    p.style = Paint.Style.FILL; p.color = 0xFF26C6DA.toInt()
                    c.drawCircle(s.x * width, s.y * height, 9f * dp, p)
                    c.drawText("${n + 1}", s.x * width, s.y * height + 4f * dp, t)
                }
                val h = handleOf(e)
                p.style = Paint.Style.FILL; p.color = Color.WHITE
                c.drawCircle(h.first, h.second, 8f * dp, p)
            }
        }
        drawToolbar(c)
        if (mode == Mode.BIND || mode == Mode.PICK_STEP) {
            val msg = if (mode == Mode.BIND) bindPrompt() else labels.tapStep
            p.style = Paint.Style.FILL; p.color = 0xEE000000.toInt()
            val w = width * 0.7f
            c.drawRoundRect(RectF((width - w) / 2, height / 2f - 30f * dp, (width + w) / 2, height / 2f + 30f * dp), 14f * dp, 14f * dp, p)
            t.alpha = 255; t.textSize = 16f * dp
            c.drawText(msg, width / 2f, height / 2f + 6f * dp, t)
            t.textSize = 12f * dp
        }
        menu?.let { drawMenu(c, it) }
    }

    private fun drawToolbar(c: Canvas) {
        val bw = 54f * dp
        val bh = 36f * dp
        val perRow = max(1, (width / bw).toInt())
        toolbarBottom = 0f
        for ((i, b) in buttons.withIndex()) {
            val row = i / perRow
            val col = i % perRow
            val rowCount = min(perRow, buttons.size - row * perRow)
            val left = (width - rowCount * bw) / 2f + col * bw
            val top = 4f * dp + row * (bh + 4f * dp)
            b.r.set(left + 2f * dp, top, left + bw - 2f * dp, top + bh)
            p.style = Paint.Style.FILL
            p.color = if (b.label == labels.grid && grid) 0xFF2E7D32.toInt() else 0xEE263238.toInt()
            c.drawRoundRect(b.r, 8f * dp, 8f * dp, p)
            t.alpha = 255
            c.drawText(b.label, b.r.centerX(), b.r.centerY() + 4f * dp, t)
            toolbarBottom = max(toolbarBottom, b.r.bottom)
        }
    }

    private fun drawMenu(c: Canvas, items: List<Pair<String, () -> Unit>>) {
        val w = 240f * dp
        val h = min(items.size * itemH, height - 16f * dp)
        menuRect.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        p.style = Paint.Style.FILL; p.color = 0xF2212121.toInt()
        c.drawRoundRect(menuRect, 12f * dp, 12f * dp, p)
        t.alpha = 255
        for ((i, it) in items.withIndex()) {
            val y = menuRect.top + i * itemH
            if (y + itemH > menuRect.bottom + 1) break
            c.drawText(it.first, menuRect.centerX(), y + itemH / 2f + 4f * dp, t)
        }
    }
}
