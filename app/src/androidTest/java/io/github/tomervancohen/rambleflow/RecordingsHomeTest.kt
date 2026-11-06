package io.github.tomervancohen.rambleflow

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class RecordingsHomeTest {
    @get:Rule val rule = createComposeRule()
    @Test fun denseRowsShowRealPreviewDurationAndHonestDate() {
        val now = 1793962800000L
        val jobs = (1..12).map { Recording("r$it", "voice", "doc$it", if (it == 1) "A small thought.m4a" else "Morning thoughts $it.m4a", if(it == 2) null else now-it*1000, state = "saved") }
        val summaries = jobs.map { RecordingSummary(it.id, "Hello to all my agents. This is my first recording.", 65000, now) }
        var opened = ""
        var records = 0
        var settingsOpened = 0
        rule.setContent { RambleTheme { Scaffold { insets ->
            RecordingsHome(jobs, summaries, false, false, modifier = Modifier.padding(insets), onOpen = { opened = it.id }, onRecord = { records++ }, onRefresh = {}, onRepair = {}, onTranscriptionSettings = { settingsOpened++ })
        } } }
        rule.onNodeWithTag("auto-transcription-status").performClick()
        rule.runOnIdle { assertEquals(1, settingsOpened) }
        rule.onAllNodesWithText("1:05").onFirst().assertIsDisplayed()
        rule.onNodeWithText("Added ", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("recording-r1").performClick()
        rule.runOnIdle { assertEquals("r1", opened) }
        // Six useful rows fit without scrolling; the old padded cards could not do this.
        rule.onNodeWithTag("recording-r6").assertIsDisplayed()
        rule.onNodeWithTag("record-button").performClick()
        rule.runOnIdle { assertEquals(1, records) }
        android.os.SystemClock.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(ApplicationProvider.getApplicationContext<RambleApp>().cacheDir, "home-compact.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        val buttonBefore = rule.onNodeWithTag("record-button").fetchSemanticsNode().boundsInRoot
        val listBounds = rule.onNodeWithTag("recordings-list").fetchSemanticsNode().boundsInRoot
        assertTrue("The list extends under the floating button", listBounds.bottom >= buttonBefore.bottom)
        rule.onNodeWithTag("recordings-list").performScrollToNode(hasTestTag("recording-r12"))
        rule.onNodeWithTag("recording-r12").assertIsDisplayed()
        assertEquals("The button stays fixed while scrolling", buttonBefore, rule.onNodeWithTag("record-button").fetchSemanticsNode().boundsInRoot)
    }
    @Test fun summaryQueryUsesChunkAudioDurationAndFirstNonemptyText() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<RambleApp>()
        val db = Room.inMemoryDatabaseBuilder(context, QueueDatabase::class.java).build()
        try {
            val q = db.queue()
            q.recording(Recording("r", "voice", "doc", "Hello", null))
            q.observation(Observation("voice", "doc", "Hello", 100, 0, 12345, 12345, false))
            q.chunk(Chunk("r", 0, "", 0, 16000, state="done", text=""))
            q.chunk(Chunk("r", 1, "", 16000, 32000, state="done", text="Hello, agents"))
            val summary = q.summaries().first().single()
            assertEquals("Hello, agents", summary.preview)
            assertEquals(3000L, summary.durationMillis)
            assertEquals(12345L, summary.addedAt)
            q.recording(q.recording("r")!!.copy(duration = 4500))
            assertEquals(4500L, q.summaries().first().single().durationMillis)
        } finally { db.close() }
    }
}
