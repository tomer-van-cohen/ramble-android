package io.github.tomervancohen.rambleflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FirstRecordingPage(
    app: RambleApp,
    capture: TrialCapture,
    job: Recording?,
    connected: Boolean,
    onTranscript: () -> Unit,
) {
    val text by
        produceState("", job?.id, job?.state) {
            if (job?.state == "saved")
                value =
                    withContext(Dispatchers.IO) {
                        runCatching {
                                LocalExport(app, app.database)
                                    .file(job.id)
                                    .readText()
                                    .substringAfter("\n\n")
                            }
                            .getOrDefault("")
                    }
        }
    val saved = job?.state == "saved"
    Text(
        appText(if (saved) R.string.first_recording_ready_title else R.string.first_recording_title),
        style = MaterialTheme.typography.headlineLarge,
    )
    if (job == null) {
        TranscriptionLanguagePicker(app.settings)
        Text(
            appText(R.string.ui_tap_record_and_say_93a1fed)
        )
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Text(
                appText(R.string.ui_this_is_my_first_ever_recording_hello_to_all__8ce490d),
                Modifier.fillMaxWidth().padding(20.dp),
                fontSize = 22.sp,
                lineHeight = 34.sp,
            )
        }
        if (capture.mode == "recording") {
            LinearProgressIndicator(
                progress = { capture.level },
                modifier = Modifier.fillMaxWidth().height(12.dp),
            )
            Text(appText(R.string.ui_microphone_level_5da562e),
                style = MaterialTheme.typography.labelMedium)
            Text(
                appText(R.string.recording_seconds,capture.seconds),
                Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
        }
        if (capture.error.isNotBlank())
            Text(
                when (capture.error) {
                    "permission" ->
                        appText(R.string.ui_allow_microphone_access_to_record_here_you_ca_5800d07)
                    "cancelled" ->
                        appText(R.string.ui_recording_cancelled_nothing_was_sent_b0893a6)
                    "interrupted" ->
                        appText(R.string.ui_leaving_the_app_cancelled_the_unsaved_recordi_1253bf4)
                    "too_short" ->
                        appText(R.string.ui_not_enough_audio_was_saved_record_for_a_few_s_4caafab)
                    else ->
                        appText(R.string.ui_couldn_t_save_the_recording_please_try_again_06369e8)
                },
                color = MaterialTheme.colorScheme.error,
            )
    } else {
        if (job.reason == "no_speech") {
            Text(appText(R.string.ui_no_clear_speech_was_detected_check_your_micro_04ed201))
            TranscriptionLanguagePicker(app.settings)
            return
        }
        val paused = app.settings.paused && job.state !in setOf("saved", "key", "rate", "uncertain", "error")
        val message =
            when {
                saved && job.driveState == "uploaded" ->
                    appText(R.string.ui_saved_on_your_phone_and_in_google_drive_730736c)
                saved && job.driveTarget.isNotBlank() ->
                    appText(R.string.ui_saved_on_your_phone_drive_upload_pending_1b52cc6)
                saved -> appText(R.string.ui_saved_on_your_phone_c4be7eb)
                job.state == "key" ->
                    appText(R.string.ui_your_api_key_needs_attention_2dbbef2)
                job.state == "rate" ->
                    appText(R.string.ui_your_provider_asked_us_to_wait_we_ll_retry_au_d42fc78)
                job.state == "uncertain" ->
                    appText(R.string.ui_review_this_attempt_before_sending_again_b64e5e0)
                job.state == "error" ->
                    appText(R.string.ui_transcription_stopped_review_it_to_try_again_e64b87a)
                job.state == "retry" -> appText(R.string.ui_waiting_for_connection_no_audio_was_sent_85a3020)
                job.state == "storage" -> appText(R.string.ui_storage_space_needed_your_progress_is_saved_41c0a86)
                paused -> appText(R.string.ui_auto_transcription_is_paused_a74e044)
                else -> appText(R.string.ui_transcribing_your_recording_9e5aa1a)
            }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = .1f),
        ) {
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!saved && !paused && job.state !in setOf("error", "key", "uncertain", "rate"))
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Icon(if (saved) Icons.Outlined.CheckCircle else Icons.Outlined.Info, null)
                Text(message, fontSize = 17.sp)
            }
        }
        if (saved) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            Text(
                appText(R.string.ui_your_recordings_8510c13),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Card(
                onClick = onTranscript,
                colors =
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Mic, null)
                    Column {
                        Text(job.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            appText(R.string.ui_first_transcript_731ec00),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
            Text(
                if (connected)
                    appText(R.string.ui_from_now_on_new_recordings_in_your_connected__ac291ef)
                else
                    appText(R.string.ui_your_first_transcript_is_here_connect_your_ph_6e2036a)
            )
            if (job.driveState == "uploaded")
                Text(
                    appText(R.string.ui_your_ai_assistants_connected_to_drive_can_fin_7af09d6),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        } else
            Text(
                appText(if (paused) R.string.paused_recording_help else R.string.pending_recording_help),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

/** One clear recording action, placed in the footer near the bottom of the screen. */
@Composable
fun TrialRecordButton(recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(144.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (recording) Color(0xFFC8231F) else Color(0xFF25D366),
                contentColor = if (recording) Color.White else Color(0xFF121212),
            ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (recording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    contentDescription = null, modifier = Modifier.size(44.dp))
                Text(if (recording) appText(R.string.ui_stop_f77e388) else appText(R.string.ui_record_08a8c66),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** A recording preference, independent of the device's interface language. */
@Composable
fun TranscriptionLanguagePicker(settings: Settings, settingsRow: Boolean = false) {
    var selected by remember { mutableStateOf(settings.transcriptionLanguage) }
    var expanded by remember { mutableStateOf(false) }
    val choices = listOf("" to appText(R.string.ui_auto_detect_336eeed),
        "he" to "עברית", "en" to "English", "ar" to "العربية", "ru" to "Русский",
        "es" to "Español", "fr" to "Français", "de" to "Deutsch", "it" to "Italiano",
        "pt" to "Português", "hi" to "हिन्दी", "ja" to "日本語", "zh" to "中文",
        "ko" to "한국어", "tr" to "Türkçe", "uk" to "Українська")
    val title = appText(R.string.ui_transcription_language_b47c15e)
    val label = choices.find { it.first == selected }?.second ?: selected
    if (settingsRow) {
        RowLink(title, label) { expanded = true }
    } else {
        TextButton(onClick = { expanded = true }) { Text("$title: $label") }
    }
    if (expanded) AlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(choices, key = { it.first }) { (code, name) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                        selected = code
                        settings.transcriptionLanguage = code
                        expanded = false
                    }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == code, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { expanded = false }) {
            Text(appText(R.string.ui_cancel_1fa68c8))
        } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
