package io.github.tomervancohen.rambleflow

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class AppRecordingsTest {
    private lateinit var app: RambleApp
    private val ids = mutableListOf<String>()

    @Before
    fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.database.clearAllTables();SyntheticAudioFixtures.clear(app)
        app.settings.trialRecording = ""
        app.settings.paused = false
        app.settings.wifiOnly = false
        app.settings.driveEnabled = false
        app.settings.provider = "groq"
        app.settings.saveKey("groq", "synthetic-onboarding-key-not-live-0001")
    }

    @After
    fun cleanup() = runBlocking {
        WorkManager.getInstance(app).cancelAllWork().result.get()
        for (job in app.database.queue().recordings()) if (job.source == "app") {
            if (job.localUri.isNotBlank())
                app.contentResolver.delete(android.net.Uri.parse(job.localUri), null, null)
            LocalExport(app, app.database).file(job.id).delete()
            AppRecordings(app).file(job.id).delete()
        }
        ids.forEach {
            AppRecordings(app).file(it).delete()
            AppRecordings(app).partial(it).delete()
        }
        app.transcriber = ProviderApi()
        app.settings.trialRecording = ""
        app.database.clearAllTables();SyntheticAudioFixtures.clear(app)
    }

    @Test
    fun firstRecordingUsesRealPipelineWithoutExternalFolderAndIsIdempotent() = runBlocking {
        val store = AppRecordings(app)
        val id = UUID.randomUUID().toString().also { ids += it }
        NativeM4aFixture.write(store.file(id))
        val once = store.submit(id)
        assertEquals(once, store.submit(id))
        assertEquals(id, store.recover()!!.id)
        assertTrue(app.database.queue().sources().isEmpty())
        assertTrue(once.completionProof.isNotBlank())
        var calls = 0
        app.transcriber =
            object : Transcriber {
                override fun transcribe(provider: String, key: String, file: File): String {
                    calls++
                    assertTrue(file.length() > 44)
                    assertEquals("RIFF", file.inputStream().use { String(it.readNBytes(4)) })
                    return "First recording — תמלול ראשון"
                }
            }
        TestListenableWorkerBuilder<ProcessWorker>(app).build().doWork()
        val done = app.database.queue().recording(id)!!
        assertEquals("saved", done.state)
        assertEquals(1, calls)
        assertTrue(LocalExport(app, app.database).file(id).readText().contains("תמלול ראשון"))
        app.settings.trialRecording = "" // crash between queue commit and preference commit
        assertEquals(id, store.recover()!!.id)
        TestListenableWorkerBuilder<ProcessWorker>(app).build().doWork()
        assertEquals(1, calls)
        assertEquals(1, app.database.queue().recordings().size)
    }

    @Test
    fun partialAndChangedAudioCannotBeSubmittedOrSnapshotted() = runBlocking {
        val store = AppRecordings(app)
        val id = UUID.randomUUID().toString().also { ids += it }
        store.partial(id).writeText("unfinished")
        assertTrue(runCatching { store.submit(id) }.isFailure)
        assertTrue(app.database.queue().recordings().isEmpty())
        NativeM4aFixture.write(store.file(id))
        val job = store.submit(id)
        store.file(id).appendBytes(byteArrayOf(1, 2, 3))
        assertTrue(runCatching { store.snapshot(job) }.isFailure)
    }

    @Test
    fun appSourceCannotReadArbitraryPaths() {
        val store = AppRecordings(app)
        assertTrue(runCatching { store.file("../../secrets") }.isFailure)
        assertTrue(runCatching { store.file("not-a-uuid") }.isFailure)
    }
}
