package io.github.tomervancohen.rambleflow

import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Closed recordings created by this app. Never accepts paths/URIs from external input. */
class AppRecordings(private val app: RambleApp) {
    companion object {
        private val active=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private val recoveryLock=Mutex()
        fun markActive(id:String) { active.add(id) }
        fun markInactive(id:String) { active.remove(id) }
        fun isActive(id:String)=id in active
    }
    suspend fun recoverInterruptedAudio(): Int = recoveryLock.withLock {
        val q=app.database.queue()
        var recovered=0
        val known=q.recordings().filter { it.source=="app" }.map { it.id }.toSet()
        val ids=directory().listFiles().orEmpty().mapNotNull {
            it.name.removeSuffix(".partial").removeSuffix(".m4a").takeIf { id -> runCatching { UUID.fromString(id).toString()==id }.getOrDefault(false) }
        }.distinct()
        for(id in ids) {
            if (isActive(id) || id in known) continue
            val source=file(id).takeIf { it.exists() } ?: partial(id)
            val proof=runCatching { source.inputStream().use { FinalizedMp4.inspect(it.channel) } }.getOrNull()
            // Only normal quick capture carries explicit ongoing-import intent. An interrupted trial
            // stays visible and requires confirmation; recovery must not silently upload a cancelled trial.
            if(id==app.settings.quickPending && proof!=null) {
                if(source==partial(id)) check(source.renameTo(file(id)))
                val job=submit(id,trial=false)
                q.recording(job.copy(title=appText(R.string.ui_recovered_recording_25d9129)))
            } else q.recording(Recording(id,"app",id,appText(R.string.ui_interrupted_recording_0395f22),null,
                state="error",reason="interrupted_audio",provider=app.settings.profile(app.settings.provider)))
            if(app.settings.quickPending==id) app.settings.quickPending=""
            recovered++
        }
        recovered
    }
    suspend fun resumeInterrupted(job: Recording) {
        require(job.source=="app" && job.reason=="interrupted_audio")
        val source=file(job.id).takeIf { it.exists() } ?: partial(job.id)
        val proof=source.inputStream().use { FinalizedMp4.inspect(it.channel) } ?: error("incomplete_audio")
        if(source==partial(job.id)) check(source.renameTo(file(job.id)))
        app.database.queue().recording(job.copy(state="queued",reason="",hash=file(job.id).sha256(),completionProof=proof.proof,
            duration=proof.durationMillis,provider=app.settings.profile(app.settings.provider)))
    }

    private fun directory() = File(app.noBackupFilesDir, "recordings").apply { mkdirs() }

    fun file(id: String): File {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) {
            "recording_id"
        }
        return File(directory(), "$id.m4a")
    }

    fun partial(id: String) = File(file(id).path + ".partial")

    suspend fun submit(id: String, trial: Boolean = true): Recording {
        val q = app.database.queue()
        q.recording(id)?.let {
            if (trial) app.settings.trialRecording = id
            return it
        }
        val audio = file(id)
        require(audio.length() in 1..Policy.MAX_SOURCE) { "recording_empty" }
        val evidence =
            audio.inputStream().use { FinalizedMp4.inspect(it.channel) }
                ?: error("recording_not_finished")
        val job =
            Recording(
                id,
                "app",
                id,
                if (trial) appText(R.string.my_first_recording_title)
                else appText(R.string.app_recording_title,java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date())),
                System.currentTimeMillis() - evidence.durationMillis,
                state = "queued",
                completionProof = evidence.proof,
                hash = audio.sha256(),
                provider = app.settings.profile(app.settings.provider),
                duration = evidence.durationMillis,
                localTarget = app.settings.outputTree.ifBlank { "downloads" },
                driveTarget = if (app.settings.driveEnabled) app.settings.driveFolder else "",
                driveState = if (app.settings.driveEnabled) "pending" else "",
            )
        // The deterministic job ID makes replay after a crash or a double tap idempotent.
        app.database.withTransaction { if (q.recording(id) == null) q.recording(job) }
        if (trial) app.settings.trialRecording = id
        return q.recording(id)!!
    }

    suspend fun recover(): Recording? {
        val q = app.database.queue()
        val id = app.settings.trialRecording
        if (id == "new") return null
        if (id.isNotBlank())
            q.recording(id)?.let {
                return it
            }
        // Covers a crash after the DB commit and before the preference commit.
        return q.recordings()
            .firstOrNull { it.source == "app" }
            ?.also {
                app.settings.trialRecording = it.id
            }
    }

    fun snapshot(job: Recording): File {
        require(job.source == "app" && job.document == job.id) { "source" }
        val source = file(job.id)
        require(source.length() in 1..Policy.MAX_SOURCE) { "source_missing" }
        require(source.sha256() == job.hash) { "source_changed" }
        val proof = source.inputStream().use { FinalizedMp4.inspect(it.channel) }
        require(proof?.proof == job.completionProof) { "recording_not_finished" }
        val dir = File(app.noBackupFilesDir, "audio/${job.id}").apply { mkdirs() }
        require(android.os.StatFs(dir.path).availableBytes > source.length() + 256L * 1024 * 1024) {
            "storage"
        }
        val temp = File(dir, "source.partial")
        source.inputStream().use { input ->
            temp.outputStream().use { out ->
                input.copyTo(out)
                out.fd.sync()
            }
        }
        require(temp.sha256() == job.hash && source.sha256() == job.hash) { "source_changed" }
        val target = File(dir, "source")
        target.delete()
        check(temp.renameTo(target))
        return target
    }
}

data class TrialCapture(val mode: String = "idle", val seconds: Int = 0, val error: String = "", val level: Float = 0f)

/** Onboarding-only, foreground capture: leaving the app cancels unsaved audio, never uploads it. */
class TrialRecorder(app: android.app.Application) : AndroidViewModel(app) {
    private val app = app as RambleApp
    private val store = AppRecordings(this.app)
    private val mutable = MutableStateFlow(TrialCapture())
    val state = mutable.asStateFlow()
    private var recorder: MediaRecorder? = null
    private var id: String? = null
    private var timer: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) { store.recover() }
    }

    @Suppress("DEPRECATION")
    fun start() {
        if (recorder != null || mutable.value.mode == "saving") return
        val next = UUID.randomUUID().toString()
        val audio = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(app) else MediaRecorder()
        AppRecordings.markActive(next)
        try {
            audio.setAudioSource(MediaRecorder.AudioSource.MIC)
            audio.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            audio.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            audio.setAudioChannels(1)
            audio.setAudioSamplingRate(44100)
            audio.setAudioEncodingBitRate(64000)
            audio.setOutputFile(store.partial(next).path)
            audio.prepare()
            audio.start()
            recorder = audio
            id = next
            mutable.value = TrialCapture("recording")
            timer = viewModelScope.launch {
                for (tick in 1..300) {
                    delay(100)
                    val peak = runCatching { audio.maxAmplitude }.getOrDefault(0)
                    val level = (kotlin.math.ln(1.0 + peak) / kotlin.math.ln(32768.0)).toFloat().coerceIn(0f, 1f)
                    mutable.value = TrialCapture("recording", tick / 10, level = level)
                }
                stop()
            }
        } catch (_: Exception) {
            audio.release()
            store.partial(next).delete()
            AppRecordings.markInactive(next)
            mutable.value = TrialCapture(error = "start")
        }
    }

    fun stop() {
        val audio = recorder ?: return
        val savedId = id ?: return
        recorder = null
        id = null
        timer?.cancel()
        timer = null
        mutable.value = TrialCapture("saving", mutable.value.seconds)
        try {
            audio.stop()
            audio.release()
            check(store.partial(savedId).renameTo(store.file(savedId)))
        } catch (_: Exception) {
            runCatching { audio.release() }
            store.partial(savedId).delete()
            AppRecordings.markInactive(savedId)
            mutable.value = TrialCapture(error = "too_short")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.submit(savedId) }
                Schedule.pump(app)
                mutable.value = TrialCapture("submitted")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutable.value = TrialCapture(error = "save")
            } finally { AppRecordings.markInactive(savedId) }
        }
    }

    fun cancel(reason: String = "interrupted") {
        val audio = recorder ?: return
        recorder = null
        timer?.cancel()
        timer = null
        runCatching { audio.stop() }
        audio.release()
        id?.let { store.partial(it).delete();AppRecordings.markInactive(it) }
        id = null
        mutable.value = TrialCapture(error = reason)
    }

    fun reset() {
        cancel()
        app.settings.trialRecording = "new"
        mutable.value = TrialCapture()
    }

    fun denied() {
        mutable.value = TrialCapture(error = "permission")
    }

    override fun onCleared() {
        cancel()
    }
}
