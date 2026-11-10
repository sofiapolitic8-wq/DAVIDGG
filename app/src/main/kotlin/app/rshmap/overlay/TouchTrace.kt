package app.rshmap.overlay

import android.view.View
import app.rshmap.core.TouchSink

/** Records live virtual touches (test mode) and asks [view] to redraw. */
class TouchTrace(private val view: View) : TouchSink {
    val active = BooleanArray(10)
    val x = FloatArray(10)
    val y = FloatArray(10)

    override fun down(id: Int, x: Float, y: Float) = set(id, true, x, y)
    override fun move(id: Int, x: Float, y: Float) = set(id, true, x, y)
    override fun up(id: Int) = set(id, false, this.x.getOrElse(id) { 0f }, this.y.getOrElse(id) { 0f })

    private fun set(id: Int, a: Boolean, px: Float, py: Float) {
        if (id !in 0..9) return
        active[id] = a; x[id] = px; y[id] = py
        view.postInvalidateOnAnimation()
    }
}
