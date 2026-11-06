package io.github.tomervancohen.rambleflow

import org.junit.Assert.*
import org.junit.Test

class RecorderDeviceTest {
    @Test
    fun samsungUsesSideButtonAndSharedRecordingParent() {
        val d = RecorderDevice.forManufacturer("Samsung")
        assertEquals(RecorderFamily.SAMSUNG, d.family)
        assertEquals("Recordings", d.suggestedFolder)
        assertEquals(listOf("com.sec.android.app.voicenote"), d.packages)
    }

    @Test
    fun pixelUsesOwnRecorderAndXiaomiBrandsShareFallback() {
        val pixel = RecorderDevice.forManufacturer("Google")
        assertEquals(RecorderFamily.PIXEL, pixel.family)
        assertTrue(pixel.packages.contains("com.google.android.apps.recorder"))
        for (name in listOf("Xiaomi", "Redmi", "POCO")) {
            assertEquals(RecorderFamily.XIAOMI, RecorderDevice.forManufacturer(name).family)
        }
        assertEquals(RecorderFamily.OTHER, RecorderDevice.forManufacturer("unknown").family)
    }
}
