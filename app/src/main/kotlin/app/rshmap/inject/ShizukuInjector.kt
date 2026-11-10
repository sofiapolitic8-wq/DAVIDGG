package app.rshmap.inject

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.view.MotionEvent
import app.rshmap.BuildConfig
import app.rshmap.input.Stats
import app.rshmap.shizuku.IInjector
import app.rshmap.shizuku.InjectorService
import rikka.shizuku.Shizuku

/**
 * Backend B: real multi-touch MotionEvents injected through a Shizuku user service.
 * Calls are one-way Binder transactions (no waiting), so latency is a couple of milliseconds.
 */
class ShizukuInjector(private val ctx: Context) : TouchInjector {
    override val name = "Shizuku"
    override var onError: ((String) -> Unit)? = null

    @Volatile private var svc: IInjector? = null
    private var binding = false

    private val args = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, InjectorService::class.java.name))
        .daemon(false)
        .processNameSuffix("injector")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            svc = if (b != null && b.pingBinder()) IInjector.Stub.asInterface(b) else null
        }

        override fun onServiceDisconnected(n: ComponentName?) {
            svc = null
            binding = false
        }
    }

    val ready: Boolean get() = svc != null

    /** Main thread. Starts binding if Shizuku is running and permitted. Returns true when usable now. */
    fun connect(): Boolean {
        if (svc != null) return true
        if (!isAvailable() || binding) return svc != null
        binding = true
        try {
            Shizuku.bindUserService(args, conn)
        } catch (e: Throwable) {
            binding = false
        }
        return false
    }

    fun disconnect() {
        try {
            if (binding || svc != null) Shizuku.unbindUserService(args, conn, true)
        } catch (ignored: Throwable) {
        }
        svc = null
        binding = false
    }

    // ---- pointer state (input thread only) ----
    private val ids = IntArray(MAX)
    private val xs = FloatArray(MAX)
    private val ys = FloatArray(MAX)
    private var count = 0
    private var downTime = 0L
    private var dirty = false

    override fun start(): Boolean {
        Stats.backend = name
        Stats.chunkMs = 0f
        return svc != null
    }

    override fun setScreen(widthPx: Int, heightPx: Int) {}

    private fun indexOf(id: Int): Int {
        for (i in 0 until count) if (ids[i] == id) return i
        return -1
    }

    override fun down(id: Int, x: Float, y: Float) {
        if (count >= MAX || indexOf(id) >= 0) return
        ids[count] = id; xs[count] = x; ys[count] = y
        count++
        val action = if (count == 1) {
            downTime = SystemClock.uptimeMillis()
            MotionEvent.ACTION_DOWN
        } else {
            MotionEvent.ACTION_POINTER_DOWN or ((count - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        }
        send(action)
    }

    override fun move(id: Int, x: Float, y: Float) {
        val i = indexOf(id)
        if (i < 0) return
        if (xs[i] != x || ys[i] != y) { xs[i] = x; ys[i] = y; dirty = true }
    }

    override fun up(id: Int) {
        val i = indexOf(id)
        if (i < 0) return
        flush()
        val action = if (count == 1) MotionEvent.ACTION_UP
        else MotionEvent.ACTION_POINTER_UP or (i shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        send(action)
        for (k in i until count - 1) { ids[k] = ids[k + 1]; xs[k] = xs[k + 1]; ys[k] = ys[k + 1] }
        count--
    }

    override fun flush() {
        if (dirty && count > 0) {
            send(MotionEvent.ACTION_MOVE)
        }
        dirty = false
    }

    private fun send(action: Int) {
        val s = svc ?: return
        try {
            val t0 = SystemClock.elapsedRealtimeNanos()
            s.inject(action, count, ids, xs, ys, downTime, SystemClock.uptimeMillis())
            val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000f
            Stats.injectOverheadMs = Stats.injectOverheadMs * 0.95f + ms * 0.05f
        } catch (e: Exception) {
            svc = null
            onError?.invoke("Shizuku connection lost")
        }
    }

    override fun releaseAll() {
        while (count > 0) up(ids[count - 1])
        dirty = false
    }

    override fun stop() = releaseAll()

    companion object {
        private const val MAX = 10
        const val PERMISSION_REQUEST = 4242

        fun installed(c: Context): Boolean = try {
            c.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true
        } catch (ignored: Exception) { false }

        fun running(): Boolean = try { Shizuku.pingBinder() } catch (ignored: Throwable) { false }

        fun isAvailable(): Boolean = try {
            Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (ignored: Throwable) { false }

        fun requestPermission() {
            try { Shizuku.requestPermission(PERMISSION_REQUEST) } catch (ignored: Throwable) {}
        }
    }
}
