package io.github.tomervancohen.rambleflow

import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class SharedAudioTest {
    private val app get() = ApplicationProvider.getApplicationContext<RambleApp>()
    private val authority = "io.github.tomervancohen.rambleflow.test.documents"
    private fun uri() = DocumentsContract.buildDocumentUri(authority, "shared-test")
    private fun fixture(bytes: ByteArray) {
        app.contentResolver.call(Uri.parse("content://$authority"), "fixture.add", null,
            Bundle().apply {
                putString("id", "shared-test"); putString("name", "call.m4a")
                putLong("size", bytes.size.toLong()); putByteArray("audio", bytes)
            })
    }
    @Before fun setup() = runBlocking {
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.database.clearAllTables();SyntheticAudioFixtures.clear(app)
        app.settings.paused = false
        app.settings.wifiOnly = false
        app.settings.driveEnabled = false
        app.settings.provider = "groq"
        app.settings.outputTree = ""
        app.settings.saveKey("groq", "synthetic-shared-test-key-not-live-001")
        app.packageManager.setComponentEnabledSetting(ComponentName(app, FixtureDocuments::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }
    @After fun cleanup() = runBlocking {
        WorkManager.getInstance(app).cancelAllWork().result.get()
        for (job in app.database.queue().recordings()) {
            if (job.localUri.isNotBlank()) app.contentResolver.delete(Uri.parse(job.localUri), null, null)
            LocalExport(app, app.database).file(job.id).delete()
        }
        File(app.noBackupFilesDir, "shared-audio").deleteRecursively()
        File(app.filesDir, "shared-test").delete()
        app.database.clearAllTables();SyntheticAudioFixtures.clear(app)
        app.transcriber = ProviderApi()
        app.packageManager.setComponentEnabledSetting(ComponentName(app, FixtureDocuments::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
    @Test fun sharedAudioSurvivesSenderLossAndUsesPipelineWithoutFolderPermission() = runBlocking {
        val input = File(app.cacheDir, "shared-fixture.m4a")
        NativeM4aFixture.write(input)
        fixture(input.readBytes()); input.delete()
        val store = SharedAudio(app)
        val staged = store.stage(uri())
        assertTrue(app.database.queue().recordings().isEmpty()) // Staging never sends audio.
        val job = store.submit(staged)
        assertEquals(job.id, store.submit(staged).id)
        assertTrue(app.database.queue().sources().isEmpty())
        File(app.filesDir, "shared-test").delete() // Temporary sender access is no longer needed.
        var calls = 0
        app.transcriber = object : Transcriber {
            override fun transcribe(provider: String, key: String, file: File): String {
                calls++
                return "Shared audio transcript"
            }
        }
        TestListenableWorkerBuilder<ProcessWorker>(app).build().doWork()
        assertEquals("saved", app.database.queue().recording(job.id)!!.state)
        assertEquals(1, calls)
        TestListenableWorkerBuilder<ProcessWorker>(app).build().doWork()
        assertEquals(1, calls)
    }
    @Test fun invalidAndChangedContentCannotBeTranscribed() = runBlocking {
        val store = SharedAudio(app)
        assertTrue(runCatching { store.stage(Uri.parse("content://${app.packageName}.files/transcripts/private.m4a")) }.isFailure)
        assertTrue(runCatching { store.stage(Uri.parse("content://0@${app.packageName}.files/transcripts/private.m4a")) }.isFailure)
        fixture("not audio".toByteArray())
        assertTrue(runCatching { store.stage(uri()) }.isFailure)
        assertTrue(runCatching { store.stage(Uri.parse("file:///data/private")) }.isFailure)
        val input = File(app.cacheDir, "shared-fixture.m4a")
        NativeM4aFixture.write(input); fixture(input.readBytes()); input.delete()
        val staged = store.stage(uri())
        val job = store.submit(staged)
        store.file(job.id).appendText("changed")
        assertTrue(runCatching { store.snapshot(job) }.isFailure)
    }
    @Test fun explicitShareCanReintroducePreviouslyDeletedAudio() = runBlocking {
        val input=File(app.cacheDir,"shared-reimport.m4a")
        try {
            NativeM4aFixture.write(input);fixture(input.readBytes())
            val store=SharedAudio(app)
            val first=store.submit(store.stage(uri()))
            assertTrue(AudioRetention(app).remove(first))
            assertFalse(store.file(first.id).exists())
            val second=store.submit(store.stage(uri()))
            assertEquals(first.id,second.id)
            assertEquals("queued",second.state)
            assertTrue(store.file(second.id).exists())
        } finally { input.delete() }
    }

}
