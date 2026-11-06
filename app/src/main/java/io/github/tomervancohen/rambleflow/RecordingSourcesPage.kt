package io.github.tomervancohen.rambleflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class ConnectedFolder(val tree: String, val name: String, val readable: Boolean, val members: List<Source>)

@Composable
internal fun RecordingSourcesPage(
    voiceOn: Boolean, callsOn: Boolean, busy: Boolean,
    folders: List<ConnectedFolder>,
    onToggle: (String, Boolean) -> Unit,
    onAdd: () -> Unit, onChange: (ConnectedFolder) -> Unit, onRemove: (ConnectedFolder) -> Unit,
) {
    Text(appText(R.string.ui_recording_sources_68d3a41), style = MaterialTheme.typography.headlineLarge)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            SourceToggleRow(appText(R.string.ui_voice_memos_79db403), "voice", voiceOn, busy, onToggle)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            SourceToggleRow(appText(R.string.ui_phone_calls_c414f29), "call", callsOn, busy, onToggle)
        }
    }
    FolderConnections(folders, busy, onAdd, onChange, onRemove)
}

@Composable
private fun SourceToggleRow(label: String, kind: String, checked: Boolean, busy: Boolean,
    onToggle: (String, Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(if (kind == "call") Icons.Outlined.Phone else Icons.Outlined.Mic, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(if (checked) appText(R.string.ui_on_1185607) else appText(R.string.ui_off_bc61b8e),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = !busy,
            modifier = Modifier.semantics { contentDescription = if (kind == "call")
                appText(R.string.ui_transcribe_phone_call_recordings_f3e1a77) else
                appText(R.string.ui_transcribe_voice_memos_e981fa3) },
            onCheckedChange = { onToggle(kind, it) })
    }
}

@Composable
internal fun FolderConnections(folders: List<ConnectedFolder>, busy: Boolean,
    onAdd: () -> Unit, onChange: (ConnectedFolder) -> Unit, onRemove: (ConnectedFolder) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(appText(R.string.ui_folders_a421041), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onAdd, enabled = !busy) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(appText(R.string.ui_add_folder_ff55ec8))
            }
        }
        if (folders.isEmpty()) Text(appText(R.string.ui_add_a_folder_to_get_started_a0b931c),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        folders.forEach { folder ->
            var menu by remember(folder.tree) { mutableStateOf(false) }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text(folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (!folder.readable) Text(appText(R.string.ui_access_needed_2d3b115),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !busy) {
                            Icon(Icons.Outlined.MoreVert,
                                appText(R.string.folder_options,folder.name))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(appText(R.string.ui_change_folder_c5c5fec)) },
                                leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) },
                                onClick = { menu = false; onChange(folder) })
                            DropdownMenuItem(text = { Text(appText(R.string.ui_remove_7d27e80), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; onRemove(folder) })
                        }
                    }
                }
            }
        }
    }
}
