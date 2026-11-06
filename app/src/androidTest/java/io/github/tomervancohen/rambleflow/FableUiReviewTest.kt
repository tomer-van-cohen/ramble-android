package io.github.tomervancohen.rambleflow

import android.app.LocaleManager
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.LocaleList
import android.provider.DocumentsContract
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class FableUiReviewTest {
    @get:Rule val rule=createEmptyComposeRule()
    private val app get()=ApplicationProvider.getApplicationContext<RambleApp>()
    private var scenario:ActivityScenario<MainActivity>?=null
    private fun show(step:Int, language:String="en", dark:Boolean=false, font:Float=1f) {
        scenario?.close()
        app.settings.step=step;app.settings.appLanguage=language
        if(android.os.Build.VERSION.SDK_INT>=33) app.getSystemService(LocaleManager::class.java).applicationLocales=LocaleList.forLanguageTags(language)
        if(android.os.Build.VERSION.SDK_INT>=31) app.getSystemService(UiModeManager::class.java).setApplicationNightMode(if(dark)UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)
        scenario=ActivityScenario.launch(Intent(app,MainActivity::class.java))
        scenario!!.onActivity { activity ->
            run {
                val config=android.content.res.Configuration(activity.resources.configuration).apply { fontScale=font }
                @Suppress("DEPRECATION")
                activity.resources.updateConfiguration(config,activity.resources.displayMetrics)
            }
            activity.setContent { RambleTheme { RambleFlow(app,RecorderDevice.forManufacturer("Samsung")) } }
        }
        rule.mainClock.autoAdvance=false
        rule.mainClock.advanceTimeBy(1800);rule.waitForIdle()
        android.os.SystemClock.sleep(800) // Let platform locale/night-mode and starting-window transitions settle.
    }
    private fun shot(name:String) {
        rule.mainClock.advanceTimeBy(200);rule.waitForIdle();android.os.SystemClock.sleep(200)
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(app.cacheDir,"fable-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    @Test fun manualFolderContinueSecurityAndCurrentDocumentationScreens() = runBlocking<Unit>(Dispatchers.IO) {
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.database.clearAllTables();SyntheticAudioFixtures.clear(app)
        app.settings.paused=true;app.settings.provider="groq";app.settings.saveKey("groq","synthetic-visual-review-key")
        try {
            show(0);shot("welcome-en")
            rule.onNodeWithText("Get started").performClick();shot("providers-en")
            show(0,"he");shot("welcome-he")
            rule.onNodeWithText("בואו נתחיל").performClick();shot("providers-he")
            show(0,"he",dark=true);shot("welcome-he-dark")
            show(0,"he",font=1.5f);shot("welcome-he-large")
            show(10);rule.onNodeWithContentDescription("Settings").performClick();shot("settings-top")
            rule.mainClock.autoAdvance=true
            rule.onNodeWithText("Keep original audio").performScrollTo();shot("privacy-settings")
            val authority="io.github.tomervancohen.rambleflow.test.documents"
            app.packageManager.setComponentEnabledSetting(ComponentName(app,FixtureDocuments::class.java),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP)
            app.contentResolver.call(Uri.parse("content://$authority"),"fixture.reset",null,null)
            val reader=SourceReader(app,app.database,app.settings)
            val tree=DocumentsContract.buildTreeDocumentUri(authority,"recordings")
            assertTrue(reader.activate(tree,"voice"));assertTrue(reader.activate(tree,"call"))
            show(3)
            rule.waitUntil(5000) { rule.mainClock.advanceTimeBy(100);rule.onAllNodesWithText("Both recording sources are connected.").fetchSemanticsNodes().isNotEmpty() }
            rule.mainClock.advanceTimeBy(2500);assertEquals(3,app.settings.step)
            shot("folders-connected-manual")
            rule.onNodeWithText("Continue").performClick();assertEquals(5,app.settings.step)
            show(3,dark=true)
            rule.waitUntil(5000) { rule.mainClock.advanceTimeBy(100);rule.onAllNodesWithText("Both recording sources are connected.").fetchSemanticsNodes().isNotEmpty() }
            shot("folders-dark")
            show(10)
            app.startActivity(Intent(app,QuickRecordActivity::class.java).putExtra("fromApp",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            rule.waitUntil(5000) { rule.mainClock.advanceTimeBy(100);rule.onAllNodesWithText("Start a recording?").fetchSemanticsNodes().isNotEmpty() }
            assertFalse(QuickRecordService.status.value.mode in setOf("recording","starting"))
            shot("external-shortcut-confirmation")
            rule.onNodeWithText("Cancel").performClick()
        } finally {
            scenario?.close()
            if(android.os.Build.VERSION.SDK_INT>=31) app.getSystemService(UiModeManager::class.java).setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
        }
    }
}
