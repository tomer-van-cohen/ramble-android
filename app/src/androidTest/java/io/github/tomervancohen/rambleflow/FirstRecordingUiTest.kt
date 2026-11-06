package io.github.tomervancohen.rambleflow

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/**
 * Disposable emulator only: real microphone API, emulator audio, fake provider, no external API.
 */
class FirstRecordingUiTest {
    private val app
        get() = ApplicationProvider.getApplicationContext<RambleApp>()

    private val calls = AtomicInteger()
    @get:Rule(order = 0)
    val reset =
        object : org.junit.rules.ExternalResource() {
            override fun before() {
                assertTrue(
                    "Only synthetic emulator tests",
                    android.os.Build.HARDWARE in setOf("ranchu", "goldfish"),
                )
                InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
                WorkManager.getInstance(app).cancelAllWork().result.get()
                runBlocking(Dispatchers.IO) { app.database.clearAllTables();SyntheticAudioFixtures.clear(app) }
                app.settings.transcriptionLanguage = ""
                app.settings.step = 7
                app.settings.trialRecording = ""
                app.settings.paused = false
                app.settings.wifiOnly = false
                app.settings.driveEnabled = false
                app.settings.provider = "groq"
                app.settings.saveKey("groq", "synthetic-onboarding-ui-key-not-live")
                app.transcriber =
                    object : Transcriber {
                        override fun transcribe(provider: String, key: String, file: File): String {
                            calls.incrementAndGet()
                            check(file.length() > 44)
                            return "My first Ramble transcription. מחר אשלח את הסיכום."
                        }
                    }
            }

            override fun after() {
                WorkManager.getInstance(app).cancelAllWork().result.get()
                runBlocking(Dispatchers.IO) {
                    for (job in app.database.queue().recordings().filter { it.source == "app" }) {
                        if (job.localUri.isNotBlank())
                            app.contentResolver.delete(
                                android.net.Uri.parse(job.localUri),
                                null,
                                null,
                            )
                        LocalExport(app, app.database).file(job.id).delete()
                        AppRecordings(app).file(job.id).delete()
                    }
                    app.database.clearAllTables();SyntheticAudioFixtures.clear(app)
                }
                app.settings.trialRecording = ""
                app.transcriber = ProviderApi()
            }
        }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(
            400
        ) // let Android's window transition settle before the evidence image
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(app.cacheDir, "trial-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun actualCaptureStopTranscriptListThenQuickSetup() {
        rule.onNode(hasText("Record") or hasText("הקלטה")).assertIsDisplayed()
        rule
            .onNode(hasText("Quick recording, for next time") or hasText("הקלטה מהירה, לפעם הבאה"))
            .assertDoesNotExist()
        shot("ready")
        rule.onNode(hasText("Record") or hasText("הקלטה")).performClick()
        rule.waitUntil(5000) {
            rule
                .onAllNodes(
                    hasText("Recording ·", substring = true) or
                        hasText("מקליטים ·", substring = true)
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertEquals(0, calls.get())
        rule.waitUntil(5000) {
            rule
                .onAllNodes(
                    (hasText("Stop") or hasText("עצירה")) and isEnabled()
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        shot("recording")
        rule.onNode(hasText("Stop") or hasText("עצירה")).performClick()
        rule.waitUntil(20000) {
            rule
                .onAllNodes(hasText("My first Ramble transcription.", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertEquals(1, calls.get())
        rule
            .onNode(hasText("Set up quick recording") or hasText("הגדרת הקלטה מהירה"))
            .assertIsDisplayed()
        shot("success")
        rule
            .onNode(hasText("Set up quick recording") or hasText("הגדרת הקלטה מהירה"))
            .performClick()
        rule
            .onNode(hasText("Quick recording, for next time") or hasText("הקלטה מהירה, לפעם הבאה"))
            .assertExists()
        shot("guide")
        rule.onNode(hasText("Continue") or hasText("המשך") or hasText("Done") or hasText("סיום")).assertIsDisplayed().performClick()
        assertEquals(10, app.settings.step)
        rule
            .onNode(hasContentDescription("Settings") or hasContentDescription("הגדרות"))
            .assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        assertEquals(1, calls.get())
        runBlocking(Dispatchers.IO) {
            assertEquals(1, app.database.queue().recordings().count { it.source == "app" })
        }
    }

    @Test
    fun leavingDuringCaptureNeverQueuesOrUploadsIt() {
        rule.onNode(hasText("Record") or hasText("הקלטה")).performClick()
        rule.waitUntil(5000) {
            rule
                .onAllNodes(
                    hasText("Recording ·", substring = true) or
                        hasText("מקליטים ·", substring = true)
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        rule.waitForIdle()
        assertEquals(0, calls.get())
        runBlocking(Dispatchers.IO) { assertTrue(app.database.queue().recordings().isEmpty()) }
        rule.onNode(hasText("Record") or hasText("הקלטה")).assertIsDisplayed()
    }
    @Test
    fun noSpeechOffersNewRecordingAndKeepsLanguageAcrossRecreation() {
        app.transcriber = object : Transcriber {
            override fun transcribe(provider: String, key: String, file: File): String = ""
        }
        rule.onNode(hasText("Transcription language:", substring = true) or
            hasText("שפת תמלול:", substring = true)).performClick()
        rule.onNodeWithText("עברית").performClick()
        assertEquals("he", app.settings.transcriptionLanguage)
        rule.activityRule.scenario.recreate()
        rule.onNode(hasText("Record") or hasText("הקלטה")).performClick()
        rule.waitUntil(5000) {
            rule.onAllNodes((hasText("Stop") or hasText("עצירה")) and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasText("Microphone level") or hasText("עוצמת מיקרופון")).assertExists()
        rule.onNode(hasText("Stop") or hasText("עצירה")).performClick()
        rule.waitUntil(20000) {
            rule.onAllNodes(hasText("Record again") or hasText("להקליט שוב"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasText("Your first recording is ready") or
            hasText("ההקלטה הראשונה שלכם, מוכנה")).assertDoesNotExist()
        shot("no-speech")
        runBlocking(Dispatchers.IO) {
            val job = app.database.queue().recordings().single()
            assertEquals("no_speech", job.reason)
            assertTrue(job.localUri.isBlank())
        }
        rule.onNode(hasText("Record again") or hasText("להקליט שוב")).performClick()
        rule.onNode(hasText("Record") or hasText("הקלטה")).assertExists()
        rule.activityRule.scenario.recreate()
        rule.onNode(hasText("Record") or hasText("הקלטה")).assertExists()
    }

}
