package io.github.tomervancohen.rambleflow

import java.io.File

/** A database reset must also remove the synthetic originals that recovery would otherwise discover. */
internal object SyntheticAudioFixtures {
    fun clear(app: RambleApp) {
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        for(name in listOf("recordings","shared-audio","audio")) File(app.noBackupFilesDir,name).deleteRecursively()
        app.settings.quickPending=""
        app.settings.keepAudio=true
    }
}
