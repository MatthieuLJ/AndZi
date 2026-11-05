package com.matthieu.zi_app.data.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlowChineseOffsetsTest {

    @Test
    fun testAll204EpisodesMapped() {
        assertEquals(204, SlowChineseOffsets.OFFSETS.size)
        for (ep in 1..204) {
            val offset = SlowChineseOffsets.OFFSETS[ep]
            assertNotNull("Episode $ep should have offset mapped", offset)
            assertTrue("Intro offset for ep $ep should be positive", offset!!.introOffsetMs > 0)
            assertTrue("Outro offset for ep $ep should be positive", offset.outroOffsetMs > 0)
        }
    }

    @Test
    fun testLookupWithValidEpisode() {
        val ep1 = SlowChineseOffsets.getOffset(1)
        assertEquals(28200, ep1.introOffsetMs)
        assertEquals(42320, ep1.outroOffsetMs)

        // Episode 114 has the long outro music for Faye Wong
        val ep114 = SlowChineseOffsets.getOffset(114)
        assertTrue("Episode 114 should have a long outro offset", ep114.outroOffsetMs > 200_000)
    }

    @Test
    fun testLookupWithNullOrUnknownFallsBackToDefault() {
        val fallbackNull = SlowChineseOffsets.getOffset(null)
        assertEquals(SlowChineseOffsets.DEFAULT, fallbackNull)

        val fallbackUnknown = SlowChineseOffsets.getOffset(999)
        assertEquals(SlowChineseOffsets.DEFAULT, fallbackUnknown)
    }
}

