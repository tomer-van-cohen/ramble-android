package io.github.tomervancohen.rambleflow

import android.content.Context
import android.media.MediaExtractor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PipelineTest {
    private lateinit var context: Context
    private lateinit var db: QueueDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, QueueDatabase::class.java).build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun keyCiphertextAndIndependentProfiles() {
        val s = Settings(context)
        s.saveKey("groq", "synthetic-not-a-live-key-00000001")
        val p = s.profile("groq")
        s.saveKey("groq", "synthetic-not-a-live-key-00000002")
        assertEquals("synthetic-not-a-live-key-00000001", s.readKey(p))
        assertEquals("synthetic-not-a-live-key-00000002", s.readKey(s.profile("groq")))
        assertFalse(
            context.getSharedPreferences("ramble", Context.MODE_PRIVATE).all.values.any {
                it.toString().contains("synthetic-not-a-live-key")
            }
        )
    }

    @Test
    fun transcriptPublishingIsIdempotentAndIndependentOfDrive() =
        runBlocking<Unit> {
            val id = "test-${java.util.UUID.randomUUID()}"
            val job = Recording(id, "voice", "fake", "בדיקה בעברית", 1000, provider = "groq")
            db.queue().recording(job)
            db.queue().chunk(Chunk(id, 0, "", 0, 16000, state = "done", text = "שלום world"))
            val export = LocalExport(context, db)
            val once = export.publish(export.assemble(job))
            val twice = export.publish(once)
            assertEquals(once.localUri, twice.localUri)
            context.contentResolver.query(android.net.Uri.parse(once.localUri),
                arrayOf(android.provider.MediaStore.Downloads.RELATIVE_PATH), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals("Download/Ramble/", it.getString(0))
            }
            assertTrue(export.file(id).readText().contains("שלום world"))
            assertEquals("", twice.driveId)
            context.contentResolver.delete(android.net.Uri.parse(twice.localUri), null, null)
            export.file(id).delete()
        }

    @Test
    fun renamedFolderRecoversPendingLegacyExportWithoutDuplicatingIt() = runBlocking<Unit> {
        val id = "test-${java.util.UUID.randomUUID()}"
        val job = Recording(id, "voice", "fake", "Legacy recovery", 1000, provider = "groq")
        db.queue().recording(job)
        db.queue().chunk(Chunk(id, 0, "", 0, 16000, state = "done", text = "Recovered"))
        val export = LocalExport(context, db)
        val assembled = export.assemble(job)
        val legacy = context.contentResolver.insert(
            android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY),
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, Policy.safeName(id))
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Ramble Flow/")
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            })!!
        try {
            assertEquals(legacy.toString(), export.publish(assembled).localUri)
        } finally {
            context.contentResolver.delete(legacy, null, null)
            export.file(id).delete()
        }
    }

    @Test
    fun decodeAndSplitActualSyntheticWav() =
        runBlocking<Unit> {
            val dir =
                File(context.cacheDir, "audio-test-${java.util.UUID.randomUUID()}").apply {
                    mkdirs()
                }
            try {
                val input = File(dir, "input.wav")
                writeWav(input, 16000 * 301)
                val parts = AudioPlanner().plan(input, File(dir, "chunks"))
                assertEquals(2, parts.size)
                assertEquals(0L, parts[0].start)
                assertEquals(parts[0].samples.toLong(), parts[1].start)
                assertEquals(16000L * 301, parts.sumOf { it.samples.toLong() })
                assertTrue(parts.all { it.file.length() < Policy.MAX_UPLOAD })
                for (p in parts) {
                    val e = MediaExtractor()
                    e.setDataSource(p.file.path)
                    assertEquals(1, e.trackCount)
                    e.release()
                }
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun narrowbandAudioPreservesSignalWhenUpsampled() =
        runBlocking<Unit> {
            val dir =
                File(context.cacheDir, "narrowband-${java.util.UUID.randomUUID()}").apply {
                    mkdirs()
                }
            try {
                val input = File(dir, "8k.wav")
                writeWav(input, 8000 * 5, 8000, 1000)
                val parts = AudioPlanner().plan(input, File(dir, "chunks"))
                assertEquals(16000L * 5, parts.sumOf { it.samples.toLong() })
                val bytes = parts.single().file.readBytes()
                val pcm = ByteBuffer.wrap(bytes, 44, 32).order(ByteOrder.LITTLE_ENDIAN)
                while (pcm.hasRemaining()) assertEquals(1000, pcm.short.toInt())
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun schemaOneUpgradesWithoutResettingBaseline() =
        runBlocking<Unit> {
            val name = "migration-${java.util.UUID.randomUUID()}.db"
            val test =
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
            val schema =
                org.json
                    .JSONObject(
                        test.assets
                            .open("io.github.tomervancohen.rambleflow.QueueDatabase/1.json")
                            .bufferedReader()
                            .use { it.readText() }
                    )
                    .getJSONObject("database")
            val path = context.getDatabasePath(name)
            path.parentFile!!.mkdirs()
            val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null)
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                raw.execSQL(e.getString("createSql").replace("$" + "{TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) raw.execSQL(
                    indices
                        .getJSONObject(j)
                        .getString("createSql")
                        .replace("$" + "{TABLE_NAME}", table)
                )
            }
            raw.execSQL(
                "INSERT INTO Source(kind,tree,document,cutoff,baselineComplete,enabled) VALUES('voice','tree','doc',12345,1,1)"
            )
            val recordingSchema=(0 until entities.length()).map { entities.getJSONObject(it) }.single { it.getString("tableName")=="Recording" }
            val fields=recordingSchema.getJSONArray("fields")
            val retained=android.content.ContentValues()
            for(i in 0 until fields.length()) {
                val field=fields.getJSONObject(i);val key=field.getString("columnName")
                if(!field.optBoolean("notNull")) retained.putNull(key)
                else if(field.getString("affinity")=="INTEGER")retained.put(key,0L) else retained.put(key,"")
            }
            retained.put("id","retained-job");retained.put("source","voice");retained.put("document","retained-doc")
            retained.put("title","Retained recording");retained.put("state","key");retained.put("provider","groq@retained-profile")
            raw.insertOrThrow("Recording",null,retained)
            raw.version = 1
            raw.close()
            val upgraded =
                Room.databaseBuilder(context, QueueDatabase::class.java, name)
                    .addMigrations(QueueMigrations.FROM_1_TO_2,QueueMigrations.FROM_2_TO_3)
                    .build()
            try {
                assertEquals(12345L, upgraded.queue().sources().single().cutoff)
                assertTrue(upgraded.queue().sources().single().baselineComplete)
                val retainedJob=upgraded.queue().recording("retained-job")!!
                assertEquals("key",retainedJob.state);assertEquals("groq@retained-profile",retainedJob.provider)
                assertTrue(retainedJob.planComplete)
            } finally {
                upgraded.close()
                context.deleteDatabase(name)
            }
        }

    @Test fun decodingResumesFromDurableChunkWithoutRewritingPrefix() = runBlocking {
        val directory=File(context.cacheDir,"resume-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        try {
            val input=File(directory,"input.wav");writeWav(input,16000*301,16000,1234)
            val full=AudioPlanner().plan(input,File(directory,"full"))
            val partial=File(directory,"partial")
            var proceed=true
            val durable=mutableListOf<AudioPlanner.Part>()
            assertFalse(AudioPlanner().resume(input,partial,0,{proceed}) { _,part -> durable+=part;proceed=false })
            assertEquals(1,durable.size)
            val prefix=durable.single().file.sha256()
            assertTrue(AudioPlanner().resume(input,partial,1,{true}) { ordinal,part -> assertEquals(1,ordinal);durable+=part })
            assertEquals(prefix,durable.first().file.sha256())
            assertEquals(full.map { it.file.sha256() },durable.map { it.file.sha256() })
            assertEquals(16000L*301,durable.sumOf { it.samples.toLong() })
        } finally { directory.deleteRecursively() }
    }

    @Test fun compressedAacResumesAfterFiveMinuteBoundary() = runBlocking {
        val directory=File(context.cacheDir,"aac-resume-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        try {
            val input=File(directory,"input.m4a");NativeM4aFixture.write(input,samples=16000*301)
            val output=File(directory,"parts")
            val parts=mutableListOf<AudioPlanner.Part>()
            var proceed=true
            assertFalse(AudioPlanner().resume(input,output,0,{proceed}) { _,part -> parts+=part;proceed=false })
            val prefixHash=parts.single().file.sha256()
            assertTrue(AudioPlanner().resume(input,output,1,{true}) { _,part -> parts+=part })
            assertEquals(2,parts.size)
            assertEquals(prefixHash,parts.first().file.sha256())
            assertEquals(Policy.CHUNK_SAMPLES.toLong(),parts.last().start)
            assertTrue(parts.last().samples in 1..32000)
            val restored=AudioPlanner().restore(input,File(directory,"repair"),1) { true }!!
            assertEquals(parts.last().file.sha256(),restored.file.sha256())
        } finally { directory.deleteRecursively() }
    }

    private fun writeWav(file: File, samples: Int, rate: Int = 16000, level: Short = 0) {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray())
            .putInt(samples * 2 + 36)
            .put("WAVEfmt ".toByteArray())
            .putInt(16)
            .putShort(1)
            .putShort(1)
            .putInt(rate)
            .putInt(rate * 2)
            .putShort(2)
            .putShort(16)
            .put("data".toByteArray())
            .putInt(samples * 2)
        file.outputStream().use { out ->
            out.write(h.array())
            val data =
                ByteBuffer.allocate(rate * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .apply { repeat(rate) { putShort(level) } }
                    .array()
            repeat(samples / rate) { out.write(data) }
            out.write(ByteArray(samples % rate * 2))
        }
    }
}
