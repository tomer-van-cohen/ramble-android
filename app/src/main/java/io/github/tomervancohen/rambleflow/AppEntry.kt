package io.github.tomervancohen.rambleflow

/** Every external entry respects setup, including old pinned recording shortcuts. */
internal object AppEntry {
    fun canRecord(step: Int, hasKey: Boolean) = step >= 10 && hasKey

    fun screen(step: Int, hasKey: Boolean, live: Boolean = false, language: Boolean = false): String = when {
        step < 10 -> if (step == 4) "3" else step.toString()
        !hasKey -> "key"
        live -> "recording-live"
        language -> "settings"
        else -> "home"
    }
}

internal fun Settings.canQuickRecord() = AppEntry.canRecord(step, hasKey(provider))
