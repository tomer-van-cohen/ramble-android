package io.github.tomervancohen.rambleflow

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    var showsLiveRecording = false
        private set
    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppLanguage.wrap(base)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showsLiveRecording = intent.getBooleanExtra("ramble.live.recording", false)
        val night =
            resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        applySystemBars(night)
        setContent { RambleTheme { RambleFlow(application as RambleApp) } }
        Schedule.scan(this)
    }
    internal fun applySystemBars(night: Boolean) {
        val barColor = android.graphics.Color.parseColor(if (night) "#1C1E1D" else "#F4F6F4")
        enableEdgeToEdge(
            statusBarStyle =
                if (night) SystemBarStyle.dark(barColor)
                else SystemBarStyle.light(barColor, barColor),
            navigationBarStyle =
                if (night) SystemBarStyle.dark(barColor)
                else SystemBarStyle.light(barColor, barColor),
        )
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(barColor))
        window.isNavigationBarContrastEnforced = false
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }

}

