package app.rshmap.service

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Used for (1) touch injection via dispatchGesture, (2) detecting which app is in the foreground so
 * profiles auto-switch, and (3) the Game Mode hotkey, which keeps working even when the capture
 * overlay is not focused. It never reads screen content (canRetrieveWindowContent = false).
 */
class PadAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        GameService.instance?.onA11yChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        GameService.instance?.onForegroundPackage(pkg)
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val gs = GameService.instance ?: return false
        if (event.keyCode != gs.hotkey) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) gs.toggleGameMode()
        return true
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        GameService.instance?.onA11yChanged()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: PadAccessibilityService? = null
    }
}
