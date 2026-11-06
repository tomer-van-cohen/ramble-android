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
internal fun RecordingAccessPage(device: RecorderDevice, setupVoiceAccess: Boolean, setupCallAccess: Boolean, customFolderHasAudio: Boolean?, hasFolders: Boolean, folders: @Composable () -> Unit) {

                    Text(
                        if (device.family == RecorderFamily.PIXEL)
                            appText(R.string.ui_share_a_recording_with_ramble_41f422a)
                        else appText(R.string.ui_find_your_recordings_c76d6a3),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    if (device.family == RecorderFamily.PIXEL) {
                        Text(appText(R.string.ui_for_a_recorded_call_open_the_call_in_phone_hi_c89b5a8))
                    } else {
                    if (device.family == RecorderFamily.XIAOMI) {
                        Text(appText(R.string.ui_allow_access_to_sound_recorder_inside_miui_if_9facebe))
                    }
                    if (customFolderHasAudio != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null,
                                tint = MaterialTheme.colorScheme.secondary)
                            Text(appText(R.string.ui_folder_connected_f218ffa),
                                style = MaterialTheme.typography.titleLarge)
                        }
                        Text(if (customFolderHasAudio == true)
                            appText(R.string.ui_audio_files_found_new_recordings_will_be_dete_5f4a32f)
                        else appText(R.string.ui_no_recordings_yet_you_re_all_set_new_recordin_9f93684))
                    } else {
                    listOf(
                        Triple(appText(R.string.ui_voice_recordings_0d8b2a0), setupVoiceAccess, Icons.Outlined.Mic),
                        Triple(appText(R.string.ui_call_recordings_d244ee2), setupCallAccess, Icons.Outlined.Phone),
                    ).forEach { (label, connected, sourceIcon) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                sourceIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(label, Modifier.weight(1f))
                            if (!connected) Text(
                                appText(R.string.ui_awaiting_access_8bbb027),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            androidx.compose.animation.AnimatedVisibility(visible = connected) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Check, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary)
                                    Text(appText(R.string.ui_connected_f479903),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                    if (setupVoiceAccess && setupCallAccess) {
                        Text(appText(R.string.ui_both_recording_sources_are_connected_e24db4c))
                    } else if (setupVoiceAccess || setupCallAccess) {
                        Text(appText(R.string.ui_connect_the_other_folder_or_continue_with_the_5ba1390))
                    } else Text(
                        appText(R.string.ui_tap_allow_recording_access_next_tap_use_this__2b3e2d8)
                    )
                    }
                    }
                    if (device.family != RecorderFamily.PIXEL && hasFolders)
                        folders()

}
