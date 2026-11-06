package io.github.tomervancohen.rambleflow

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.room.withTransaction
import java.io.File
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock

/** Owns an immutable local copy while the sender's temporary URI permission is valid. */
class SharedAudio(private val app: RambleApp) {
    data class Staged(val id: String, val duration: Long)
    private fun directory() = File(app.noBackupFilesDir, "shared-audio").apply { mkdirs() }
    fun file(id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(directory(), id)
    }

    suspend fun stage(uri: Uri): Staged {
        require(uri.scheme == "content") { "unsupported_uri" }
        val authority = uri.authority.orEmpty()
        // A share sender must not make us exercise our private FileProvider privileges.
        require(authority.isNotBlank() && '@' !in authority && authority != "${app.packageName}.files") { "unsupported_uri" }
        val temp = File.createTempFile("incoming-", ".partial", directory())
        try {
            val used = directory().listFiles().orEmpty().sumOf { it.length() }
            val input = app.contentResolver.openInputStream(uri) ?: error("unreadable_audio")
            input.use { source -> temp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var size = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    size += count
                    require(size <= Policy.MAX_SOURCE && Policy.canStageAudio(used, size)) { "storage" }
                    require(android.os.StatFs(directory().path).availableBytes > count + 256L * 1024 * 1024) { "storage" }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
                require(size > 0) { "empty_audio" }
            } }
            val extractor = MediaExtractor()
            val duration = try {
                extractor.setDataSource(temp.path)
                val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                require(formats.size == 1 && formats.single().getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { "unsupported_audio" }
                val format = formats.single()
                require(format.containsKey(MediaFormat.KEY_DURATION)) { "unsupported_duration" }
                (format.getLong(MediaFormat.KEY_DURATION) / 1000).also {
                    require(it in 1..Policy.MAX_DURATION_MS) { "unsupported_duration" }
                }
            } finally { extractor.release() }
            val hash = temp.sha256()
            val id = UUID.nameUUIDFromBytes(hash.toByteArray(Charsets.UTF_8)).toString()
            AudioRetention.mutationLock.withLock {
                app.database.queue().recording(id)?.takeIf { it.state=="deleted" }?.let {
                    app.database.queue().recording(it.copy(nextAttempt=-1))
                }
                val target=file(id)
                if(target.exists()) require(target.sha256()==hash) else check(temp.renameTo(target))
                target.setLastModified(System.currentTimeMillis())
            }
            return Staged(id, duration)
        } finally { temp.delete() }
    }

    suspend fun submit(staged: Staged): Recording = AudioRetention.mutationLock.withLock {
        app.database.queue().recording(staged.id)?.takeIf { it.state!="deleted" }?.let { return@withLock it }
        require(app.settings.hasKey(app.settings.provider)) { "key" }
        val audio = file(staged.id)
        require(audio.length() in 1..Policy.MAX_SOURCE)
        val job = Recording(
            id = staged.id, source = "shared", document = staged.id,
            title = appText(R.string.ui_shared_recording_145c6cc),
            recordedAt = null, state = "queued", hash = audio.sha256(),
            provider = app.settings.profile(app.settings.provider), duration = staged.duration,
            localTarget = app.settings.outputTree.ifBlank { "downloads" },
            driveTarget = if (app.settings.driveEnabled) app.settings.driveFolder else "",
            driveState = if (app.settings.driveEnabled) "pending" else "",
        )
        app.database.withTransaction {
            if (app.database.queue().recording(job.id)?.state in setOf(null,"deleted")) app.database.queue().recording(job)
        }
        app.database.queue().recording(job.id)!!
    }

    fun snapshot(job: Recording): File {
        require(job.source == "shared" && job.document == job.id)
        val source = file(job.id)
        require(source.length() in 1..Policy.MAX_SOURCE && source.sha256() == job.hash) { "source_changed" }
        val dir = File(app.noBackupFilesDir, "audio/${job.id}").apply { mkdirs() }
        require(android.os.StatFs(dir.path).availableBytes > source.length() + 256L * 1024 * 1024) { "storage" }
        val target = File(dir, "source")
        source.copyTo(target, overwrite = true)
        require(target.sha256() == job.hash) { "source_changed" }
        return target
    }
}
