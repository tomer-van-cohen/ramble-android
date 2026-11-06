package io.github.tomervancohen.rambleflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.*

class QuickGuideTest {
    @get:Rule val rule = createComposeRule()

    private fun show(maker: String) {
        rule.setContent {
            RambleTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides
                        MaterialTheme.colorScheme.onBackground
                ) {
                    Column(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("$maker · Ramble", color = MaterialTheme.colorScheme.onBackground)
                        QuickRecordingGuide(RecorderDevice.forManufacturer(maker))
                    }
                }
            }
        }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(300)
        val app = ApplicationProvider.getApplicationContext<RambleApp>()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(app.cacheDir, "quick-guide-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun samsungExplainsTheShortcutBeforeSetup() {
        show("Samsung")
        rule.onNodeWithText("Ramble, on your Side button.").performScrollTo().assertIsDisplayed()
        shot("samsung")
        rule.onNodeWithText("Double press").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Ramble").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("For direct recording, use the Record shortcut or the Quick Settings tile.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pixelUsesBackTapNotPowerButtonRecording() {
        show("Google")
        rule.onNodeWithText("Turn on Quick Tap").performScrollTo().assertIsDisplayed()
        shot("pixel")
        rule.onNodeWithText("Double press").assertDoesNotExist()
        rule.onNodeWithText("Choose Open app → Ramble").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun xiaomiDoesNotPromiseUnsupportedActionButton() {
        show("Xiaomi")
        rule.onNodeWithText("Choose Record, or add a shortcut to your Home screen").performScrollTo().assertIsDisplayed()
        shot("xiaomi")
        rule.onNodeWithText("Double press").assertDoesNotExist()
    }
    @Test fun rambleWaveMovesAndHasNoRedRecordingDot() {
        rule.mainClock.autoAdvance = false
        show("Samsung")
        rule.mainClock.advanceTimeBy(160)
        val first = rule.onNodeWithTag("side-button-art").captureToImage().toPixelMap()
        rule.mainClock.advanceTimeBy(850)
        val second = rule.onNodeWithTag("side-button-art").captureToImage().toPixelMap()
        var changed = 0
        var red = 0
        for (y in 0 until first.height step 2) for (x in 0 until first.width step 2) {
            if (first[x,y] != second[x,y]) changed++
            val color = second[x,y]
            if (color.red > .8f && color.green < .3f && color.blue < .3f) red++
        }
        org.junit.Assert.assertTrue("Wave must animate", changed > 10)
        org.junit.Assert.assertEquals(0, red)
        shot("wave")
    }

}
