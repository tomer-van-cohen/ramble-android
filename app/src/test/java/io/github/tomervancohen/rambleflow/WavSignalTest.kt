package io.github.tomervancohen.rambleflow

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class WavSignalTest {
    private fun wav(samples: ShortArray, check: (File) -> Unit) {
        val bytes = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray())
        buffer.putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
        buffer.putShort(2).putShort(16).put("data".toByteArray()).putInt(bytes)
        samples.forEach { buffer.putShort(it) }
        val file = File.createTempFile("signal-", ".wav")
        try { file.writeBytes(buffer.array()); check(file) } finally { file.delete() }
    }
    @Test fun digitalSilenceIsRejected() = wav(ShortArray(24000)) {
        assertTrue(WavSignal.isDigitalSilence(it))
    }
    @Test fun shortQuietSignalIsRetained() = wav(shortArrayOf(0, 0, 20, -20, 0)) {
        assertFalse(WavSignal.isDigitalSilence(it))
    }
    @Test fun negativePeakDoesNotOverflow() = wav(shortArrayOf(Short.MIN_VALUE)) {
        assertFalse(WavSignal.isDigitalSilence(it))
    }
    @Test fun unknownFileIsNotClassifiedAsSilence() {
        val file = File.createTempFile("unknown-", ".wav")
        try { file.writeBytes(ByteArray(44)); assertFalse(WavSignal.isDigitalSilence(file)) }
        finally { file.delete() }
    }
}
