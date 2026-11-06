package io.github.tomervancohen.rambleflow

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Opt-in interactive lab pilot. Never runs as part of the normal suite. No provider network. */
class SamsungNativePilotTest {
    @Test
    fun manualNativeRecorderBackgroundPilot() =
        runBlocking<Unit> {
            org.junit.Assume.assumeTrue(
                InstrumentationRegistry.getArguments().getString("rambleLabPilot") == "true"
            )
            val app = ApplicationProvider.getApplicationContext<RambleApp>()
            WorkManager.getInstance(app).cancelAllWork().result.get()
            app.database.clearAllTables();SyntheticAudioFixtures.clear(app)
            app.settings.paused = false
            app.settings.tree = ""
            app.settings.outputTree = ""
            app.settings.driveEnabled = false
            app.settings.wifiOnly = false
            app.settings.provider = "groq"
            app.settings.saveKey("groq", "synthetic-samsung-lab-key-not-live")
            app.settings.step = 3
            var calls = 0
            app.transcriber =
                object : Transcriber {
                    override fun transcribe(provider: String, key: String, file: File): String {
                        calls++
                        return "Synthetic provider: Samsung native recorder background delivery verified."
                    }
                }
            try {
                app.startActivity(
                    Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                println("PILOT: Grant Recordings folder in the UI now.")
                withTimeout(240_000) {
                    while (app.database.queue().sources().none { it.enabled }) delay(500)
                }
                WorkManager.getInstance(app).cancelAllWork().result.get()
                MediaWake.arm(app, replace = true)
                InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .executeShellCommand("input keyevent KEYCODE_HOME")
                    .close()
                val started = System.currentTimeMillis()
                println("PILOT: Armed. Record/save in Samsung recorder now; do not open Ramble.")
                withTimeout(300_000) {
                    while (app.database.queue().recordings().none { it.state == "saved" }) delay(
                        1000
                    )
                }
                val saved = app.database.queue().recordings().single { it.state == "saved" }
                assertEquals(1, calls)
                assertTrue(
                    app.getSharedPreferences("media_wake", 0).getLong("lastEvent", 0) >= started
                )
                assertTrue(
                    LocalExport(app, app.database)
                        .file(saved.id)
                        .readText()
                        .contains("Samsung native recorder")
                )
                println(
                    "PILOT: Saved id=${saved.id}, elapsedMs=${System.currentTimeMillis()-started}, calls=$calls, event=${app.getSharedPreferences("media_wake",0).getLong("lastEvent",0)}"
                )
            } finally {
                app.settings.paused = true
                MediaWake.arm(app)
                WorkManager.getInstance(app).cancelAllWork().result.get()
                app.transcriber = ProviderApi()
            }
        }
}
