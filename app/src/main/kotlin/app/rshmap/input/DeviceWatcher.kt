package app.rshmap.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice

/** Tracks keyboard / mouse hot-plug and publishes a human readable list to [Stats.devices]. */
class DeviceWatcher(context: Context, private val onChange: () -> Unit) : InputManager.InputDeviceListener {
    private val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager

    var hasMouse = false
        private set
    var hasKeyboard = false
        private set

    fun start() {
        im.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        im.unregisterInputDeviceListener(this)
    }

    fun refresh() {
        val sb = StringBuilder()
        var m = false
        var k = false
        for (id in im.inputDeviceIds) {
            val d = im.getInputDevice(id) ?: continue
            if (d.isVirtual) continue
            val mouse = d.supportsSource(InputDevice.SOURCE_MOUSE) || d.supportsSource(InputDevice.SOURCE_MOUSE_RELATIVE)
            val kbd = d.supportsSource(InputDevice.SOURCE_KEYBOARD) && d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
            if (!mouse && !kbd) continue
            m = m || mouse
            k = k || kbd
            sb.append(if (mouse) "🖱 " else "⌨ ").append(d.name).append('\n')
        }
        hasMouse = m
        hasKeyboard = k
        Stats.devices = sb.toString().trimEnd()
    }

    override fun onInputDeviceAdded(deviceId: Int) { refresh(); onChange() }
    override fun onInputDeviceRemoved(deviceId: Int) { refresh(); onChange() }
    override fun onInputDeviceChanged(deviceId: Int) { refresh() }
}
