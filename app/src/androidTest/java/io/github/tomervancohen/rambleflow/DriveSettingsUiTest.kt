package io.github.tomervancohen.rambleflow

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.*
import org.junit.Assert.*

class DriveSettingsUiTest {
    private val app get() = ApplicationProvider.getApplicationContext<RambleApp>()
    @get:Rule(order = 0) val reset = object : org.junit.rules.ExternalResource() {
        override fun before() {
            app.settings.provider = "groq"
            app.settings.saveKey("groq", "synthetic-ui-test-key-not-live")
            check(Build.HARDWARE in setOf("ranchu", "goldfish"))
            app.settings.step = 10
            app.settings.paused = true
            app.settings.appLanguage = "en"
            app.settings.resetPromptLanguage()
            if (Build.VERSION.SDK_INT >= 33)
                app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en")
            app.settings.driveEnabled = true
            app.settings.driveFolder = "synthetic-ui-folder"
            app.settings.driveFolderName = "Ramble"
            runBlocking(Dispatchers.IO) { app.database.clearAllTables();SyntheticAudioFixtures.clear(app) }
        }
        override fun after() {
            app.settings.appLanguage = ""
            app.settings.resetPromptLanguage()
            if (Build.VERSION.SDK_INT >= 33)
                app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
            java.util.Locale.setDefault(android.content.res.Resources.getSystem().configuration.locales[0])
        }
    }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()
    private fun settings() {
        rule.onNode(hasContentDescription("Settings") or hasContentDescription("הגדרות")).performClick()
    }
    private fun shot(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(400)
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(app.cacheDir, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    @Test fun driveSettingsHasFolderActionsAndNoContinue() {
        settings()
        rule.onNodeWithText("Google Drive").performClick()
        rule.onNodeWithText("Open Google Drive").assertExists()
        rule.onNodeWithText("Change folder").assertExists()
        rule.onNodeWithText("Disconnect Drive").assertExists()
        rule.onNodeWithText("Continue").assertDoesNotExist()
        rule.onNode(hasText("Ramble") and hasClickAction()).assertExists()
        shot("drive-settings-new")
        rule.onNodeWithText("Disconnect Drive").performClick()
        assertFalse(app.settings.driveEnabled)
        assertEquals("synthetic-ui-folder", app.settings.driveFolder)
    }
    @Test fun instructionsDefaultToInterfaceLanguageAndCanBeChanged() {
        assertTrue(app.settings.promptEnglish)
        settings()
        rule.onNodeWithText("AI assistant instructions").performScrollTo().performClick()
        rule.onNodeWithText("English").assertIsSelected()
        rule.onNodeWithText("עברית").performClick()
        assertFalse(app.settings.promptEnglish)
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNode(hasText("App language:", substring = true)).performClick()
        rule.onNodeWithText("עברית").performClick()
        rule.waitUntil(8000) {
            rule.onAllNodesWithText("הכול במקום אחד").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("הנחיות לעוזר ה-AI").performScrollTo().performClick()
        rule.onNodeWithText("עברית").assertIsSelected()
        assertFalse(app.settings.promptEnglish)
        shot("agent-localized-hebrew")
        rule.onNodeWithContentDescription("חזרה").performClick()
        rule.onNode(hasText("שפת האפליקציה:", substring = true)).performClick()
        rule.onNodeWithText("English").performClick()
        rule.waitUntil(8000) {
            rule.onAllNodesWithText("Everything in one place").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(app.settings.promptEnglish)
        rule.onNodeWithText("AI assistant instructions").performScrollTo().performClick()
        rule.onNodeWithText("English").assertIsSelected()
        shot("agent-localized-english")
    }
}
