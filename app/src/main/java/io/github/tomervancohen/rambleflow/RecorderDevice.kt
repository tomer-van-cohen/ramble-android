package io.github.tomervancohen.rambleflow

import android.os.Build
import java.util.Locale


enum class RecorderFamily {
    SAMSUNG,
    PIXEL,
    XIAOMI,
    OTHER,
}

data class RecorderDevice(
    val family: RecorderFamily,
    val packages: List<String>,
    val suggestedFolder: String,
) {
    companion object {
        fun forManufacturer(manufacturer: String): RecorderDevice =
            when (manufacturer.lowercase(Locale.ROOT)) {
                "samsung" ->
                    RecorderDevice(
                        RecorderFamily.SAMSUNG,
                        listOf("com.sec.android.app.voicenote"),
                        "Recordings",
                    )
                "google" ->
                    RecorderDevice(
                        RecorderFamily.PIXEL,
                        listOf("com.google.android.apps.recorder"),
                        "Recordings",
                    )
                "xiaomi",
                "redmi",
                "poco" ->
                    RecorderDevice(
                        RecorderFamily.XIAOMI,
                        listOf("com.android.soundrecorder", "com.miui.soundrecorder"),
                        "MIUI/sound_recorder",
                    )
                else ->
                    RecorderDevice(
                        RecorderFamily.OTHER,
                        listOf("com.android.soundrecorder"),
                        "Recordings",
                    )
            }

        fun current() = forManufacturer(Build.MANUFACTURER)

        val callFolders = setOf("Call", "call_rec")
        val voiceFolders = listOf("Voice Recorder", "Recorder", "sound_recorder", "Sound_recorder")
    }
}
