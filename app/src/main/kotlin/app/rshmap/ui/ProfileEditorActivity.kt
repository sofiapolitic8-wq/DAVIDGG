package app.rshmap.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import app.rshmap.R
import app.rshmap.core.MouseSettings
import app.rshmap.core.Profile
import app.rshmap.core.ProfileCodec
import app.rshmap.core.SmoothingMode
import app.rshmap.core.Templates
import app.rshmap.data.ProfileStore
import app.rshmap.databinding.ActivityProfileBinding
import app.rshmap.service.GameService
import app.rshmap.util.Perms

class ProfileEditorActivity : BaseActivity() {
    private lateinit var b: ActivityProfileBinding
    private lateinit var store: ProfileStore
    private lateinit var pkg: String
    private lateinit var profile: Profile
    private lateinit var templates: List<Profile>
    private var loading = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(b.root)
        store = ProfileStore(this)
        pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
        if (pkg.isEmpty()) { finish(); return }
        templates = Templates.all()
        profile = store.get(pkg)
            ?: (Templates.forPackage(pkg) ?: templates.first { it.name == "Generic FPS" }).copy(packageName = pkg)

        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        b.tvApp.text = "$label\n$pkg"

        b.spTemplate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, templates.map { it.name })
        b.spMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("EMA", "1-Euro"))

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { updateLabels() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }
        for (s in listOf(b.sbSensX, b.sbSensY, b.sbAds, b.sbSmooth)) s.setOnSeekBarChangeListener(seekListener)

        b.btnApplyTemplate.setOnClickListener {
            val t = templates[b.spTemplate.selectedItemPosition]
            profile = t.copy(packageName = pkg, name = profile.name)
            bind(); Toast.makeText(this, R.string.template_applied, Toast.LENGTH_SHORT).show()
        }
        b.btnSave.setOnClickListener { save(); Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show() }
        b.btnPlay.setOnClickListener {
            if (!ready()) return@setOnClickListener
            save(); GameService.send(this, GameService.ACTION_START, pkg)
        }
        b.btnEditLayout.setOnClickListener {
            if (!ready()) return@setOnClickListener
            save(); GameService.send(this, GameService.ACTION_EDIT, pkg)
            Toast.makeText(this, R.string.editing_hint, Toast.LENGTH_LONG).show()
        }
        b.btnDelete.setOnClickListener { store.delete(pkg); Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show(); finish() }
        b.btnExport.setOnClickListener {
            val json = ProfileCodec.encode(collect())
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("profile", json))
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, json), null))
        }
        b.btnExportFile.setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "${profile.name.ifBlank { pkg }}.rshmap.json"),
                REQ_EXPORT,
            )
        }
        b.btnImport.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            importJson(text)
        }
        b.btnImportFile.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_IMPORT)
        }
        bind()
    }

    private fun ready(): Boolean {
        if (!Perms.canOverlay(this)) {
            Toast.makeText(this, R.string.err_no_overlay, Toast.LENGTH_LONG).show()
            startActivity(Perms.overlayIntent(this))
            return false
        }
        return true
    }

    private fun bind() {
        loading = true
        val m = profile.mouse
        b.etName.setText(profile.name)
        b.sbSensX.progress = (m.sensX * 20f).toInt()
        b.sbSensY.progress = (m.sensY * 20f).toInt()
        b.sbAds.progress = (m.adsMultiplier * 100f).toInt()
        b.sbSmooth.progress = m.smoothing
        b.spMode.setSelection(if (m.smoothingMode == SmoothingMode.ONE_EURO) 1 else 0)
        b.swInvert.isChecked = m.invertY
        b.swAccel.isChecked = m.accelEnabled
        loading = false
        updateLabels()
    }

    private fun updateLabels() {
        b.tvSensX.text = getString(R.string.sens_x, sens(b.sbSensX))
        b.tvSensY.text = getString(R.string.sens_y, sens(b.sbSensY))
        b.tvAds.text = getString(R.string.ads_mult, b.sbAds.progress / 100f)
        b.tvSmooth.text = getString(R.string.smoothing, b.sbSmooth.progress)
    }

    private fun sens(s: SeekBar) = (s.progress / 20f).coerceAtLeast(0.05f)

    private fun collect(): Profile = profile.copy(
        name = b.etName.text.toString().ifBlank { pkg },
        packageName = pkg,
        mouse = MouseSettings(
            sensX = sens(b.sbSensX), sensY = sens(b.sbSensY),
            adsMultiplier = (b.sbAds.progress / 100f).coerceAtLeast(0.05f),
            smoothingMode = if (b.spMode.selectedItemPosition == 1) SmoothingMode.ONE_EURO else SmoothingMode.EMA,
            smoothing = b.sbSmooth.progress,
            accelEnabled = b.swAccel.isChecked,
            accelGain = profile.mouse.accelGain,
            invertY = b.swInvert.isChecked,
            deadZone = profile.mouse.deadZone,
        ),
    )

    private fun save() {
        profile = ProfileCodec.sanitize(collect())
        store.save(profile)
    }

    private fun importJson(text: String) {
        try {
            val p = ProfileCodec.decode(text)
            profile = p.copy(packageName = pkg)
            bind()
            Toast.makeText(this, R.string.imported, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        try {
            when (requestCode) {
                REQ_EXPORT -> contentResolver.openOutputStream(uri)?.use { it.write(ProfileCodec.encode(collect()).toByteArray()) }
                REQ_IMPORT -> importJson(contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty())
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        private const val REQ_EXPORT = 11
        private const val REQ_IMPORT = 12
    }
}
