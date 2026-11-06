package io.github.tomervancohen.rambleflow

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

internal fun recordingDuration(millis: Long): String {
    if (millis <= 0) return ""
    val seconds = millis / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
internal fun RecordingsHome(
    jobs: List<Recording>, summaries: List<RecordingSummary>, paused: Boolean, accessError: Boolean,
    modifier: Modifier = Modifier, onOpen: (Recording) -> Unit, onRecord: () -> Unit,
    onRefresh: () -> Unit, onRepair: () -> Unit, onTranscriptionSettings: () -> Unit,
) {
    val capture by QuickRecordService.status.collectAsState()
    val context = LocalContext.current
    val details = remember(summaries) { summaries.associateBy { it.recording } }
    val sorted = remember(jobs, details) { jobs.sortedByDescending { it.recordedAt ?: details[it.id]?.addedAt ?: 0L } }
    val attention = jobs.count { it.state in setOf("key", "error", "uncertain", "storage") }
    val pending = jobs.count { it.state !in setOf("saved", "excluded", "duplicate", "skipped") }
    Box(modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(appText(R.string.ui_recordings_8f44160), Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
            IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, appText(R.string.ui_check_for_new_recordings_3dbdbc6)) }
        }
        Row(Modifier.fillMaxWidth().testTag("auto-transcription-status").clickable(role = Role.Button, onClick = onTranscriptionSettings).padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (accessError || attention > 0) Icons.Outlined.Info else if (paused) Icons.Outlined.PauseCircle else Icons.Outlined.CheckCircle,
                null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(6.dp))
            Text(when {
                accessError -> appText(R.string.ui_folder_access_needs_attention_1e640ac)
                paused -> appText(R.string.ui_transcription_paused_9d8f35b)
                attention > 0 -> appText(R.string.attention_count,attention)
                pending > 0 -> appText(R.string.processing_count,pending)
                else -> appText(R.string.ui_auto_transcription_on_f3330ce)
            }, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (accessError) TextButton(onClick = onRepair) { Text(appText(R.string.ui_fix_dff6353)) }
            else Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("recordings-list"), contentPadding = PaddingValues(bottom = 108.dp)) {
            if (sorted.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.GraphicEq, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text(appText(R.string.ui_what_s_on_your_mind_35ba4f0), style = MaterialTheme.typography.titleLarge)
                    Text(appText(R.string.ui_record_here_or_add_audio_to_your_connected_fo_6757869),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(sorted, key = { it.id }) { job ->
                RecordingRow(job, details[job.id]) { onOpen(job) }
                HorizontalDivider(Modifier.padding(start = 54.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            }
        }
      }
      Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
          if (capture.mode in setOf("recording", "starting", "saving")) RecordingPill(capture) {
              context.startService(Intent(context, QuickRecordService::class.java).setAction(QuickRecordService.STOP))
          } else FloatingActionButton(onClick = onRecord, modifier = Modifier.size(76.dp).testTag("record-button"),
              shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
              Icon(Icons.Outlined.Mic, appText(R.string.ui_record_08a8c66), Modifier.size(40.dp))
          }
      }
    }
}

@Composable
internal fun RecordingRow(job: Recording, summary: RecordingSummary?, onOpen: () -> Unit) {
    val good = job.state == "saved"
    val needs = job.state in setOf("error", "key", "uncertain", "storage")
    val state = when (job.state) {
        "saved" -> if (job.driveState == "uploaded") appText(R.string.ui_saved_drive_f3d7363) else appText(R.string.ui_saved_a64d43e)
        "transcribing" -> appText(R.string.ui_transcribing_7b67bab)
        "waiting", "confirm" -> appText(R.string.ui_waiting_2a654ae)
        "key" -> appText(R.string.ui_transcription_key_needed_bec4da1)
        "error", "uncertain" -> appText(R.string.ui_needs_attention_ff2f815)
        "retry" -> appText(R.string.ui_waiting_for_connection_7cca616)
        "storage" -> appText(R.string.ui_storage_space_needed_cff7b36)
        "planning" -> appText(R.string.ui_preparing_audio_821fc84)
        "rate" -> appText(R.string.ui_retrying_later_528d8fe)
        "excluded" -> appText(R.string.ui_existing_recording_b22f5bb)
        "duplicate" -> appText(R.string.ui_already_transcribed_b4729a4)
        "skipped" -> appText(R.string.ui_skipped_ba4d944)
        else -> appText(R.string.ui_processing_c4760c8)
    }
    val timestamp = job.recordedAt ?: summary?.addedAt?.takeIf { it > 0 }
    val date = timestamp?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
    val meta = listOfNotNull(date?.let { if (job.recordedAt == null) appText(R.string.ui_added_ef5682a) + it else it }, state).joinToString(" · ")
    Row(Modifier.fillMaxWidth().testTag("recording-${job.id}").clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (job.source == "call") Icons.Outlined.Phone else Icons.Outlined.Mic, null, Modifier.size(22.dp),
            tint = if (needs) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(job.title.replace(Regex("\\.(m4a|mp3|wav|ogg|opus|aac|flac|amr|mp4)$", RegexOption.IGNORE_CASE), ""), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!summary?.preview.isNullOrBlank()) Text(summary!!.preview.replace(Regex("\\s+"), " "),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(meta, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (needs) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(recordingDuration(summary?.durationMillis?.takeIf { it > 0 } ?: job.duration), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (good) Icon(Icons.Outlined.Check, appText(R.string.ui_transcript_ready_792bf46), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
        }
    }
}
