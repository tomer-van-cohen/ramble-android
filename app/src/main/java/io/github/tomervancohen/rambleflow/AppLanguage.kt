package io.github.tomervancohen.rambleflow

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.util.Locale

internal object AppLanguage {
    fun wrap(base: Context): Context {
        val chosen = Settings(base).appLanguage
        val system = Resources.getSystem().configuration.locales[0]
        val osChoice = if (Build.VERSION.SDK_INT >= 33)
            base.getSystemService(LocaleManager::class.java).applicationLocales.let {
                if (it.isEmpty) null else it[0]
            } else null
        val locale = if (chosen.isBlank()) osChoice ?: system else Locale.forLanguageTag(chosen)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}

@Composable
fun AppLanguagePicker(activity: Activity, settings: Settings) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("" to appText(R.string.ui_phone_language_5bc1fa5), "en" to "English", "he" to "עברית")
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(appText(R.string.ui_app_language_0f6939c) + ": " +
                (options.find { it.first == settings.appLanguage }?.second ?: "English"))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (code, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    expanded = false
                    settings.appLanguage = code
                    settings.resetPromptLanguage()
                    activity.intent.putExtra("ramble.language.settings", true)
                    if (Build.VERSION.SDK_INT >= 33) {
                        val manager = activity.getSystemService(LocaleManager::class.java)
                        val next = if (code.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(code)
                        val same = manager.applicationLocales == next
                        manager.applicationLocales = next
                        if (same) activity.recreate()
                    } else activity.recreate()
                })
            }
        }
    }
}
