package io.github.tomervancohen.rambleflow

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import kotlinx.coroutines.*

/** A share grants access to this one file; it does not grant access to the Phone library. */
class ReceiveAudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (RecorderDevice.current().family != RecorderFamily.PIXEL) { finish(); return }
        val app = application as RambleApp
        val uri = if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true)
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) else null
        setContent { RambleTheme {
            SharedAudioPage(app, uri, onClose = { finish() }, onOpenApp = {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            })
        } }
    }
}

/** Shared production UI, also rendered with synthetic audio in emulator walkthroughs. */
@Composable
internal fun SharedAudioPage(app: RambleApp, uri: Uri?, onClose: () -> Unit, onOpenApp: () -> Unit) {
                var staged by remember { mutableStateOf<SharedAudio.Staged?>(null) }
                var error by remember { mutableStateOf(false) }
                var busy by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(uri) {
                    try {
                        require(uri != null)
                        staged = withContext(Dispatchers.IO) {
                            withTimeout(120_000) { SharedAudio(app).stage(uri) }
                        }
                    } catch (e: CancellationException) {
                        if (e is TimeoutCancellationException) error = true else throw e
                    } catch (_: Exception) { error = true }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(appText(R.string.ui_transcribe_shared_audio_95496b1), style = MaterialTheme.typography.headlineLarge)
                        Text(appText(R.string.ui_audio_will_go_to_your_selected_transcription__072f7a0))
                        if (error) Text(appText(R.string.ui_couldn_t_read_this_audio_return_to_the_call_a_2174881))
                        else if (staged == null || busy) CircularProgressIndicator()
                        else if (!app.settings.hasKey(app.settings.provider)) {
                            Text(appText(R.string.ui_set_up_your_transcription_provider_first_then_de17462))
                            Button(onClick = { onOpenApp() }) {
                                Text(appText(R.string.ui_set_up_ramble_1224fe1))
                            }
                        } else Button(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { SharedAudio(app).submit(staged!!) }
                                    Schedule.pump(app)
                                    onOpenApp()
                                } catch (e: CancellationException) { throw e }
                                catch (_: Exception) { error = true; busy = false }
                            }
                        }) { Text(appText(R.string.ui_transcribe_5c07ff5)) }
                        TextButton(onClick = onClose) { Text(appText(R.string.ui_close_2873260)) }
                    }
                }
}
