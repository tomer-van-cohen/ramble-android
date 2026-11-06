package io.github.tomervancohen.rambleflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun QuickRecordingGuide(device: RecorderDevice) {
    if (device.family == RecorderFamily.SAMSUNG) {
        SamsungRecordingShortcut()
        return
    }
    Text(appText(R.string.ui_quick_recording_for_next_time_41c1c4c),
        style = MaterialTheme.typography.headlineLarge)
    when (device.family) {
        RecorderFamily.PIXEL -> {
            Icon(Icons.Outlined.Smartphone, null, Modifier.size(90.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(appText(R.string.ui_two_taps_on_the_back_open_ramble_tap_the_micr_9b5eddc))
        }
        else -> Icon(Icons.Outlined.Apps, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.secondary)
    }
    Text(appText(R.string.ui_the_same_recorder_is_inside_ramble_stop_from__93187d9), style = MaterialTheme.typography.bodyMedium)
    if (device.family == RecorderFamily.PIXEL) Text(appText(R.string.ui_on_phones_with_quick_tap_your_phone_may_need__2a595f9), style = MaterialTheme.typography.bodySmall)
    if (device.family == RecorderFamily.XIAOMI) Text(appText(R.string.ui_no_system_button_mapping_needed_gesture_optio_72685fb), style = MaterialTheme.typography.bodySmall)
    val route = when (device.family) {
        RecorderFamily.PIXEL -> appText(R.string.ui_settings_system_gestures_quick_tap_8cf09df)
        else -> appText(R.string.ui_ramble_record_shortcut_or_quick_settings_d4a4a39)
    }
    val instructions = when (device.family) {
        RecorderFamily.SAMSUNG -> listOf(
            appText(R.string.ui_turn_on_double_press_4012cef),
            appText(R.string.ui_choose_open_app_voice_recorder_9a13abd),
            appText(R.string.ui_choose_start_recording_if_available_920f634))
        RecorderFamily.PIXEL -> listOf(
            appText(R.string.ui_turn_on_quick_tap_2a11452),
            appText(R.string.ui_choose_open_app_ramble_587fb95))
        else -> listOf(
            appText(R.string.ui_long_press_the_ramble_icon_7e7d0e9),
            appText(R.string.ui_choose_record_or_add_a_shortcut_to_your_home__a956de4),
            appText(R.string.ui_tap_once_to_record_again_to_stop_fd2d31d))
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(route, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            instructions.forEach { text ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Text("•", color = MaterialTheme.colorScheme.secondary)
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** Describes the benefit before asking the user to change a phone preference. */
@Composable
private fun SamsungRecordingShortcut() {
    Text(appText(R.string.ui_optional_shortcut_6c503e7),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(appText(R.string.ui_ramble_on_your_side_button_e0f16ce),
        style = MaterialTheme.typography.headlineLarge)
    Text(appText(R.string.ui_set_your_side_button_to_ramble_double_press_t_4a3e560),
        style = MaterialTheme.typography.bodyLarge)
    SideButtonArt(height = 140.dp)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(appText(R.string.ui_in_the_settings_that_open_11e3c83),
                style = MaterialTheme.typography.titleMedium)
            ShortcutSetting(appText(R.string.ui_double_press_4b8a7bd), appText(R.string.ui_turn_on_447a7dd))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            ShortcutSetting(appText(R.string.ui_open_app_e9d0fe1), "Ramble")
        }
    }
    Text(appText(R.string.ui_for_direct_recording_use_the_record_shortcut__8162d42),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ShortcutSetting(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
    }
}
