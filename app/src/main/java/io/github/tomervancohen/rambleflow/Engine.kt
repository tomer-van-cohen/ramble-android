package io.github.tomervancohen.rambleflow

import android.app.Application
import android.content.Context
import android.os.StatFs
import androidx.room.Room
import androidx.room.withTransaction
import androidx.work.*
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

class RambleApp : Application() {
    companion object { lateinit var instance: RambleApp; private set }
    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppLanguage.wrap(base)) }
    val settings by lazy { Settings(this) }
    private val maintenanceScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    fun maintainPrivateAudio() { maintenanceScope.launch { runCatching { AppRecordings(this@RambleApp).recoverInterruptedAudio();AudioRetention(this@RambleApp).clean() } } }
    internal var transcriber: Transcriber = ProviderApi(language = { settings.transcriptionLanguage })
    val database by lazy {
        Room.databaseBuilder(this, QueueDatabase::class.java, "queue.db")
            .addMigrations(QueueMigrations.FROM_1_TO_2, QueueMigrations.FROM_2_TO_3)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance=this
        settings.initializeAudioRetention()
        maintainPrivateAudio()
        val shareComponent = android.content.ComponentName(this, ReceiveAudioActivity::class.java)
        val shareState = if (RecorderDevice.current().family == RecorderFamily.PIXEL)
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (packageManager.getComponentEnabledSetting(shareComponent) != shareState)
            packageManager.setComponentEnabledSetting(shareComponent, shareState,
                android.content.pm.PackageManager.DONT_KILL_APP)
        Schedule.install(this)
    }
}

object Schedule {
    fun install(context: Context) {
        MediaWake.arm(context)
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "ramble.scan.v1",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ScanWorker>(15, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 60, TimeUnit.SECONDS)
                    .build(),
            )
    }

    fun scan(context: Context): Operation {
        MediaWake.arm(context)
        return WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "ramble.scan.now",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ScanWorker>()
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 60, TimeUnit.SECONDS)
                    .build(),
            )
    }

    fun wake(context: Context, delay: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork("ramble.retry.wake",ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RetryWakeWorker>().setInitialDelay(delay.coerceAtLeast(30_000),TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    fun pump(context: Context, delay: Long = 0) {
        val s = (context.applicationContext as RambleApp).settings
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(
                    if (s.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "ramble.process.v1",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<ProcessWorker>()
                    .setConstraints(constraints)
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .build(),
            )
    }
}

class RetryWakeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result { Schedule.pump(applicationContext);return Result.success() }
}

class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val app = applicationContext as RambleApp
            try {
                AppRecordings(app).recoverInterruptedAudio()
                AudioRetention(app).clean()
                val complete = SourceReader(app, app.database, app.settings).scan()
                app.settings.error = if (complete) "" else "source"
                if (!app.settings.paused) Schedule.pump(app)
                // Finalized audio needs a second observation. Do not leave it waiting for
                // another 15-minute periodic run (or for the user to reopen the app).
                val enabled =
                    app.database.queue().sources().filter { it.enabled }.map { it.kind }.toSet()
                val stabilizing =
                    app.database.queue().recordings().any {
                        it.source in enabled &&
                            it.state == "waiting" &&
                            it.reason == "file_stabilizing"
                    }
                if (complete && !app.settings.paused && stabilizing && runAttemptCount < 2)
                    Result.retry()
                else Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                app.settings.error = "source"
                Result.success()
            }
        }
}

class ProcessWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val app = applicationContext as RambleApp
            val db = app.database
            val q = db.queue()
            val settings = app.settings
            if (settings.paused || (settings.wifiOnly && metered(app)))
                return@withContext Result.success()
            val owner = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val acquired = db.withTransaction {
                val l = q.lease("processor")
                if (l != null && l.expires > now) false
                else {
                    q.lease(Lease("processor", owner, now + 10 * 60_000))
                    true
                }
            }
            if (!acquired) return@withContext Result.success()
            try {
                withTimeout(7 * 60_000L) {
                    val deadline = android.os.SystemClock.elapsedRealtime() + 6 * 60_000
                    for (job in q.recordings()) {
                        if (
                            settings.paused ||
                                (settings.wifiOnly && metered(app)) ||
                                android.os.SystemClock.elapsedRealtime() > deadline
                        )
                            break
                        if (
                            job.state in
                                setOf(
                                    "queued",
                                    "planning",
                                    "transcribing",
                                    "assemble",
                                    "publish",
                                    "rate",
                                    "retry",
                                    "storage",
                                ) && job.nextAttempt <= System.currentTimeMillis()
                        ) {
                            try {
                                process(app, job, deadline)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                val latest = q.recording(job.id) ?: job
                                if (
                                    e.message in
                                        setOf("source_changed", "recording_not_finished") &&
                                        q.chunks(job.id).isEmpty()
                                ) {
                                    q.recording(
                                        latest.copy(
                                            state = "waiting",
                                            reason = "recording_finish",
                                            completionProof = "",
                                            hash = "",
                                        )
                                    )
                                    continue
                                }
                                if (StorageFailures.isFull(e)) {
                                    q.recording(latest.copy(state = "storage", reason = "storage",
                                        nextAttempt = System.currentTimeMillis() + 60_000))
                                    continue
                                }
                                if (latest.state !in setOf("uncertain", "key", "rate", "retry"))
                                    q.recording(
                                        latest.copy(
                                            state = "error",
                                            reason =
                                                when (e) {
                                                    is RemoteFailure -> e.kind
                                                    is SecurityException -> "source"
                                                    else ->
                                                        e.message?.takeIf {
                                                            it.matches(Regex("[a-z_]{1,40}"))
                                                        } ?: "processing"
                                                },
                                        )
                                    )
                            }
                        }
                    }
                    val audioRoot = File(app.noBackupFilesDir, "audio")
                    for (known in q.recordings()) {
                        val folder = File(audioRoot, known.id)
                        if (
                            known.state in setOf("saved", "skipped", "duplicate")
                        )
                            folder.deleteRecursively()
                    }
                    AudioRetention(app).clean()
                    if (settings.driveEnabled && !settings.paused) {
                        for (job in
                            q.recordings().filter {
                                it.state == "saved" &&
                                    it.driveState != "uploaded" &&
                                    it.driveTarget.isNotBlank()
                            }) {
                            if (
                                settings.paused ||
                                    (settings.wifiOnly && metered(app)) ||
                                    android.os.SystemClock.elapsedRealtime() > deadline
                            )
                                break
                            try {
                                val updated =
                                    DriveExport(app, settings)
                                        .upload(job, db, LocalExport(app, db).file(job.id))
                                q.recording(updated)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                q.recording(
                                    (q.recording(job.id) ?: job).copy(
                                        driveState =
                                            if (e is RemoteFailure) e.kind else "drive_retry"
                                    )
                                )
                            }
                        }
                    }
                }
                val retry = q.recordings().filter { it.state in setOf("planning","queued","transcribing","assemble","publish","retry","storage","rate") }
                    .minOfOrNull { it.nextAttempt.coerceAtLeast(System.currentTimeMillis()+30_000) }
                if(retry!=null) Schedule.wake(app,retry-System.currentTimeMillis())
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } finally {
                withContext(NonCancellable) { q.release("processor", owner) }
            }
        }

    private fun metered(context: Context) =
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager)
            .isActiveNetworkMetered

    private suspend fun process(app: RambleApp, initial: Recording, deadline: Long) {
        val q = app.database.queue()
        val settings = app.settings
        var job = q.recording(initial.id)!!
        if (job.source !in setOf("app", "shared") && q.sources().none { it.kind == job.source && it.enabled }) return
        val exports = LocalExport(app, app.database)
        if (job.state == "publish") {
            exports.publish(job)
            return
        }
        if (job.state == "assemble") {
            exports.publish(exports.assemble(job))
            return
        }
        val dir = File(app.noBackupFilesDir, "audio/${job.id}").apply { mkdirs() }
        var chunks = q.chunks(job.id)
        if (chunks.isEmpty() || !job.planComplete) {
            val cached = File(dir,"source")
            val source = if (job.hash.isNotBlank() && cached.exists() && cached.sha256() == job.hash) cached
                else SourceReader(app, app.database, settings).snapshot(job)
            require(StatFs(dir.path).availableBytes > 256L*1024*1024 + Policy.CHUNK_SAMPLES*2L) { "storage" }
            val hash = source.sha256()
            val duplicate = q.recordings().find { it.id != job.id && it.hash == hash && it.state == "saved" }
            if (duplicate != null) {
                q.recording(job.copy(state="duplicate",reason="",hash=hash));dir.deleteRecursively();return
            }
            if (job.hash.isNotBlank() && job.hash != hash) error("source_changed")
            job = job.copy(state="planning",hash=hash,provider=job.provider.ifBlank { settings.profile(settings.provider) },planComplete=false)
            q.recording(job)
            val completed = AudioPlanner().resume(source,dir,chunks.size,
                shouldContinue = { !settings.paused && android.os.SystemClock.elapsedRealtime()+10_000 < deadline },
                onPart = { ordinal, part -> withContext(NonCancellable) {
                    q.chunk(Chunk(job.id,ordinal,part.file.path,part.start,part.samples,hash=part.file.sha256()))
                } })
            if (!completed) return
            job = job.copy(state="transcribing",planComplete=true)
            q.recording(job)
            chunks = q.chunks(job.id)
            require(chunks.isNotEmpty()) { "unsupported_audio" }
            source.delete()
        }
        if (chunks.any { it.state != "done" && !File(it.file).exists() }) {
            val cached = File(dir,"source")
            val recovered = if (cached.exists() && cached.sha256()==job.hash) cached else SourceReader(app,app.database,settings).snapshot(job)
            require(recovered.sha256()==job.hash) { "source_changed" }
            for (stored in chunks.filter { it.state!="done" && !File(it.file).exists() }) {
                val part = AudioPlanner().restore(recovered,dir,stored.ordinal) {
                    !settings.paused && android.os.SystemClock.elapsedRealtime()+10_000 < deadline
                } ?: return
                require(part.start==stored.startSample && part.samples==stored.samples && part.file.sha256()==stored.hash) { "source_changed" }
            }
            recovered.delete()
        }
        for (chunk in chunks) {
            if (chunk.attempts >= 8 && chunk.state != "done") {
                q.recording(job.copy(state = "error", reason = "retry_limit"))
                return
            }
            if (chunk.state == "done") continue
            if (chunk.state == "inflight") {
                q.recording(job.copy(state = "uncertain", reason = "possible_charge"))
                return
            }
            if (chunk.nextAttempt > System.currentTimeMillis()) return
            if (
                settings.paused ||
                    (settings.wifiOnly && metered(app)) ||
                    android.os.SystemClock.elapsedRealtime() + 130_000 > deadline
            )
                return
            val key =
                try {
                    settings.readKey(job.provider)
                } catch (_: Exception) {
                    q.recording(job.copy(state = "key", reason = "key"))
                    return
                }
            val file = File(chunk.file)
            require(file.exists() && file.sha256() == chunk.hash) { "source_missing" }
            val attempt = UUID.randomUUID().toString()
            q.attempt(
                Attempt(attempt, job.id, chunk.ordinal, System.currentTimeMillis(), "sending")
            )
            q.chunk(
                chunk.copy(state = "inflight", attempt = attempt, attempts = chunk.attempts + 1)
            )
            q.recording(job.copy(state = "transcribing"))
            try {
                val response =
                    withContext(NonCancellable + Dispatchers.IO) {
                        app.transcriber.transcribe(job.provider, key, file).also {
                            if (it.isBlank()) throw RemoteFailure("no_speech")
                        }
                    }
                withContext(NonCancellable) {
                    q.attempt(
                        Attempt(attempt, job.id, chunk.ordinal, System.currentTimeMillis(), "saved")
                    )
                    q.chunk(
                        chunk.copy(
                            state = "done",
                            text = response,
                            attempt = attempt,
                            attempts = chunk.attempts + 1,
                        )
                    )
                }
            } catch (e: Exception) {
                val type = (e as? RemoteFailure)?.kind ?: "uncertain"
                if (type == "no_speech") {
                    // A silent chunk must not discard speech from another part of a long recording.
                    withContext(NonCancellable) {
                        q.attempt(Attempt(attempt, job.id, chunk.ordinal, System.currentTimeMillis(), type))
                        q.chunk(chunk.copy(state = "done", text = "", attempt = attempt,
                            attempts = chunk.attempts + 1))
                    }
                    continue
                }
                withContext(NonCancellable) {
                    q.attempt(
                        Attempt(attempt, job.id, chunk.ordinal, System.currentTimeMillis(), type)
                    )
                    q.chunk(
                        chunk.copy(
                            state = if (type in setOf("key", "rate", "network", "rejected")) "ready" else "inflight",
                            attempt = attempt,
                            attempts = if (type in setOf("key", "rate", "network", "rejected")) chunk.attempts else chunk.attempts + 1,
                            nextAttempt =
                                if (type in setOf("rate", "network"))
                                    System.currentTimeMillis() +
                                        ((e as? RemoteFailure)?.retryAfter ?: 15 * 60_000)
                                else 0,
                        )
                    )
                    q.recording(
                        job.copy(
                            state =
                                if (type == "key") "key"
                                else if (type == "rate") "rate"
                                else if (type == "network") "retry"
                                else if (type in setOf("no_speech", "rejected")) "error" else "uncertain",
                            reason = if (type == "rejected") "provider_${(e as RemoteFailure).status}" else type,
                            nextAttempt =
                                if (type in setOf("rate", "network"))
                                    System.currentTimeMillis() +
                                        ((e as? RemoteFailure)?.retryAfter ?: 15 * 60_000)
                                else 0,
                        )
                    )
                }
                return
            }
        }
        val completed = q.chunks(job.id)
        if (completed.all { it.state == "done" }) {
            if (completed.none { it.text.isNotBlank() }) {
                q.recording(job.copy(state = "error", reason = "no_speech"))
                return
            }
            job =
                job.copy(
                    state = "assemble",
                    driveTarget = if (settings.driveEnabled) settings.driveFolder else "",
                    driveState = if (settings.driveEnabled) "queued" else "",
                )
            q.recording(job)
            exports.publish(exports.assemble(job))
            dir.deleteRecursively()
        }
    }
}
