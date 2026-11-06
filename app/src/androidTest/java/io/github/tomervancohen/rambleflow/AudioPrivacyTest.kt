package io.github.tomervancohen.rambleflow

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class AudioPrivacyTest {
    private val app get()=ApplicationProvider.getApplicationContext<RambleApp>()
    private val ownIds=mutableListOf<String>()
    @Before fun prepare() = runBlocking {
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.settings.paused=true;app.settings.provider="groq";app.settings.saveKey("groq","synthetic-private-audio-key")
        app.database.clearAllTables();SyntheticAudioFixtures.clear(app)
    }
    @After fun cleanup() = runBlocking {
        for(id in ownIds) { AppRecordings(app).file(id).delete();AppRecordings(app).partial(id).delete();
            SharedAudio(app).file(id).delete();LocalExport(app,app.database).file(id).delete() }
        app.database.clearAllTables();SyntheticAudioFixtures.clear(app);app.settings.keepAudio=true
    }
    private fun id()=UUID.randomUUID().toString().also { ownIds+=it }
    @Test fun completedAudioCanBeRemovedWithoutDeletingPendingOrTranscript() = runBlocking {
        val saved=id();val pending=id();val imported=id()
        AppRecordings(app).file(saved).writeBytes(byteArrayOf(1,2,3))
        AppRecordings(app).file(pending).writeBytes(byteArrayOf(4,5,6))
        SharedAudio(app).file(imported).writeBytes(byteArrayOf(7,8,9))
        for((key,state,source) in listOf(Triple(saved,"saved","app"),Triple(pending,"key","app"),Triple(imported,"saved","shared")))
            app.database.queue().recording(Recording(key,source,key,"test",null,state=state))
        for(key in listOf(saved,imported)) LocalExport(app,app.database).file(key).apply { parentFile?.mkdirs();writeText("Durable transcript") }
        app.settings.keepAudio=true;AudioRetention(app).clean()
        assertTrue(AppRecordings(app).file(saved).exists())
        app.settings.keepAudio=false;AudioRetention(app).clean()
        assertFalse(AppRecordings(app).file(saved).exists());assertFalse(SharedAudio(app).file(imported).exists())
        assertTrue(AppRecordings(app).file(pending).exists());assertTrue(LocalExport(app,app.database).file(saved).exists())
    }
    @Test fun unsubmittedSharedCopiesExpireButPendingJobsDoNot() = runBlocking {
        val orphan=id();val pending=id()
        for(key in listOf(orphan,pending)) SharedAudio(app).file(key).apply { writeBytes(byteArrayOf(1));setLastModified(System.currentTimeMillis()-2*24*60*60_000L) }
        app.database.queue().recording(Recording(pending,"shared",pending,"pending",null,state="key"))
        AudioRetention(app).clean()
        assertFalse(SharedAudio(app).file(orphan).exists());assertTrue(SharedAudio(app).file(pending).exists())
    }
    @Test fun interruptedTrialIsVisibleAndNeverQueuedWithoutConfirmation() = runBlocking {
        val key=id();NativeM4aFixture.write(AppRecordings(app).file(key))
        app.settings.quickPending=""
        AppRecordings(app).recoverInterruptedAudio()
        val job=app.database.queue().recording(key)!!
        assertEquals("error",job.state);assertEquals("interrupted_audio",job.reason)
        AppRecordings(app).resumeInterrupted(job)
        assertEquals("queued",app.database.queue().recording(key)!!.state)
    }
    @Test fun incompletePartialIsVisibleAndExplicitDeletionCannotReimportIt() = runBlocking {
        val key=id();AppRecordings(app).partial(key).writeBytes(byteArrayOf(1,2,3))
        app.settings.quickPending=key
        AppRecordings(app).recoverInterruptedAudio()
        val job=app.database.queue().recording(key)!!
        assertEquals("interrupted_audio",job.reason);assertTrue(AppRecordings(app).partial(key).exists())
        assertTrue(AudioRetention(app).remove(job))
        assertFalse(AppRecordings(app).partial(key).exists())
        assertEquals("deleted",app.database.queue().recording(key)!!.state)
        assertEquals("",app.database.queue().recording(key)!!.title)
    }
    @Test fun deletionNeverRacesAnActiveProviderRequest() = runBlocking {
        val key=id();AppRecordings(app).file(key).writeBytes(byteArrayOf(1,2,3))
        val job=Recording(key,"app",key,"pending",null,state="transcribing")
        app.database.queue().recording(job)
        app.database.queue().lease(Lease("processor","fixture-inflight",System.currentTimeMillis()+60_000))
        assertFalse(AudioRetention(app).remove(job))
        assertTrue(AppRecordings(app).file(key).exists())
        assertEquals("transcribing",app.database.queue().recording(key)!!.state)
        app.database.queue().release("processor","fixture-inflight")
    }

    @Test fun retentionMigrationPreservesExistingUsersAndExplicitChoices() {
        val context=object:android.content.ContextWrapper(app) {
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences = super.getSharedPreferences("retention-migration-fixture",mode)
        }
        val prefs=context.getSharedPreferences("ramble",android.content.Context.MODE_PRIVATE)
        try {
            prefs.edit().clear().commit()
            Settings(context).initializeAudioRetention();assertFalse(Settings(context).keepAudio)
            prefs.edit().clear().putInt("step",10).commit()
            Settings(context).initializeAudioRetention();assertTrue(Settings(context).keepAudio)
            Settings(context).keepAudio=false
            Settings(context).initializeAudioRetention();assertFalse(Settings(context).keepAudio)
        } finally { prefs.edit().clear().commit() }
    }
    @Test fun nativeAndWrappedDiskFullErrorsRemainRecoverable() {
        assertTrue(StorageFailures.isFull(android.system.ErrnoException("write",android.system.OsConstants.ENOSPC)))
        assertTrue(StorageFailures.isFull(java.io.IOException("wrapped",android.system.ErrnoException("write",android.system.OsConstants.EDQUOT))))
        assertFalse(StorageFailures.isFull(java.io.IOException("source read error")))
    }

}
