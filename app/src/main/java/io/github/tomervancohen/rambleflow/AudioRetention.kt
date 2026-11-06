package io.github.tomervancohen.rambleflow

import androidx.room.withTransaction
import java.io.File
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Touches app-private copies only. Original source folders and public/Drive exports are never deleted. */
class AudioRetention(private val app: RambleApp) {
    companion object { internal val mutationLock=Mutex() }
    suspend fun clean(forceCompleted: Boolean = false): Int = mutationLock.withLock { cleanLocked(forceCompleted) }
    suspend fun remove(job: Recording): Boolean = mutationLock.withLock { removeLocked(job) }

    private suspend fun cleanLocked(forceCompleted: Boolean): Int {
        val jobs=app.database.queue().recordings()
        var removed=0
        for (job in jobs) {
            if (job.state=="deleted") {
                if (job.nextAttempt>=0) { erasePrivate(job,includeTranscript=true)
                    if(!privateFilesPresent(job)) app.database.queue().recording(job.copy(nextAttempt=-1)) }
                continue
            }
            val durable = job.state=="saved" && LocalExport(app,app.database).file(job.id).let { it.exists() && it.length()>0 }
            val duplicate = job.state=="duplicate" && jobs.any { it.state=="saved" && it.hash==job.hash &&
                LocalExport(app,app.database).file(it.id).let { file -> file.exists() && file.length()>0 } }
            if ((forceCompleted || !app.settings.keepAudio) && (durable || duplicate)) {
                if (erasePrivate(job,includeTranscript=false)) removed++
            }
        }
        // Abandoned shared imports are copies; a sender's original is outside our ownership.
        val known=jobs.filter { it.state!="deleted" }.map { it.id }.toSet()
        val cutoff=System.currentTimeMillis()-24*60*60_000L
        File(app.noBackupFilesDir,"shared-audio").listFiles().orEmpty().filter {
            it.name !in known && it.isFile && it.lastModified()<cutoff
        }.forEach { it.delete() }
        return removed
    }

    private suspend fun removeLocked(job: Recording): Boolean {
        if (AppRecordings.isActive(job.id)) return false
        val q=app.database.queue()
        val owner=UUID.randomUUID().toString()
        val now=System.currentTimeMillis()
        val acquired=app.database.withTransaction {
            val lease=q.lease("processor")
            if (lease!=null && lease.expires>now) false
            else { q.lease(Lease("processor",owner,now+60_000));true }
        }
        if (!acquired) return false
        try {
            app.database.withTransaction {
                // Keep a minimal origin tombstone so scanning never reimports something the user removed.
                q.recording(job.copy(state="deleted",title="",recordedAt=null,reason="",hash="",provider="",
                    duration=0,localUri="",localTarget="",driveId="",driveTarget="",driveState="",nextAttempt=0))
                q.deleteChunks(job.id);q.deleteAttempts(job.id)
            }
            erasePrivate(job,includeTranscript=true)
            if(!privateFilesPresent(job)) q.recording(q.recording(job.id)!!.copy(nextAttempt=-1))
            return true
        } finally { q.release("processor",owner) }
    }

    private fun privateFilesPresent(job: Recording): Boolean {
        val original=runCatching { when(job.source) {
            "app" -> AppRecordings(app).file(job.id)
            "shared" -> SharedAudio(app).file(job.id)
            else -> null
        } }.getOrNull()
        return listOfNotNull(original,original?.let { File(it.path+".partial") },
            File(app.noBackupFilesDir,"audio/${job.id}"),LocalExport(app,app.database).file(job.id)).any { it.exists() }
    }
    private fun erasePrivate(job: Recording, includeTranscript: Boolean): Boolean {
        require(job.id.matches(Regex("[a-zA-Z0-9-]+")))
        var changed=false
        if (!AppRecordings.isActive(job.id)) {
            val original = runCatching {
                when(job.source) {
                    "app" -> AppRecordings(app).file(job.id)
                    "shared" -> SharedAudio(app).file(job.id)
                    else -> null
                }
            }.getOrNull()
            for(file in listOfNotNull(original,original?.let { File(it.path+".partial") }))
                if (file.exists() && file.delete()) changed=true
        }
        val decoded=File(app.noBackupFilesDir,"audio/${job.id}")
        if(decoded.exists() && decoded.deleteRecursively()) changed=true
        if (includeTranscript) LocalExport(app,app.database).file(job.id).delete()
        return changed
    }
}
