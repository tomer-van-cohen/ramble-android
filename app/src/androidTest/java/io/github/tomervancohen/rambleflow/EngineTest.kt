package io.github.tomervancohen.rambleflow

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineTest {
    private lateinit var app: RambleApp
    private val authority = "io.github.tomervancohen.rambleflow.test.documents"
    private val exportUris = mutableListOf<Uri>()

    @Before
    fun setup() =
        runBlocking<Unit> {
            app = ApplicationProvider.getApplicationContext()
            app.packageManager.setComponentEnabledSetting(
                android.content.ComponentName(app, FixtureDocuments::class.java),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP,
            )
            WorkManager.getInstance(app).cancelAllWork().result.get()
            app.database.clearAllTables();SyntheticAudioFixtures.clear(app)
            app.settings.paused = false
            app.settings.driveEnabled = false
            app.settings.wifiOnly = false
            app.settings.saveKey("groq", "synthetic-not-a-live-key-engine-0000")
            app.settings.provider = "groq"
            app.contentResolver.call(Uri.parse("content://$authority"), "fixture.reset", null, null)
        }

    @After
    fun close() {
        app.packageManager.setComponentEnabledSetting(
            android.content.ComponentName(app, FixtureDocuments::class.java),
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP,
        )
        app.transcriber = ProviderApi()
        exportUris.forEach { app.contentResolver.delete(it, null, null) }
        app.database.clearAllTables();SyntheticAudioFixtures.clear(app)
    }

    @Test
    fun finalizedRecordingSchedulesFollowupWithoutReopeningActivity() =
        runBlocking<Unit> {
            val reader = SourceReader(app, app.database, app.settings)
            val tree = DocumentsContract.buildTreeDocumentUri(authority, "recordings")
            assertTrue(reader.activate(tree, "voice"))
            val source = app.database.queue().sources().single()
            app.database.queue().source(source.copy(cutoff = System.currentTimeMillis() - 60_000))
            val file = File(app.cacheDir, "followup.m4a")
            NativeM4aFixture.write(file) {}
            val audio = file.readBytes()
            file.delete()
            app.contentResolver.call(
                Uri.parse("content://$authority"),
                "fixture.add",
                null,
                Bundle().apply {
                    putString("id", "followup")
                    putString("name", "New recording.m4a")
                    putLong("size", audio.size.toLong())
                    putLong("modified", 1)
                    putByteArray("audio", audio)
                },
            )
            val first = TestListenableWorkerBuilder<ScanWorker>(app).build().doWork()
            assertEquals(androidx.work.ListenableWorker.Result.retry(), first)
            assertEquals("file_stabilizing", app.database.queue().recordings().single().reason)
            WorkManager.getInstance(app).cancelAllWork().result.get()
            // The retry is bounded: a still-changing file must not cause an endless minute loop.
            val bounded =
                TestListenableWorkerBuilder<ScanWorker>(app).setRunAttemptCount(2).build().doWork()
            assertEquals(androidx.work.ListenableWorker.Result.success(), bounded)
            WorkManager.getInstance(app).cancelAllWork().result.get()
            app.settings.paused = true
            assertEquals(
                androidx.work.ListenableWorker.Result.success(),
                TestListenableWorkerBuilder<ScanWorker>(app).build().doWork(),
            )
        }

    private suspend fun candidate(): Recording {
        val reader = SourceReader(app, app.database, app.settings)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "recordings")
        assertTrue(reader.activate(tree, "voice"))
        val audio = wav(16000)
        app.contentResolver.call(
            Uri.parse("content://$authority"),
            "fixture.add",
            null,
            Bundle().apply {
                putString("id", "synthetic")
                putString("name", "synthetic.wav")
                putLong("size", audio.size.toLong())
                putLong("modified", 1)
                putByteArray("audio", audio)
            },
        )
        reader.scan()
        val o = app.database.queue().observation("voice", "synthetic")!!
        app.database.queue().observation(o.copy(stableSince = System.currentTimeMillis() - 61_000))
        reader.scan()
        val job = app.database.queue().recordings().single()
        assertEquals("waiting", job.state)
        app.database.queue().recording(job.copy(state = "queued"))
        return job
    }

    private suspend fun automaticCandidate(): Recording {
        val reader = SourceReader(app, app.database, app.settings)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "recordings")
        assertTrue(reader.activate(tree, "voice"))
        val source = app.database.queue().sources().single()
        app.database.queue().source(source.copy(cutoff = System.currentTimeMillis() - 10000))
        val file = File(app.cacheDir, "native-finalization.m4a")
        NativeM4aFixture.write(file) { open ->
            repeat(3) { assertNull(open.inputStream().use { FinalizedMp4.inspect(it.channel) }) }
        }
        val proof = file.inputStream().use { FinalizedMp4.inspect(it.channel) }
        assertNotNull(proof)
        val audio = file.readBytes()
        file.delete()
        app.contentResolver.call(
            Uri.parse("content://$authority"),
            "fixture.add",
            null,
            Bundle().apply {
                putString("id", "native-auto")
                putString("name", "New recording.m4a")
                putLong("size", audio.size.toLong() - 8)
                putLong("modified", 1)
                putByteArray("audio", audio)
            },
        )
        reader.scan()
        val o = app.database.queue().observation("voice", "native-auto")!!
        assertTrue(o.proof.isNotBlank())
        app.database
            .queue()
            .observation(
                o.copy(
                    stableSince = System.currentTimeMillis() - 61000,
                    proofAt = System.currentTimeMillis() - 61000,
                )
            )
        reader.scan()
        val job = app.database.queue().recordings().single()
        assertEquals("queued", job.state)
        assertTrue(job.completionProof.isNotBlank())
        return job
    }

    @Test
    fun actualWorkerExportsAndRestartDoesNotRepeatKnownRequest() =
        runBlocking<Unit> {
            val job = automaticCandidate()
            var calls = 0
            app.transcriber =
                object : Transcriber {
                    override fun transcribe(provider: String, key: String, file: File): String {
                        calls++
                        assertTrue(file.length() > 44)
                        return "שלום synthetic transcript"
                    }
                }
            TestListenableWorkerBuilder<ProcessWorker>(app).build().doWork()
            val done = app.database.queue().recording(job.id)!!
            assertEquals("saved", done.state)
            assertEquals(1, calls)
            exportUris += Uri.parse(done.localUri)
            assertTrue(
                LocalExport(app, app.database)
                    .file(job.id)
                    .readText()
                    .contains("שלום synthetic transcript")
            )
            TestListenableWorkerBuilder<ProcessWorker>(app).build().doWork()
            assertEquals(1, calls)
            LocalExport(app, app.database).file(job.id).delete()
        }

    @Test
    fun uncertainRequestBlocksAutomaticRepeat() =
        runBlocking<Unit> {
            val job = candidate()
            var calls = 0
            app.transcriber =
                object : Transcriber {
                    override fun transcribe(provider: String, key: String, file: File): String {
                        calls++
                        throw IOException("synthetic lost response")
                    }
                }
            TestListenableWorkerBuilder<ProcessWorker>(app).build().doWork()
            assertEquals("uncertain", app.database.queue().recording(job.id)!!.state)
            TestListenableWorkerBuilder<ProcessWorker>(app).build().doWork()
            assertEquals(1, calls)
        }

    private fun wav(samples: Int): ByteArray {
        val b = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray())
            .putInt(samples * 2 + 36)
            .put("WAVEfmt ".toByteArray())
            .putInt(16)
            .putShort(1)
            .putShort(1)
            .putInt(16000)
            .putInt(32000)
            .putShort(2)
            .putShort(16)
            .put("data".toByteArray())
            .putInt(samples * 2)
        return b.array()
    }
    @Test fun storageBlockedJobResumesOnceSpaceIsAvailable() = runBlocking {
        val job=candidate()
        app.database.queue().recording(job.copy(state="storage",reason="storage",nextAttempt=0))
        app.transcriber=object:Transcriber { override fun transcribe(provider:String,key:String,file:File)="Recovered after freeing space." }
        TestListenableWorkerBuilder<ProcessWorker>(app).build().doWork()
        assertEquals("saved",app.database.queue().recording(job.id)!!.state)
    }
    @Test fun definiteRejectionNeverBecomesUncertainOrAutomaticallyReplays() = runBlocking {
        val job=candidate();var calls=0
        app.transcriber=object:Transcriber { override fun transcribe(provider:String,key:String,file:File):String { calls++;throw RemoteFailure("rejected",400) } }
        TestListenableWorkerBuilder<ProcessWorker>(app).build().doWork()
        val rejected=app.database.queue().recording(job.id)!!
        assertEquals("error",rejected.state);assertEquals("provider_400",rejected.reason)
        assertEquals("ready",app.database.queue().chunks(job.id).single().state)
        assertEquals(0,app.database.queue().chunks(job.id).single().attempts)
        TestListenableWorkerBuilder<ProcessWorker>(app).build().doWork()
        assertEquals(1,calls)
    }
    @Test fun networkFailureBeforeBodyRetriesWithoutChargeConfirmation() = runBlocking {
        val job=candidate()
        app.transcriber=object:Transcriber { override fun transcribe(provider:String,key:String,file:File):String { throw RemoteFailure("network",retryAfter=0) } }
        TestListenableWorkerBuilder<ProcessWorker>(app).build().doWork()
        assertEquals("retry",app.database.queue().recording(job.id)!!.state)
        assertEquals("ready",app.database.queue().chunks(job.id).single().state)
        assertEquals(0,app.database.queue().chunks(job.id).single().attempts)
        app.transcriber=object:Transcriber { override fun transcribe(provider:String,key:String,file:File)="Connected again." }
        TestListenableWorkerBuilder<ProcessWorker>(app).build().doWork()
        assertEquals("saved",app.database.queue().recording(job.id)!!.state)
    }

}
