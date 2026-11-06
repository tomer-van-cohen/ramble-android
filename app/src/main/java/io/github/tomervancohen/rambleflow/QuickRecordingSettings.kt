package io.github.tomervancohen.rambleflow

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Only exported Settings entry points. No hidden fragments, permissions or phone-setting changes. */
internal object QuickRecordingSettings {
    enum class Destination { SIDE_BUTTON, ADVANCED_FEATURES, SETTINGS }
    internal fun candidates(device: RecorderDevice): List<Pair<Destination, Intent>> {
        val result = mutableListOf<Pair<Destination, Intent>>()
        if (device.family == RecorderFamily.SAMSUNG) {
            // Confirmed from PackageManager on the Samsung One UI lab device, then launched as our UID.
            result += Destination.SIDE_BUTTON to Intent().setComponent(ComponentName(
                "com.android.settings", "com.android.settings.Settings\$FunctionKeySettingsActivity"))
            result += Destination.ADVANCED_FEATURES to Intent().setComponent(ComponentName(
                "com.android.settings", "com.android.settings.Settings\$UsefulFeatureMainActivity"))
        }
        result += Destination.SETTINGS to Intent(Settings.ACTION_SETTINGS)
        return result
    }
    fun open(context: Context, device: RecorderDevice): Destination {
        return launch(device) { intent ->
            @Suppress("DEPRECATION")
            val entry = context.packageManager.resolveActivity(intent, 0)?.activityInfo
            if (entry == null || !entry.exported) false else {
                try {
                    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } catch (_: android.content.ActivityNotFoundException) { false }
                catch (_: SecurityException) { false }
            }
        }
    }
    internal fun launch(device: RecorderDevice, start: (Intent) -> Boolean): Destination {
        for ((destination, intent) in candidates(device)) if (start(intent)) return destination
        error("settings_unavailable")
    }
}
