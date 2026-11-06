package io.github.tomervancohen.rambleflow

import android.Manifest
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RecordingOverlayTest {
    @Test fun staysVisibleOpensRambleAndStopsWithRecording() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rambleOverlayTest") == "true")
        val app = ApplicationProvider.getApplicationContext<RambleApp>()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun command(value: String) { automation.executeShellCommand(value).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
        val hadOverlay = android.provider.Settings.canDrawOverlays(app)
        val serviceInfo = automation.serviceInfo
        serviceInfo.flags = serviceInfo.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        fun findOverlay(): android.view.accessibility.AccessibilityNodeInfo? {
            fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
                if(node == null) return null
                if(node.packageName == app.packageName && node.contentDescription?.toString()?.startsWith("Ramble is recording.") == true) return node
                for(i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
                return null
            }
            return automation.windows.firstNotNullOfOrNull { find(it.root) }
        }
        fun await(mode: String) {
            val end = android.os.SystemClock.elapsedRealtime() + 8000
            while(QuickRecordService.status.value.mode != mode && android.os.SystemClock.elapsedRealtime() < end) android.os.SystemClock.sleep(100)
            assertEquals(mode, QuickRecordService.status.value.mode)
        }
        try {
            command("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
            automation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
            if(android.os.Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
            command("input keyevent KEYCODE_WAKEUP"); command("wm dismiss-keyguard")
            app.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            app.startActivity(Intent(app, InternalRecordActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("recording")
            android.os.SystemClock.sleep(2200) // Longer than the startup indication: the overlay must remain.
            assertNotEquals("com.android.settings", automation.rootInActiveWindow?.packageName?.toString())
            assertNotNull(findOverlay())
            val monitor = InstrumentationRegistry.getInstrumentation().addMonitor(MainActivity::class.java.name, null, false)
            assertTrue(findOverlay()!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            val opened = monitor.waitForActivityWithTimeout(8000) as? MainActivity
            InstrumentationRegistry.getInstrumentation().removeMonitor(monitor)
            assertNotNull("Indicator must open the actual Ramble activity", opened)
            assertTrue("Ramble must show the live recording page", opened!!.showsLiveRecording)
            val deadline = android.os.SystemClock.elapsedRealtime() + 4000
            while(!opened.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(100)
            assertTrue(opened.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            val bitmap = automation.takeScreenshot()
            java.io.File(app.cacheDir, "overlay-open.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            command("input keyevent KEYCODE_SLEEP")
            android.os.SystemClock.sleep(700)
            assertNull("Overlay must not cover the keyguard", findOverlay())
            val notification = app.getSystemService(android.app.NotificationManager::class.java).activeNotifications.single { it.id == 41 }.notification
            assertEquals(android.app.Notification.VISIBILITY_PUBLIC, notification.visibility)
            assertTrue(notification.extras.getBoolean(android.app.Notification.EXTRA_SHOW_CHRONOMETER))
            notification.actions.single().actionIntent.send()
            await("saved")
            assertNull(findOverlay())
        } finally {
            app.startService(Intent(app, QuickRecordService::class.java).setAction(QuickRecordService.STOP))
            command("input keyevent KEYCODE_WAKEUP"); command("wm dismiss-keyguard")
            if(!hadOverlay) command("appops set ${app.packageName} SYSTEM_ALERT_WINDOW default")
        }
    }
}
