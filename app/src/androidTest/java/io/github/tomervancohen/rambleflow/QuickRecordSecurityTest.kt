package io.github.tomervancohen.rambleflow

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class QuickRecordSecurityTest {
    @get:Rule val rule=createEmptyComposeRule()
    @Test fun exportedEntryCannotStartOrStopEvenWithForgedInternalExtras() {
        val app=ApplicationProvider.getApplicationContext<RambleApp>()
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        app.settings.step=10;app.settings.paused=true;app.settings.provider="groq"
        app.settings.saveKey("groq","synthetic-security-test-key")
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(app.packageName,Manifest.permission.RECORD_AUDIO)
        if(android.os.Build.VERSION.SDK_INT>=33) automation.grantRuntimePermission(app.packageName,Manifest.permission.POST_NOTIFICATIONS)
        @Suppress("DEPRECATION")
        val private=app.packageManager.getActivityInfo(ComponentName(app,InternalRecordActivity::class.java),0)
        assertFalse(private.exported)
        val forged=Intent(app,QuickRecordActivity::class.java).putExtra("fromApp",true)
            .putExtra("trusted",true).putExtra("controls",false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(forged)
            rule.waitUntil(5000) { rule.onAllNodesWithText("Start a recording?").fetchSemanticsNodes().isNotEmpty() }
            android.os.SystemClock.sleep(750)
            assertFalse(QuickRecordService.status.value.mode in setOf("recording","starting"))
            rule.onNodeWithText("Record").performClick()
            rule.waitUntil(10000) { QuickRecordService.status.value.mode=="recording" }
            val pending=app.settings.quickPending
            app.startActivity(forged)
            rule.waitUntil(5000) { rule.onAllNodesWithText("Recording in progress").fetchSemanticsNodes().isNotEmpty() }
            android.os.SystemClock.sleep(750)
            assertEquals("recording",QuickRecordService.status.value.mode)
            assertEquals(pending,app.settings.quickPending)
            rule.onNodeWithText("Stop and save").performClick()
            rule.waitUntil(10000) { QuickRecordService.status.value.mode=="saved" }
        } finally { app.startService(Intent(app,QuickRecordService::class.java).setAction(QuickRecordService.STOP)) }
    }
}
