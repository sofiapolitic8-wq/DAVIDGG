package app.rshmap.util

import android.view.KeyEvent
import app.rshmap.core.Keys

object KeyNames {
    fun of(code: Int): String {
        if (code == 0) return "–"
        Keys.mouseName(code)?.let { return it }
        return when (code) {
            KeyEvent.KEYCODE_SPACE -> "Space"
            KeyEvent.KEYCODE_SHIFT_LEFT -> "LShift"
            KeyEvent.KEYCODE_SHIFT_RIGHT -> "RShift"
            KeyEvent.KEYCODE_CTRL_LEFT -> "LCtrl"
            KeyEvent.KEYCODE_CTRL_RIGHT -> "RCtrl"
            KeyEvent.KEYCODE_ALT_LEFT -> "LAlt"
            KeyEvent.KEYCODE_GRAVE -> "`"
            KeyEvent.KEYCODE_DPAD_UP -> "↑"
            KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
            KeyEvent.KEYCODE_DPAD_LEFT -> "←"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
            else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").removePrefix("NUMPAD_")
        }
    }

    fun joined(keys: List<Int>): String = keys.joinToString(" ") { of(it) }
}
