package app.rshmap.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import app.rshmap.R
import app.rshmap.databinding.ActivitySettingsBinding
import app.rshmap.util.KeyNames
import app.rshmap.util.Settings

class SettingsActivity : BaseActivity() {
    private lateinit var b: ActivitySettingsBinding
    private lateinit var s: Settings
    private val colors = intArrayOf(Color.GREEN, Color.RED, Color.WHITE, Color.CYAN, Color.YELLOW, Color.MAGENTA)
    private val langs = arrayOf("", "en", "hi")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        s = Settings(this)

        updateHotkeyLabel()
        b.btnHotkey.setOnClickListener {
            AlertDialog.Builder(this).setMessage(R.string.press_hotkey)
                .setOnKeyListener { d, code, ev ->
                    if (ev.action == KeyEvent.ACTION_DOWN && code != KeyEvent.KEYCODE_BACK) {
                        s.hotkey = code; updateHotkeyLabel(); d.dismiss(); true
                    } else false
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        }

        spinner(b.spBackend, listOf(getString(R.string.backend_auto), getString(R.string.backend_a11y), "Shizuku"), s.backend) { s.backend = it }
        spinner(b.spLang, listOf(getString(R.string.lang_system), "English", "हिन्दी"), langs.indexOf(s.language).coerceAtLeast(0)) {
            if (langs[it] != s.language) {
                s.language = langs[it]
                recreate()
            }
        }
        spinner(b.spShape, listOf(getString(R.string.shape_dot), getString(R.string.shape_plus), getString(R.string.shape_circle), getString(R.string.shape_gap)), s.crosshairShape) { s.crosshairShape = it }
        spinner(b.spColor, listOf("Green", "Red", "White", "Cyan", "Yellow", "Magenta"), colors.indexOf(s.crosshairColor).coerceAtLeast(0)) { s.crosshairColor = colors[it] }

        seek(b.sbGlobal, (s.globalSens * 100).toInt() - 10, { s.globalSens = (it + 10) / 100f }) {
            b.tvGlobal.text = getString(R.string.global_sens, (it + 10) / 100f)
        }
        seek(b.sbSize, s.crosshairSizeDp - 4, { s.crosshairSizeDp = it + 4 }) { b.tvSize.text = getString(R.string.crosshair_size, it + 4) }
        seek(b.sbOffX, s.crosshairOffX + 100, { s.crosshairOffX = it - 100 }) { b.tvOffX.text = getString(R.string.crosshair_off_x, it - 100) }
        seek(b.sbOffY, s.crosshairOffY + 100, { s.crosshairOffY = it - 100 }) { b.tvOffY.text = getString(R.string.crosshair_off_y, it - 100) }

        b.swAuto.isChecked = s.autoGameMode; b.swAuto.setOnCheckedChangeListener { _, v -> s.autoGameMode = v }
        b.swBubble.isChecked = s.bubble; b.swBubble.setOnCheckedChangeListener { _, v -> s.bubble = v }
        b.swHints.isChecked = s.keyHints; b.swHints.setOnCheckedChangeListener { _, v -> s.keyHints = v }
        b.swCross.isChecked = s.crosshair; b.swCross.setOnCheckedChangeListener { _, v -> s.crosshair = v }
    }

    private fun updateHotkeyLabel() {
        b.btnHotkey.text = getString(R.string.hotkey_label, KeyNames.of(s.hotkey))
    }

    private fun spinner(sp: Spinner, items: List<String>, selected: Int, onPick: (Int) -> Unit) {
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        sp.setSelection(selected)
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { onPick(pos) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun seek(sb: SeekBar, initial: Int, store: (Int) -> Unit, label: (Int) -> Unit) {
        sb.progress = initial.coerceIn(0, sb.max)
        label(sb.progress)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { label(p); if (fromUser) store(p) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }
}
