package app.rshmap.util

import android.content.Context
import android.graphics.Color
import android.view.KeyEvent

/** Thin SharedPreferences wrapper. All values have safe defaults. */
class Settings(context: Context) {
    private val sp = (context.applicationContext ?: context).getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun int(k: String, d: Int) = sp.getInt(k, d)
    private fun bool(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun float(k: String, d: Float) = sp.getFloat(k, d)
    private fun str(k: String, d: String) = sp.getString(k, d) ?: d

    var hotkey: Int
        get() = int("hotkey", KeyEvent.KEYCODE_F1)
        set(v) = sp.edit().putInt("hotkey", v).apply()

    /** 0 = auto (Shizuku if ready, else Accessibility), 1 = Accessibility, 2 = Shizuku. */
    var backend: Int
        get() = int("backend", BACKEND_AUTO)
        set(v) = sp.edit().putInt("backend", v).apply()

    /** "" = follow system, otherwise "en" or "hi". */
    var language: String
        get() = str("lang", "")
        set(v) = sp.edit().putString("lang", v).apply()

    var autoGameMode: Boolean
        get() = bool("auto", true)
        set(v) = sp.edit().putBoolean("auto", v).apply()

    var bubble: Boolean
        get() = bool("bubble", true)
        set(v) = sp.edit().putBoolean("bubble", v).apply()

    var keyHints: Boolean
        get() = bool("hints", false)
        set(v) = sp.edit().putBoolean("hints", v).apply()

    var globalSens: Float
        get() = float("gsens", 1f)
        set(v) = sp.edit().putFloat("gsens", v).apply()

    var crosshair: Boolean
        get() = bool("xh", false)
        set(v) = sp.edit().putBoolean("xh", v).apply()

    /** 0 dot, 1 plus, 2 circle, 3 gap-cross. */
    var crosshairShape: Int
        get() = int("xhShape", 1)
        set(v) = sp.edit().putInt("xhShape", v).apply()

    var crosshairSizeDp: Int
        get() = int("xhSize", 14)
        set(v) = sp.edit().putInt("xhSize", v).apply()

    var crosshairColor: Int
        get() = int("xhColor", Color.GREEN)
        set(v) = sp.edit().putInt("xhColor", v).apply()

    var crosshairOffX: Int
        get() = int("xhOffX", 0)
        set(v) = sp.edit().putInt("xhOffX", v).apply()

    var crosshairOffY: Int
        get() = int("xhOffY", 0)
        set(v) = sp.edit().putInt("xhOffY", v).apply()

    var editorOpacity: Float
        get() = float("edAlpha", 0.8f)
        set(v) = sp.edit().putFloat("edAlpha", v).apply()

    var editorGrid: Boolean
        get() = bool("edGrid", true)
        set(v) = sp.edit().putBoolean("edGrid", v).apply()

    /** Package of the profile used when Game Mode is toggled manually. */
    var activePkg: String
        get() = str("activePkg", "")
        set(v) = sp.edit().putString("activePkg", v).apply()

    var onboardingDone: Boolean
        get() = bool("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    companion object {
        const val BACKEND_AUTO = 0
        const val BACKEND_ACCESSIBILITY = 1
        const val BACKEND_SHIZUKU = 2
    }
}
