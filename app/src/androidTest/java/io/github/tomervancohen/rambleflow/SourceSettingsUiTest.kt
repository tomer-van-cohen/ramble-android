package io.github.tomervancohen.rambleflow

import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class SourceSettingsUiTest {
    private val app
        get() = ApplicationProvider.getApplicationContext<RambleApp>()

    private val authority = "io.github.tomervancohen.rambleflow.test.documents"

    @get:Rule(order = 0)
    val reset =
        object : org.junit.rules.ExternalResource() {
            override fun before() {
            app.settings.provider = "groq"
            app.settings.saveKey("groq", "synthetic-ui-test-key-not-live")
                check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
                app.settings.removedSourceTrees = emptySet()
                app.settings.tree = ""
                app.packageManager.setComponentEnabledSetting(
                    ComponentName(app, FixtureDocuments::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
                app.contentResolver.call(
                    Uri.parse("content://$authority"),
                    "fixture.reset",
                    null,
                    null,
                )
                runBlocking(Dispatchers.IO) { app.database.clearAllTables();SyntheticAudioFixtures.clear(app) }
                app.settings.step = 10
                app.settings.paused = true
            }

            override fun after() {
                app.packageManager.setComponentEnabledSetting(
                    ComponentName(app, FixtureDocuments::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun openSources(voiceRoot: Boolean = false, invalid: Boolean = false) {
        val tree =
            DocumentsContract.buildTreeDocumentUri(
                authority,
                if (invalid) "missing-folder" else if (voiceRoot) "voice" else "recordings",
            )
        app.settings.tree = tree.toString()
        runBlocking(Dispatchers.IO) {
            app.database
                .queue()
                .source(
                    Source(
                        "voice",
                        tree.toString(),
                        if (invalid) "missing-folder" else "voice",
                        12345,
                        baselineComplete = true,
                    )
                )
        }
        rule
            .onNode(hasContentDescription("Settings") or hasContentDescription("הגדרות"))
            .performClick()
        rule.onNode(hasText("Recording sources") or hasText("תיקיות ההקלטות")).performClick()
    }

    @Test
    fun setupRequiresAccessAndAllowsContinuingWithOneSource() {
        app.settings.step = 3
        app.settings.tree = ""
        rule.activityRule.scenario.recreate()
        rule.onNode(hasText("Allow recording access") or hasText("מתן גישה להקלטות")).assertExists()
        rule.onNode(hasText("Continue") or hasText("המשך")).assertDoesNotExist()
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "voice")
        runBlocking(Dispatchers.IO) {
            assertTrue(SourceReader(app, app.database, app.settings).activate(tree, "voice"))
        }
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5000) {
            rule.onAllNodes(hasText("Add folder") or hasText("הוספת תיקייה"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasText("Continue") or hasText("המשך")).assertExists()
        assertEquals(3, app.settings.step)
    }

    @Test
    fun setupReviewDoesNotAdvanceWhenBothSourcesAreReadable() {
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "recordings")
        runBlocking(Dispatchers.IO) {
            val reader = SourceReader(app, app.database, app.settings)
            assertTrue(reader.activate(tree, "voice"))
            assertTrue(reader.activate(tree, "call"))
        }
        app.settings.step = 5
        rule.activityRule.scenario.recreate()
        rule.onNode(hasContentDescription("Back") or hasContentDescription("חזרה")).performClick()
        rule.waitForIdle()
        rule.onNode(hasText("Find your recordings") or hasText("נמצא את ההקלטות שלכם")).assertExists()
        rule.onNode(hasText("Continue") or hasText("המשך")).assertExists()
        rule.onNode(hasText("Add folder") or hasText("הוספת תיקייה")).assertExists()
    }


    private fun toggle(call: Boolean) = rule.onNode(if (call)
        hasContentDescription("Transcribe phone-call recordings") or hasContentDescription("תמלול הקלטות שיחות")
        else hasContentDescription("Transcribe voice memos") or hasContentDescription("תמלול הקלטות קול"))

    private fun waitSources() {
        rule.waitUntil(5000) { rule.onAllNodesWithText("Recordings").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }
    private fun shot(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(350)
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(app.cacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun bothSwitchesAreIndependentAndOffKeepsFolderVisible() {
        openSources()
        waitSources()
        toggle(false).assertIsOn().performClick()
        rule.waitUntil(5000) { runBlocking(Dispatchers.IO) { !app.database.queue().sources().single { it.kind == "voice" }.enabled } }
        toggle(true).assertIsOn()
        rule.onAllNodesWithText("Recordings").assertCountEquals(1)
        val cutoffs = runBlocking(Dispatchers.IO) { app.database.queue().sources().associate { it.kind to it.cutoff } }
        toggle(true).performClick()
        rule.waitUntil(5000) { runBlocking(Dispatchers.IO) { app.database.queue().sources().none { it.enabled } } }
        rule.onAllNodesWithText("Recordings").assertCountEquals(1)
        toggle(false).performClick()
        rule.waitUntil(5000) { runBlocking(Dispatchers.IO) { app.database.queue().sources().single { it.kind == "voice" }.enabled } }
        toggle(true).assertIsOff().performClick()
        rule.waitUntil(5000) { runBlocking(Dispatchers.IO) { app.database.queue().sources().all { it.enabled } } }
        assertEquals(cutoffs, runBlocking(Dispatchers.IO) { app.database.queue().sources().associate { it.kind to it.cutoff } })
        rule.onNodeWithText("New recordings from enabled sources are transcribed automatically.").assertDoesNotExist()
        shot("sources-clean.png")
    }
    @Test fun defaultAndExtraFoldersShareOneListAndDefaultCanBeRemoved() {
        runBlocking(Dispatchers.IO) {
            SourceReader(app, app.database, app.settings).activate(
                DocumentsContract.buildTreeDocumentUri(authority, "unrelated"), "folder:extra")
        }
        openSources()
        waitSources()
        rule.onNodeWithText("Pictures").assertExists()
        shot("sources-clean-multiple.png")
        rule.onNodeWithContentDescription("Folder options for Recordings").performClick()
        rule.onNode(hasText("Remove") or hasText("הסרה")).performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Recordings").fetchSemanticsNodes().isEmpty() }
        toggle(true).assertIsOff()
        toggle(false).assertIsOn()
        rule.onNodeWithText("Pictures").assertExists()
        runBlocking(Dispatchers.IO) { SourceReader(app, app.database, app.settings).restoreCoveredSources() }
        rule.activityRule.scenario.recreate()
        rule.onNode(hasContentDescription("Settings") or hasContentDescription("הגדרות")).performClick()
        rule.onNode(hasText("Recording sources") or hasText("תיקיות ההקלטות")).performClick()
        rule.onNodeWithText("Recordings").assertDoesNotExist()
        rule.onNodeWithText("Pictures").assertExists()
        rule.onNodeWithContentDescription("Folder options for Pictures").performClick()
        rule.onNode(hasText("Remove") or hasText("הסרה")).performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("Pictures").fetchSemanticsNodes().isEmpty() }
        toggle(false).assertIsOff()
        toggle(true).assertIsOff()
    }
    @Test fun partialGrantStillShowsBothConsistentSwitches() {
        openSources(voiceRoot = true)
        rule.waitUntil(5000) { rule.onAllNodesWithText("Voice Recorder").fetchSemanticsNodes().isNotEmpty() }
        toggle(false).assertIsOn()
        toggle(true).assertIsOff().assertIsEnabled()
        rule.onNodeWithText("Not available yet").assertDoesNotExist()
    }
    @Test fun unreadableGrantIsShownOnFolderRow() {
        openSources(invalid = true)
        rule.waitUntil(5000) { rule.onAllNodes(hasText("Access needed") or hasText("נדרשת גישה מחדש")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("Access needed") or hasText("נדרשת גישה מחדש")).assertExists()
    }
}
