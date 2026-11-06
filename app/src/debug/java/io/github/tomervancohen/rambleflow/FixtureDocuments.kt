package io.github.tomervancohen.rambleflow

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract as D
import android.provider.DocumentsProvider
import java.io.File

/** Test APK only. It is not compiled into either production application variant. */
class FixtureDocuments : DocumentsProvider() {
    private val fields =
        arrayOf(
            D.Document.COLUMN_DOCUMENT_ID,
            D.Document.COLUMN_DISPLAY_NAME,
            D.Document.COLUMN_MIME_TYPE,
            D.Document.COLUMN_FLAGS,
            D.Document.COLUMN_SIZE,
            D.Document.COLUMN_LAST_MODIFIED,
        )
    private var recordingChildren = true
    private val fileParents = mutableMapOf<String, String>()
    private val files = linkedMapOf<String, Triple<String, Long, Long>>()

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(
                arrayOf(
                    D.Root.COLUMN_ROOT_ID,
                    D.Root.COLUMN_DOCUMENT_ID,
                    D.Root.COLUMN_TITLE,
                    D.Root.COLUMN_FLAGS,
                )
            )
            .apply {
                addRow(arrayOf("root", "recordings", "Recordings", D.Root.FLAG_SUPPORTS_IS_CHILD))
            }

    private fun row(id: String) =
        when (id) {
            "recordings" -> arrayOf<Any>(id, "Recordings", D.Document.MIME_TYPE_DIR, 0, 0, 0)
            "voice" -> arrayOf(id, "Voice Recorder", D.Document.MIME_TYPE_DIR, 0, 0, 0)
            "call" -> arrayOf(id, "Call", D.Document.MIME_TYPE_DIR, 0, 0, 0)
            "unrelated" -> arrayOf(id, "Pictures", D.Document.MIME_TYPE_DIR, 0, 0, 0)
            "miui" -> arrayOf(id, "MIUI", D.Document.MIME_TYPE_DIR, 0, 0, 0)
            "xiaomi-voice" -> arrayOf(id, "Sound_Recorder", D.Document.MIME_TYPE_DIR, 0, 0, 0)
            "xiaomi-call" -> arrayOf(id, "Call_Rec", D.Document.MIME_TYPE_DIR, 0, 0, 0)
            "pixel" -> arrayOf(id, "Recordings", D.Document.MIME_TYPE_DIR, 0, 0, 0)
            else ->
                files.getValue(id).let {
                    arrayOf(id, it.first, "audio/mp4", 0, it.second, it.third)
                }
        }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(fields).apply { addRow(row(documentId)) }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor =
        MatrixCursor(fields).apply {
            if (parentDocumentId == "recordings" && recordingChildren) {
                addRow(row("voice"))
                addRow(row("call"))
            } else if (parentDocumentId == "miui") addRow(row("xiaomi-voice"))
            else if (parentDocumentId == "xiaomi-voice") {
                if (recordingChildren) addRow(row("xiaomi-call"))
                files.keys.filter { fileParents[it] == null || fileParents[it] == parentDocumentId }.forEach { addRow(row(it)) }
            } else if (parentDocumentId in setOf("call", "xiaomi-call")) {
                files.keys.filter { fileParents[it] == parentDocumentId }.forEach { addRow(row(it)) }
            } else if (parentDocumentId in setOf("voice", "unrelated", "pixel")) files.keys.filter { fileParents[it] == null || fileParents[it] == parentDocumentId }.forEach { addRow(row(it)) }
        }

    override fun isChildDocument(parentDocumentId: String, documentId: String) =
        fileParents[documentId] == parentDocumentId || parentDocumentId == "recordings" ||
            (parentDocumentId == "miui" && (documentId in setOf("xiaomi-voice", "xiaomi-call") || files.containsKey(documentId))) ||
            (parentDocumentId == "xiaomi-voice" && (documentId == "xiaomi-call" || files.containsKey(documentId))) ||
            (parentDocumentId in setOf("voice", "unrelated", "pixel") && files.containsKey(documentId))

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor =
        ParcelFileDescriptor.open(
            File(context!!.filesDir, documentId),
            ParcelFileDescriptor.MODE_READ_ONLY,
        )

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "fixture.children") {
            recordingChildren = extras?.getBoolean("enabled", true) ?: true
            return Bundle()
        }
        if (method == "fixture.reset") {
            recordingChildren = true
            files.clear()
            fileParents.clear()
            return Bundle()
        }
        if (method == "fixture.add") {
            val id = extras!!.getString("id")!!
            val name = extras.getString("name")!!
            val size = extras.getLong("size")
            val modified = extras.getLong("modified")
            files[id] = Triple(name, size, modified)
            extras.getString("parent")?.let { fileParents[id] = it }
            extras.getByteArray("audio")?.let { File(context!!.filesDir, id).writeBytes(it) }
            return Bundle()
        }
        return super.call(method, arg, extras)
    }
}
