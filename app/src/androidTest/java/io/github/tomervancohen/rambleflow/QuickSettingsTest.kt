package io.github.tomervancohen.rambleflow

import org.junit.Assert.*
import org.junit.Test

class QuickSettingsTest {
    private val samsung = RecorderDevice.forManufacturer("Samsung")
    @Test fun startsWithSideButtonWithoutOpeningGeneralSettings() {
        val seen = mutableListOf<android.content.Intent>()
        assertEquals(QuickRecordingSettings.Destination.SIDE_BUTTON,
            QuickRecordingSettings.launch(samsung) { seen += it; true })
        assertEquals(1, seen.size)
        assertTrue(seen.single().component!!.className.endsWith("FunctionKeySettingsActivity"))
    }
    @Test fun unavailableSidePageFallsBackToAdvancedFeatures() {
        val seen = mutableListOf<android.content.Intent>()
        assertEquals(QuickRecordingSettings.Destination.ADVANCED_FEATURES,
            QuickRecordingSettings.launch(samsung) { seen += it; seen.size == 2 })
        assertTrue(seen.last().component!!.className.endsWith("UsefulFeatureMainActivity"))
    }
    @Test fun generalSettingsIsLastResort() {
        val seen = mutableListOf<android.content.Intent>()
        assertEquals(QuickRecordingSettings.Destination.SETTINGS,
            QuickRecordingSettings.launch(samsung) { seen += it; it.action == android.provider.Settings.ACTION_SETTINGS })
        assertEquals(3, seen.size)
    }
}
