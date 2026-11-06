package io.github.tomervancohen.rambleflow

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

fun File.sha256(): String =
    inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val b = ByteArray(65536)
        while (true) {
            val n = input.read(b)
            if (n < 0) break
            digest.update(b, 0, n)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

class LocalExport(private val context: Context, private val db: QueueDatabase) {
    fun file(id: String) = File(context.filesDir, "transcripts/${Policy.safeName(id)}")

    suspend fun assemble(job: Recording): Recording {
        val chunks = db.queue().chunks(job.id)
        require(chunks.isNotEmpty() && chunks.all { it.state == "done" })
        require(chunks.sumOf { it.text.toByteArray().size.toLong() } <= 16L * 1024 * 1024) {
            "transcript_limit"
        }
        val output = file(job.id)
        output.parentFile!!.mkdirs()
        val text = buildString {
            append("Title: ${job.title.replace('\n',' ').replace('\r',' ')}\n")
            append(
                "Recorded at${if(job.reason=="auto_metadata_time")" (estimated from container)" else ""}: ${job.recordedAt?.let{Instant.ofEpochMilli(it).toString()}?:"Unknown"}\nSource: ${job.source}\nTranscript ID: ${job.id}\nTranscribed at: ${Instant.now()}\nProvider: ${job.provider}\n\n"
            )
            append(chunks.joinToString("\n\n") { it.text })
        }
        if (!output.exists()) {
            val partial = File(output.path + ".partial")
            partial.outputStream().use {
                it.write(text.toByteArray())
                it.fd.sync()
            }
            check(partial.renameTo(output))
        }
        val next = job.copy(state = "publish", reason = "")
        db.queue().recording(next)
        return next
    }

    suspend fun publish(job: Recording): Recording {
        if (job.localTarget.startsWith("content://")) return publishCustom(job)
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var uri = job.localUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        if (uri == null) {
            val name = Policy.safeName(job.id)
            // Recover the insert-before-DB-commit crash using app-owned deterministic identity.
            resolver
                .query(
                    MediaStore.setIncludePending(collection),
                    arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.OWNER_PACKAGE_NAME),
                    "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH} IN (?, ?)",
                    arrayOf(name, "Download/Ramble/", "Download/Ramble Flow/"),
                    null,
                )
                ?.use { c ->
                    while (c.moveToNext()) if (c.getString(1) == context.packageName) {
                        uri = android.content.ContentUris.withAppendedId(collection, c.getLong(0))
                        break
                    }
                }
            if (uri == null) {
                val values =
                    ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/Ramble/")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                uri = resolver.insert(collection, values) ?: error("storage")
            }
            db.queue().recording(job.copy(localUri = uri.toString()))
        }
        val pending =
            resolver.query(uri!!, arrayOf(MediaStore.Downloads.IS_PENDING), null, null, null)?.use {
                if (it.moveToFirst()) it.getInt(0) != 0 else true
            } ?: true
        if (!pending) {
            val same =
                resolver.openInputStream(uri!!)?.use { stream ->
                    val d = MessageDigest.getInstance("SHA-256")
                    val b = ByteArray(65536)
                    while (true) {
                        val n = stream.read(b)
                        if (n < 0) break
                        d.update(b, 0, n)
                    }
                    d.digest().joinToString("") { "%02x".format(it) }
                } == file(job.id).sha256()
            require(same) { "storage_conflict" }
        } else
            resolver.openOutputStream(uri!!, "wt")?.use { out ->
                file(job.id).inputStream().use { it.copyTo(out) }
            } ?: error("storage")
        resolver.update(
            uri!!,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null,
        )
        val next = job.copy(localUri = uri.toString(), state = "saved", reason = "")
        db.queue().recording(next)
        return next
    }

    private suspend fun publishCustom(job: Recording): Recording {
        val resolver = context.contentResolver
        val existing =
            job.localUri
                .takeIf { it.isNotBlank() }
                ?.let {
                    androidx.documentfile.provider.DocumentFile.fromSingleUri(
                        context,
                        Uri.parse(it),
                    )
                }
        val tree =
            androidx.documentfile.provider.DocumentFile.fromTreeUri(
                context,
                Uri.parse(job.localTarget),
            ) ?: error("storage")
        val canonical = file(job.id)
        val doc =
            existing
                ?: tree.findFile(Policy.safeName(job.id))?.takeIf { candidate ->
                    candidate.length() == canonical.length() &&
                        resolver.openInputStream(candidate.uri)?.use {
                            it.readBytes().contentEquals(canonical.readBytes())
                        } == true
                }
                ?: tree.createFile("text/plain", Policy.safeName(job.id))
                ?: error("storage")
        db.queue().recording(job.copy(localUri = doc.uri.toString()))
        resolver.openOutputStream(doc.uri, "wt")?.use { out ->
            canonical.inputStream().use { it.copyTo(out) }
        } ?: error("storage")
        val result = job.copy(state = "saved", reason = "", localUri = doc.uri.toString())
        db.queue().recording(result)
        return result
    }
}

class DriveExport
internal constructor(
    private val context: Context,
    private val settings: Settings,
    private val client: OkHttpClient,
) {
    constructor(
        context: Context,
        settings: Settings,
    ) : this(
        context,
        settings,
        OkHttpClient.Builder()
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .build(),
    )

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    }

    fun authRequest() =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    suspend fun token(refreshFolder: Boolean = true): String {
        val r = Identity.getAuthorizationClient(context).authorize(authRequest()).await()
        if (r.hasResolution()) throw RemoteFailure("drive_auth")
        val token = r.accessToken ?: throw RemoteFailure("drive_auth")
        verifyAccount(token)
        if (refreshFolder && settings.driveFolder.isNotBlank()) refreshFolderName(token, settings.driveFolder)
        return token
    }

    private fun call(
        token: String,
        path: String,
        method: String = "GET",
        body: RequestBody? = null,
    ): JSONObject {
        val response =
            client
                .newCall(
                    Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/$path")
                        .header("Authorization", "Bearer $token")
                        .method(method, body)
                        .build()
                )
                .execute()
        return response.use {
            if (it.code == 401 || it.code == 403) throw RemoteFailure("drive_auth", it.code)
            if (it.code == 404) throw RemoteFailure("drive_missing", 404)
            if (!it.isSuccessful) throw RemoteFailure("drive_retry", it.code)
            val src = it.body?.source() ?: throw RemoteFailure("drive_retry")
            if (src.request(2 * 1024 * 1024 + 1L)) throw RemoteFailure("drive_retry")
            JSONObject(src.readUtf8())
        }
    }

    private fun json(value: JSONObject) =
        value.toString().toRequestBody("application/json".toMediaType())

    private fun generate(token: String) =
        call(token, "files/generateIds?count=1&space=drive&type=files")
            .getJSONArray("ids")
            .getString(0)

    fun verifyAccount(token: String) {
        val account =
            call(token, "about?fields=user(permissionId)")
                .getJSONObject("user")
                .getString("permissionId")
        if (settings.driveAccount.isNotBlank() && account != settings.driveAccount)
            throw RemoteFailure("drive_account")
        settings.driveAccount = account
    }

    private fun applyFolderName(token: String, id: String, metadata: JSONObject) {
        if (metadata.optBoolean("trashed")) throw RemoteFailure("drive_missing")
        val name = metadata.optString("name", "Ramble")
        val owned = metadata.optJSONObject("appProperties")?.optString("ramble") == "folder-v1"
        if (owned && name in setOf("Ramble Flow", "Ramble Transcripts")) {
            call(token, "files/$id", "PATCH", json(JSONObject().put("name", "Ramble")))
            settings.driveFolderName = "Ramble"
        } else settings.driveFolderName = name
    }

    private fun refreshFolderName(token: String, id: String) {
        applyFolderName(token, id, call(token, "files/$id?fields=id,name,trashed,appProperties"))
    }

    data class Folder(val id: String, val name: String)

    fun folders(token: String): List<Folder> {
        val result = mutableListOf<Folder>()
        var page = ""
        do {
            val response = call(token, "files?q=" + java.net.URLEncoder.encode(
                "trashed=false and mimeType='application/vnd.google-apps.folder'", "UTF-8") +
                "&fields=nextPageToken,files(id,name)&pageSize=100&orderBy=name" +
                if (page.isBlank()) "" else "&pageToken=" + java.net.URLEncoder.encode(page, "UTF-8"))
            val files = response.getJSONArray("files")
            for (i in 0 until files.length()) result += files.getJSONObject(i).let {
                Folder(it.getString("id"), it.getString("name"))
            }
            require(result.size <= 1000) { "drive_folder_limit" }
            page = response.optString("nextPageToken")
        } while (page.isNotBlank())
        return result
    }

    fun selectFolder(token: String, id: String) {
        require(id.matches(Regex("[a-zA-Z0-9_-]+")))
        verifyAccount(token)
        val folder = call(token, "files/$id?fields=id,name,mimeType,trashed,capabilities(canAddChildren)")
        require(!folder.optBoolean("trashed") && folder.getString("mimeType") == "application/vnd.google-apps.folder")
        require(folder.optJSONObject("capabilities")?.optBoolean("canAddChildren", true) != false)
        val allowed = settings.driveFolders + id
        settings.driveFolders = allowed
        settings.driveFolderName = folder.getString("name")
        settings.driveFolder = id
        settings.driveEnabled = true
    }

    fun createFolder(token: String, name: String): Folder {
        require(name.isNotBlank() && name.length <= 100)
        verifyAccount(token)
        val id = settings.drivePendingFolder.ifBlank {
            generate(token).also { settings.drivePendingFolder = it }
        }
        val existing = try { call(token, "files/$id?fields=id,name") }
            catch (e: RemoteFailure) { if (e.status == 404) null else throw e }
        val folder = existing ?: call(token, "files", "POST", json(JSONObject()
            .put("id", id).put("name", name.trim())
            .put("mimeType", "application/vnd.google-apps.folder")
            .put("appProperties", JSONObject().put("ramble", "folder-v1"))))
        settings.drivePendingFolder = ""
        return Folder(id, folder.optString("name", name.trim()))
    }

    fun provision(token: String): String {
        verifyAccount(token)
        if (settings.driveFolder.isNotBlank()) {
            refreshFolderName(token, settings.driveFolder)
            settings.driveEnabled = true
            return settings.driveFolder
        }
        val files =
            call(
                    token,
                    "files?q=" +
                        java.net.URLEncoder.encode(
                            "trashed=false and mimeType='application/vnd.google-apps.folder' and appProperties has { key='ramble' and value='folder-v1' }",
                            "UTF-8",
                        ) +
                        "&fields=files(id,name,appProperties)&pageSize=100",
                )
                .getJSONArray("files")
        val id =
            if (files.length() > 0) {
                val folder = files.getJSONObject(0)
                folder.getString("id").also { applyFolderName(token, it, folder) }
            } else {
                val pending =
                    settings.drivePendingFolder.ifBlank {
                        generate(token).also { settings.drivePendingFolder = it }
                    }
                val exists =
                    try {
                        call(token, "files/$pending?fields=id")
                    } catch (e: RemoteFailure) {
                        if (e.status == 404) null else throw e
                    }
                if (exists == null)
                    call(
                        token,
                        "files",
                        "POST",
                        json(
                            JSONObject()
                                .put("id", pending)
                                .put("name", "Ramble")
                                .put("mimeType", "application/vnd.google-apps.folder")
                                .put("appProperties", JSONObject().put("ramble", "folder-v1"))
                        ),
                    )
                settings.driveFolderName = "Ramble"
                pending
            }
        settings.driveFolder = id
        settings.drivePendingFolder = ""
        settings.driveEnabled = true
        return id
    }

    suspend fun upload(job: Recording, db: QueueDatabase, file: File): Recording {
        return uploadAuthorized(job, db, file, token())
    }

    internal suspend fun uploadAuthorized(
        job: Recording,
        db: QueueDatabase,
        file: File,
        token: String,
    ): Recording {
        val target = job.driveTarget
        if (target.isBlank() || target !in settings.driveFolders || !settings.driveEnabled)
            throw RemoteFailure("drive_target")
        val id =
            job.driveId.ifBlank {
                generate(token).also {
                    db.queue().recording(job.copy(driveId = it, driveState = "uploading"))
                }
            }
        val hash = file.sha256()
        val existing =
            try {
                call(token, "files/$id?fields=id,trashed,appProperties,md5Checksum")
            } catch (e: RemoteFailure) {
                if (e.status == 404) null else throw e
            }
        if (existing != null) {
            val props = existing.optJSONObject("appProperties")
            if (
                existing.optBoolean("trashed") ||
                    props?.optString("export") != job.id ||
                    props.optString("sha256") != hash ||
                    existing.optString("md5Checksum") !=
                        file.inputStream().use { stream ->
                            val d = MessageDigest.getInstance("MD5")
                            val b = ByteArray(65536)
                            while (true) {
                                val n = stream.read(b)
                                if (n < 0) break
                                d.update(b, 0, n)
                            }
                            d.digest().joinToString("") { "%02x".format(it) }
                        }
            )
                throw RemoteFailure("drive_conflict")
            return job.copy(driveId = id, driveState = "uploaded")
        }
        val metadata =
            JSONObject()
                .put("id", id)
                .put("name", Policy.safeName(job.id))
                .put("mimeType", "text/plain")
                .put("parents", org.json.JSONArray().put(target))
                .put("appProperties", JSONObject().put("export", job.id).put("sha256", hash))
        val body =
            MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(json(metadata))
                .addPart(file.readBytes().toRequestBody("text/plain; charset=utf-8".toMediaType()))
                .build()
        client
            .newCall(
                Request.Builder()
                    .url(
                        "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id"
                    )
                    .header("Authorization", "Bearer $token")
                    .post(body)
                    .build()
            )
            .execute()
            .use { if (!it.isSuccessful) throw RemoteFailure("drive_retry", it.code) }
        return job.copy(driveId = id, driveState = "uploaded")
    }
}
