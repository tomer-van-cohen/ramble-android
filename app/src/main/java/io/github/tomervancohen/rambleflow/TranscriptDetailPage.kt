package io.github.tomervancohen.rambleflow

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TranscriptDetailPage(app: RambleApp, job: Recording?, onOpenUri: (String) -> Unit) {

                    if (job != null) {
                        Text(job.title, style = MaterialTheme.typography.headlineLarge)
                        Text(
                            listOfNotNull(
                                    job.recordedAt?.let {
                                        DateFormat.getDateTimeInstance(
                                                DateFormat.SHORT,
                                                DateFormat.SHORT,
                                            )
                                            .format(Date(it))
                                    },
                                    if (job.duration > 0)
                                        recordingDuration(job.duration)
                                    else null,
                                    if (job.source == "call") appText(R.string.ui_phone_call_47c6b93)
                                    else appText(R.string.ui_voice_recorder_6c23f5b),
                                )
                                .joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Panel(stateLabel(job))
                        if (job.state == "saved") {
                            val text =
                                remember(job.id) {
                                    runCatching {
                                            LocalExport(app, app.database).file(job.id).readText()
                                        }
                                        .getOrDefault("")
                                }
                            Text(text)
                            if (job.driveState == "uploaded")
                                TextButton(
                                    onClick = {
                                        onOpenUri("https://drive.google.com/file/d/${job.driveId}/view")
                                    }
                                ) {
                                    Text(appText(R.string.ui_open_in_drive_53aa754))
                                }
                        } else if (job.state == "key") {
                            Text(
                                appText(R.string.ui_add_or_replace_your_api_key_to_resume_the_rem_0c0489f)
                            )
                        } else if (job.reason in setOf("audio_budget","unsupported_duration","unsupported_size","unsupported_audio","unsupported_pcm","multiple_tracks","audio_timestamp_gap")) {
                            Text(appText(R.string.unsupported_audio_help))
                        } else if (job.reason == "interrupted_audio") {
                            Text(appText(R.string.ui_recording_was_interrupted_its_audio_was_kept__42bb1c9))
                        } else if (job.state == "storage") {
                            Text(appText(R.string.ui_free_up_space_on_your_phone_we_ll_retry_autom_c8e3d2b))
                        } else if (job.state == "retry") {
                            Text(appText(R.string.ui_no_audio_was_sent_we_ll_retry_when_the_connec_ecb0456))
                        } else if (job.reason.startsWith("provider_")) {
                            Text(appText(R.string.ui_the_provider_rejected_this_request_check_the__e4a3d4c) + " (HTTP " + job.reason.removePrefix("provider_") + ")")
                        } else if (job.state in setOf("uncertain", "error", "rate")) {
                            Text(
                                if (job.state == "uncertain")
                                    appText(R.string.ui_the_provider_may_already_have_processed_a_chu_287c797)
                                else
                                    appText(R.string.ui_processing_stopped_after_an_error_retry_the_r_861863c)
                            )
                        } else
                            Text(
                                appText(R.string.ui_this_recording_is_queued_you_can_return_later_b0d45f4)
                            )
                    }

}

internal fun stateLabel(job: Recording): String =
    when (job.state) {
        "saved" ->
            when (job.driveState) {
                "uploaded" -> appText(R.string.ui_saved_on_phone_and_drive_d720701)
                "" -> appText(R.string.ui_saved_on_phone_0edd0a8)
                else -> appText(R.string.ui_saved_locally_drive_pending_9eb8db4)
            }
        "waiting",
        "confirm" -> appText(R.string.ui_waiting_for_the_recording_to_finish_saving_8df9fd7)
        "uncertain" -> appText(R.string.ui_review_possible_duplicate_charge_9b0e48c)
        "key" -> appText(R.string.ui_api_key_needs_attention_7c4c6f5)
        "retry" -> appText(R.string.ui_waiting_for_connection_no_audio_sent_ccdd09f)
        "storage" -> appText(R.string.ui_waiting_for_storage_space_9f5a98a)
        "planning" -> appText(R.string.ui_preparing_audio_5695a06)
        "rate" -> appText(R.string.ui_waiting_for_provider_limit_092ec40)
        "error" -> appText(R.string.ui_recording_needs_attention_3503a05)
        "transcribing" -> appText(R.string.ui_transcribing_11c367b)
        "skipped" -> appText(R.string.ui_skipped_f088064)
        "excluded" -> appText(R.string.ui_historical_recording_not_uploaded_cf8a57c)
        "duplicate" -> appText(R.string.ui_already_transcribed_b4729a4)
        else -> appText(R.string.ui_waiting_for_transcription_f969478)
    }

