package app.rshmap.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager

/** Draggable floating bubble: tap = toggle Game Mode, long-press = open layout editor. */
class BubbleView(
    ctx: Context,
    private val wm: WindowManager,
    private val lp: WindowManager.LayoutParams,
    private val onTap: () -> Unit,
    private val onLong: () -> Unit,
) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 16f * resources.displayMetrics.density; isFakeBoldText = true
    }
    private var on = false
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false
    private var longFired = false
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val longRun = Runnable { if (!moved) { longFired = true; onLong() } }

    fun setActive(v: Boolean) { on = v; invalidate() }

    override fun onDraw(c: Canvas) {
        val r = width / 2f
        paint.color = if (on) 0xCC2E7D32.toInt() else 0xCC455A64.toInt()
        c.drawCircle(r, r, r, paint)
        c.drawText("R", r, r + txt.textSize / 3f, txt)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                moved = false; longFired = false
                postDelayed(longRun, 700)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (!moved && (dx * dx + dy * dy) > slop * slop) { moved = true; removeCallbacks(longRun) }
                if (moved) {
                    lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt()
                    try { wm.updateViewLayout(this, lp) } catch (ignored: Exception) {}
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longRun)
                if (!moved && !longFired) onTap()
            }
            MotionEvent.ACTION_CANCEL -> removeCallbacks(longRun)
        }
        return true
    }
}
