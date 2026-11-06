package io.github.tomervancohen.rambleflow

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Disposable-emulator visual inventory. All accounts, transcripts and folder data are synthetic. */
class UiWalkthroughTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<RambleApp>()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val directory get() = File(app.cacheDir, "ui-walkthrough").apply { mkdirs() }
    private fun io(block: suspend () -> Unit) = runBlocking(Dispatchers.IO) { block() }
    @Before fun prepare() {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.settings.appLanguage = "en"
        app.settings.provider = "groq"
        app.settings.paused = true
        app.settings.driveEnabled = false
        app.settings.transcriptionLanguage = ""
        app.settings.promptEnglish = true
        app.settings.error = ""
        app.settings.trialRecording = "new"
        app.settings.tree = ""
        app.settings.removedSourceTrees = emptySet()
        app.settings.saveKey("groq", "synthetic-walkthrough-not-a-live-api-key")
        io { app.database.clearAllTables();SyntheticAudioFixtures.clear(app) }
        rule.mainClock.autoAdvance = false
    }
    @After fun close() { scenario?.close(); WorkManager.getInstance(app).cancelAllWork().result.get() }
    private fun render(step: Int, maker: String = "Google") {
        scenario?.close()
        app.settings.step = step
        scenario = ActivityScenario.launch(Intent(app, MainActivity::class.java))
        scenario!!.onActivity { activity -> activity.setContent { RambleTheme { RambleFlow(app, RecorderDevice.forManufacturer(maker)) } } }
        rule.mainClock.advanceTimeBy(1800)
        rule.waitForIdle()
    }
    private fun shot(name: String, advance: Long = 1600) {
        rule.mainClock.advanceTimeBy(advance)
        rule.waitForIdle()
        android.os.SystemClock.sleep(250)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    private fun click(text: String) { val node=rule.onNodeWithText(text); runCatching { node.performScrollTo() }; node.performClick(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle() }
    private fun back() { rule.onNodeWithContentDescription("Back").performClick(); rule.mainClock.advanceTimeBy(500); rule.waitForIdle() }
    private fun settings() { render(10); rule.onNodeWithContentDescription("Settings").performClick(); rule.mainClock.advanceTimeBy(500) }
    private fun openSetting(text: String) { settings(); click(text) }
    private fun waitText(text: String) {
        rule.waitUntil(10000) { rule.mainClock.advanceTimeBy(100); runCatching { rule.onAllNodesWithText(text, substring=true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
    @Test fun allScreensInOrder() {
        app.getSharedPreferences("ramble",android.content.Context.MODE_PRIVATE).edit()
            .remove("profile.groq").remove("key.groq").remove("draftKey.groq").commit()
        render(0); shot("01-welcome")
        click("Get started"); shot("02-provider")
        click("Groq"); shot("03-groq-key")
        back(); click("OpenAI"); shot("04-openai-key")
        app.settings.saveKey("groq", "synthetic-walkthrough-not-a-live-api-key")
        render(3,"Samsung"); shot("05-folder-access-samsung")
        click("Allow recording access"); shot("06-system-folder-picker")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close()
        render(3,"Google"); shot("07-pixel-sharing")
        click("Continue"); assertEquals(5,app.settings.step); shot("08-connect-drive")
        click("Save only on this phone"); assertEquals(7,app.settings.step); shot("09-first-recording")
        rule.onNodeWithText("Transcription language:",substring=true).performClick(); shot("10-transcription-language")
        click("עברית"); assertEquals("he",app.settings.transcriptionLanguage)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(app.packageName,Manifest.permission.RECORD_AUDIO)
        if(android.os.Build.VERSION.SDK_INT>=33) automation.grantRuntimePermission(app.packageName,Manifest.permission.POST_NOTIFICATIONS)
        click("Record"); waitText("Recording ·"); android.os.SystemClock.sleep(1200); shot("11-recording-level")
        click("Stop"); shot("12-transcription-pending")
        io {
            val job=app.database.queue().recordings().first { it.source=="app" }
            app.database.queue().recording(job.copy(state="error",reason="no_speech"))
        }
        waitText("Record again"); shot("13-no-clear-speech")
        click("Record again"); shot("14-record-again")
        val id=UUID.randomUUID().toString()
        val saved=Recording(id,"app",id,"My first recording",System.currentTimeMillis(),state="saved",duration=6200,provider=app.settings.profile("groq"))
        io { app.database.queue().recording(saved); LocalExport(app,app.database).file(id).apply { parentFile?.mkdirs(); writeText("My first recording\n\nHello to all my AI agents. I'm here!") } }
        app.settings.trialRecording=id
        render(7); waitText("Hello to all my AI agents."); shot("15-first-transcript-ready")
        click("Set up quick recording"); shot("16-quick-recording-pixel")
        click("Continue"); assertEquals(10,app.settings.step); shot("17-home-after-onboarding")
        render(8,"Samsung"); shot("18-quick-recording-samsung")
        render(8,"Xiaomi"); shot("19-quick-recording-xiaomi")
        render(3,"Xiaomi"); shot("20-folder-access-xiaomi")
        app.settings.driveEnabled=true;app.settings.driveFolder="example-review-folder";app.settings.driveFolderName="Ramble"
        render(5);shot("21-drive-connected-onboarding")
        click("Continue");shot("22-ai-instructions-english")
        click("עברית");shot("23-ai-instructions-hebrew")
        click("Copy");rule.onNodeWithText("Copied").assertExists()
        render(9);shot("24-legacy-ready")
        captureSettings(saved)
    }
    @Test fun remainingScreens() {
        val id=UUID.randomUUID().toString()
        val saved=Recording(id,"app",id,"My first recording",System.currentTimeMillis(),state="saved",duration=6200,provider=app.settings.profile("groq"))
        io { LocalExport(app,app.database).file(id).apply { parentFile?.mkdirs(); writeText("My first recording\n\nHello to all my AI agents. I'm here!") } }
        app.settings.driveEnabled=true;app.settings.driveFolder="example-review-folder";app.settings.driveFolderName="Ramble"
        captureSettings(saved)
    }
    private fun captureSettings(saved: Recording) {
        rule.mainClock.autoAdvance = true
        val id=saved.id
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        io { app.database.clearAllTables();SyntheticAudioFixtures.clear(app) };app.settings.trialRecording="new"
        render(10);shot("25-home-empty")
        val jobs=listOf(saved,
            saved.copy(id=UUID.randomUUID().toString(),title="Short voice memo",state="key",reason="key",duration=4000),
            saved.copy(id=UUID.randomUUID().toString(),title="Meeting notes",state="uncertain",reason="network",duration=156000),
            saved.copy(id=UUID.randomUUID().toString(),title="An idea for tomorrow",state="queued",duration=23000)).map { it.copy(document=it.id) }
        io { jobs.forEach { app.database.queue().recording(it) }; app.database.queue().chunk(Chunk(id,0,"",0,99200,state="done",text="Hello to all my AI agents. I'm here!")) }
        render(10);waitText(saved.title);shot("26-home-recordings")
        for((i,job) in jobs.withIndex()) { waitText(job.title);rule.onNodeWithTag("recording-${job.id}").performClick();shot("${27+i}-detail-${job.state}");back() }
        settings();shot("31-settings-top")
        rule.onNodeWithText("No Ramble server.",substring=true).performScrollTo();shot("32-settings-bottom")
        settings();rule.onNodeWithText("App language:",substring=true).performClick();shot("33-app-language-menu")
        automation.executeShellCommand("input keyevent 4").close()
        openSetting("Transcription language");shot("34-settings-transcription-language");click("Cancel")
        openSetting("Local transcripts");shot("35-local-transcripts")
        click("Choose a folder");shot("36-output-folder-picker");automation.executeShellCommand("input keyevent 4").close()
        openSetting("Transcription provider");shot("37-settings-provider");click("Groq");shot("38-settings-saved-key")
        openSetting("Google Drive");shot("39-settings-drive-connected")
        click("Change folder");android.os.SystemClock.sleep(1200);shot("40-drive-folder-selection-offline")
        openSetting("AI assistant instructions");shot("41-settings-ai-instructions")
        app.settings.driveEnabled=false
        openSetting("Google Drive");shot("42-settings-drive-disconnected")
        openSetting("AI assistant instructions");shot("43-ai-needs-drive")
        openSetting("Recording sources");shot("44-recording-sources-empty")
        fixtureFolders()
        openSetting("Recording sources");waitText("Recordings");shot("45-recording-sources-connected")
        rule.onNodeWithContentDescription("Folder options for Recordings").performClick();shot("46-folder-actions")
        automation.executeShellCommand("input keyevent 4").close()
        openSetting("Quick recording");shot("47-settings-quick-recording")
        openSetting("Try a recording");shot("48-settings-recording-trial")
        openSetting("License");shot("49-license-top")
        rule.onRoot().performTouchInput { swipeUp() };rule.mainClock.advanceTimeBy(500);shot("50-license-more")
        settings();rule.onNodeWithTag("auto-transcription-switch").performClick();assertFalse(app.settings.paused)
        rule.onNodeWithTag("auto-transcription-switch").performClick();assertTrue(app.settings.paused)
        rule.mainClock.autoAdvance = false
        render(0);shot("51-welcome-review-end")
    }
    @Test fun realEmptyRecordingFoldersRequireContinueAndReviewStaysOpen() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("mkdir -p '/sdcard/Recordings/Voice Recorder' /sdcard/Recordings/Call").close()
        render(3,"Samsung")
        click("Allow recording access")
        clickNative("Use this folder")
        shot("52-real-folder-permission-confirmation",0)
        clickNative("Allow")
        waitText("Both recording sources are connected.")
        shot("53-empty-folders-both-connected",200)
        rule.mainClock.advanceTimeBy(2000)
        assertEquals(3,app.settings.step)
        click("Continue")
        rule.waitUntil(5000) { app.settings.step==5 }
        shot("54-continued-to-saving")
        back(); rule.mainClock.advanceTimeBy(2000)
        assertEquals(5,app.settings.step) // Back changes the view, not completed setup progress.
        rule.onNodeWithText("Find your recordings").assertExists()
        rule.onNodeWithText("Continue").assertExists()
        shot("55-folder-review-stays-open")
    }
    @Test fun finalSupplementScreens() {
        val id=UUID.randomUUID().toString()
        val job=Recording(id,"app",id,"My first recording",System.currentTimeMillis(),state="saved",duration=6200,provider=app.settings.profile("groq"))
        io {
            app.database.queue().recording(job)
            LocalExport(app,app.database).file(id).apply { parentFile?.mkdirs();writeText("My first recording\n\nHello to all my AI agents. I'm here!") }
        }
        app.settings.trialRecording=id
        render(7);waitText("Hello to all my AI agents.");shot("15-first-transcript-ready")
        scenario?.close();scenario=null
        app.settings.appLanguage="he"
        if(android.os.Build.VERSION.SDK_INT>=33) app.getSystemService(android.app.LocaleManager::class.java).applicationLocales=android.os.LocaleList.forLanguageTags("he")
        render(0);shot("56-welcome-hebrew")
        render(10);rule.onNodeWithContentDescription("הגדרות").performClick();shot("57-settings-hebrew")
        scenario?.close();scenario=null
        app.settings.appLanguage="en"
        if(android.os.Build.VERSION.SDK_INT>=33) app.getSystemService(android.app.LocaleManager::class.java).applicationLocales=android.os.LocaleList.forLanguageTags("en")
        if(android.os.Build.VERSION.SDK_INT>=31) app.getSystemService(android.app.UiModeManager::class.java).setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_YES)
        render(10);waitText("Recordings");shot("58-home-dark")
        scenario!!.onActivity { activity ->
            assertFalse(androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView).isAppearanceLightStatusBars)
        }
        rule.onNodeWithContentDescription("Settings").performClick();shot("59-settings-dark")
        scenario?.close();scenario=null
        if(android.os.Build.VERSION.SDK_INT>=31) app.getSystemService(android.app.UiModeManager::class.java).setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_NO)
    }
    @Test fun sharedAudioAndQuickRecordingScreens() {
        rule.mainClock.autoAdvance=true
        fixtureFolders()
        val audio=File(app.cacheDir,"walkthrough-share.m4a")
        NativeM4aFixture.write(audio)
        val bytes=audio.readBytes()
        val authority="io.github.tomervancohen.rambleflow.test.documents"
        app.contentResolver.call(Uri.parse("content://$authority"),"fixture.add",null,android.os.Bundle().apply {
            putString("id","walkthrough-shared");putString("name","call.m4a")
            putLong("size",bytes.size.toLong());putByteArray("audio",bytes)
        })
        val uri=DocumentsContract.buildDocumentUri(authority,"walkthrough-shared")
        fun sharePage(input: Uri?) {
            render(10)
            scenario!!.onActivity { activity -> activity.setContent { RambleTheme {
                SharedAudioPage(app,input,onClose={},onOpenApp={})
            } } }
        }
        sharePage(uri)
        rule.waitUntil(10000) { rule.onAllNodesWithText("Transcribe").fetchSemanticsNodes().isNotEmpty() }
        shot("62-shared-audio-ready")
        app.settings.provider="walkthrough-missing"
        sharePage(uri);waitText("Set up your transcription provider first");shot("63-shared-audio-needs-setup")
        app.settings.provider="groq"
        sharePage(null);waitText("Couldn't read this audio");shot("64-shared-audio-unreadable")
        render(10)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(app.packageName,Manifest.permission.RECORD_AUDIO)
        if(android.os.Build.VERSION.SDK_INT>=33) automation.grantRuntimePermission(app.packageName,Manifest.permission.POST_NOTIFICATIONS)
        app.startActivity(Intent(app,InternalRecordActivity::class.java).putExtra("fromApp",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        rule.waitUntil(10000) { QuickRecordService.status.value.mode=="recording" }
        shot("60-quick-recording-controls")
        app.startActivity(Intent(app,MainActivity::class.java).putExtra("ramble.live.recording",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        waitText("Background recording");shot("61-notification-recording-screen")
        app.startService(Intent(app,QuickRecordService::class.java).setAction(QuickRecordService.STOP))
        rule.waitUntil(10000) { QuickRecordService.status.value.mode=="saved" }
    }
    private fun clickNative(text: String) {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.text?.toString()?.equals(text,ignoreCase=true)==true)return node
            for(i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        val deadline=android.os.SystemClock.elapsedRealtime()+15000
        while(android.os.SystemClock.elapsedRealtime()<deadline) {
            var node=find(automation.rootInActiveWindow)
            while(node!=null) {
                if(node.isClickable && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))return
                node=node.parent
            }
            android.os.SystemClock.sleep(100)
        }
        error("Native button not found: $text")
    }
    private fun fixtureFolders() {
        val authority="io.github.tomervancohen.rambleflow.test.documents"
        app.packageManager.setComponentEnabledSetting(ComponentName(app,FixtureDocuments::class.java),PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP)
        app.contentResolver.call(Uri.parse("content://$authority"),"fixture.reset",null,null)
        io {
            val reader=SourceReader(app,app.database,app.settings)
            assertTrue(reader.activate(DocumentsContract.buildTreeDocumentUri(authority,"recordings"),"voice"))
            assertTrue(reader.activate(DocumentsContract.buildTreeDocumentUri(authority,"unrelated"),"folder:extra"))
        }
    }
}
