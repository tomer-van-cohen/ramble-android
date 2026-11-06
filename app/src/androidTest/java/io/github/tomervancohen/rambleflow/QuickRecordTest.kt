package io.github.tomervancohen.rambleflow

import android.Manifest
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After

class QuickRecordTest {
    private var oldStep = 0
    private var oldPaused = false
    private var oldProvider = "groq"
    @Before fun configuredUser() {
        val settings = ApplicationProvider.getApplicationContext<RambleApp>().settings
        oldStep = settings.step
        oldPaused = settings.paused
        settings.paused = true
        oldProvider = settings.provider
        settings.provider = "quick-record-test"
        settings.saveKey("quick-record-test", "synthetic-not-a-real-api-key")
        settings.step = 10
    }
    @After fun restoreUser() {
        val app = ApplicationProvider.getApplicationContext<RambleApp>()
        app.startService(Intent(app, QuickRecordService::class.java).setAction(QuickRecordService.STOP))
        app.settings.paused = oldPaused
        app.settings.step = oldStep
        app.settings.provider = oldProvider
    }

    private fun awaitMode(mode: String) {
        val end = android.os.SystemClock.elapsedRealtime() + 10000
        while(QuickRecordService.status.value.mode != mode && android.os.SystemClock.elapsedRealtime() < end)
            android.os.SystemClock.sleep(100)
        assertEquals(mode, QuickRecordService.status.value.mode)
    }
    @Test fun inAppEntryStartsTheSharedRecorder() {
        val app = ApplicationProvider.getApplicationContext<RambleApp>()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
        if(android.os.Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val trialBefore = app.settings.trialRecording
        try {
            app.startActivity(Intent(app, InternalRecordActivity::class.java).putExtra("fromApp", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitMode("recording")
            val id = app.settings.quickPending
            android.os.SystemClock.sleep(1500)
            app.startService(Intent(app, QuickRecordService::class.java).setAction(QuickRecordService.STOP))
            awaitMode("saved")
            runBlocking {
                val saved = app.database.queue().recording(id)
                assertNotNull(saved)
                assertEquals("app", saved!!.source)
                assertTrue(saved.duration >= 1000)
            }
            assertEquals(trialBefore, app.settings.trialRecording)
        } finally { app.startService(Intent(app, QuickRecordService::class.java).setAction(QuickRecordService.STOP)) }
    }
    @Test fun launchingShortcutAgainStopsAndQueuesAudio() {
        val app = ApplicationProvider.getApplicationContext<RambleApp>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
        if(android.os.Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        try {
            app.startActivity(Intent(app, InternalRecordActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitMode("recording")
            val id = app.settings.quickPending
            android.os.SystemClock.sleep(1800)
            app.startActivity(Intent(app, InternalRecordActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitMode("saved")
            runBlocking { assertNotNull(app.database.queue().recording(id)) }
            assertEquals("", app.settings.quickPending)
            assertFalse(app.getSystemService(android.app.NotificationManager::class.java).activeNotifications.any { it.id == 41 })
        } finally {
            app.startService(Intent(app, QuickRecordService::class.java).setAction(QuickRecordService.STOP))
        }
    }
    @Test fun launchesImmediatelySurvivesBackgroundAndSavesOnce() {
        val app = ApplicationProvider.getApplicationContext<RambleApp>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
        if(android.os.Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val trialBefore = app.settings.trialRecording
        try {
            instrumentation.startActivitySync(Intent(app, InternalRecordActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitMode("recording")
            val id = app.settings.quickPending
            assertTrue(id.isNotBlank())
            // In-app Record must reuse the session, never accidentally stop it.
            app.startActivity(Intent(app, InternalRecordActivity::class.java).putExtra("fromApp", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            android.os.SystemClock.sleep(600)
            assertEquals("recording", QuickRecordService.status.value.mode)
            assertEquals(id, app.settings.quickPending)
            // The notification opens controls without toggling the active session.
            app.startActivity(Intent(app, InternalRecordActivity::class.java).putExtra("controls", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            android.os.SystemClock.sleep(500)
            assertEquals(id, app.settings.quickPending)
            app.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            automation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            val elapsed = QuickRecordService.status.value.seconds
            android.os.SystemClock.sleep(2200)
            assertFalse("Display must actually be asleep", app.getSystemService(android.os.PowerManager::class.java).isInteractive)
            assertEquals("recording", QuickRecordService.status.value.mode)
            assertTrue(QuickRecordService.status.value.seconds > elapsed)
            val notification = app.getSystemService(android.app.NotificationManager::class.java).activeNotifications.single { it.id == 41 }
            notification.notification.actions.single().actionIntent.send()
            awaitMode("saved")
            runBlocking {
                val job = app.database.queue().recording(id)
                assertNotNull(job)
                assertTrue(job!!.duration >= 2000)
                assertEquals("app", job.source)
                assertTrue(AppRecordings(app).file(id).length() > 0)
            }
            assertEquals(trialBefore, app.settings.trialRecording)
            assertEquals("", app.settings.quickPending)
        } finally {
            app.startService(Intent(app, QuickRecordService::class.java).setAction(QuickRecordService.STOP))
            automation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            automation.executeShellCommand("wm dismiss-keyguard").close()
        }
    }
}
