package app.rshmap.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import app.rshmap.R
import app.rshmap.databinding.ActivityOnboardingBinding
import app.rshmap.databinding.ItemStepBinding
import app.rshmap.inject.ShizukuInjector
import app.rshmap.service.PadAccessibilityService
import app.rshmap.util.Perms

class OnboardingActivity : BaseActivity() {
    private lateinit var b: ActivityOnboardingBinding
    private val rows = ArrayList<Row>()

    private class Row(val vb: ItemStepBinding, val ok: () -> Boolean, val act: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)

        add(R.string.step_overlay, R.string.step_overlay_d, { Perms.canOverlay(this) }) { startActivity(Perms.overlayIntent(this)) }
        add(R.string.step_a11y, R.string.step_a11y_d, { PadAccessibilityService.instance != null || Perms.a11yEnabled(this) }) {
            Toast.makeText(this, R.string.a11y_hint, Toast.LENGTH_LONG).show()
            startActivity(Perms.a11yIntent())
        }
        add(R.string.step_notif, R.string.step_notif_d, { Perms.notificationsEnabled(this) }) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        add(R.string.step_battery, R.string.step_battery_d, { Perms.batteryOptimizationIgnored(this) }) {
            runCatching { startActivity(Perms.batteryIntent(this)) }
        }
        add(R.string.step_shizuku, R.string.step_shizuku_d, { ShizukuInjector.isAvailable() }) {
            when {
                !ShizukuInjector.installed(this) ->
                    Toast.makeText(this, R.string.shizuku_install, Toast.LENGTH_LONG).show()
                !ShizukuInjector.running() ->
                    Toast.makeText(this, R.string.shizuku_start, Toast.LENGTH_LONG).show()
                else -> ShizukuInjector.requestPermission()
            }
        }
    }

    private fun add(title: Int, desc: Int, ok: () -> Boolean, act: () -> Unit) {
        val vb = ItemStepBinding.inflate(layoutInflater, b.container, true)
        vb.title.setText(title)
        vb.desc.setText(desc)
        vb.action.setText(R.string.open)
        vb.action.setOnClickListener { act() }
        rows.add(Row(vb, ok, act))
    }

    override fun onResume() {
        super.onResume()
        for (r in rows) {
            val good = r.ok()
            r.vb.state.setText(if (good) R.string.ok else R.string.missing)
            r.vb.state.setTextColor(getColor(if (good) R.color.ok else R.color.bad))
        }
    }
}
