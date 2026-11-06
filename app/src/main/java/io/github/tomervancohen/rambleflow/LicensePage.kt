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
internal fun LicensePage(context: Context) {

                    Text("PolyForm Noncommercial 1.0.0", style = MaterialTheme.typography.headlineLarge)
                    Text(appText(R.string.ui_noncommercial_use_under_the_license_terms_the_53209f5))
                    Text(remember { context.assets.open("NOTICE.txt").bufferedReader().use { it.readText() } }, style = MaterialTheme.typography.bodySmall)
                    Text(remember { context.assets.open("LICENSE.txt").bufferedReader().use { it.readText() } }, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))

}
