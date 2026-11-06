package io.github.tomervancohen.rambleflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

@Composable
fun AgentInstructionsPage(
    english: Boolean,
    text: String?,
    copied: Boolean,
    onLanguage: (Boolean) -> Unit,
    onCopy: () -> Unit,
) {
    Text(appText(R.string.ui_say_hi_to_your_ai_0a76b76),
        style = MaterialTheme.typography.headlineLarge)
    Text(appText(R.string.ui_copy_this_to_your_ai_assistant_3d63901))
    if (text != null) {
        Column(Modifier.fillMaxWidth()) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                PrimaryTabRow(selectedTabIndex = if (english) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.secondary) {
                    Tab(selected = english, onClick = { onLanguage(true) }, text = { Text("English") },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    Tab(selected = !english, onClick = { onLanguage(false) }, text = { Text("עברית") },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onCopy) {
                            Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (copied) appText(R.string.ui_copied_e67845b) else appText(R.string.ui_copy_5d3eebd),
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(text, Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = if (english) TextDirection.Ltr else TextDirection.Rtl,
                            textAlign = if (english) TextAlign.Left else TextAlign.Right))
                }
            }
        }
    }
    AgentMarks()
}
