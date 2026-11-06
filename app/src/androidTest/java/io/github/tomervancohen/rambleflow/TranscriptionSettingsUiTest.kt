package io.github.tomervancohen.rambleflow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class TranscriptionSettingsUiTest {
    private val app get() = ApplicationProvider.getApplicationContext<RambleApp>()
    @get:Rule(order = 0) val fixture = object : org.junit.rules.ExternalResource() {
        override fun before() {
            app.settings.provider = "groq"
            app.settings.saveKey("groq", "synthetic-ui-test-key-not-live")
            check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
            app.settings.step = 10
            app.settings.paused = true
            app.settings.error = ""
            app.settings.tree = ""
            runBlocking(Dispatchers.IO) {
                app.database.clearAllTables();SyntheticAudioFixtures.clear(app)
                val now = System.currentTimeMillis()
                for (i in 1..9) {
                    app.database.queue().recording(Recording("demo$i", "voice", "demo$i", "Morning thoughts $i", now - i*3600000, state="saved", duration=65000))
                    app.database.queue().chunk(Chunk("demo$i",0,"",0,1040000,state="done",text="Hello to all my agents. This is my first recording."))
                }
            }
        }
        override fun after() { app.settings.paused = true }
    }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()
    @Test fun statusOpensSettingsAndTogglePersistsBothDirections() {
        rule.onNodeWithTag("auto-transcription-status").performClick()
        rule.onNodeWithText("Auto-transcription").assertIsDisplayed()
        rule.onNodeWithTag("auto-transcription-switch").assertIsOff().performClick()
        rule.runOnIdle { assertFalse(app.settings.paused); assertFalse(Settings(app).paused) }
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Auto-transcription on").assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        rule.onNodeWithTag("record-button").assertIsDisplayed()
        rule.waitForIdle()
        android.os.SystemClock.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(app.cacheDir,"home-full-updated.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        rule.onNodeWithTag("auto-transcription-status").performClick()
        rule.onNodeWithText("License").performScrollTo().performClick()
        rule.onNodeWithText("PolyForm Noncommercial 1.0.0").assertIsDisplayed()
        rule.onNodeWithText("Required Notice: Copyright (c) 2026 Tomer Cohen", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag("auto-transcription-switch").assertIsOn().performScrollTo().performClick()
        rule.runOnIdle { assertTrue(app.settings.paused); assertTrue(Settings(app).paused) }
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Transcription paused").assertIsDisplayed()
    }
}
