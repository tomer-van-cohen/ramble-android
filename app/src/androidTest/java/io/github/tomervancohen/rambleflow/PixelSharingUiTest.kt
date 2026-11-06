package io.github.tomervancohen.rambleflow

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class PixelSharingUiTest {
    private val app get() = ApplicationProvider.getApplicationContext<RambleApp>()
    @get:Rule(order = 0) val reset = object : org.junit.rules.ExternalResource() {
        override fun before() {
            runBlocking { app.database.clearAllTables();SyntheticAudioFixtures.clear(app) }
            app.settings.step = 3
            app.settings.paused = true
        }
    }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()
    @Test fun pixelExplainsSharingAndContinuesWithoutAnyFolderGrant() {
        rule.runOnUiThread {
            rule.activity.setContent {
                RambleTheme { RambleFlow(app, RecorderDevice.forManufacturer("Google")) }
            }
        }
        rule.onNode(hasText("Share a recording with Ramble") or hasText("משתפים הקלטה עם Ramble")).assertExists()
        rule.onNode(hasText("Share → Ramble", substring = true) or hasText("Share ← Ramble", substring = true)).assertExists()
        rule.onNode(hasText("Allow recording access") or hasText("מתן גישה להקלטות")).assertDoesNotExist()
        rule.onNode(hasText("Connect another folder") or hasText("חיבור התיקייה הנוספת")).assertDoesNotExist()
        rule.onNode(hasText("Continue") or hasText("המשך")).performClick()
        rule.waitUntil(5000) { app.settings.step == 5 }
        assertTrue(runBlocking { app.database.queue().sources().isEmpty() })
    }
}
