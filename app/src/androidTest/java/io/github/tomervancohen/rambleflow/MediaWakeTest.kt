package io.github.tomervancohen.rambleflow

import android.app.job.JobScheduler
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaWakeTest {
    @Test
    fun systemMediaEventsScanAndTranscribeWithoutActivity() =
        runBlocking<Unit> {
            val app = ApplicationProvider.getApplicationContext<RambleApp>()
            val wm = WorkManager.getInstance(app)
            val scheduler = app.getSystemService(JobScheduler::class.java)
            val authority = "io.github.tomervancohen.rambleflow.test.documents"
            val fixture = Uri.parse("content://$authority")
            val uris = mutableListOf<Uri>()
            val output = mutableListOf<Uri>()
            val component = ComponentName(app, FixtureDocuments::class.java)
            wm.cancelAllWork().result.get()
            scheduler.cancel(MediaWake.JOB_ID)
            app.database.clearAllTables();SyntheticAudioFixtures.clear(app)
            app.settings.paused = false
            app.settings.wifiOnly = false
            app.settings.driveEnabled = false
            app.settings.outputTree = ""
            app.settings.provider = "groq"
            app.settings.saveKey("groq", "synthetic-mediawake-key-not-live")
            var calls = 0
            app.transcriber =
                object : Transcriber {
                    override fun transcribe(provider: String, key: String, file: File): String {
                        calls++
                        return "Synthetic background media wake transcript"
                    }
                }
            try {
                app.packageManager.setComponentEnabledSetting(component, 1, 1)
                app.contentResolver.call(fixture, "fixture.reset", null, null)
                val reader = SourceReader(app, app.database, app.settings)
                assertTrue(
                    reader.activate(
                        DocumentsContract.buildTreeDocumentUri(authority, "recordings"),
                        "voice",
                    )
                )
                val source = app.database.queue().sources().single()
                app.database
                    .queue()
                    .source(source.copy(cutoff = System.currentTimeMillis() - 60_000))
                val f = File(app.cacheDir, "mediawake-test.m4a")
                NativeM4aFixture.write(f)
                val audio = f.readBytes()
                f.delete()
                app.contentResolver.call(
                    fixture,
                    "fixture.add",
                    null,
                    Bundle().apply {
                        putString("id", "media-wake-audio")
                        putString("name", "Background recording.m4a")
                        putLong("size", audio.size.toLong())
                        putLong("modified", 1)
                        putByteArray("audio", audio)
                    },
                )
                // No Activity is launched, no manual refresh or forced JobScheduler run.
                InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .executeShellCommand("input keyevent KEYCODE_HOME")
                    .close()
                app.getSharedPreferences("media_wake", 0).edit().clear().commit()
                MediaWake.arm(app)
                assertNotNull(scheduler.getPendingJob(MediaWake.JOB_ID))
                fun publish() {
                    val uri =
                        app.contentResolver.insert(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            ContentValues().apply {
                                put(
                                    MediaStore.MediaColumns.DISPLAY_NAME,
                                    "ramble-wake-${System.nanoTime()}.m4a",
                                )
                                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/RambleTest")
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                            },
                        )!!
                    uris.add(uri)
                    app.contentResolver.openOutputStream(uri)!!.use { it.write(audio) }
                    app.contentResolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null,
                    )
                }
                publish()
                withTimeout(90_000) {
                    while (
                        app.database.queue().recordings().none { it.reason == "file_stabilizing" }
                    ) delay(500)
                }
                val firstEvent = app.getSharedPreferences("media_wake", 0).getLong("lastEvent", 0)
                assertTrue(firstEvent > 0)
                assertNotNull(scheduler.getPendingJob(MediaWake.JOB_ID))
                // Remove all WorkManager work to isolate a SECOND actual MediaStore callback.
                wm.cancelAllWork().result.get()
                val observation = app.database.queue().observation("voice", "media-wake-audio")!!
                app.database
                    .queue()
                    .observation(observation.copy(proofAt = System.currentTimeMillis() - 61_000))
                delay(1500)
                publish()
                withTimeout(90_000) {
                    while (app.database.queue().recordings().none { it.state == "saved" }) delay(
                        500
                    )
                }
                assertTrue(
                    app.getSharedPreferences("media_wake", 0).getLong("lastEvent", 0) > firstEvent
                )
                assertEquals(1, calls)
                val job = app.database.queue().recordings().single()
                output.add(Uri.parse(job.localUri))
                assertTrue(
                    LocalExport(app, app.database)
                        .file(job.id)
                        .readText()
                        .contains("Synthetic background")
                )
                app.settings.paused = true
                MediaWake.arm(app)
                assertNull(scheduler.getPendingJob(MediaWake.JOB_ID))
            } finally {
                wm.cancelAllWork().result.get()
                scheduler.cancel(MediaWake.JOB_ID)
                uris.forEach { app.contentResolver.delete(it, null, null) }
                output.forEach { app.contentResolver.delete(it, null, null) }
                app.transcriber = ProviderApi()
                app.settings.paused = true
                app.database.clearAllTables();SyntheticAudioFixtures.clear(app)
                app.packageManager.setComponentEnabledSetting(component, 2, 1)
            }
        }
}
