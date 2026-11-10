package app.rshmap.ui

import android.os.Bundle
import app.rshmap.BuildConfig
import app.rshmap.R
import app.rshmap.databinding.ActivityTextBinding

class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityTextBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.body.text = getString(R.string.about_body, BuildConfig.VERSION_NAME) + "\n\n" +
            getString(R.string.fair_play) + "\n\n" + getString(R.string.faq)
    }
}
