package io.github.tomervancohen.rambleflow

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure policies used by storage, workers and tests; no arrival-time substitution. */
object Policy {
    const val AUDIO_BUDGET = 2L * 1024 * 1024 * 1024

    fun canStageAudio(used: Long, extra: Long) =
        used >= 0 && extra >= 0 && extra <= AUDIO_BUDGET && used <= AUDIO_BUDGET - extra

    const val MAX_UPLOAD = 24_000_000L
    const val CHUNK_SAMPLES = 16_000 * 300
    const val MAX_SOURCE = 2_000_000_000L
    const val MAX_DURATION_MS = 8 * 60 * 60 * 1000L

    fun eligible(baseline: Boolean, recordedAt: Long?, cutoff: Long) =
        !baseline &&
            recordedAt != null &&
            recordedAt > cutoff &&
            recordedAt <= System.currentTimeMillis() + 60_000

    fun safeName(id: String) = "ramble-${id.replace(Regex("[^a-zA-Z0-9-]"), "")}.txt"

    fun disposition(code: Int): String =
        when (code) {
            401,
            403 -> "key"
            429 -> "rate"
            in 200..299 -> "ok"
            in 400..499 -> if (code == 408) "uncertain" else "rejected"
            else -> "uncertain"
        }

    fun prompt(english: Boolean, folder: String): String {
        require(folder.matches(Regex("[a-zA-Z0-9_-]+")))
        val url = "https://drive.google.com/drive/folders/$folder"
        return if (english)
            "My recordings are transcribed in the Ramble folder on Google Drive:\n$url\nRemember this. From now on, when I ask about my conversations or meetings, use these transcripts and cite the source."
        else
            "תמלולי ההקלטות שלי נמצאים בתיקיית Ramble ב־Google Drive:\n$url\nשמור את זה בזיכרון שלך. מעתה והלאה, כשאשאל על שיחות או פגישות שלי, השתמש בתמלולים וציין את המקור."
    }

    // Only explicit date patterns, never an arbitrary digit substring or file-modification time.
    fun filenameDate(name: String, zone: ZoneId): Long? {
        val match =
            Regex("(?:^|[_ ])(20\\d{2}[01]\\d[0-3]\\d)[_ ]([0-2]\\d[0-5]\\d[0-5]\\d)(?:[_. ]|$)")
                .find(name)
                ?: Regex("(?:^|[_ ])(\\d{6})[_ ]([0-2]\\d[0-5]\\d[0-5]\\d)(?:[_. ]|$)").find(name)
                ?: return null
        return runCatching {
            val local =
                LocalDateTime.parse(
                    (if (match.groupValues[1].length == 6) "20" else "") +
                        match.groupValues[1] +
                        match.groupValues[2],
                    DateTimeFormatter.ofPattern("uuuuMMddHHmmss", Locale.ROOT)
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT),
                )
            val offsets = zone.rules.getValidOffsets(local)
            if (offsets.size != 1) null else local.toInstant(offsets[0]).toEpochMilli()
        }
            .getOrNull()
    }
}
