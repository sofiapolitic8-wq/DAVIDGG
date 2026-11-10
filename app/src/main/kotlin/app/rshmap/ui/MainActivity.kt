package app.rshmap.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import app.rshmap.R
import app.rshmap.data.ProfileStore
import app.rshmap.databinding.ActivityMainBinding
import app.rshmap.databinding.ItemGameBinding
import app.rshmap.inject.ShizukuInjector
import app.rshmap.service.GameService
import app.rshmap.service.PadAccessibilityService
import app.rshmap.util.Perms
import app.rshmap.util.Settings

class MainActivity : BaseActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var store: ProfileStore
    private lateinit var settings: Settings
    private val items = ArrayList<AppItem>()
    private val adapter = Adapter()

    private class AppItem(val pkg: String, val label: String, val game: Boolean, val info: ApplicationInfo, var hasProfile: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        store = ProfileStore(this)
        settings = Settings(this)
        b.list.adapter = adapter
        b.list.setOnItemClickListener { _, _, pos, _ ->
            startActivity(Intent(this, ProfileEditorActivity::class.java).putExtra(ProfileEditorActivity.EXTRA_PKG, items[pos].pkg))
        }
        b.btnSetup.setOnClickListener { startActivity(Intent(this, OnboardingActivity::class.java)) }
        b.btnDiag.setOnClickListener { startActivity(Intent(this, DiagnosticsActivity::class.java)) }
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.btnAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        b.btnService.setOnClickListener {
            if (GameService.instance != null) GameService.send(this, GameService.ACTION_STOP)
            else GameService.send(this, GameService.ACTION_START)
            b.root.postDelayed({ refreshStatus() }, 500)
        }
        loadApps()
        if (!settings.onboardingDone) {
            settings.onboardingDone = true
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        for (i in items) i.hasProfile = store.has(i.pkg)
        adapter.notifyDataSetChanged()
        refreshStatus()
    }

    private fun refreshStatus() {
        val ok = getString(R.string.ok)
        val no = getString(R.string.missing)
        val shiz = if (ShizukuInjector.isAvailable()) ok else no
        b.tvStatus.text = getString(
            R.string.status_line,
            if (Perms.canOverlay(this)) ok else no,
            if (PadAccessibilityService.instance != null) ok else no,
            shiz,
        )
        b.btnService.setText(if (GameService.instance != null) R.string.stop_service else R.string.start_service)
    }

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(launcher, 0).mapNotNull { ri ->
                val ai = ri.activityInfo.applicationInfo
                if (ai.packageName == packageName) return@mapNotNull null
                @Suppress("DEPRECATION")
                val game = ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                AppItem(ai.packageName, ri.loadLabel(pm).toString(), game, ai, store.has(ai.packageName))
            }.distinctBy { it.pkg }
                .sortedWith(compareBy({ !it.hasProfile }, { !it.game }, { it.label.lowercase() }))
            runOnUiThread {
                items.clear(); items.addAll(list); adapter.notifyDataSetChanged()
            }
        }.start()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val vb = if (convertView == null) ItemGameBinding.inflate(LayoutInflater.from(this@MainActivity), parent, false)
            else ItemGameBinding.bind(convertView)
            val it = items[position]
            vb.name.text = it.label
            vb.sub.text = it.pkg
            vb.icon.setImageDrawable(it.info.loadIcon(packageManager))
            vb.badge.text = when {
                it.hasProfile -> getString(R.string.badge_profile)
                it.game -> getString(R.string.badge_game)
                else -> ""
            }
            return vb.root
        }
    }
}
