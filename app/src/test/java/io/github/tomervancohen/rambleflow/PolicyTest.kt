package io.github.tomervancohen.rambleflow

import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    @Test
    fun samsungShortDateIsParsed() {
        assertEquals(
            1793536200000L,
            Policy.filenameDate("Voice_261101_123000.m4a", java.time.ZoneId.of("UTC")),
        )
    }

    @Test
    fun stagingCannotExceedBudgetOrOverflow() {
        assertTrue(Policy.canStageAudio(0, 1024))
        assertFalse(Policy.canStageAudio(Policy.AUDIO_BUDGET, 1))
        assertFalse(Policy.canStageAudio(1, Long.MAX_VALUE))
        assertFalse(Policy.canStageAudio(-1, 100))
    }

    @Test
    fun archiveNeverEligible() {
        assertFalse(Policy.eligible(true, 200, 100))
        assertFalse(Policy.eligible(false, 99, 100))
        assertFalse(Policy.eligible(false, null, 100))
        assertTrue(Policy.eligible(false, 200, 100))
    }

    @Test
    fun exactCutoffExcluded() {
        assertFalse(Policy.eligible(false, 100, 100))
    }

    @Test
    fun invalidDatesNeverBecomeArrivalTime() {
        assertNull(Policy.filenameDate("memo.m4a", ZoneId.of("UTC")))
        assertNull(Policy.filenameDate("Voice_20260230_123000.m4a", ZoneId.of("UTC")))
    }

    @Test
    fun knownSamsungDateParsed() {
        assertEquals(
            1793536200000L,
            Policy.filenameDate("Voice_20261101_123000.m4a", ZoneId.of("UTC")),
        )
    }

    @Test
    fun ambiguousDstNotGuessed() {
        assertNull(Policy.filenameDate("Voice_20261025_033000.m4a", ZoneId.of("Europe/Athens")))
    }

    @Test
    fun noAutomaticRetryForAmbiguousOutcome() {
        listOf(408, 500, 502, 503, 504).forEach {
            assertEquals("uncertain", Policy.disposition(it))
        }
        assertEquals("rate", Policy.disposition(429))
        assertEquals("key", Policy.disposition(401))
    }

    @Test
    fun outputNameCannotTraverse() {
        assertFalse(Policy.safeName("../../bad\u202e").contains(".."))
        assertFalse(Policy.safeName("a/b").contains('/'))
    }

    @Test
    fun onlyActualDriveIdsInAgentPrompt() {
        assertTrue(
            Policy.prompt(true, "abc_123")
                .contains("https://drive.google.com/drive/folders/abc_123")
        )
        assertTrue(Policy.prompt(false, "abc").contains("שיחות או פגישות"))
        assertFalse(Policy.prompt(true, "abc").contains("not found"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun urlInjectionRejected() {
        Policy.prompt(true, "abc?share=public")
    }

    @Test
    fun chunkBytesStayBelowProviderLimit() {
        assertTrue(Policy.CHUNK_SAMPLES * 2L + 44 < Policy.MAX_UPLOAD)
    }
}
