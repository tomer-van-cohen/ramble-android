package io.github.tomervancohen.rambleflow

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WelcomeWaveTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun waveUsesItsHeightAndKeepsFlowing() {
        val app = ApplicationProvider.getApplicationContext<RambleApp>()
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1800)
        rule.onNodeWithTag("welcome-wave").assertIsDisplayed()
        val bounds=rule.onNodeWithTag("welcome-wave").fetchSemanticsNode().boundsInRoot
        fun shot(name: String): Bitmap {
            rule.waitForIdle()
            android.os.SystemClock.sleep(120)
            val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(app.cacheDir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            return bitmap
        }
        val first=shot("welcome-wave-frame-a.png")
        val x0=bounds.left.toInt(); val y0=bounds.top.toInt()
        val x1=bounds.right.toInt(); val y1=bounds.bottom.toInt()
        var top=y1; var bottom=y0
        for(y in y0 until y1) for(x in x0 until x1) {
            val color=first.getPixel(x,y)
            if(android.graphics.Color.green(color)>140 && android.graphics.Color.red(color)<90 && android.graphics.Color.blue(color)<150) {
                top=minOf(top,y); bottom=maxOf(bottom,y)
            }
        }
        assertTrue("Wave has a substantial vertical swing, not a flat ripple", (bottom-top).toDouble()/(y1-y0)>.35)
        rule.mainClock.advanceTimeBy(500)
        val second=shot("welcome-wave-frame-b.png")
        var changed=0
        for(y in y0 until y1 step 3) for(x in x0 until x1 step 3) if(first.getPixel(x,y)!=second.getPixel(x,y)) changed++
        assertTrue("Wave continues animating",changed>50)
        first.recycle(); second.recycle()
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion = 33)
    @Test fun welcomeCopyEnglishAndHebrewPreviews() {
        val app = ApplicationProvider.getApplicationContext<RambleApp>()
        val manager = app.getSystemService(android.app.LocaleManager::class.java)
        val original = manager.applicationLocales
        rule.mainClock.autoAdvance = false
        try {
            for ((language, title) in listOf("en" to "Talk freely.\nYour AI stays in the loop.", "he" to "דברו חופשי.\nה־AI שלכם נשאר בתמונה.")) {
                if (java.util.Locale.getDefault().language != language) {
                    rule.runOnUiThread { manager.applicationLocales = android.os.LocaleList.forLanguageTags(language) }
                    rule.waitUntil(10000) { java.util.Locale.getDefault().language == language }
                }
                rule.waitUntil(10000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
                rule.waitUntil(10000) { rule.activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
                repeat(3) {
                    rule.mainClock.advanceTimeBy(1400)
                    rule.waitForIdle()
                    android.os.SystemClock.sleep(180)
                }
                rule.onNodeWithText(title).assertIsDisplayed()
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                rule.onNodeWithTag("welcome-headline").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals("Exactly two intentional headline lines",2,layouts.single().lineCount)
                assertFalse("The second sentence fits without clipping",layouts.single().hasVisualOverflow)
                rule.onNodeWithText(if(language == "he") "בואו נתחיל" else "Get started").assertIsDisplayed()
                val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                File(app.cacheDir,"welcome-$language-updated.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
                image.recycle()
            }
        } finally {
            rule.runOnUiThread { manager.applicationLocales = original }
        }
    }

}
