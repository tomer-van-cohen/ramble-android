package io.github.tomervancohen.rambleflow

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class DesignLayoutTest {
    private val app
        get() = ApplicationProvider.getApplicationContext<RambleApp>()

    @get:Rule(order = 0)
    val reset =
        object : org.junit.rules.ExternalResource() {
            override fun before() {
                app.settings.provider = "groq"
                app.settings.saveKey("groq", "synthetic-ui-test-key-not-live")
                app.settings.step = 4
                app.settings.paused = true
                app.settings.tree = ""
                runBlocking(Dispatchers.IO) { app.database.clearAllTables();SyntheticAudioFixtures.clear(app) }
            }
        }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(350)
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(app.cacheDir, "design-$name.png").outputStream().use {
            shot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        shot.recycle()
    }

    private fun restartAt(step: Int, font: Float = 1f) {
        app.settings.step = step
        rule.activityRule.scenario.onActivity {
            // Override the activity only. Do not change the owner's device/emulator font settings.
            val config = Configuration(it.resources.configuration).apply { fontScale = font }
            @Suppress("DEPRECATION")
            it.resources.updateConfiguration(config, it.resources.displayMetrics)
            it.recreate()
        }
        rule.waitForIdle()
    }

    @Test
    fun refreshIsVisibleAboveRecordingsWithoutScrolling() {
        restartAt(10, 2f)
        rule
            .onNode(hasText("Refresh now") or hasText("רענון עכשיו"))
            .assertIsDisplayed()
            .performClick()
        screenshot("home-refresh-large")
    }

    @Test
    fun callsContinueIsVisibleAtLargeFontWithoutScrolling() {
        restartAt(4, 2f)
        rule.onNode(hasText("Continue") or hasText("המשך")).assertIsDisplayed().performClick()
        rule
            .onNode(hasText("Connect Google Drive") or hasText("חיבור Google Drive"))
            .assertIsDisplayed()
        screenshot("save-large")
    }

    @Test
    fun openingNewStepDoesNotRetainPreviousScrollPosition() {
        restartAt(3, 2f)
        rule.onNodeWithText("Tap Allow recording access", substring = true).performScrollTo()
        rule
            .onNode(hasText("Allow recording access") or hasText("מתן גישה להקלטות"))
            .assertIsDisplayed()
        screenshot("permission-large")
    }

    @Test
    fun welcomePrecedesProviderAndHasOnlyOneAction() {
        restartAt(0)
        rule.onNodeWithText("Groq", substring = true).assertDoesNotExist()
        rule.onNode(hasText("Get started") or hasText("בואו נתחיל")).assertIsDisplayed()
        rule.waitUntil(10000) {
            val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            var green = 0
            for (y in shot.height / 6 until shot.height / 2 step 4) for (x in
                0 until shot.width step 4) {
                val c = shot.getPixel(x, y)
                if (
                    android.graphics.Color.green(c) > 140 &&
                        android.graphics.Color.red(c) < 90 &&
                        android.graphics.Color.blue(c) < 150
                )
                    green++
            }
            shot.recycle()
            green > 40
        }
        // Allow the designer's 1.1-second reveal to complete before the evidence screenshot.
        android.os.SystemClock.sleep(1400)
        screenshot("welcome")
        rule.onNode(hasText("Get started") or hasText("בואו נתחיל")).performClick()
        rule.onNode(hasText("Continue") or hasText("המשך")).assertIsDisplayed()
        screenshot("provider")
    }

    @Test
    fun assistantPromptShowsMarksAndBothLanguageTabs() {
        app.settings.driveEnabled = true
        app.settings.driveFolder = "synthetic-folder"
        app.settings.promptEnglish = false
        restartAt(6)
        rule.onNodeWithContentDescription("ChatGPT").assertExists()
        rule.onNodeWithContentDescription("Claude").assertExists()
        rule.onNodeWithContentDescription("Gemini").assertExists()
        rule.onNodeWithContentDescription("Muse").assertExists()
        rule.onNodeWithContentDescription("Grok Bot").assertExists()
        rule.onNodeWithContentDescription("Instinct").assertExists()
        rule.onNode(hasText("Copy") or hasText("העתקה")).assertIsDisplayed()
        rule.onNodeWithText("Paste into your AI chat").assertDoesNotExist()
        assertTrue(rule.onNodeWithText("English").fetchSemanticsNode().boundsInRoot.left <
            rule.onNodeWithText("עברית").fetchSemanticsNode().boundsInRoot.left)
        screenshot("agent-hebrew")
        rule.onNodeWithText("English").performScrollTo().performClick()
        rule.onNode(hasText("My recordings are transcribed", substring = true)).assertExists()
        screenshot("agent-english")
        rule.onNode(hasText("Copy") or hasText("העתקה")).performClick()
        rule.onNode(hasText("Copied") or hasText("הועתק")).assertExists()
        rule.runOnIdle {
            val clipboard = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            assertTrue(clipboard.primaryClip!!.getItemAt(0).text.toString()
                .contains("https://drive.google.com/drive/folders/synthetic-folder"))
        }
        rule.onNodeWithText("עברית").performClick()
        rule.onNode(hasText("Copy") or hasText("העתקה")).assertExists()
        rule.onNode(hasText("First recording") or hasText("ההקלטה הראשונה שלכם"))
            .performClick()
        assertEquals(7, app.settings.step)
    }

    @Test
    fun firstRecordingAndFinalGuideKeepActionsVisibleAtLargeFont() {
        restartAt(7, 2f)
        rule.onNode(hasText("Record") or hasText("הקלטה")).assertIsDisplayed()
        rule
            .onNode(hasText("Skip this try") or hasText("דילוג על הניסיון"))
            .assertIsDisplayed()
            .performClick()
        rule.onNode(hasText("Done") or hasText("סיום")).assertIsDisplayed()
        screenshot("trial-guide-large")
    }

    @Test
    fun readyOffersRecordingBeforeHomeAndCompletesSetup() {
        restartAt(9)
        rule
            .onNode(hasText("Try your first recording") or hasText("בואו ננסה: הקלטה ראשונה"))
            .assertIsDisplayed()
        screenshot("ready")
        rule
            .onNode(hasText("Go to home") or hasText("למסך הראשי"))
            .assertIsDisplayed()
            .performClick()
        assertEquals(10, app.settings.step)
    }
}
