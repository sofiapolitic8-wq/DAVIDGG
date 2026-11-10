package app.rshmap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import app.rshmap.R
import app.rshmap.core.Profile
import app.rshmap.core.Templates
import app.rshmap.data.ProfileStore
import app.rshmap.inject.AccessibilityInjector
import app.rshmap.inject.ShizukuInjector
import app.rshmap.inject.TouchInjector
import app.rshmap.input.DeviceWatcher
import app.rshmap.input.InputHub
import app.rshmap.input.Stats
import app.rshmap.overlay.BubbleView
import app.rshmap.overlay.CaptureView
import app.rshmap.overlay.EditorView
import app.rshmap.overlay.Overlays
import app.rshmap.overlay.TouchTrace
import app.rshmap.ui.MainActivity
import app.rshmap.util.Dbg
import app.rshmap.util.LocaleUtil
import app.rshmap.util.Perms
import app.rshmap.util.Settings

/**
 * Foreground service that owns the input thread, the overlays and the injector backends.
 * Main-thread only, except where noted (work for the input thread goes through [InputHub.post]).
 */
class GameService : Service(), CaptureView.Callback {

    private lateinit var wm: WindowManager
    private lateinit var settings: Settings
    private lateinit var store: ProfileStore
    private lateinit var hub: InputHub
    private lateinit var devices: DeviceWatcher
    private val main = Handler(Looper.getMainLooper())

    private var shizuku: ShizukuInjector? = null
    private var a11yInjector: AccessibilityInjector? = null
    private var a11yOwner: PadAccessibilityService? = null

    private var capture: CaptureView? = null
    private var bubble: BubbleView? = null
    private var editor: EditorView? = null

    private var gameMode = false
    private var manual = false
    private var currentProfile: Profile? = null
    private var currentFg = ""

    val hotkey: Int get() = settings.hotkey
    val isGameMode: Boolean get() = gameMode

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            disableGameMode()
        }
    }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(LocaleUtil.wrap(base))

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        settings = Settings(this)
        store = ProfileStore(this)
        hub = InputHub()
        devices = DeviceWatcher(this) { main.post { capture?.reRequestCapture() } }
        devices.start()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        startForegroundCompat()
        if (settings.backend != Settings.BACKEND_ACCESSIBILITY) {
            shizuku = ShizukuInjector(this).also { it.connect() }
        }
        if (settings.bubble && Perms.canOverlay(this)) addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        val pkg = intent?.getStringExtra(EXTRA_PKG).orEmpty()
        when (intent?.action) {
            ACTION_START -> startGame(pkg)
            ACTION_EDIT -> startEdit(pkg)
            ACTION_TOGGLE -> toggleGameMode()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (gameMode) pushScreenSize()
    }

    override fun onDestroy() {
        disableGameMode()
        removeEditor()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        runCatching { unregisterReceiver(screenOff) }
        devices.stop()
        hub.shutdown()
        // Give the input thread time to lift every finger before the Shizuku binder goes away.
        val sz = shizuku
        Handler(Looper.getMainLooper()).postDelayed({ sz?.disconnect() }, 800)
        instance = null
        Stats.gameMode = false
        super.onDestroy()
    }

    // ------------------------------------------------------------------ notification

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)
    }

    private fun buildNotification(): Notification {
        fun pi(action: String, code: Int) = PendingIntent.getService(
            this, code, intent(this, action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(if (gameMode) R.string.notif_on else R.string.notif_off))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_toggle), pi(ACTION_TOGGLE, 1)).build())
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_stop), pi(ACTION_STOP, 2)).build())
            .build()
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        bubble?.setActive(gameMode)
    }

    // ------------------------------------------------------------------ public control (main thread)

    fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    fun startGame(pkg: String) {
        if (pkg.isEmpty()) return
        settings.activePkg = pkg
        val p = store.get(pkg) ?: (Templates.forPackage(pkg) ?: Templates.all().first { it.name == "Generic FPS" }).copy(packageName = pkg)
        currentProfile = p
        manual = false
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(it) }
        }
        disableGameMode()
        enableGameMode()
    }

    fun toggleGameMode() {
        if (editor != null) return
        if (gameMode) { disableGameMode(); return }
        if (currentProfile == null) currentProfile = store.get(settings.activePkg)
        if (currentProfile == null) { toast(R.string.err_no_profile); return }
        manual = true
        enableGameMode()
    }

    fun onA11yChanged() {
        main.post { capture?.reRequestCapture() }
    }

    /** Called by the accessibility service (main thread) whenever a window changes. */
    fun onForegroundPackage(pkg: String) {
        if (pkg == packageName || pkg == "com.android.systemui" || pkg == currentFg) return
        currentFg = pkg
        if (!settings.autoGameMode || editor != null) return
        val p = store.get(pkg)
        if (p != null) {
            if (gameMode && currentProfile?.packageName == pkg) return
            currentProfile = p
            settings.activePkg = pkg
            manual = false
            if (gameMode) disableGameMode()
            enableGameMode()
        } else if (gameMode && !manual) {
            disableGameMode()
        }
    }

    // ------------------------------------------------------------------ game mode

    private fun screenSize(): Point {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            val pt = Point()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(pt)
            pt
        }
    }

    private fun pushScreenSize() {
        val s = screenSize()
        hub.post { hub.engine.setScreen(s.x, s.y); hub.injector?.setScreen(s.x, s.y) }
    }

    private fun pickInjector(): TouchInjector? {
        val pref = settings.backend
        val sz = shizuku
        if (pref != Settings.BACKEND_ACCESSIBILITY && sz != null && !sz.ready) sz.connect()
        val shizukuOk = sz != null && sz.ready
        if (pref == Settings.BACKEND_SHIZUKU && !shizukuOk) toast(R.string.err_shizuku_not_ready)
        if (shizukuOk && pref != Settings.BACKEND_ACCESSIBILITY) return sz
        val svc = PadAccessibilityService.instance
        if (svc == null) { toast(R.string.err_no_a11y); return null }
        if (a11yInjector == null || a11yOwner !== svc) {
            a11yInjector = AccessibilityInjector(svc, hub.handler)
            a11yOwner = svc
        }
        return a11yInjector
    }

    fun enableGameMode(showTouches: Boolean = false) {
        if (gameMode) return
        val p = currentProfile ?: run { toast(R.string.err_no_profile); return }
        if (!Perms.canOverlay(this)) { toast(R.string.err_no_overlay); return }
        val inj = pickInjector() ?: return
        inj.onError = { msg -> main.post { toast(getString(R.string.err_injection, msg)); disableGameMode() } }
        gameMode = true
        Stats.gameMode = true
        val s = screenSize()
        val mul = settings.globalSens
        val cv = CaptureView(this, hub, settings, this).also {
            it.hotkey = settings.hotkey
            it.setProfile(p)
        }
        if (showTouches) {
            val tr = TouchTrace(cv)
            cv.trace = tr
            hub.post { hub.trace = tr }
        } else hub.post { hub.trace = null }
        try {
            wm.addView(cv, Overlays.fullScreen(focusable = true, touchable = false))
            capture = cv
        } catch (e: Exception) {
            Dbg.e("addView failed", e)
            gameMode = false; Stats.gameMode = false
            toast(R.string.err_no_overlay)
            return
        }
        hub.post {
            hub.injector = inj
            inj.start()
            inj.setScreen(s.x, s.y)
            hub.engine.setScreen(s.x, s.y)
            hub.engine.setProfile(p, mul)
            hub.engine.setActive(true)
        }
        hub.startFrames()
        refreshNotification()
    }

    fun disableGameMode() {
        if (!gameMode) return
        gameMode = false
        manual = false
        Stats.gameMode = false
        Stats.captured = false
        capture?.let { runCatching { wm.removeView(it) } }
        capture = null
        hub.post {
            hub.engine.setActive(false)   // lifts every virtual finger
            hub.injector?.releaseAll()
            hub.trace = null
        }
        hub.stopFrames()
        refreshNotification()
    }

    // ------------------------------------------------------------------ CaptureView.Callback

    override fun onHotkey() = toggleGameMode()

    override fun onNeedFallback() {
        val cv = capture ?: return
        toast(R.string.warn_no_capture)
        cv.setFallback(true)
        runCatching { wm.updateViewLayout(cv, Overlays.fullScreen(focusable = true, touchable = true)) }
    }

    // ------------------------------------------------------------------ editor

    fun startEdit(pkg: String) {
        if (pkg.isEmpty()) return
        settings.activePkg = pkg
        val p = store.get(pkg) ?: (Templates.forPackage(pkg) ?: Templates.all().first { it.name == "Generic FPS" }).copy(packageName = pkg)
        currentProfile = p
        disableGameMode()
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(it) }
        }
        main.postDelayed({ showEditor(p) }, 1500)
    }

    private fun showEditor(p: Profile) {
        if (editor != null || !Perms.canOverlay(this)) return
        val labels = EditorView.Labels(
            add = "+", bind = getString(R.string.ed_bind), opts = getString(R.string.ed_opts), del = getString(R.string.ed_del),
            undo = getString(R.string.ed_undo), redo = getString(R.string.ed_redo), grid = getString(R.string.ed_grid),
            opMinus = "α−", opPlus = "α+", test = getString(R.string.ed_test), save = getString(R.string.ed_save), exit = getString(R.string.ed_exit),
            pressKey = getString(R.string.ed_press_key), pressKeyFor = getString(R.string.ed_press_key_for),
            tapStep = getString(R.string.ed_tap_step), up = getString(R.string.dir_up), left = getString(R.string.dir_left),
            down = getString(R.string.dir_down), right = getString(R.string.dir_right), modifier = getString(R.string.ed_modifier),
        )
        val v = EditorView(this, p, settings, labels, object : EditorView.Callback {
            override fun onSave(p: Profile) {
                store.save(p.copy(packageName = p.packageName.ifEmpty { settings.activePkg }))
                currentProfile = store.get(settings.activePkg)
                removeEditor()
                toast(R.string.saved)
            }

            override fun onTest(p: Profile) {
                store.save(p.copy(packageName = p.packageName.ifEmpty { settings.activePkg }))
                currentProfile = store.get(settings.activePkg)
                removeEditor()
                enableGameMode(showTouches = true)
            }

            override fun onExit() = removeEditor()
        })
        try {
            wm.addView(v, Overlays.fullScreen(focusable = true, touchable = true))
            editor = v
        } catch (e: Exception) {
            Dbg.e("editor addView", e)
        }
    }

    private fun removeEditor() {
        editor?.let { runCatching { wm.removeView(it) } }
        editor = null
    }

    // ------------------------------------------------------------------ bubble

    private fun addBubble() {
        if (bubble != null) return
        val size = (52 * resources.displayMetrics.density).toInt()
        val lp = Overlays.small(size).apply { x = 12; y = 200 }
        val b = BubbleView(this, wm, lp, { toggleGameMode() }, { if (editor == null) startEdit(settings.activePkg) })
        b.setActive(gameMode)
        try {
            wm.addView(b, lp)
            bubble = b
        } catch (e: Exception) {
            Dbg.e("bubble", e)
        }
    }

    companion object {
        const val ACTION_START = "app.rshmap.action.START"
        const val ACTION_EDIT = "app.rshmap.action.EDIT"
        const val ACTION_TOGGLE = "app.rshmap.action.TOGGLE"
        const val ACTION_STOP = "app.rshmap.action.STOP"
        const val EXTRA_PKG = "pkg"
        private const val CHANNEL = "rshmap_service"
        private const val NOTIF_ID = 1

        @Volatile var instance: GameService? = null

        fun intent(c: Context, action: String, pkg: String? = null): Intent =
            Intent(c, GameService::class.java).setAction(action).apply { if (pkg != null) putExtra(EXTRA_PKG, pkg) }

        fun send(c: Context, action: String, pkg: String? = null) {
            if (action == ACTION_STOP) {
                if (instance != null) c.startService(intent(c, action))
            } else {
                c.startForegroundService(intent(c, action, pkg))
            }
        }
    }
}
