package io.github.tomervancohen.rambleflow

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EntryIntegrationTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<RambleApp>()

    @Test fun onlyRambleIsAnAppAndRecordIsALongPressShortcut() {
        val launchers = app.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.packageName), 0)
        assertEquals(listOf(MainActivity::class.java.name), launchers.map { it.activityInfo.name })
        val shortcut = app.getSystemService(ShortcutManager::class.java).manifestShortcuts.single { it.id == "ramble-record" }
        assertEquals(ComponentName(app, MainActivity::class.java), shortcut.activity)
        assertEquals(ComponentName(app, QuickRecordActivity::class.java), shortcut.intent!!.component)
    }

    @Test fun oldQuickShortcutAndNotificationCannotBypassFreshSetup() {
        val oldStep = app.settings.step
        val oldPaused = app.settings.paused
        app.settings.step = 0
        app.settings.paused = true
        rule.mainClock.autoAdvance = false
        try {
            for (intent in listOf(
                Intent(app, QuickRecordActivity::class.java),
                Intent(app, MainActivity::class.java).putExtra("ramble.live.recording", true),
                Intent(app, MainActivity::class.java),
            )) {
                ActivityScenario.launch<android.app.Activity>(intent).use {
                    rule.waitUntil(10000) { runCatching { rule.onAllNodesWithTag("welcome-headline").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
                    rule.onNodeWithTag("welcome-headline").assertIsDisplayed()
                    assertEquals(0, app.settings.step)
                    assertFalse(QuickRecordService.status.value.mode in setOf("recording", "starting"))
                }
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val monitor = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    androidx.test.runner.lifecycle.Stage.values().filter { it != androidx.test.runner.lifecycle.Stage.DESTROYED }
                        .flatMap { monitor.getActivitiesInStage(it) }.distinct().forEach { it.finish() }
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
        } finally { app.settings.step = oldStep; app.settings.paused = oldPaused }
    }

    @Test fun validatedKeyRecoversOnlyKeyBlockedJobsForItsProvider() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, QueueDatabase::class.java).build()
        try {
            val q = db.queue()
            for ((id, provider, state) in listOf(
                Triple("missing", "groq", "key"), Triple("old-key", "groq@old", "key"),
                Triple("other", "openai", "key"), Triple("uncertain", "groq", "uncertain"),
                Triple("done", "groq", "saved"),
            )) q.recording(Recording(id, "app", id, id, 0, state = state, provider = provider))
            assertEquals(2, q.resumeKeyBlocked("groq", "groq@new"))
            for (id in listOf("missing", "old-key")) {
                assertEquals("queued", q.recording(id)!!.state)
                assertEquals("groq@new", q.recording(id)!!.provider)
            }
            assertEquals("key", q.recording("other")!!.state)
            assertEquals("uncertain", q.recording("uncertain")!!.state)
            assertEquals("saved", q.recording("done")!!.state)
        } finally { db.close() }
    }
}
