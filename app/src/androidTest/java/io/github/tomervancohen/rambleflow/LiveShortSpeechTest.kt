package io.github.tomervancohen.rambleflow

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in only: two short synthetic speech clips sent to the configured provider. */
class LiveShortSpeechTest {
    @Test fun shortHebrewAndEnglishReachConfiguredProvider() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rambleLiveSpeech") == "true")
        val app = ApplicationProvider.getApplicationContext<RambleApp>()
        val provider = app.settings.profile(app.settings.provider)
        val key = app.settings.readKey(provider)
        require(key.isNotBlank() && !key.startsWith("synthetic")) { "A configured live key is required" }
        for ((language, expected) in listOf("he" to "שלום", "en" to "hello")) {
            val file = File(app.cacheDir, "live-short-$language.wav")
            try {
                InstrumentationRegistry.getInstrumentation().context.assets
                    .open("speech/short-$language.wav").use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                // Three seconds or less, without padding to manufacture a longer sample.
                assertTrue(file.length() < 3 * 32000 + 44)
                val text = ProviderApi(language = { language }).transcribe(provider, key, file)
                assertTrue("Short $language speech should be recognized", text.lowercase().contains(expected))
            } finally { file.delete() }
        }
    }
    @Test fun silenceDoesNotBecomeASuccessfulTranscript() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rambleLiveSpeech") == "true")
        val app = ApplicationProvider.getApplicationContext<RambleApp>()
        val provider = app.settings.profile(app.settings.provider)
        val key = app.settings.readKey(provider)
        require(key.isNotBlank() && !key.startsWith("synthetic"))
        val file = File(app.cacheDir, "live-silence.wav")
        try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("speech/silence.wav")
                .use { input -> file.outputStream().use { input.copyTo(it) } }
            try {
                ProviderApi().transcribe(provider, key, file)
                fail("Silence must not produce a success")
            } catch (e: RemoteFailure) { assertEquals("no_speech", e.kind) }
        } finally { file.delete() }
    }

}
