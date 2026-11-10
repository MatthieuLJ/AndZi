package com.matthieu.zi_app.data.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeaTimePodcastAdapterTest {

    @Test
    fun testExtractEpisodeNumber() {
        assertEquals(1, TeaTimePodcastAdapter.extractEpisodeNumber("第01集：让我们开始“茶歇中文”吧", "01-introducing-teatime-chinese"))
        // Title has priority over misleading slug (e.g. 3-proverbs slug for ep 65)
        assertEquals(65, TeaTimePodcastAdapter.extractEpisodeNumber("第65集: 三个关于 “坚持” 的成语故事", "3-chinese-proverbs-on-perseverance"))
        // Title has priority over off-by-one slug (e.g. 29-zhou-chu slug for ep 30)
        assertEquals(30, TeaTimePodcastAdapter.extractEpisodeNumber("第30集: 周处: 人是可以改变的", "29-zhou-chu-a-person-can-always-change-for-the-better"))
        // Fallback to slug if title has no 第XX集
        assertEquals(15, TeaTimePodcastAdapter.extractEpisodeNumber("My Childhood in Yunnan", "15-my-childhood"))
    }

    @Test
    fun testGetSrtUrlForEpisode() {
        assertEquals(
            "https://teatime2026.s3.us-east-1.amazonaws.com/teatimechinese/TTCN+01/TTCN+01.srt",
            TeaTimePodcastAdapter.getSrtUrlForEpisode(1)
        )
        assertEquals(
            "https://teatime2026.s3.us-east-1.amazonaws.com/teatimechinese/TTCN+09/TTCN+09.srt",
            TeaTimePodcastAdapter.getSrtUrlForEpisode(9)
        )
        assertEquals(
            "https://teatime2026.s3.us-east-1.amazonaws.com/teatimechinese/TTCN+10/TTCN+10.srt",
            TeaTimePodcastAdapter.getSrtUrlForEpisode(10)
        )
        assertEquals(
            "https://teatime2026.s3.us-east-1.amazonaws.com/teatimechinese/TTCN+100/TTCN+100.srt",
            TeaTimePodcastAdapter.getSrtUrlForEpisode(100)
        )
    }

    @Test
    fun testParseSrt() {
        val srtSample = """
            1
            00:00:00,000 --> 00:00:01,400
            茶歇中文第一集：

            2
            00:00:02,040 --> 00:00:13,840
            让我们开始茶歇中文

            3
            00:00:13,840 --> 00:00:18,120
            大家好，欢迎来到茶歇中文。
            [BREAK]

            4
            00:01:05,500 --> 00:01:09,200
            我是Nathan。
        """.trimIndent()

        val cues = TeaTimePodcastAdapter.parseSrt(srtSample)
        assertEquals(4, cues.size)

        assertEquals(1, cues[0].index)
        assertEquals(0, cues[0].startMs)
        assertEquals(1400, cues[0].endMs)
        assertEquals("茶歇中文第一集：", cues[0].text)

        assertEquals(2, cues[1].index)
        assertEquals(2040, cues[1].startMs)
        assertEquals(13840, cues[1].endMs)
        assertEquals("让我们开始茶歇中文", cues[1].text)

        assertEquals(3, cues[2].index)
        assertEquals(13840, cues[2].startMs)
        assertEquals(18120, cues[2].endMs)
        assertEquals("大家好，欢迎来到茶歇中文。", cues[2].text)

        assertEquals(4, cues[3].index)
        assertEquals(65500, cues[3].startMs)
        assertEquals(69200, cues[3].endMs)
        assertEquals("我是Nathan。", cues[3].text)
    }
}
