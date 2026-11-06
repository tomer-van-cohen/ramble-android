package io.github.tomervancohen.rambleflow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

/** Launcher toggles capture; the notification opens controls without toggling it. */
open class QuickRecordActivity : ComponentActivity() {
    private var pendingActivation = true
    private var controls by mutableStateOf(false)
    private var fromApp = false
    private var needsConfirmation by mutableStateOf(true)
    private val internalEntry get() = this is InternalRecordActivity
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pendingActivation = true; activateWhenVisible()
        } else QuickRecordService.status.value = QuickRecordState("permission")
    }
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(AppLanguage.wrap(base)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirectToSetup()) return
        setShowWhenLocked(true) // Only recording controls, never the main app or other locked data.
        fromApp = internalEntry && intent.getBooleanExtra("fromApp", false)
        needsConfirmation = !internalEntry
        controls = !internalEntry || intent.getBooleanExtra("controls", false) || fromApp
        pendingActivation = internalEntry && (savedInstanceState?.getBoolean("pendingActivation") ?: (!controls || fromApp))
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        window.setLayout((resources.displayMetrics.density * 340).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        window.attributes = window.attributes.apply { y = (resources.displayMetrics.density * 12).toInt() }
        // Stopping via the key is silent: no control window flashes over the current app.
        if(!controls && QuickRecordService.status.value.mode in setOf("recording", "saving")) window.decorView.alpha = 0f
        setContent {
            RambleTheme {
                val capture by QuickRecordService.status.collectAsState()
                LaunchedEffect(capture.mode, controls) {
                    val notificationsAllowed = android.os.Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if(capture.mode == "recording" && !controls && notificationsAllowed) { delay(650); finishAndRemoveTask() }
                    if(capture.mode == "saved" && !needsConfirmation) { delay(900); finishAndRemoveTask() }
                }
                if (needsConfirmation) {
                    Surface(shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val active = capture.mode == "recording"
                            Text(if (active) appText(R.string.ui_recording_in_progress_2d5ec0c)
                                else appText(R.string.ui_start_a_recording_7b04882))
                            Button(onClick = {
                                needsConfirmation = false
                                pendingActivation = true
                                activateWhenVisible()
                            }) { Text(if (active) appText(R.string.ui_stop_and_save_becc0e8) else appText(R.string.ui_record_08a8c66)) }
                            TextButton(onClick = { finishAndRemoveTask() }) { Text(appText(R.string.ui_cancel_1fa68c8)) }
                        }
                    }
                } else if(capture.mode in setOf("permission", "error")) {
                    Surface(shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if(capture.mode == "permission") appText(R.string.ui_microphone_access_needed_35dcee0) else appText(R.string.ui_couldn_t_record_5bf9e1d))
                            Button(onClick = {
                                if(capture.mode == "permission") permission.launch(permissions())
                                else { pendingActivation = true; activateWhenVisible() }
                            }) { Text(appText(R.string.ui_try_again_a8e3285)) }
                        }
                    }
                } else RecordingPill(capture) {
                    startService(Intent(this@QuickRecordActivity, QuickRecordService::class.java).setAction(QuickRecordService.STOP))
                }
            }
        }
    }
    override fun onPostResume() { super.onPostResume(); window.decorView.post { activateWhenVisible() } }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (redirectToSetup()) return
        fromApp = internalEntry && intent.getBooleanExtra("fromApp", false)
        needsConfirmation = !internalEntry
        controls = !internalEntry || intent.getBooleanExtra("controls", false) || fromApp
        pendingActivation = internalEntry && (!controls || fromApp)
        if(!controls && QuickRecordService.status.value.mode in setOf("recording", "saving")) window.decorView.alpha = 0f
        else window.decorView.alpha = 1f
        activateWhenVisible()
    }
    private fun redirectToSetup(): Boolean {
        // Always allow an existing capture to be stopped, even if settings changed.
        if (QuickRecordService.status.value.mode in setOf("recording", "saving") ||
            (application as RambleApp).settings.canQuickRecord()) return false
        pendingActivation = false
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finishAndRemoveTask()
        return true
    }
    private fun permissions(): Array<String> = if(android.os.Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.RECORD_AUDIO)
    private fun activateWhenVisible() {
        if(!pendingActivation || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        if (redirectToSetup()) return
        pendingActivation = false
        val mode = QuickRecordService.status.value.mode
        if(mode == "saving" || mode == "starting") { finishAndRemoveTask(); return }
        if(mode == "recording" && fromApp) return
        if(mode == "recording") {
            startService(Intent(this, QuickRecordService::class.java).setAction(QuickRecordService.TOGGLE))
            finishAndRemoveTask()
            return
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(permissions()); return
        }
        runCatching { startForegroundService(Intent(this, QuickRecordService::class.java).setAction(QuickRecordService.TOGGLE)) }
            .onFailure { QuickRecordService.status.value = QuickRecordState("error") }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("pendingActivation", pendingActivation); super.onSaveInstanceState(outState) }
}

@Composable
internal fun RecordingPill(capture: QuickRecordState, stop: () -> Unit) {
    val green = Color(0xFF25D366)
    Surface(color = Color(0xFF151716), contentColor = Color.White, shape = RoundedCornerShape(44.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Canvas(Modifier.size(66.dp, 34.dp)) {
                for(i in 0..10) {
                    val shape = .3f + .7f * kotlin.math.abs(sin(i * .85f + capture.seconds))
                    val amplitude = if(capture.mode == "recording") .15f + capture.level * shape * .85f else .12f
                    val x = size.width * i / 10
                    drawLine(green, Offset(x, size.height * (.5f - amplitude / 2)), Offset(x, size.height * (.5f + amplitude / 2)), 3.dp.toPx(), StrokeCap.Round)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(when(capture.mode) {
                    "recording" -> "%02d:%02d".format(capture.seconds / 60, capture.seconds % 60)
                    "saved" -> appText(R.string.ui_saved_a64d43e)
                    "saving" -> appText(R.string.ui_saving_8d4c7f0)
                    else -> appText(R.string.ui_starting_2ff22c8)
                }, fontSize = 23.sp)
                Text(appText(R.string.ui_ramble_background_recording_8f16e31), fontSize = 10.sp, color = Color(0xFFB5BDB7))
            }
            if(capture.mode == "recording") OutlinedIconButton(onClick = stop, modifier = Modifier.size(46.dp),
                shape = CircleShape, border = BorderStroke(1.5.dp, Color.White)) {
                Icon(Icons.Filled.Stop, appText(R.string.ui_stop_and_save_becc0e8), tint = Color(0xFFFF6269), modifier = Modifier.size(27.dp))
            }
        }
    }
}

/** Non-exported capability: only our UID or our immutable PendingIntent can enter directly. */
class InternalRecordActivity : QuickRecordActivity()
