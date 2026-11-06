package io.github.tomervancohen.rambleflow

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

internal data class QuickRecordState(val mode: String = "idle", val seconds: Int = 0, val level: Float = 0f)

/** User-started recording; never starts at boot and never restarts the microphone after a crash. */
class QuickRecordService : Service() {
    companion object {
        const val START = "ramble.quick.START"
        const val TOGGLE = "ramble.quick.TOGGLE"
        const val STOP = "ramble.quick.STOP"
        private const val CHANNEL = "ramble_recording"
        private const val NOTIFICATION = 41
        internal val status = MutableStateFlow(QuickRecordState())
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: MediaRecorder? = null
    private var id: String? = null
    private var timer: Job? = null
    private var saving = false
    private var indicator: RecordingOverlay? = null
    override fun onCreate() { super.onCreate(); indicator = RecordingOverlay(this) }
    private val app get() = application as RambleApp
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action == STOP) { if(recorder == null && !saving) stopSelf() else stopCapture(); return START_NOT_STICKY }
        if(intent?.action == TOGGLE && recorder != null) { stopCapture(); return START_NOT_STICKY }
        if(intent?.action != START && intent?.action != TOGGLE) { stopSelf(); return START_NOT_STICKY }
        if(recorder != null || saving) return START_NOT_STICKY
        if (!app.settings.canQuickRecord()) { stopSelf(); return START_NOT_STICKY }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Ramble recording", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra("ramble.live.recording", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(this, 1, Intent(this, QuickRecordService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(appText(R.string.ui_ramble_is_recording_413b770))
                .setContentText(appText(R.string.ui_tap_stop_to_save_your_recording_a1fe4ae))
                .setContentIntent(open).setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setWhen(System.currentTimeMillis()).setUsesChronometer(true).setShowWhen(true)
                .setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(null, appText(R.string.ui_stop_f77e388), stop).build()).build()
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            // Submit any finalized recording interrupted between finalization and queue insertion.
            saving = true
            status.value = QuickRecordState("starting")
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { recoverPending() } }
                    .onSuccess { saving = false; startCapture() }
                    .onFailure { fail() }
            }
        } catch (_: Exception) { fail() }
        return START_NOT_STICKY
    }
    private suspend fun recoverPending() { AppRecordings(app).recoverInterruptedAudio() }
    @Suppress("DEPRECATION")
    private fun startCapture() {
        val next = UUID.randomUUID().toString()
        val store = AppRecordings(app)
        AppRecordings.markActive(next)
        val audio = if(Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        try {
            audio.setAudioSource(MediaRecorder.AudioSource.MIC)
            audio.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            audio.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            audio.setAudioChannels(1)
            audio.setAudioSamplingRate(44100)
            audio.setAudioEncodingBitRate(64000)
            audio.setOutputFile(store.partial(next).path)
            audio.setMaxDuration(Policy.MAX_DURATION_MS.toInt())
            audio.setMaxFileSize(Policy.MAX_SOURCE)
            audio.setOnInfoListener { _, what, _ ->
                if(what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) stopCapture()
            }
            audio.setOnErrorListener { _, _, _ -> stopCapture() }
            app.settings.quickPending = next
            audio.prepare(); audio.start()
            recorder = audio; id = next
            status.value = QuickRecordState("recording")
            indicator?.update(status.value)
            timer = scope.launch {
                var tick = 0
                while(isActive) {
                    delay(100); tick++
                    val peak = runCatching { audio.maxAmplitude }.getOrDefault(0)
                    val level = (kotlin.math.ln(1.0 + peak) / kotlin.math.ln(32768.0)).toFloat().coerceIn(0f, 1f)
                    status.value = QuickRecordState("recording", tick / 10, level)
                    indicator?.update(status.value)
                }
            }
        } catch (_: Exception) {
            runCatching { audio.release() }
            store.partial(next).delete()
            AppRecordings.markInactive(next)
            app.settings.quickPending = ""
            fail()
        }
    }
    private fun stopCapture() {
        val audio = recorder ?: return
        val savedId = id ?: return
        recorder = null; id = null
        timer?.cancel(); timer = null
        saving = true
        status.value = QuickRecordState("saving", status.value.seconds)
        indicator?.update(status.value)
        val store = AppRecordings(app)
        try {
            audio.stop(); audio.release()
            check(store.partial(savedId).renameTo(store.file(savedId)))
        } catch (_: Exception) {
            runCatching { audio.release() }
            AppRecordings.markInactive(savedId)
            fail(); return
        }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { store.submit(savedId, trial = false) } }
                .onSuccess {
                    app.settings.quickPending = ""
                    Schedule.pump(app)
                    status.value = QuickRecordState("saved")
                }.onFailure { status.value = QuickRecordState("error") }
            AppRecordings.markInactive(savedId)
            saving = false
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }
    private fun fail() {
        saving = false
        status.value = QuickRecordState("error")
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() {
        // A normal service stop finalizes and queues audio; a process kill never resumes capture.
        if(recorder != null) stopCapture()
        if(!saving) scope.cancel()
        indicator?.close(); indicator = null
        super.onDestroy()
    }
}
