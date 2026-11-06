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
internal fun WelcomePage(modifier: Modifier = Modifier, onStart: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                BrandWave(Modifier.fillMaxWidth().height(160.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val headline = appText(R.string.ui_talk_freely_your_ai_stays_in_the_loop_6719342)
                    val measurer = rememberTextMeasurer()
                    val density = LocalDensity.current
                    val base = MaterialTheme.typography.headlineLarge
                    val width = with(density) { maxWidth.toPx() }
                    val size = remember(headline, width, density.fontScale, measurer, base) {
                        (32 downTo 10).firstOrNull { candidate ->
                            measurer.measure(headline.substringAfter('\n'), style = base.copy(fontSize = candidate.sp), softWrap = false).size.width <= width - 2
                        } ?: 10
                    }
                    Text(headline, Modifier.fillMaxWidth().testTag("welcome-headline"),
                        style = base.copy(fontSize = size.sp, lineHeight = (size * 1.25f).sp),
                        textAlign = TextAlign.Center, maxLines = 2, softWrap = false)
                }
                Text(
                    appText(R.string.ui_ramble_automatically_turns_your_recordings_in_8e0ebb8),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Action(appText(R.string.ui_get_started_6f5b4d5), onClick = onStart)
    }
}
