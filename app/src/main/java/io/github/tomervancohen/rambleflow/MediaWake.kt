package io.github.tomervancohen.rambleflow

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import kotlinx.coroutines.*

/** MediaStore is a wake-up hint only. SourceReader still reads only granted SAF sources. */
object MediaWake {
    const val JOB_ID = 741932

    fun arm(context: Context, replace: Boolean = false) {
        val settings = Settings(context)
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (settings.paused || settings.tree.isBlank()) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (!replace && scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, MediaWakeService::class.java))
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                    )
                )
                .setTriggerContentUpdateDelay(1000)
                .setTriggerContentMaxDelay(5000)
                .build()
        )
    }
}

class MediaWakeService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var task: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        task = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (!Settings(this@MediaWakeService).paused) {
                        // Wait for durable WorkManager insertion before acknowledging the event.
                        Schedule.scan(this@MediaWakeService).result.get()
                        getSharedPreferences("media_wake", Context.MODE_PRIVATE)
                            .edit()
                            .putLong("lastEvent", System.currentTimeMillis())
                            .commit()
                    }
                }
                task = null
                // Content-triggered jobs are one-shot. Replace before finishing to keep observing.
                MediaWake.arm(this@MediaWakeService, replace = true)
                jobFinished(params, false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                task = null
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        task?.cancel()
        task = null
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

class MediaWakeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED))
            MediaWake.arm(context)
    }
}
