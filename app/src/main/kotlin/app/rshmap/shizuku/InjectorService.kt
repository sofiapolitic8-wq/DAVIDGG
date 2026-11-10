package app.rshmap.shizuku

import android.os.IBinder
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.lang.reflect.Method
import kotlin.system.exitProcess

/**
 * Runs inside the Shizuku server process (shell uid), so it is allowed to inject input events.
 * PointerProperties/PointerCoords are pooled; one MotionEvent is obtained+recycled per call.
 * Must keep a public no-arg constructor (instantiated by Shizuku by class name).
 */
class InjectorService : IInjector.Stub() {
    private val props = Array(MAX) { MotionEvent.PointerProperties() }
    private val coords = Array(MAX) { MotionEvent.PointerCoords() }
    private var target: Any? = null
    private var method: Method? = null

    init {
        exemptHiddenApis()
        resolve()
    }

    override fun destroy() {
        exitProcess(0)
    }

    override fun ping(): Boolean = method != null

    override fun inject(action: Int, count: Int, ids: IntArray, xs: FloatArray, ys: FloatArray, downTime: Long, eventTime: Long) {
        val m = method ?: return
        val n = minOf(count, MAX, ids.size, xs.size, ys.size)
        if (n <= 0) return
        for (i in 0 until n) {
            props[i].clear()
            props[i].id = ids[i]
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[i].clear()
            coords[i].x = xs[i]
            coords[i].y = ys[i]
            coords[i].pressure = 1f
            coords[i].size = 1f
        }
        val ev = MotionEvent.obtain(
            downTime, eventTime, action, n, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        try {
            m.invoke(target, ev, 0) // INJECT_INPUT_EVENT_MODE_ASYNC
        } catch (ignored: Throwable) {
        } finally {
            ev.recycle()
        }
    }

    private fun resolve() {
        for (c in arrayOf("android.hardware.input.InputManagerGlobal", "android.hardware.input.InputManager")) {
            try {
                val cls = Class.forName(c)
                val inst = cls.getMethod("getInstance").invoke(null)
                method = cls.getMethod("injectInputEvent", InputEvent::class.java, Integer.TYPE)
                target = inst
                return
            } catch (ignored: Throwable) {
            }
        }
        try {
            val sm = Class.forName("android.os.ServiceManager")
            val binder = sm.getMethod("getService", String::class.java).invoke(null, "input") as IBinder
            val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
            val inst = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
            method = inst.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Integer.TYPE)
            target = inst
        } catch (ignored: Throwable) {
        }
    }

    /** Best effort: lets reflection reach @hide input APIs inside this (non-app) process. */
    private fun exemptHiddenApis() {
        try {
            val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod", String::class.java, Class.forName("[Ljava.lang.Class;"),
            )
            val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null) as Method
            val setExemptions = getDeclaredMethod.invoke(
                vmRuntimeClass, "setHiddenApiExemptions", arrayOf<Class<*>>(Array<String>::class.java),
            ) as Method
            setExemptions.invoke(getRuntime.invoke(null), arrayOf("L"))
        } catch (ignored: Throwable) {
        }
    }

    private companion object {
        const val MAX = 10
    }
}
