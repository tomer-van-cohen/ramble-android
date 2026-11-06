package io.github.tomervancohen.rambleflow

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Read-only, explicitly opted-in inspection of exported Android Settings entry points. */
class QuickSettingsProbeTest {
    @Test fun exportedSettingsEntries() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rambleSettingsProbe") == "true")
        val app = ApplicationProvider.getApplicationContext<RambleApp>()
        @Suppress("DEPRECATION")
        val activities = app.packageManager.getPackageInfo("com.android.settings", PackageManager.GET_ACTIVITIES).activities.orEmpty()
        val entries = activities.filter { Regex("side|useful|advanced|gesture|power|functionkey", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
            .joinToString("\n") { "${it.name} exported=${it.exported} permission=${it.permission.orEmpty()}" }
        java.io.File(app.cacheDir, "quick-settings-entries.txt").writeText(entries)
    }
    @Test fun sideButtonSettingsLaunchesAsTheAppUid() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rambleSettingsLaunch") == "true")
        val app = ApplicationProvider.getApplicationContext<RambleApp>()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.injectInputEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_WAKEUP), true)
        automation.injectInputEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_WAKEUP), true)
        org.junit.Assert.assertEquals(QuickRecordingSettings.Destination.SIDE_BUTTON,
            QuickRecordingSettings.open(app, RecorderDevice.forManufacturer("Samsung")))
        val deadline = android.os.SystemClock.elapsedRealtime() + 8000
        var found = false
        while (!found && android.os.SystemClock.elapsedRealtime() < deadline) {
            val root = automation.rootInActiveWindow
            found = root?.findAccessibilityNodeInfosByText("Double press")?.isNotEmpty() == true ||
                root?.findAccessibilityNodeInfosByText("לחיצה כפולה")?.isNotEmpty() == true
            if (!found) android.os.SystemClock.sleep(150)
        }
        val root = automation.rootInActiveWindow
        val labels = mutableListOf<String>()
        fun collect(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int = 0) {
            if (node == null || depth > 20 || labels.size >= 60) return
            if (node.packageName?.toString() == "com.android.settings")
                node.text?.toString()?.takeIf { it.isNotBlank() }?.let { labels += it }
            for (i in 0 until node.childCount) collect(node.getChild(i), depth + 1)
        }
        collect(root)
        java.io.File(app.cacheDir, "side-button-state.txt").writeText(
            "interactive=" + app.getSystemService(android.os.PowerManager::class.java).isInteractive +
            " package=" + root?.packageName + "\n" + labels.joinToString("\n"))
        android.os.SystemClock.sleep(400)
        val image = automation.takeScreenshot()
        java.io.File(app.cacheDir, "side-button-settings-proof.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
        org.junit.Assert.assertTrue("Side button page must visibly contain Double press; unlock the test device first", found)
    }

}
