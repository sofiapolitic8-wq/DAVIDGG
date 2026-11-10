package app.rshmap.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleUtil {
    fun wrap(base: Context): Context {
        val lang = Settings(base).language
        if (lang.isEmpty()) return base
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(Locale(lang))
        return base.createConfigurationContext(cfg)
    }
}
