package com.matthieu.zi_app.data.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlowChineseTranscriptsTest {

    @Test
    fun testEpisode56PatchedTranscript() {
        val transcript = SlowChineseTranscripts.getPatchedTranscript(channelId = 1, episodeNum = 56)
        assertNotNull(transcript)
        assertTrue(transcript!!.contains("中国人吃饭的时候不用刀和叉"))
        assertTrue(transcript.contains("筷子最早起源于中国"))
        assertTrue(transcript.contains("筷子一头大一头小"))
        assertTrue(transcript.contains("虎口"))
        assertTrue(transcript.contains("不能把筷子插在饭碗里"))
        // Check character length is around ~820 characters
        val hanziCount = transcript.filter { it in '\u4e00'..'\u9fff' }.length
        assertTrue("Expected ~800+ Hanzi, got $hanziCount", hanziCount > 750)
    }

    @Test
    fun testUnpatchedEpisodeReturnsNull() {
        val transcript = SlowChineseTranscripts.getPatchedTranscript(channelId = 1, episodeNum = 1)
        assertNull(transcript)
    }

    @Test
    fun testOtherChannelReturnsNull() {
        val transcript = SlowChineseTranscripts.getPatchedTranscript(channelId = 2, episodeNum = 56)
        assertNull(transcript)
    }

    @Test
    fun testPacedSpeechRateWithEpisode56() {
        val transcript = SlowChineseTranscripts.EPISODE_56_TRANSCRIPT
        val timing = SlowChineseOffsets.getOffset(56)

        assertEquals(427570, timing.durationMs)
        assertEquals(26880, timing.introOffsetMs)
        assertEquals(40090, timing.outroOffsetMs)

        val sentences = PacedSpeechRateEngine.computeSentenceTimestamps(
            episodeId = 56,
            transcript = transcript,
            totalDurationMs = timing.durationMs,
            introOffsetMs = timing.introOffsetMs,
            outroOffsetMs = timing.outroOffsetMs
        )

        assertTrue("Should have multiple sentences", sentences.size > 20)
        assertEquals("First sentence should start at intro offset", timing.introOffsetMs, sentences.first().startMs)
        val expectedEndMs = timing.durationMs - timing.outroOffsetMs
        assertEquals("Last sentence should end at speech boundary", expectedEndMs, sentences.last().endMs)
    }
}

