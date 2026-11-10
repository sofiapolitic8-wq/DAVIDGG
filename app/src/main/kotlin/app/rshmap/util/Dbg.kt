package app.rshmap.util

import android.util.Log
import app.rshmap.BuildConfig

/** Debug-only logging. Calls compile to a constant-false branch in release (and R8 strips Log). */
object Dbg {
    fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d("RSHMap", msg)
    }

    fun e(msg: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e("RSHMap", msg, t)
    }
}
