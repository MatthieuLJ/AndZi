package com.matthieu.zi_app.data.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaoMiPodcastAdapterTest {

    @Test
    fun testCleanTranscriptFromModernMmeHtml() {
        val html = """
            <div class="mme-transcript-body mme-lang-zh">
                <p>大家好！欢迎回来 MaoMi Chinese！</p>
                <p>&nbsp;</p>
                <p>今天我们来聊一聊中国的数字人。数字人会取代我们吗？</p>
            </div>
            <div class="mme-transcript-body mme-lang-en">
                <p>Hello everyone! Welcome back to MaoMi Chinese!</p>
                <p>Today let's talk about digital clones.</p>
            </div>
        """.trimIndent()

        val cleaned = MaoMiPodcastAdapter.cleanTranscriptFromHtml(html)
        assertTrue(cleaned.contains("大家好！欢迎回来 MaoMi Chinese！"))
        assertTrue(cleaned.contains("今天我们来聊一聊中国的数字人。"))
        assertFalse("Should not include English div text", cleaned.contains("Hello everyone!"))
    }

    @Test
    fun testCleanTranscriptFromElementorTabs() {
        val html = """
            <div id="chinese-tab" class="clearfix eael-tab-content-item active-default" data-title-link="chinese-tab">
                <p style="font-weight: 400">大家好！欢迎回来MaoMi Chinese！</p>
                <p>&nbsp;</p>
                <p style="font-weight: 400">最近&#8211;有听众让我聊一聊小红书。</p>
            </div>
            <div id="english-tab" class="clearfix eael-tab-content-item" data-title-link="english-tab">
                <p>Hello everyone! Welcome back!</p>
            </div>
        """.trimIndent()

        val cleaned = MaoMiPodcastAdapter.cleanTranscriptFromHtml(html)
        assertTrue(cleaned.contains("大家好！欢迎回来MaoMi Chinese！"))
        assertTrue(cleaned.contains("最近–有听众让我聊一聊小红书。"))
        assertFalse(cleaned.contains("Hello everyone!"))
    }

    @Test
    fun testCleanTranscriptFromFallbackParagraphs() {
        val html = """
            <div class="elementor-widget-container">
                <p>Xiaohongshu: a Chinese social media platform.</p>
                <p>MaoMi Chinese 是一个帮助大家学中文的播客。</p>
                <p>中国有很多好玩的城市。</p>
                <p>支持我们请加入会员。</p>
                <p>去过北京的人都觉得故宫很壮观。</p>
            </div>
        """.trimIndent()

        val cleaned = MaoMiPodcastAdapter.cleanTranscriptFromHtml(html)
        assertTrue(cleaned.contains("中国有很多好玩的城市。"))
        assertTrue(cleaned.contains("去过北京的人都觉得故宫很壮观。"))
        assertFalse("Should filter out boilerplate", cleaned.contains("加入会员"))
        assertFalse("Should filter out boilerplate", cleaned.contains("是一个帮助大家学中文的播客"))
        assertFalse("Should filter out pure english paragraphs", cleaned.contains("Chinese social media platform"))
    }

    @Test
    fun testConstantsAndTiming() {
        assertEquals(3, MaoMiPodcastAdapter.CHANNEL_ID)
        assertEquals(12000, MaoMiPodcastAdapter.DEFAULT_INTRO_OFFSET_MS)
        assertEquals(16000, MaoMiPodcastAdapter.DEFAULT_OUTRO_OFFSET_MS)

        val timing = PacedSpeechRateEngine.resolveOffsets(
            channelId = 3,
            episodeNum = 1,
            channelFallbackIntroMs = 0,
            channelFallbackOutroMs = 0
        )
        assertEquals(12000, timing.introOffsetMs)
        assertEquals(16000, timing.outroOffsetMs)
    }
}
