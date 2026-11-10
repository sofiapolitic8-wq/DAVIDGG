package app.rshmap.core

/**
 * Key-code space used by profiles. Positive values below 400 are Android KeyEvent codes
 * (kept as plain constants so this module has no Android dependency). Mouse buttons and
 * wheel pulses live at 10001+.
 */
object Keys {
    const val NONE = 0

    const val MOUSE_LEFT = 10001
    const val MOUSE_RIGHT = 10002
    const val MOUSE_MIDDLE = 10003
    const val MOUSE_BACK = 10004
    const val MOUSE_FORWARD = 10005
    const val WHEEL_UP = 10006
    const val WHEEL_DOWN = 10007

    const val TABLE_SIZE = 512
    private const val MOUSE_TABLE_BASE = 400

    // Android KeyEvent.KEYCODE_* values used by built-in templates.
    const val N0 = 7; const val N1 = 8; const val N2 = 9; const val N3 = 10; const val N4 = 11
    const val N5 = 12; const val N6 = 13; const val N7 = 14; const val N8 = 15; const val N9 = 16
    const val A = 29; const val B = 30; const val C = 31; const val D = 32; const val E = 33
    const val F = 34; const val G = 35; const val H = 36; const val I = 37; const val J = 38
    const val K = 39; const val L = 40; const val M = 41; const val N = 42; const val O = 43
    const val P = 44; const val Q = 45; const val R = 46; const val S = 47; const val T = 48
    const val U = 49; const val V = 50; const val W = 51; const val X = 52; const val Y = 53
    const val Z = 54
    const val DPAD_UP = 19; const val DPAD_DOWN = 20; const val DPAD_LEFT = 21; const val DPAD_RIGHT = 22
    const val ALT_LEFT = 57; const val SHIFT_LEFT = 59; const val TAB = 61; const val SPACE = 62
    const val ENTER = 66; const val GRAVE = 68; const val ESCAPE = 111; const val CTRL_LEFT = 113
    const val CAPS_LOCK = 115; const val F1 = 131; const val F2 = 132

    /** Maps a key code to a slot in a [TABLE_SIZE] lookup table, or -1 when unsupported. */
    fun index(code: Int): Int = when {
        code in 1..399 -> code
        code in 10001..10050 -> MOUSE_TABLE_BASE + (code - 10000)
        else -> -1
    }

    fun isMouse(code: Int): Boolean = code >= 10001

    fun mouseName(code: Int): String? = when (code) {
        MOUSE_LEFT -> "LMB"
        MOUSE_RIGHT -> "RMB"
        MOUSE_MIDDLE -> "MMB"
        MOUSE_BACK -> "M4"
        MOUSE_FORWARD -> "M5"
        WHEEL_UP -> "Wheel↑"
        WHEEL_DOWN -> "Wheel↓"
        else -> null
    }
}
