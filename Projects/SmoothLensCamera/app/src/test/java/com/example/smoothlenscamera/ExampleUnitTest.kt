package com.example.smoothlenscamera

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun formatsRecordingDuration() {
        assertEquals("00:14", formatDuration(14_000L))
        assertEquals("01:05", formatDuration(65_000L))
    }
}
