package io.github.tomervancohen.rambleflow

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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

private val Brand = Color(0xFF25D366)
private val Ink = Color(0xFF121212)

@Composable
fun RambleTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val mainActivity = LocalActivity.current as? MainActivity
    SideEffect { mainActivity?.applySystemBars(dark) }
    val scheme =
        if (dark)
            darkColorScheme(
                primary = Brand,
                onPrimary = Ink,
                background = Color(0xFF1C1E1D),
                surface = Color(0xFF262928),
                surfaceVariant = Color(0xFF303433),
                onBackground = Color(0xFFF1F3F2),
                onSurface = Color(0xFFF1F3F2),
                onSurfaceVariant = Color(0xFFABB2AF),
                secondary = Color(0xFF3DDC84),
                secondaryContainer = Color(0xFF303433),
                onSecondaryContainer = Color(0xFFF1F3F2),
                error = Color(0xFFFF6B61),
                outlineVariant = Color(0xFF3C413F),
                outline = Color(0xFF5A615E),
            )
        else
            lightColorScheme(
                primary = Brand,
                onPrimary = Ink,
                background = Color(0xFFF4F6F4),
                surface = Color.White,
                surfaceVariant = Color(0xFFEDF0EE),
                onBackground = Color(0xFF151817),
                onSurface = Color(0xFF151817),
                onSurfaceVariant = Color(0xFF5B625F),
                secondary = Color(0xFF0F7A3E),
                secondaryContainer = Color(0xFFEDF0EE),
                onSecondaryContainer = Color(0xFF151817),
                error = Color(0xFFC8231F),
                outlineVariant = Color(0xFFD6DBD8),
                outline = Color(0xFFAEB6B2),
            )
    MaterialTheme(
        colorScheme = scheme,
        typography =
            Typography(
                bodyLarge =
                    androidx.compose.ui.text.TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
                bodyMedium =
                    androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                headlineLarge =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 28.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            ),
        content = content,
    )
}

@Composable
internal fun Action(
    label: String,
    enabled: Boolean = true,
    secondary: Boolean = false,
    onClick: () -> Unit,
) {
    if (secondary)
        TextButton(
            onClick,
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = enabled,
            colors =
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
        ) {
            Text(
                label,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                textDecoration = TextDecoration.Underline,
            )
        }
    else
        Button(
            onClick,
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                label,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
        }
}

@Composable
internal fun Panel(
    title: String,
    body: String = "",
    selected: Boolean = false,
    disabled: Boolean = false,
    icon: Int? = null,
    infoLabel: String? = null,
    onInfoClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val color =
        if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    Card(
        modifier =
            Modifier.fillMaxWidth()
                .then(if (disabled) Modifier.semantics { disabled() } else Modifier)
                .then(
                    if (onClick != null) Modifier.clickable(enabled = !disabled, onClick = onClick)
                    else Modifier
                )
                .border(
                    if (selected) 2.dp else 1.dp,
                    color.copy(alpha = if (disabled) .35f else 1f),
                    RoundedCornerShape(20.dp),
                ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(painterResource(icon), null, Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
                        color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    if (infoLabel != null && onInfoClick != null) Text(infoLabel,
                        Modifier.clickable(onClick = onInfoClick).padding(vertical = 6.dp),
                        fontSize = 13.sp, textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (body.isNotBlank()) Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun RowLink(title: String, subtitle: String = "", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp)
            if (subtitle.isNotBlank())
                Text(subtitle, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
}

