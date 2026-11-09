package com.matthieu.zi_app.data.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacedSpeechRateEngineTest {

    @Test
    fun testSplitTranscriptIntoSentences() {
        val transcript = "中国农历的五月五日是一个重要的节日，叫做端午节。它是一个古老的传统节日，有两千多年的历史。祝大家端午安康！"
        val sentences = PacedSpeechRateEngine.splitTranscriptIntoSentences(transcript)

        assertEquals(3, sentences.size)
        assertEquals("中国农历的五月五日是一个重要的节日，叫做端午节。", sentences[0])
        assertEquals("它是一个古老的传统节日，有两千多年的历史。", sentences[1])
        assertEquals("祝大家端午安康！", sentences[2])
    }

    @Test
    fun testCalculateSentenceWeightAccountsForPunctuation() {
        val plainText = "端午节"
        val commaText = "端午节，"
        val periodText = "端午节。"

        val plainWeight = PacedSpeechRateEngine.calculateSentenceWeight(plainText)
        val commaWeight = PacedSpeechRateEngine.calculateSentenceWeight(commaText)
        val periodWeight = PacedSpeechRateEngine.calculateSentenceWeight(periodText)

        assertEquals(3.0, plainWeight, 0.001)
        assertEquals(3.0 + PacedSpeechRateEngine.WEIGHT_PAUSE, commaWeight, 0.001)
        assertEquals(3.0 + PacedSpeechRateEngine.WEIGHT_PERIOD, periodWeight, 0.001)
    }

    @Test
    fun testComputeSentenceTimestampsDistributesDuration() {
        val transcript = "句子一。句子二长一点，有两个分句。句子三！"
        val totalDurationMs = 60_000 // 60s
        val introOffsetMs = 10_000   // 10s
        val outroOffsetMs = 5_000    // 5s
        // Active dialogue = 45s (10s to 55s)

        val sentences = PacedSpeechRateEngine.computeSentenceTimestamps(
            episodeId = 1,
            transcript = transcript,
            totalDurationMs = totalDurationMs,
            introOffsetMs = introOffsetMs,
            outroOffsetMs = outroOffsetMs
        )

        assertEquals(3, sentences.size)

        // Sentence 0 starts at introOffsetMs
        assertEquals(10_000, sentences[0].startMs)
        // Sentences are monotonically increasing
        assertTrue(sentences[0].endMs <= sentences[1].startMs)
        assertTrue(sentences[1].endMs <= sentences[2].startMs)
        // Last sentence ends at totalDurationMs - outroOffsetMs
        assertEquals(totalDurationMs - outroOffsetMs, sentences[2].endMs)
    }

    @Test
    fun testSplitTranscriptPreservesTrailingQuotes() {
        val transcript = "他说：“我们一起去吧！”她回答：“好啊。”"
        val sentences = PacedSpeechRateEngine.splitTranscriptIntoSentences(transcript)

        assertEquals(2, sentences.size)
        assertEquals("他说：“我们一起去吧！”", sentences[0])
        assertEquals("她回答：“好啊。”", sentences[1])
    }

    @Test
    fun testResolveOffsetsForSlowChinese() {
        val ep1 = PacedSpeechRateEngine.resolveOffsets(
            channelId = 1,
            episodeNum = 1,
            channelFallbackIntroMs = 3000,
            channelFallbackOutroMs = 4000
        )
        // Ep 1 measured intro is 28200ms
        assertEquals(28200, ep1.introOffsetMs)
        assertEquals(42320, ep1.outroOffsetMs)
    }

    @Test
    fun testResolveOffsetsForReadingChina() {
        val ep1 = PacedSpeechRateEngine.resolveOffsets(
            channelId = 2,
            episodeNum = 1,
            channelFallbackIntroMs = 5000,
            channelFallbackOutroMs = 6000
        )
        assertEquals(12800, ep1.introOffsetMs)
        assertEquals(5900, ep1.outroOffsetMs)
        assertEquals(68000, ep1.durationMs)

        val ep2 = PacedSpeechRateEngine.resolveOffsets(
            channelId = 2,
            episodeNum = 2,
            channelFallbackIntroMs = 5000,
            channelFallbackOutroMs = 6000
        )
        assertEquals(4000, ep2.introOffsetMs)
        assertEquals(5900, ep2.outroOffsetMs)
    }

    @Test
    fun testResolveOffsetsForMaoMiAndFallback() {
        val maomi = PacedSpeechRateEngine.resolveOffsets(
            channelId = 3,
            episodeNum = 1,
            channelFallbackIntroMs = 12000,
            channelFallbackOutroMs = 16000
        )
        assertEquals(12000, maomi.introOffsetMs)
        assertEquals(16000, maomi.outroOffsetMs)

        val unknown = PacedSpeechRateEngine.resolveOffsets(
            channelId = 99,
            episodeNum = 1,
            channelFallbackIntroMs = 3000,
            channelFallbackOutroMs = 4000
        )
        assertEquals(3000, unknown.introOffsetMs)
        assertEquals(4000, unknown.outroOffsetMs)
    }
}

