package app.rshmap.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import app.rshmap.core.ElementType
import app.rshmap.core.Keys
import app.rshmap.core.Profile
import app.rshmap.input.InputHub
import app.rshmap.input.Stats
import app.rshmap.util.KeyNames
import app.rshmap.util.Settings
import android.os.SystemClock

/**
 * Full-screen transparent window shown while Game Mode is on. It owns keyboard focus and Pointer Capture
 * so it receives raw relative mouse deltas, and forwards everything to [InputHub] without allocating.
 * It also draws the optional crosshair, key hints and test-mode touch points.
 */
class CaptureView(
    ctx: Context,
    private val hub: InputHub,
    private val settings: Settings,
    private val cb: Callback,
) : View(ctx) {

    interface Callback {
        fun onHotkey()
        fun onNeedFallback()
    }

    var hotkey = KeyEvent.KEYCODE_F1
    var trace: TouchTrace? = null

    private var lastButtons = 0
    private var fallback = false
    private var lastHx = Float.NaN
    private var lastHy = Float.NaN
    private var hzCount = 0
    private var hzStart = 0L

    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11f * dp; textAlign = Paint.Align.CENTER }
    private var hintT = emptyArray<String>()

    private val fallbackCheck = Runnable {
        if (isAttachedToWindow && !hasPointerCapture() && !fallback) cb.onNeedFallback()
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        defaultFocusHighlightEnabled = false
    }

    fun setProfile(p: Profile) {
        val els = p.elements.filter { it.type != ElementType.MOUSE_LOOK }
        hintT = Array(els.size) { i ->
            val e = els[i]
            if (e.type == ElementType.JOYSTICK) KeyNames.joined(e.keys) else KeyNames.of(e.keys.firstOrNull() ?: 0)
        }
        // positions are relative; resolved in onDraw so rotation just works
        hintRel = els.map { it.x to it.y }
        invalidate()
    }

    private var hintRel: List<Pair<Float, Float>> = emptyList()

    fun setFallback(v: Boolean) {
        fallback = v
        lastHx = Float.NaN
    }

    fun reRequestCapture() {
        if (hasWindowFocus() && !fallback) requestPointerCapture()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(fallbackCheck)
        Stats.captured = false
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && !fallback) {
            requestFocus()
            requestPointerCapture()
            removeCallbacks(fallbackCheck)
            postDelayed(fallbackCheck, 900)
        }
    }

    override fun onPointerCaptureChange(hasCapture: Boolean) {
        super.onPointerCaptureChange(hasCapture)
        Stats.captured = hasCapture
    }

    // ------------------------------------------------------------------ keyboard

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val code = e.keyCode
        when (code) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_UNKNOWN -> return super.dispatchKeyEvent(e)
        }
        if (code == hotkey) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) cb.onHotkey()
            return true
        }
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount == 0) hub.key(code, true)
        } else if (e.action == KeyEvent.ACTION_UP) {
            hub.key(code, false)
        }
        return true
    }

    // ------------------------------------------------------------------ mouse

    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        pointer(event, true)
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return super.onGenericMotionEvent(event)
        pointer(event, false)
        return fallback
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!fallback || !event.isFromSource(InputDevice.SOURCE_MOUSE)) return false
        pointer(event, false)
        return true
    }

    private fun pointer(e: MotionEvent, relative: Boolean) {
        syncButtons(e.buttonState)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                if (relative) {
                    var dx = 0f
                    var dy = 0f
                    val n = e.historySize
                    for (i in 0 until n) { dx += e.getHistoricalX(i); dy += e.getHistoricalY(i) }
                    dx += e.x; dy += e.y
                    hub.mouse(dx, dy)
                    countHz(n + 1)
                } else {
                    if (!lastHx.isNaN()) { hub.mouse(e.x - lastHx, e.y - lastHy); countHz(1) }
                    lastHx = e.x; lastHy = e.y
                }
            }
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_EXIT, MotionEvent.ACTION_DOWN -> {
                lastHx = if (relative) Float.NaN else e.x
                lastHy = if (relative) Float.NaN else e.y
            }
            MotionEvent.ACTION_SCROLL -> {
                val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                val k = if (v > 0f) Keys.WHEEL_UP else if (v < 0f) Keys.WHEEL_DOWN else 0
                if (k != 0) { hub.key(k, true); hub.key(k, false) }
            }
        }
    }

    private fun syncButtons(bs: Int) {
        val ch = bs xor lastButtons
        if (ch == 0) return
        lastButtons = bs
        btn(ch, bs, MotionEvent.BUTTON_PRIMARY, Keys.MOUSE_LEFT)
        btn(ch, bs, MotionEvent.BUTTON_SECONDARY, Keys.MOUSE_RIGHT)
        btn(ch, bs, MotionEvent.BUTTON_TERTIARY, Keys.MOUSE_MIDDLE)
        btn(ch, bs, MotionEvent.BUTTON_BACK, Keys.MOUSE_BACK)
        btn(ch, bs, MotionEvent.BUTTON_FORWARD, Keys.MOUSE_FORWARD)
    }

    private fun btn(changed: Int, state: Int, mask: Int, key: Int) {
        if ((changed and mask) != 0) hub.key(key, (state and mask) != 0)
    }

    private fun countHz(samples: Int) {
        val now = SystemClock.uptimeMillis()
        if (hzStart == 0L) hzStart = now
        hzCount += samples
        val el = now - hzStart
        if (el >= 1000) {
            Stats.mouseHz = hzCount * 1000f / el
            hzCount = 0
            hzStart = now
        }
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(c: Canvas) {
        if (settings.crosshair) drawCrosshair(c)
        if (settings.keyHints) {
            for (i in hintT.indices) {
                val p = hintRel[i]
                c.drawText(hintT[i], p.first * width, p.second * height + 4f * dp, text)
            }
        }
        val t = trace ?: return
        paint.style = Paint.Style.FILL
        paint.color = 0x88FFC107.toInt()
        for (i in 0 until 10) if (t.active[i]) {
            c.drawCircle(t.x[i], t.y[i], 22f * dp, paint)
            c.drawText(i.toString(), t.x[i], t.y[i] + 4f * dp, text)
        }
    }

    private fun drawCrosshair(c: Canvas) {
        val cx = width / 2f + settings.crosshairOffX * dp
        val cy = height / 2f + settings.crosshairOffY * dp
        val s = settings.crosshairSizeDp * dp
        paint.color = settings.crosshairColor
        paint.strokeWidth = 1.6f * dp
        paint.style = Paint.Style.STROKE
        when (settings.crosshairShape) {
            0 -> { paint.style = Paint.Style.FILL; c.drawCircle(cx, cy, s / 5f, paint) }
            1 -> { c.drawLine(cx - s, cy, cx + s, cy, paint); c.drawLine(cx, cy - s, cx, cy + s, paint) }
            2 -> c.drawCircle(cx, cy, s, paint)
            else -> {
                val g = s / 3f
                c.drawLine(cx - s, cy, cx - g, cy, paint); c.drawLine(cx + g, cy, cx + s, cy, paint)
                c.drawLine(cx, cy - s, cx, cy - g, paint); c.drawLine(cx, cy + g, cx, cy + s, paint)
            }
        }
    }
}
