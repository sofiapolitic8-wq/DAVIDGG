package app.rshmap.ui

import android.app.Activity
import android.content.Context
import app.rshmap.util.LocaleUtil

open class BaseActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtil.wrap(newBase))
    }
}
