package io.github.tomervancohen.rambleflow

import java.io.File
import java.io.RandomAccessFile

/** Conservative digital-silence check for our PCM16 mono WAV upload format.
 * Unknown encodings are left to the provider. No minimum speech/recording duration is imposed.
 * This is not a voice-activity detector: background noise may still contain signal.
 */
internal object WavSignal {
    fun isDigitalSilence(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { input ->
            fun tag(): String = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
            fun uint(): Long = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
            fun ushort(): Int = java.lang.Short.reverseBytes(input.readShort()).toInt() and 0xffff
            if (input.length() < 44 || tag() != "RIFF") return@use false
            uint()
            if (tag() != "WAVE") return@use false
            var pcm = false
            while (input.filePointer + 8 <= input.length()) {
                val tag = tag()
                val bytes = uint()
                val start = input.filePointer
                if (bytes > input.length() - start) return@use false
                if (tag == "fmt " && bytes >= 16) {
                    val encoding = ushort()
                    val channels = ushort()
                    uint(); uint(); ushort()
                    val bits = ushort()
                    pcm = encoding == 1 && channels == 1 && bits == 16
                }
                if (tag == "data") {
                    if (!pcm || bytes < 2 || bytes % 2 != 0L) return@use false
                    var remaining = bytes
                    val buffer = ByteArray(8192)
                    while (remaining > 0) {
                        val count = minOf(buffer.size.toLong(), remaining).toInt()
                        input.readFully(buffer, 0, count)
                        for (i in 0 until count step 2) {
                            val sample = ((buffer[i].toInt() and 255) or
                                (buffer[i + 1].toInt() shl 8)).toShort().toInt()
                            if (kotlin.math.abs(sample) > 1) return@use false
                        }
                        remaining -= count
                    }
                    return@use true
                }
                input.seek(start + bytes + bytes % 2)
            }
            false
        }
    }.getOrDefault(false)
}
