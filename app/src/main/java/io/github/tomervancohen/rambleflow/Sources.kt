package io.github.tomervancohen.rambleflow

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract as D
import androidx.room.withTransaction
import java.time.ZoneId
import java.util.UUID

/** Native document IDs contain the folder path; custom folders otherwise default to voice audio. */
internal fun Source.isCallRecordingSource(): Boolean = kind == "call" ||
    (kind.startsWith("folder:") && RecorderDevice.callFolders.any {
        it.equals(document.substringAfterLast('/').substringAfterLast(':'), ignoreCase = true)
    })

class SourceReader(
    private val context: Context,
    private val db: QueueDatabase,
    private val settings: Settings,
    private val device: RecorderDevice = RecorderDevice.current(),
) {
    data class Entry(
        val id: String,
        val name: String,
        val size: Long,
        val modified: Long,
        val mime: String,
    )

    private val resolver = context.contentResolver

    private fun Cursor.s(name: String) =
        getColumnIndex(name).let { if (it < 0 || isNull(it)) "" else getString(it) }

    private fun Cursor.l(name: String) =
        getColumnIndex(name).let { if (it < 0 || isNull(it)) 0L else getLong(it) }

    fun entries(tree: String, doc: String): List<Entry> {
        val uri = D.buildChildDocumentsUriUsingTree(Uri.parse(tree), doc)
        val rows = mutableListOf<Entry>()
        val fields =
            arrayOf(
                D.Document.COLUMN_DOCUMENT_ID,
                D.Document.COLUMN_DISPLAY_NAME,
                D.Document.COLUMN_SIZE,
                D.Document.COLUMN_LAST_MODIFIED,
                D.Document.COLUMN_MIME_TYPE,
            )
        val cursor = resolver.query(uri, fields, null, null, null) ?: error("source")
        cursor.use { c ->
            while (c.moveToNext()) {
                check(rows.size < 20_000) { "source_too_large" }
                rows +=
                    Entry(
                        c.s(fields[0]),
                        c.s(fields[1]),
                        c.l(fields[2]),
                        c.l(fields[3]),
                        c.s(fields[4]),
                    )
            }
        }
        return rows
    }

    /**
     * Checks the granted folder itself, without reading recording contents or changing its
     * baseline.
     */
    fun canRead(source: Source): Boolean =
        try {
            check(source.tree !in settings.removedSourceTrees)
            val uri = D.buildDocumentUriUsingTree(Uri.parse(source.tree), source.document)
            resolver.query(uri, arrayOf(D.Document.COLUMN_MIME_TYPE), null, null, null)?.use {
                it.moveToFirst() && it.s(D.Document.COLUMN_MIME_TYPE) == D.Document.MIME_TYPE_DIR
            } == true
        } catch (_: Exception) {
            false
        }

    fun folderLabel(source: Source): String {
        if (source.document.contains(':')) return source.document.substringAfter(':')
        val uri = D.buildDocumentUriUsingTree(Uri.parse(source.tree), source.document)
        return runCatching {
            resolver.query(uri, arrayOf(D.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.s(D.Document.COLUMN_DISPLAY_NAME) else ""
            }.orEmpty().ifBlank { source.document }
        }.getOrDefault(source.document)
    }

    /** Recover sources covered by an already connected parent, without requesting permission again.
     * Explicitly disabled sources remain disabled; activation never resets an existing baseline.
     */
    suspend fun restoreCoveredSources() = db.withTransaction {
        val sources = db.queue().sources()
        val existing = sources.find { it.kind == "call" }
        if (existing != null && (!existing.enabled || canRead(existing))) return@withTransaction
        for (source in sources.filter { (it.kind == "voice" || it.kind.startsWith("folder:")) && it.enabled && it.baselineComplete && canRead(it) }) {
            val tree = Uri.parse(source.tree)
            if ((existing == null || existing.tree == source.tree) && hasKnownCallFolder(tree)) {
                if (existing != null) db.queue().source(existing.copy(
                    document = resolveFolder(tree, "call", allowCustom = false)!!))
                else activate(tree, "call")
                return@withTransaction
            }
        }
    }

    private fun standardCallParent(name: String?) = when (device.family) {
        RecorderFamily.SAMSUNG -> name.equals("Recordings", true)
        RecorderFamily.XIAOMI -> name.equals("MIUI", true) || name.equals("sound_recorder", true)
        else -> false
    }

    fun hasKnownCallFolder(tree: Uri): Boolean =
        try {
            resolveFolder(tree, "call", allowCustom = false) != null
        } catch (_: Exception) {
            false
        }

    fun resolveFolder(tree: Uri, kind: String, allowCustom: Boolean = true): String? {
        val root = D.getTreeDocumentId(tree)
        if (kind.startsWith("folder:")) {
            entries(tree.toString(), root)
            return root
        }
        fun matches(name: String?, names: Collection<String>) = names.any { name.equals(it, ignoreCase = true) }
        val mapped =
            if (kind == "call") RecorderDevice.callFolders else RecorderDevice.voiceFolders.toSet()
        val child =
            entries(tree.toString(), root).find {
                matches(it.name, mapped) && it.mime == D.Document.MIME_TYPE_DIR
            }
        val rootName =
            D.buildDocumentUriUsingTree(tree, root).let { u ->
                resolver.query(u, arrayOf(D.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.s(D.Document.COLUMN_DISPLAY_NAME) else ""
                }
            }
        val otherKindFolders =
            if (kind == "call") RecorderDevice.voiceFolders.toSet() else RecorderDevice.callFolders
        val callParent = kind == "call" && standardCallParent(rootName)
        if (child == null && matches(rootName, otherKindFolders) && !callParent) return null
        // Xiaomi may grant MIUI rather than sound_recorder. Descend only through
        // this known branch, never recursively through unrelated folders.
        val xiaomiRecorder = if (rootName.equals("MIUI", ignoreCase = true))
            entries(tree.toString(), root).find {
                it.name.equals("sound_recorder", ignoreCase = true) && it.mime == D.Document.MIME_TYPE_DIR
            } else null
        val nested = if (kind == "call") {
            val recorderDoc = xiaomiRecorder?.id ?: child?.id
            recorderDoc?.let { doc ->
                entries(tree.toString(), doc).find {
                    matches(it.name, RecorderDevice.callFolders) && it.mime == D.Document.MIME_TYPE_DIR
                }?.id
            }
        } else xiaomiRecorder?.id
        val doc =
            nested ?: child?.id
                ?: if (
                    matches(rootName, mapped) || callParent ||
                        allowCustom ||
                        (rootName.equals("Recordings", ignoreCase = true) && kind == "voice")
                )
                    root
                else return null
        entries(tree.toString(), doc)
        return doc
    }

    suspend fun activate(tree: Uri, kind: String, enabled: Boolean = true): Boolean {
        if (kind.startsWith("folder:") && tree.toString() in settings.removedSourceTrees) {
            val previous = db.queue().sources().filter { it.tree == tree.toString() && it.kind in setOf("voice", "call") }
            if (previous.isNotEmpty()) {
                var connected = false
                previous.forEach { if (activate(tree, it.kind, enabled)) connected = true }
                return connected
            }
        }
        val doc = resolveFolder(tree, kind) ?: return false
        // Verify access to the resolved recording folder, including when reconnecting it.
        val files = sourceEntries(Source(kind, tree.toString(), doc, 0))
        val saved = db.queue().sources()
        val old = saved.find { it.kind == kind } ?: saved.find {
            it.document == doc && Uri.parse(it.tree).authority == tree.authority &&
                kind.startsWith("folder:") && (it.kind.startsWith("folder:") || it.kind == "voice")
        }
        if (
            old != null &&
                old.document == doc &&
                Uri.parse(old.tree).authority == tree.authority &&
                old.baselineComplete
        ) {
            db.queue().source(old.copy(tree = tree.toString(), enabled = enabled))
            settings.removedSourceTrees = settings.removedSourceTrees - tree.toString()
            settings.tree = tree.toString()
            return true
        }
        val source = Source(kind, tree.toString(), doc, System.currentTimeMillis(), enabled = enabled)
        db.queue().source(source)
        // Activation is a baseline-only operation. A crash keeps baselineComplete=false.
        for (e in files.filter { audio(it) }) db.queue()
            .observation(
                Observation(
                    kind,
                    e.id,
                    e.name,
                    e.size,
                    e.modified,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    true,
                )
            )
        db.queue().source(source.copy(baselineComplete = true))
        settings.removedSourceTrees = settings.removedSourceTrees - tree.toString()
        settings.tree = tree.toString()
        return true
    }

    /** Switches processing without forgetting the folder or its activation baseline. */
    suspend fun setCategoryEnabled(kind: String, enabled: Boolean): Boolean = db.withTransaction {
        val visible = db.queue().sources().filter { it.tree !in settings.removedSourceTrees && it.baselineComplete }
        val members = visible.filter { if (kind == "call") it.isCallRecordingSource() else !it.isCallRecordingSource() }
        if (!enabled) {
            members.forEach { db.queue().source(it.copy(enabled = false)) }
            return@withTransaction true
        }
        val readable = members.filter { canRead(it) }
        if (readable.isNotEmpty()) {
            readable.forEach { db.queue().source(it.copy(enabled = true)) }
            return@withTransaction true
        }
        // A disabled sibling still carries a usable parent grant.
        for (source in visible.filter { canRead(it) }) {
            val tree = Uri.parse(source.tree)
            if (resolveFolder(tree, kind, allowCustom = false) != null)
                return@withTransaction activate(tree, kind)
        }
        false
    }

    suspend fun removeFolder(tree: String) = db.withTransaction {
        settings.removedSourceTrees = settings.removedSourceTrees + tree
        db.queue().sources().filter { it.tree == tree }.forEach {
            db.queue().source(it.copy(enabled = false))
        }
        if (settings.tree == tree) settings.tree = ""
        // Only release this app's grant; never delete the user's folder or audio files.
        runCatching { resolver.releasePersistableUriPermission(Uri.parse(tree), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        Unit
    }

    suspend fun replaceFolder(previous: String, next: Uri): Boolean = db.withTransaction {
        val members = db.queue().sources().filter { it.tree == previous }
        var changed = false
        for (source in members) {
            val known = resolveFolder(next, source.kind, allowCustom = false)
            if (known != null || members.size == 1 || source.kind == "voice" || source.kind.startsWith("folder:")) {
                if (activate(next, source.kind, source.enabled)) changed = true
            }
        }
        if (changed && previous != next.toString()) removeFolder(previous)
        changed
    }

    fun audio(e: Entry) =
        e.mime.startsWith("audio/") ||
            e.name.substringAfterLast('.').lowercase() in
                setOf("m4a", "mp3", "wav", "aac", "amr", "ogg", "mp4", "webm")

    /** Empty standard parents can gain their recorder child after the first recording. */
    private fun sourceEntries(source: Source): List<Entry> {
        val direct = entries(source.tree, source.document)
        val tree = Uri.parse(source.tree)
        if (source.kind == "call" && source.document == D.getTreeDocumentId(tree)) {
            val name = folderLabel(source).substringAfterLast('/')
            if (!standardCallParent(name)) return direct
            val target = resolveFolder(tree, "call", allowCustom = false) ?: return emptyList()
            // A parent grant covers future Call/call_rec; never treat its voice files as calls.
            return if (target != source.document) entries(source.tree, target) else emptyList()
        }
        if ((source.kind != "voice" && !source.kind.startsWith("folder:")) ||
            source.document != D.getTreeDocumentId(tree)) return direct
        val uri = D.buildDocumentUriUsingTree(tree, source.document)
        val name = resolver.query(uri, arrayOf(D.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.s(D.Document.COLUMN_DISPLAY_NAME) else ""
        }
        if (!name.equals("Recordings", true) && !name.equals("MIUI", true)) return direct
        val folders = listOfNotNull(
            resolveFolder(tree, "voice", allowCustom = false),

        ).distinct().filter { it != source.document }
        return (direct + folders.flatMap { entries(source.tree, it) }).distinctBy { it.id }
    }

    suspend fun scan(): Boolean {
        restoreCoveredSources()
        val q = db.queue()
        val now = System.currentTimeMillis()
        var complete = true
        for (source in q.sources().filter { it.enabled && it.baselineComplete && it.tree !in settings.removedSourceTrees }) {
            try {
                for (e in sourceEntries(source).filter { audio(it) }) {
                    val old = q.observation(source.kind, e.id)
                    if (old?.baseline == true) continue
                    val existing = q.origin(source.kind, e.id)
                    if (existing != null && existing.state !in setOf("waiting", "confirm", "excluded")) continue
                    val evidence = probe(source, e.id)
                    val unchanged = old != null && old.size == e.size && old.modified == e.modified
                    val sameProof = evidence != null && old?.proof == evidence.proof
                    val proofAt =
                        if (sameProof) old!!.proofAt else if (evidence != null) now else 0L
                    q.observation(
                        Observation(
                            source.kind,
                            e.id,
                            e.name,
                            e.size,
                            e.modified,
                            old?.firstSeen ?: now,
                            if (unchanged) old!!.stableSince else now,
                            false,
                            evidence?.proof ?: "",
                            proofAt,
                        )
                    )
                    val filenameDate = Policy.filenameDate(e.name, ZoneId.systemDefault())
                    // Android writes mvhd creation time at close. Subtract duration conservatively
                    // when no filename start time exists; never substitute filesystem arrival time.
                    val inferred = evidence?.createdMillis?.minus(evidence.durationMillis + 1000)
                    val date =
                        if (filenameDate != null)
                            evidence?.createdMillis?.let { minOf(filenameDate, it) } ?: filenameDate
                        else inferred
                    // The activation baseline records what was already present. A newly
                    // copied/imported file is eligible even if its embedded recording date is old.
                    val ready = unchanged && evidence != null && sameProof && proofAt > 0 &&
                        now - proofAt >= 60_000
                    val state = if (ready) "queued" else "waiting"
                    val reason = if (ready) "auto_new_file" else if (evidence == null)
                        "recording_finish" else "file_stabilizing"
                    val candidate =
                        (existing
                                ?: Recording(
                                    UUID.randomUUID().toString(),
                                    source.kind,
                                    e.id,
                                    e.name,
                                    date,
                                    localTarget = settings.outputTree.ifBlank { "downloads" },
                                ))
                            .copy(
                                recordedAt = date,
                                state = state,
                                reason = reason,
                                completionProof = if (ready) evidence!!.proof else "",
                                hash = "",
                            )
                    db.withTransaction {
                        val current = q.origin(source.kind, e.id)
                        if (current == null || current.state in setOf("waiting", "confirm", "excluded"))
                            q.recording(candidate.copy(id = current?.id ?: candidate.id))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                complete = false
            }
        }
        if (complete) settings.lastScan = now
        return complete
    }

    fun probe(source: Source, document: String): FinalizedMp4.Evidence? {
        val uri = D.buildDocumentUriUsingTree(Uri.parse(source.tree), document)
        return try {
            val fd = resolver.openFileDescriptor(uri, "r") ?: return null
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {
                FolderAudioEvidence.inspect(it.fd, it.channel)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun snapshot(job: Recording): java.io.File {
        if (job.source == "shared")
            return SharedAudio(context.applicationContext as RambleApp).snapshot(job)
        if (job.source == "app")
            return AppRecordings(context.applicationContext as RambleApp).snapshot(job)
        val source =
            db.queue().sources().find { it.kind == job.source && it.enabled && it.tree !in settings.removedSourceTrees } ?: error("source")
        val before =
            sourceEntries(source).find { it.id == job.document }
                ?: error("source_missing")
        val dir = java.io.File(context.noBackupFilesDir, "audio/${job.id}").apply { mkdirs() }
        val target = java.io.File(dir, "source")
        val partial = java.io.File(dir, "source.partial")
        partial.delete()
        target.delete()
        val uri = D.buildDocumentUriUsingTree(Uri.parse(source.tree), job.document)
        val fd = resolver.openFileDescriptor(uri, "r") ?: error("source")
        var actualSize = 0L
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
            actualSize = input.channel.size()
            require(actualSize in 1..Policy.MAX_SOURCE) { "unsupported_size" }
            val used = dir.parentFile!!.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            require(
                Policy.canStageAudio(used, actualSize) &&
                    android.os.StatFs(dir.path).availableBytes > actualSize + 256L * 1024 * 1024
            ) {
                "storage"
            }
            if (job.completionProof.isNotBlank()) {
                val proof = FolderAudioEvidence.inspect(input.fd, input.channel)
                require(proof?.proof == job.completionProof) { "recording_not_finished" }
            }
            input.channel.position(0)
            partial.outputStream().use { out ->
                val buffer = ByteArray(65536)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= actualSize) { "source_changed" }
                    out.write(buffer, 0, n)
                }
                out.fd.sync()
            }
        }
        val after =
            sourceEntries(source).find { it.id == job.document }
                ?: error("source_missing")
        if (
            before.size != after.size ||
                before.modified != after.modified ||
                partial.length() != actualSize
        ) {
            partial.delete()
            error("source_changed")
        }
        if (job.completionProof.isNotBlank()) {
            val copy = java.io.FileInputStream(partial).use { FolderAudioEvidence.inspect(it.fd, it.channel) }
            if (
                copy?.proof != job.completionProof ||
                    probe(source, job.document)?.proof != job.completionProof
            ) {
                partial.delete()
                error("recording_not_finished")
            }
        }
        check(partial.renameTo(target))
        return target
    }
}
