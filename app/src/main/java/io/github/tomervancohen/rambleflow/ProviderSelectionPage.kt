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
internal fun ProviderSelectionPage(candidate: String, onProvider: (String) -> Unit, onInfo: (String) -> Unit) {

                    Text(
                        appText(R.string.ui_who_will_transcribe_45b4972),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        appText(R.string.ui_bring_your_own_key_2ac255b),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Panel(
                        "Groq",
                        appText(R.string.ui_recommended_free_tier_available_cd6bf52),
                        candidate == "groq",
                        icon = R.drawable.provider_groq,
                        infoLabel = appText(R.string.ui_more_information_21fd399),
                        onInfoClick = { onInfo("https://console.groq.com/docs/model/whisper-large-v3") },
                    ) {
                        onProvider("groq")
                    }
                    Panel(
                        "OpenAI",
                        appText(R.string.ui_high_quality_transcription_usage_based_pricin_f1aac75),
                        candidate == "openai",
                        icon = R.drawable.agent_chatgpt,
                        infoLabel = appText(R.string.ui_more_info_pricing_79003d6),
                        onInfoClick = { onInfo("https://developers.openai.com/api/docs/models/gpt-transcribe") },
                    ) {
                        onProvider("openai")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
                    Panel(
                        appText(R.string.ui_let_ramble_transcribe_5c866b7),
                        appText(R.string.ui_coming_soon_no_key_needed_0006635),
                        disabled = true,
                    )

}
