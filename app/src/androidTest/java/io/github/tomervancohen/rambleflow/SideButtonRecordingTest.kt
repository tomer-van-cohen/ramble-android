package io.github.tomervancohen.rambleflow

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in physical Samsung check. Configure Open app → Ramble Record before running. */
class SideButtonRecordingTest {
    @Test fun twoPowerPressesStartBackgroundRecording() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("rambleSideButtonTest") == "true")
        val app = ApplicationProvider.getApplicationContext<RambleApp>()
        val automation = instrumentation.uiAutomation
        fun command(value: String) { automation.executeShellCommand(value).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        fun awaitMode(mode: String) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 12000
            while(QuickRecordService.status.value.mode != mode && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(100)
            assertEquals(mode, QuickRecordService.status.value.mode)
        }
        try {
            app.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            command("input keyevent KEYCODE_WAKEUP")
            command("wm dismiss-keyguard")
            android.os.SystemClock.sleep(800)
            // No Ramble activity/service launch here: Samsung's configured key handler must do it.
            command("input keyevent KEYCODE_POWER KEYCODE_POWER")
            awaitMode("recording")
            val id = app.settings.quickPending
            assertTrue(id.isNotBlank())
            command("input keyevent KEYCODE_SLEEP")
            val seconds = QuickRecordService.status.value.seconds
            android.os.SystemClock.sleep(3200)
            assertFalse(app.getSystemService(android.os.PowerManager::class.java).isInteractive)
            assertEquals("recording", QuickRecordService.status.value.mode)
            assertTrue(QuickRecordService.status.value.seconds > seconds)
            // A second double press must stop from the sleeping/locked state, with no notification tap.
            command("input keyevent KEYCODE_POWER KEYCODE_POWER")
            awaitMode("saved")
            val duration = runBlocking { app.database.queue().recording(id)!!.duration }
            assertTrue(duration >= 3000)
            File(app.cacheDir, "side-button-record-result.txt").writeText("Samsung double-power-press: recording\nBackground with display asleep: recording\nSecond double-power-press: saved\nFinalized duration: $duration ms\n")
        } finally {
            app.startService(Intent(app, QuickRecordService::class.java).setAction(QuickRecordService.STOP))
            command("input keyevent KEYCODE_WAKEUP")
            command("wm dismiss-keyguard")
        }
    }
}
