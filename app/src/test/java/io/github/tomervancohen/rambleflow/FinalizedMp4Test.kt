package io.github.tomervancohen.rambleflow

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class FinalizedMp4Test {
    private fun ints(vararg n: Long) =
        ByteBuffer.allocate(n.size * 4)
            .order(ByteOrder.BIG_ENDIAN)
            .apply { n.forEach { putInt(it.toInt()) } }
            .array()

    private fun box(t: String, b: ByteArray) = ints((b.size + 8).toLong()) + t.toByteArray() + b

    private fun fixture(
        open: Boolean = false,
        wrongOffset: Boolean = false,
        wrongCount: Boolean = false,
        fragmented: Boolean = false,
    ): ByteArray {
        val ftyp = box("ftyp", "M4A ".toByteArray() + ints(0) + "M4A mp42".toByteArray())
        val mdat =
            box("mdat", byteArrayOf(1, 2, 3, 4, 5, 6)).also {
                if (open) java.util.Arrays.fill(it, 0, 4, 0.toByte())
            }
        val created = 2_082_844_800L + 1_793_534_400L
        val clock = ints(0, created, created, 16000, 2048)
        val table =
            box("stsd", ints(0, 1) + box("mp4a", ByteArray(28))) +
                box("stsz", ints(0, 0, if (wrongCount) 3 else 2, 3, 3)) +
                box("stts", ints(0, 1, 2, 1024)) +
                box("stsc", ints(0, 1, 1, 2, 1)) +
                box("stco", ints(0, 1, if (wrongOffset) 999999 else (ftyp.size + 8).toLong()))
        val track =
            box(
                "trak",
                box(
                    "mdia",
                    box("mdhd", clock) +
                        box("hdlr", ints(0, 0) + "soun".toByteArray()) +
                        box("minf", box("stbl", table)),
                ),
            )
        return ftyp +
            mdat +
            box(
                "moov",
                box("mvhd", clock) +
                    track +
                    if (fragmented) box("mvex", byteArrayOf()) else byteArrayOf(),
            )
    }

    private fun inspect(bytes: ByteArray): FinalizedMp4.Evidence? {
        val f = File.createTempFile("finalized-", ".m4a")
        return try {
            f.writeBytes(bytes)
            f.inputStream().use { FinalizedMp4.inspect(it.channel) }
        } finally {
            f.delete()
        }
    }

    @Test
    fun closedTableAndMediaProduceEvidence() {
        val b = fixture()
        val e = inspect(b)
        assertNotNull(e)
        assertEquals(b.size.toLong(), e!!.bytes)
        assertEquals(128, e.durationMillis)
        assertEquals(64, e.proof.length)
    }

    @Test
    fun unchangedOpenWriterNeverProducesEvidence() {
        repeat(3) { assertNull(inspect(fixture(open = true))) }
    }

    @Test
    fun truncatedFooterNeverProducesEvidence() {
        val b = fixture()
        for (n in listOf(1, 8, 24)) {
            assertNull(inspect(b.copyOf(b.size - n)))
        }
    }

    @Test
    fun sampleOutsideAudioPayloadRejected() {
        assertNull(inspect(fixture(wrongOffset = true)))
    }

    @Test
    fun inconsistentSampleTableRejected() {
        assertNull(inspect(fixture(wrongCount = true)))
    }

    @Test
    fun fragmentedRecordingCannotBeMistakenForClosed() {
        assertNull(inspect(fixture(fragmented = true)))
    }

    @Test
    fun tableChangesInvalidateProof() {
        val b = fixture()
        val c = b.copyOf()
        val needle = "mvhd".toByteArray()
        val p = (0..b.size - 4).first { b.copyOfRange(it, it + 4).contentEquals(needle) }
        c[p + 8] = (c[p + 8].toInt() xor 1).toByte()
        assertNotEquals(inspect(b)!!.proof, inspect(c)!!.proof)
    }
}
