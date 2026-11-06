package io.github.tomervancohen.rambleflow

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceTest {
    private lateinit var context: Context
    private lateinit var db: QueueDatabase
    private lateinit var testSettings: Settings
    private val authority = "io.github.tomervancohen.rambleflow.test.documents"

    private fun add(id: String) {
        context.contentResolver.call(
            Uri.parse("content://$authority"),
            "fixture.add",
            null,
            Bundle().apply {
                putString("id", id)
                putString("name", "$id.m4a")
                putLong("size", 100)
                putLong("modified", 1)
            },
        )
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("source-tests-ramble", Context.MODE_PRIVATE).edit().clear().commit()
        testSettings = Settings(object : android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences("source-tests-$name", mode)
        })
        context.packageManager.setComponentEnabledSetting(
            android.content.ComponentName(context, FixtureDocuments::class.java),
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            android.content.pm.PackageManager.DONT_KILL_APP,
        )
        db = Room.inMemoryDatabaseBuilder(context, QueueDatabase::class.java).build()
        context.contentResolver.call(Uri.parse("content://$authority"), "fixture.reset", null, null)
    }

    @After
    fun close() {
        context.packageManager.setComponentEnabledSetting(
            android.content.ComponentName(context, FixtureDocuments::class.java),
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP,
        )
        db.close()
    }

    @Test
    fun standardParentAddedAsExtraFolderFindsLaterRecorderChildren() = runBlocking<Unit> {
        context.contentResolver.call(Uri.parse("content://$authority"), "fixture.children", null,
            Bundle().apply { putBoolean("enabled", false) })
        val reader = SourceReader(context, db, testSettings)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "recordings")
        assertTrue(reader.activate(tree, "folder:parent"))
        context.contentResolver.call(Uri.parse("content://$authority"), "fixture.children", null,
            Bundle().apply { putBoolean("enabled", true) })
        add("later-child")
        reader.scan()
        assertNotNull(db.queue().observation("folder:parent", "later-child"))
        val cutoff = db.queue().sources().single { it.kind == "folder:parent" }.cutoff
        assertTrue(reader.activate(tree, "folder:duplicate"))
        assertEquals(1, db.queue().sources().count { it.kind.startsWith("folder:") })
        assertEquals(cutoff, db.queue().sources().single { it.kind == "folder:parent" }.cutoff)
    }

    @Test
    fun copiedWavIsQueuedAndSnapshottedAfterStabilityCheck() = runBlocking<Unit> {
        val reader = SourceReader(context, db, testSettings)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "unrelated")
        assertTrue(reader.activate(tree, "folder:wav"))
        val pcmSize = 16000 * 2
        val bytes = java.nio.ByteBuffer.allocate(44 + pcmSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + pcmSize).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(pcmSize).array()
        context.contentResolver.call(Uri.parse("content://$authority"), "fixture.add", null,
            Bundle().apply {
                putString("id", "copied-wav"); putString("name", "old recording.wav")
                putLong("size", bytes.size.toLong()); putLong("modified", 1)
                putByteArray("audio", bytes)
            })
        reader.scan()
        val observation = db.queue().observation("folder:wav", "copied-wav")!!
        assertTrue(observation.proof.startsWith("audio-sha256:"))
        db.queue().observation(observation.copy(proofAt = System.currentTimeMillis() - 61_000))
        reader.scan()
        val job = db.queue().recordings().single()
        assertEquals("queued", job.state)
        val snapshot = reader.snapshot(job)
        try { assertArrayEquals(bytes, snapshot.readBytes()) }
        finally { snapshot.parentFile!!.deleteRecursively() }
    }

    @Test
    fun extraFoldersRemainIndependentAndImportedOldAudioIsQueued() = runBlocking<Unit> {
        val reader = SourceReader(context, db, testSettings)
        val voice = DocumentsContract.buildTreeDocumentUri(authority, "voice")
        val other = DocumentsContract.buildTreeDocumentUri(authority, "unrelated")
        assertTrue(reader.activate(voice, "folder:first"))
        assertTrue(reader.activate(other, "folder:second"))
        assertEquals(2, db.queue().sources().size)
        val temp = java.io.File.createTempFile("import-test", ".m4a", context.cacheDir)
        try {
            NativeM4aFixture.write(temp)
            context.contentResolver.call(Uri.parse("content://$authority"), "fixture.add", null,
                Bundle().apply {
                    putString("id", "imported-old")
                    putString("name", "20200101_120000.m4a")
                    putLong("size", temp.length())
                    putLong("modified", 1)
                    putByteArray("audio", temp.readBytes())
                })
            reader.scan()
            for (kind in listOf("folder:first", "folder:second")) {
                val observation = db.queue().observation(kind, "imported-old")!!
                assertFalse(observation.baseline)
                assertTrue(observation.proof.isNotBlank())
                db.queue().observation(observation.copy(proofAt = System.currentTimeMillis() - 61_000))
            }
            val previouslyExcluded = db.queue().recordings().first()
            db.queue().recording(previouslyExcluded.copy(state = "excluded", reason = "historical"))
            reader.scan()
            assertEquals(2, db.queue().recordings().size)
            assertTrue(db.queue().recordings().all { it.state == "queued" })
            db.queue().source(db.queue().sources().first().copy(enabled = false))
            assertEquals(1, db.queue().sources().count { it.enabled })
        } finally { temp.delete() }
    }

    @Test
    fun emptyStandardParentDetectsRecorderChildCreatedLater() = runBlocking<Unit> {
        val reader = SourceReader(context, db, testSettings)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "recordings")
        fun children(enabled: Boolean) {
            context.contentResolver.call(Uri.parse("content://$authority"), "fixture.children", null,
                Bundle().apply { putBoolean("enabled", enabled) })
        }
        children(false)
        assertEquals("recordings", reader.resolveFolder(tree, "voice", allowCustom = false))
        assertTrue(reader.activate(tree, "voice"))
        assertTrue(reader.canRead(db.queue().sources().single()))
        children(true)
        add("created-later")
        reader.scan()
        assertNotNull(db.queue().observation("voice", "created-later"))
        assertFalse(db.queue().observation("voice", "created-later")!!.baseline)
    }

    @Test
    fun xiaomiParentAndRecorderGrantsResolveVoiceAndCallsWithCaseVariants() = runBlocking<Unit> {
        val reader = SourceReader(context, db, testSettings)
        for (root in listOf("miui", "xiaomi-voice")) {
            val tree = DocumentsContract.buildTreeDocumentUri(authority, root)
            assertEquals("xiaomi-voice", reader.resolveFolder(tree, "voice", allowCustom = false))
            assertEquals("xiaomi-call", reader.resolveFolder(tree, "call", allowCustom = false))
            assertTrue(reader.hasKnownCallFolder(tree))
            assertTrue(reader.activate(tree, "voice"))
            assertTrue(reader.activate(tree, "call"))
        }
        assertEquals(2, db.queue().sources().size)
    }

    @Test
    fun pixelPublicFolderHandlesDirectAudioAndNewRecordings() = runBlocking<Unit> {
        val reader = SourceReader(context, db, testSettings)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "pixel")
        assertEquals("pixel", reader.resolveFolder(tree, "voice", allowCustom = false))
        assertNull(reader.resolveFolder(tree, "call", allowCustom = false))
        add("old-pixel")
        assertTrue(reader.activate(tree, "voice"))
        add("new-pixel")
        reader.scan()
        assertTrue(db.queue().observation("voice", "old-pixel")!!.baseline)
        assertFalse(db.queue().observation("voice", "new-pixel")!!.baseline)
    }

    @Test
    fun customFolderDetectsNewAudioAfterAnEmptyConnection() = runBlocking<Unit> {
        val reader = SourceReader(context, db, testSettings)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "unrelated")
        assertTrue(reader.activate(tree, "voice"))
        assertTrue(reader.entries(tree.toString(), "unrelated").isEmpty())
        add("custom-new")
        reader.scan()
        assertNotNull(db.queue().observation("voice", "custom-new"))
        assertFalse(db.queue().observation("voice", "custom-new")!!.baseline)
        assertEquals("custom-new", db.queue().recordings().single().document)
    }

    @Test
    fun setupRecognizesEmptySourcesButRejectsUnrelatedFolders() = runBlocking<Unit> {
        val reader = SourceReader(context, db, testSettings)
        val parent = DocumentsContract.buildTreeDocumentUri(authority, "recordings")
        assertEquals("voice", reader.resolveFolder(parent, "voice", allowCustom = false))
        assertEquals("call", reader.resolveFolder(parent, "call", allowCustom = false))
        val unrelated = DocumentsContract.buildTreeDocumentUri(authority, "unrelated")
        assertNull(reader.resolveFolder(unrelated, "voice", allowCustom = false))
        assertNull(reader.resolveFolder(unrelated, "call", allowCustom = false))
        assertEquals("unrelated", reader.resolveFolder(unrelated, "voice"))
        assertTrue(reader.activate(unrelated, "voice"))
        assertTrue(db.queue().sources().single().baselineComplete)
        assertTrue(reader.canRead(db.queue().sources().single()))
        assertTrue(reader.activate(parent, "voice"))
        assertTrue(reader.activate(parent, "call", enabled = false))
        val sources = db.queue().sources()
        assertTrue(sources.all { reader.canRead(it) })
        assertTrue(sources.single { it.kind == "voice" }.enabled)
        assertFalse(sources.single { it.kind == "call" }.enabled)
    }

    @Test
    fun emptyFoldersWorkAndSeparateGrantsDoNotConnectTheWrongSource() = runBlocking<Unit> {
        val reader = SourceReader(context, db, testSettings)
        val voiceTree = DocumentsContract.buildTreeDocumentUri(authority, "voice")
        val callTree = DocumentsContract.buildTreeDocumentUri(authority, "call")
        assertFalse(reader.activate(callTree, "voice"))
        assertFalse(reader.activate(voiceTree, "call"))
        assertTrue(db.queue().sources().isEmpty())
        assertTrue(reader.activate(voiceTree, "voice"))
        assertEquals(listOf("voice"), db.queue().sources().map { it.kind })
        assertTrue(reader.activate(callTree, "call"))
        assertEquals(2, db.queue().sources().size)
        assertTrue(db.queue().sources().all { it.baselineComplete && reader.canRead(it) })
        reader.scan()
        assertTrue(db.queue().recordings().isEmpty())
    }

    @Test
    fun singleParentGrantMapsBothSourcesAndBaselineIsNeverQueued() =
        runBlocking<Unit> {
            add("old")
            val reader = SourceReader(context, db, testSettings)
            val tree = DocumentsContract.buildTreeDocumentUri(authority, "recordings")
            assertTrue(reader.activate(tree, "voice"))
            assertTrue(reader.activate(tree, "call"))
            val sources = db.queue().sources()
            assertEquals(2, sources.size)
            assertEquals(1, sources.map { it.tree }.distinct().size)
            reader.scan()
            assertTrue(db.queue().recordings().isEmpty())
            add("new")
            reader.scan()
            val o = db.queue().observation("voice", "new")!!
            db.queue().observation(o.copy(stableSince = System.currentTimeMillis() - 61_000))
            reader.scan()
            val jobs = db.queue().recordings()
            assertEquals(1, jobs.size)
            assertEquals("waiting", jobs.single().state)
            reader.scan()
            assertEquals(1, db.queue().recordings().size)
            val cutoff = sources.first { it.kind == "voice" }.cutoff
            reader.activate(tree, "voice")
            assertEquals(cutoff, db.queue().sources().first { it.kind == "voice" }.cutoff)
        }
    @Test
    fun samsungParentGrantRestoresCallsWithoutAChildAndDetectsItLater() = runBlocking<Unit> {
        context.contentResolver.call(Uri.parse("content://$authority"), "fixture.children", null,
            Bundle().apply { putBoolean("enabled", false) })
        val reader = SourceReader(context, db, testSettings, RecorderDevice.forManufacturer("samsung"))
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "recordings")
        assertTrue(reader.activate(tree, "voice"))
        val cutoff = db.queue().sources().single().cutoff
        reader.restoreCoveredSources()
        var sources = db.queue().sources()
        assertEquals(setOf("voice", "call"), sources.map { it.kind }.toSet())
        assertTrue(sources.all { reader.canRead(it) && it.enabled })
        assertEquals("Recordings", reader.folderLabel(sources.single { it.kind == "voice" }))
        assertEquals(cutoff, sources.single { it.kind == "voice" }.cutoff)
        context.contentResolver.call(Uri.parse("content://$authority"), "fixture.children", null,
            Bundle().apply { putBoolean("enabled", true) })
        context.contentResolver.call(Uri.parse("content://$authority"), "fixture.add", null,
            Bundle().apply {
                putString("id", "later-call"); putString("name", "later-call.m4a")
                putLong("size", 100); putLong("modified", 1); putString("parent", "call")
            })
        reader.scan()
        assertNotNull(db.queue().observation("call", "later-call"))
        assertNull(db.queue().observation("voice", "later-call"))
        val call = db.queue().sources().single { it.kind == "call" }
        db.queue().source(call.copy(enabled = false))
        reader.restoreCoveredSources()
        assertFalse(db.queue().sources().single { it.kind == "call" }.enabled)
    }

    @Test
    fun customCallFolderStillScansItsDirectAudio() = runBlocking<Unit> {
        val reader = SourceReader(context, db, testSettings)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "unrelated")
        assertTrue(reader.activate(tree, "call"))
        add("custom-call")
        reader.scan()
        assertNotNull(db.queue().observation("call", "custom-call"))
    }

    @Test
    fun xiaomiParentGrantWaitsForCallChildWithoutScanningVoiceAsCalls() = runBlocking<Unit> {
        context.contentResolver.call(Uri.parse("content://$authority"), "fixture.children", null,
            Bundle().apply { putBoolean("enabled", false) })
        val reader = SourceReader(context, db, testSettings, RecorderDevice.forManufacturer("xiaomi"))
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "xiaomi-voice")
        assertTrue(reader.activate(tree, "voice"))
        reader.restoreCoveredSources()
        add("voice-only")
        reader.scan()
        assertNotNull(db.queue().observation("voice", "voice-only"))
        assertNull(db.queue().observation("call", "voice-only"))
        context.contentResolver.call(Uri.parse("content://$authority"), "fixture.children", null,
            Bundle().apply { putBoolean("enabled", true) })
        context.contentResolver.call(Uri.parse("content://$authority"), "fixture.add", null,
            Bundle().apply {
                putString("id", "xiaomi-later-call"); putString("name", "call.m4a")
                putLong("size", 100); putLong("modified", 1); putString("parent", "xiaomi-call")
            })
        reader.scan()
        assertNotNull(db.queue().observation("call", "xiaomi-later-call"))
        assertNull(db.queue().observation("voice", "xiaomi-later-call"))
    }

    @Test fun removedParentDoesNotRestoreAndExplicitReAddReusesItsSources() = runBlocking<Unit> {
        val reader = SourceReader(context, db, testSettings)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "recordings")
        add("original")
        reader.activate(tree, "voice")
        reader.activate(tree, "call")
        val cutoffs = db.queue().sources().associate { it.kind to it.cutoff }
        reader.removeFolder(tree.toString())
        reader.restoreCoveredSources()
        reader.scan()
        assertTrue(db.queue().sources().none { it.enabled })
        assertTrue(tree.toString() in testSettings.removedSourceTrees)
        assertTrue(reader.entries(tree.toString(), "voice").any { it.id == "original" })
        reader.activate(tree, "folder:readded")
        assertEquals(setOf("voice", "call"), db.queue().sources().map { it.kind }.toSet())
        assertTrue(db.queue().sources().all { it.enabled })
        assertEquals(cutoffs, db.queue().sources().associate { it.kind to it.cutoff })
        assertFalse(tree.toString() in testSettings.removedSourceTrees)
    }
    @Test fun replacingParentMovesBothMembersAndHidesPreviousGrant() = runBlocking<Unit> {
        val reader = SourceReader(context, db, testSettings)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "recordings")
        val next = DocumentsContract.buildTreeDocumentUri(authority, "miui")
        reader.activate(tree, "voice")
        reader.activate(tree, "call")
        assertTrue(reader.replaceFolder(tree.toString(), next))
        assertTrue(db.queue().sources().all { it.tree == next.toString() && it.enabled })
        assertTrue(tree.toString() in testSettings.removedSourceTrees)
        assertFalse(next.toString() in testSettings.removedSourceTrees)
    }

    @Test fun genericParentObeysSeparateVoiceAndCallSwitches() = runBlocking<Unit> {
        val reader = SourceReader(context, db, testSettings)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "recordings")
        reader.activate(tree, "folder:all")
        reader.restoreCoveredSources()
        assertTrue(reader.setCategoryEnabled("call", false))
        context.contentResolver.call(Uri.parse("content://$authority"), "fixture.add", null,
            Bundle().apply {
                putString("id", "call-while-off"); putString("name", "call.m4a")
                putLong("size", 100); putLong("modified", 1); putString("parent", "call")
            })
        reader.scan()
        assertNull(db.queue().observation("folder:all", "call-while-off"))
        assertNull(db.queue().observation("call", "call-while-off"))
        reader.setCategoryEnabled("voice", false)
        reader.setCategoryEnabled("call", true)
        reader.scan()
        assertNotNull(db.queue().observation("call", "call-while-off"))
        assertTrue(db.queue().sources().single { it.kind == "folder:all" }.enabled == false)
    }

    @Test fun manuallyAddedCallFolderUsesTheCallSwitch() = runBlocking<Unit> {
        val reader = SourceReader(context, db, testSettings)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "call")
        reader.activate(tree, "folder:chosen-call")
        assertTrue(db.queue().sources().single().isCallRecordingSource())
        reader.setCategoryEnabled("call", false)
        assertFalse(db.queue().sources().single().enabled)
        assertFalse(reader.setCategoryEnabled("voice", true))
        assertTrue(reader.setCategoryEnabled("call", true))
        assertTrue(db.queue().sources().single().enabled)
    }

}
