package app.rshmap.core

/**
 * Keeps a virtual camera-swipe finger inside a rectangle. [move] returns false when the finger hit
 * the safe border; the caller should then lift it, call [recenter] and re-apply [leftoverX]/[leftoverY].
 */
class CameraArea(
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    marginFrac: Float = 0.15f,
) {
    private val safeL = left + (right - left) * marginFrac
    private val safeR = right - (right - left) * marginFrac
    private val safeT = top + (bottom - top) * marginFrac
    private val safeB = bottom - (bottom - top) * marginFrac

    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    var x = (left + right) / 2f
        private set
    var y = (top + bottom) / 2f
        private set
    var leftoverX = 0f
        private set
    var leftoverY = 0f
        private set

    fun recenter() {
        x = centerX
        y = centerY
        leftoverX = 0f
        leftoverY = 0f
    }

    fun move(dx: Float, dy: Float): Boolean {
        val nx = x + dx
        val ny = y + dy
        if (nx in safeL..safeR && ny in safeT..safeB) {
            x = nx; y = ny
            leftoverX = 0f; leftoverY = 0f
            return true
        }
        val cx = nx.coerceIn(safeL, safeR)
        val cy = ny.coerceIn(safeT, safeB)
        leftoverX = nx - cx
        leftoverY = ny - cy
        x = cx; y = cy
        return false
    }
}
