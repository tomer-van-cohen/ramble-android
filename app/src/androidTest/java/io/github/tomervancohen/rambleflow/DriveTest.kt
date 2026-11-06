package io.github.tomervancohen.rambleflow

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveTest {
    private lateinit var context: Context
    private lateinit var settings: Settings
    private lateinit var db: QueueDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ramble", Context.MODE_PRIVATE).edit().clear().commit()
        settings = Settings(context)
        db = Room.inMemoryDatabaseBuilder(context, QueueDatabase::class.java).build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun api(respond: (Request) -> Pair<Int, String>): DriveExport {
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    assertEquals("Bearer synthetic-token", request.header("Authorization"))
                    assertEquals("https", request.url.scheme)
                    assertEquals("www.googleapis.com", request.url.host)
                    val (status, text) = respond(request)
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(status)
                        .message("Synthetic test response")
                        .body(text.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
        return DriveExport(context, settings, client)
    }

    @Test
    fun reconnectRestoresEnabledFlagWithoutCreatingAnotherFolder() {
        settings.driveFolder = "existing-folder"
        settings.driveAccount = "test-account"
        settings.driveEnabled = false
        var requests = 0
        val export = api { r ->
            requests++
            assertEquals("GET", r.method)
            when (r.url.encodedPath) {
                "/drive/v3/about" -> 200 to """{"user":{"permissionId":"test-account"}}"""
                "/drive/v3/files/existing-folder" ->
                    200 to """{"id":"existing-folder","trashed":false}"""
                else -> error("Unexpected API request")
            }
        }
        assertEquals("existing-folder", export.provision("synthetic-token"))
        assertTrue(settings.driveEnabled)
        assertEquals(2, requests)
    }

    @Test
    fun legacyAppFolderIsRenamedWithoutChangingItsId() {
        settings.driveFolder = "legacy-folder"
        var renamed = false
        val export = api { r ->
            when (r.url.encodedPath) {
                "/drive/v3/about" -> 200 to """{"user":{"permissionId":"test-account"}}"""
                "/drive/v3/files/legacy-folder" -> if (r.method == "PATCH") {
                    val body = Buffer().also { r.body!!.writeTo(it) }.readUtf8()
                    assertEquals("Ramble", org.json.JSONObject(body).getString("name"))
                    renamed = true
                    200 to """{"id":"legacy-folder"}"""
                } else 200 to """{"id":"legacy-folder","name":"Ramble Flow","appProperties":{"ramble":"folder-v1"}}"""
                else -> error("Unexpected request")
            }
        }
        assertEquals("legacy-folder", export.provision("synthetic-token"))
        assertTrue(renamed)
        assertEquals("Ramble", settings.driveFolderName)
    }

    @Test
    fun newFolderUsesRambleName() {
        val export = api { r ->
            when (r.url.encodedPath) {
                "/drive/v3/about" -> 200 to """{"user":{"permissionId":"test-account"}}"""
                "/drive/v3/files/generateIds" -> 200 to """{"ids":["new-folder"]}"""
                "/drive/v3/files/new-folder" -> 404 to "{}"
                "/drive/v3/files" ->
                    if (r.method == "GET") 200 to """{"files":[]}"""
                    else {
                        val body = Buffer().also { r.body!!.writeTo(it) }.readUtf8()
                        assertEquals(
                            "Ramble",
                            org.json.JSONObject(body).getString("name"),
                        )
                        200 to """{"id":"new-folder"}"""
                    }
                else -> error("Unexpected request")
            }
        }
        assertEquals("new-folder", export.provision("synthetic-token"))
        assertEquals("Ramble", settings.driveFolderName)
    }

    @Test
    fun failedFolderLookupDoesNotBecomeFolderCreation() {
        settings.drivePendingFolder = "pending-folder"
        val export = api { r ->
            assertEquals("GET", r.method)
            when (r.url.encodedPath) {
                "/drive/v3/about" -> 200 to """{"user":{"permissionId":"test-account"}}"""
                "/drive/v3/files" -> 200 to """{"files":[]}"""
                "/drive/v3/files/pending-folder" -> 401 to "{}"
                else -> error("Unexpected API request")
            }
        }
        val failure = runCatching { export.provision("synthetic-token") }.exceptionOrNull()
        assertTrue(failure is RemoteFailure)
        assertEquals("drive_auth", (failure as RemoteFailure).kind)
        assertFalse(settings.driveEnabled)
        assertEquals("pending-folder", settings.drivePendingFolder)
    }

    @Test
    fun accountMismatchStopsBeforeUsingExistingFolder() {
        settings.driveAccount = "original-account"
        val export = api { r ->
            assertEquals("/drive/v3/about", r.url.encodedPath)
            200 to """{"user":{"permissionId":"different-account"}}"""
        }
        val failure = runCatching { export.provision("synthetic-token") }.exceptionOrNull()
        assertEquals("drive_account", (failure as RemoteFailure).kind)
        assertEquals("original-account", settings.driveAccount)
    }

    @Test
    fun realMultipartExportAndKnownIdRetryCreateOnlyOneFile() =
        runBlocking<Unit> {
            settings.driveFolder = "test-folder"
            settings.driveEnabled = true
            val file = File(context.cacheDir, "drive-test-${java.util.UUID.randomUUID()}")
            try {
                file.writeText("Title: Synthetic Drive test\n\nשלום עולם — integration test")
                val hash = file.sha256()
                val md5 =
                    MessageDigest.getInstance("MD5").digest(file.readBytes()).joinToString("") {
                        "%02x".format(it)
                    }
                val job =
                    Recording(
                        "drive-${java.util.UUID.randomUUID()}",
                        "voice",
                        "test-document",
                        "Synthetic",
                        1000,
                        driveTarget = "test-folder",
                    )
                db.queue().recording(job)
                var exists = false
                var uploads = 0
                val export = api { r ->
                    when (r.url.encodedPath) {
                        "/drive/v3/files/generateIds" -> 200 to """{"ids":["stable-file-id"]}"""
                        "/drive/v3/files/stable-file-id" ->
                            if (!exists) 404 to "{}"
                            else
                                200 to
                                    """{"id":"stable-file-id","trashed":false,"appProperties":{"export":"${job.id}","sha256":"$hash"},"md5Checksum":"$md5"}"""
                        "/upload/drive/v3/files" -> {
                            assertEquals("POST", r.method)
                            assertEquals("multipart", r.url.queryParameter("uploadType"))
                            val body = Buffer().also { r.body!!.writeTo(it) }.readUtf8()
                            assertTrue(body.contains("שלום עולם"))
                            assertTrue(body.contains("test-folder"))
                            assertTrue(body.contains("stable-file-id"))
                            assertFalse(body.contains("audio/"))
                            exists = true
                            uploads++
                            200 to """{"id":"stable-file-id"}"""
                        }
                        else -> error("Unexpected API request")
                    }
                }
                val once = export.uploadAuthorized(job, db, file, "synthetic-token")
                // Simulate a successful remote response followed by process loss before marking
                // uploaded.
                settings.driveFolders = settings.driveFolders + "new-folder"
                settings.driveFolder = "new-folder"
                val checkpoint = db.queue().recording(job.id)!!
                assertEquals("stable-file-id", checkpoint.driveId)
                val twice = export.uploadAuthorized(checkpoint, db, file, "synthetic-token")
                assertEquals("uploaded", once.driveState)
                assertEquals(once.driveId, twice.driveId)
                assertEquals(1, uploads)
            } finally {
                file.delete()
            }
        }
    @Test
    fun paginatedFolderChoicesAndSelectionKeepPreviousTargetAuthorized() {
        settings.driveFolder = "old-folder"
        settings.driveAccount = "test-account"
        val export = api { r ->
            when (r.url.encodedPath) {
                "/drive/v3/about" -> 200 to """{"user":{"permissionId":"test-account"}}"""
                "/drive/v3/files" -> if (r.url.queryParameter("pageToken") == null)
                    200 to """{"files":[{"id":"old-folder","name":"Ramble"}],"nextPageToken":"next"}"""
                    else 200 to """{"files":[{"id":"new-folder","name":"Meeting notes"}]}"""
                "/drive/v3/files/new-folder" -> 200 to """{"id":"new-folder","name":"Meeting notes","mimeType":"application/vnd.google-apps.folder","capabilities":{"canAddChildren":true}}"""
                else -> error("Unexpected request")
            }
        }
        assertEquals(listOf("old-folder", "new-folder"), export.folders("synthetic-token").map { it.id })
        export.selectFolder("synthetic-token", "new-folder")
        assertEquals("new-folder", settings.driveFolder)
        assertEquals("Meeting notes", settings.driveFolderName)
        assertTrue(settings.driveFolders.containsAll(listOf("old-folder", "new-folder")))
    }

    @Test
    fun nonWritableFolderDoesNotReplaceCurrentTarget() {
        settings.driveFolder = "old-folder"
        val export = api { r ->
            if (r.url.encodedPath == "/drive/v3/about") 200 to """{"user":{"permissionId":"test-account"}}"""
            else 200 to """{"name":"Read only","mimeType":"application/vnd.google-apps.folder","capabilities":{"canAddChildren":false}}"""
        }
        try { export.selectFolder("synthetic-token", "read-only"); fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals("old-folder", settings.driveFolder)
    }

    @Test
    fun createFolderRecoversKnownIdAfterLostResponse() {
        var created = false
        var posts = 0
        val export = api { r ->
            when (r.url.encodedPath) {
                "/drive/v3/about" -> 200 to """{"user":{"permissionId":"test-account"}}"""
                "/drive/v3/files/generateIds" -> 200 to """{"ids":["stable-folder"]}"""
                "/drive/v3/files/stable-folder" -> if (created)
                    200 to """{"id":"stable-folder","name":"My notes"}""" else 404 to "{}"
                "/drive/v3/files" -> { created = true; posts++; throw java.io.IOException("Synthetic lost response") }
                else -> error("Unexpected request")
            }
        }
        try { export.createFolder("synthetic-token", "My notes"); fail() } catch (_: java.io.IOException) { }
        assertEquals("stable-folder", settings.drivePendingFolder)
        assertEquals("stable-folder", export.createFolder("synthetic-token", "My notes").id)
        assertEquals(1, posts)
    }

}
