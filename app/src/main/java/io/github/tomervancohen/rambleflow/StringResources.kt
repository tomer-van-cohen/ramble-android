package io.github.tomervancohen.rambleflow

import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

/** Android resource catalog shared by Compose and background-generated user messages. */
internal fun appText(@StringRes id: Int, vararg args: Any): String {
    val app=RambleApp.instance
    val config=Configuration(app.resources.configuration).apply { setLocale(Locale.getDefault()) }
    return app.createConfigurationContext(config).getString(id,*args)
}
