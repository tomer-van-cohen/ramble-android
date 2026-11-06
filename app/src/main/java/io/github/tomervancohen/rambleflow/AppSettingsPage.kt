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
internal fun AppSettingsPage(activity: ComponentActivity, settings: Settings, onAutoTranscription: (Boolean) -> Unit,
    onKeepAudio: (Boolean) -> Unit, onClearAudio: () -> Unit, onWifiOnly: (Boolean) -> Unit,
    onNavigate: (String) -> Unit) {

                    Text(
                        appText(R.string.ui_everything_in_one_place_094435f),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(appText(R.string.ui_auto_transcription_489cf86), Modifier.weight(1f))
                        Switch(
                            !settings.paused,
                            {
                                onAutoTranscription(it)
                            },
                            modifier = Modifier.testTag("auto-transcription-switch"),
                        )
                    }
                    AppLanguagePicker(activity, settings)
                    RowLink(appText(R.string.ui_license_d52ecf4), "PolyForm Noncommercial") { onNavigate("license") }
                    RowLink(appText(R.string.ui_transcription_provider_c2ef7e2), settings.provider) {
                        onNavigate("provider")
                    }
                    RowLink(appText(R.string.ui_recording_sources_c3353a9), "Recordings") {
                        onNavigate("sources")
                    }
                    RowLink(
                        appText(R.string.ui_local_transcripts_85107af),
                        if (settings.outputTree.isBlank()) "Download / Ramble"
                        else appText(R.string.ui_custom_folder_e068357),
                    ) {
                        onNavigate("output")
                    }
                    RowLink(
                        "Google Drive",
                        if (settings.driveEnabled) appText(R.string.ui_connected_f479903)
                        else appText(R.string.ui_not_connected_ff066a3),
                    ) {
                        onNavigate("drive")
                    }
                    RowLink(
                        appText(R.string.ui_ai_assistant_instructions_74db7f0),
                        if (settings.driveEnabled) appText(R.string.ui_copy_for_your_assistant_b112590)
                        else appText(R.string.ui_requires_google_drive_feba960),
                    ) {
                        onNavigate("agent")
                    }
                    TranscriptionLanguagePicker(settings, settingsRow = true)
                    RowLink(appText(R.string.ui_try_a_recording_55b91c5)) {
                        onNavigate("trial")
                    }
                    RowLink(appText(R.string.ui_quick_recording_462793f)) { onNavigate("guide") }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(appText(R.string.ui_keep_original_audio_32fac61),Modifier.weight(1f))
                        Switch(settings.keepAudio,onKeepAudio)
                    }
                    Text(appText(R.string.ui_when_off_private_audio_copies_are_removed_aft_f0c674b),style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick=onClearAudio) { Text(appText(R.string.ui_clear_completed_audio_af5f5f2)) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(appText(R.string.ui_upload_on_wi_fi_only_4d70051), Modifier.weight(1f))
                        Switch(
                            settings.wifiOnly,
                            {
                                onWifiOnly(it)
                            },
                        )
                    }
                    Text(
                        appText(R.string.ui_audio_goes_only_to_your_provider_drive_receiv_b80e342),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

}
