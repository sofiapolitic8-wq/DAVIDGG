package app.rshmap.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import app.rshmap.R
import app.rshmap.databinding.ActivityTextBinding
import app.rshmap.input.Stats

class DiagnosticsActivity : BaseActivity() {
    private lateinit var b: ActivityTextBinding
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            h.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTextBinding.inflate(layoutInflater)
        setContentView(b.root)
    }

    override fun onResume() { super.onResume(); h.post(tick) }
    override fun onPause() { h.removeCallbacks(tick); super.onPause() }

    private fun render() {
        val on = getString(R.string.on)
        val off = getString(R.string.off)
        val latency = if (Stats.chunkMs > 0f)
            getString(R.string.diag_latency_a11y, Stats.chunkMs, Stats.injectOverheadMs)
        else getString(R.string.diag_latency_shizuku, Stats.injectOverheadMs)
        b.body.text = buildString {
            append(getString(R.string.diag_devices)).append('\n')
            append(Stats.devices.ifEmpty { getString(R.string.diag_none) }).append("\n\n")
            append(getString(R.string.diag_hz, Stats.mouseHz)).append('\n')
            append(getString(R.string.diag_backend, Stats.backend)).append('\n')
            append(getString(R.string.diag_latency, latency)).append('\n')
            append(getString(R.string.diag_gamemode, if (Stats.gameMode) on else off)).append('\n')
            append(getString(R.string.diag_capture, if (Stats.captured) on else off)).append('\n')
            append(getString(R.string.diag_touches, Stats.activeTouches)).append("\n\n")
            append(getString(R.string.diag_hint))
        }
    }
}
