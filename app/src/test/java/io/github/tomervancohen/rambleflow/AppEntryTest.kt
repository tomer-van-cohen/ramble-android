package io.github.tomervancohen.rambleflow

import org.junit.Assert.*
import org.junit.Test

class AppEntryTest {
    @Test fun externalExtrasNeverSkipSetup() {
        for (step in 0..9) for (key in listOf(false, true)) {
            val expected = if (step == 4) "3" else step.toString()
            assertEquals(expected, AppEntry.screen(step, key, live = true, language = true))
            assertFalse(AppEntry.canRecord(step, key))
        }
    }
    @Test fun missingKeyRequiresRepairEvenAfterSetup() {
        assertEquals("key", AppEntry.screen(10, false, live = true))
        assertFalse(AppEntry.canRecord(10, false))
    }
    @Test fun configuredUsersKeepNormalAndQuickEntrypoints() {
        assertEquals("home", AppEntry.screen(10, true))
        assertEquals("recording-live", AppEntry.screen(10, true, live = true))
        assertEquals("settings", AppEntry.screen(10, true, language = true))
        assertTrue(AppEntry.canRecord(10, true))
    }
}
