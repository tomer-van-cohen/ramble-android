package io.github.tomervancohen.rambleflow

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Official product artwork; illustrative assistant choices, not dedicated integrations. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentMarks() {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        for ((name, resource) in listOf(
            "ChatGPT" to R.drawable.agent_chatgpt,
            "Claude" to R.drawable.agent_claude,
            "Gemini" to R.drawable.agent_gemini,
            "Muse" to R.drawable.agent_muse,
            "Grok Bot" to R.drawable.agent_grokbot,
            "Instinct" to R.drawable.agent_instinct,
        )) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(resource), contentDescription = name,
                    modifier = Modifier.size(if (name == "Muse") 36.dp else 32.dp))
            }
        }
    }
}
