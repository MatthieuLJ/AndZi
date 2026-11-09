package com.matthieu.zi_app.data.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingChinaOffsetsTest {

    @Test
    fun testAll20EpisodesMapped() {
        assertEquals(20, ReadingChinaOffsets.OFFSETS.size)
        for (ep in 1..20) {
            val offset = ReadingChinaOffsets.OFFSETS[ep]
            assertNotNull("Episode $ep should have offset mapped", offset)
            assertTrue("Intro offset for ep $ep should be positive", offset!!.introOffsetMs > 0)
            assertTrue("Outro offset for ep $ep should be positive", offset.outroOffsetMs > 0)
            assertTrue("Duration for ep $ep should be positive", offset.durationMs > 0)
        }
    }

    @Test
    fun testLookupWithValidEpisode() {
        val ep1 = ReadingChinaOffsets.getOffset(1)
        assertEquals(12800, ep1.introOffsetMs)
        assertEquals(5900, ep1.outroOffsetMs)
        assertEquals(68000, ep1.durationMs)

        val ep2 = ReadingChinaOffsets.getOffset(2)
        assertEquals(4000, ep2.introOffsetMs)
        assertEquals(5900, ep2.outroOffsetMs)
        assertEquals(39900, ep2.durationMs)
    }

    @Test
    fun testLookupWithNullOrUnknownFallsBackToDefault() {
        val fallbackNull = ReadingChinaOffsets.getOffset(null)
        assertEquals(ReadingChinaOffsets.DEFAULT, fallbackNull)

        val fallbackUnknown = ReadingChinaOffsets.getOffset(999)
        assertEquals(ReadingChinaOffsets.DEFAULT, fallbackUnknown)
    }

    @Test
    fun testCleanReadingChinaTranscriptStripsVocabAndGrammar() {
        val rawHtml = """
            “安”的意思是什么？ 你去过天安门吗？你知道汉字“安”的意思是什么吗？
            上汉字课的时候，老师一边说一边在黑板上写了一个“宀”，告诉大家，“宀”在汉字中是“房屋”的意思。
            一个学生回答：“麻烦！”
            大家都笑了。
            老师微笑着说：“如果房子里有个女人，就会平安。所以汉字的‘安’字就是‘宀’下边加一个‘女’字。”
            词语 Words and Expressions
            1. 告诉 v. tell, let know
            2. 房屋 n. house, building
            语言点 Language Points
            一边……一边 …while…; …as…
            “一边……一边……”，关联副词。表示两种以上的动作同时进行。
        """.trimIndent()

        val title = "中文天天读 1 - “安”的意思是什么？"
        val cleaned = PodcastRssParser.cleanReadingChinaTranscript(rawHtml, title)

        // Title line "“安”的意思是什么？" is stripped from the beginning
        assertFalse(cleaned.startsWith("“安”的意思是什么？"))
        assertTrue(cleaned.startsWith("你去过天安门吗？"))

        // Dialogue is preserved
        assertTrue(cleaned.contains("上汉字课的时候，老师一边说一边在黑板上写了一个“宀”"))
        assertTrue(cleaned.contains("老师微笑着说：“如果房子里有个女人，就会平安。所以汉字的‘安’字就是‘宀’下边加一个‘女’字。”"))

        // Vocabulary and grammar notes are stripped
        assertFalse(cleaned.contains("Words and Expressions"))
        assertFalse(cleaned.contains("词语"))
        assertFalse(cleaned.contains("语言点"))
        assertFalse(cleaned.contains("Language Points"))
        assertFalse(cleaned.contains("一边……一边……"))
    }

    @Test
    fun testCleanReadingChinaTranscriptCleansEpisode3WithExclamation() {
        val raw = """
            我很困！ 
            想睡觉的时候有人打扰你怎么办？看看下面这个司机是怎么做的。
            一位汽车司机把车停在路边，想睡一会儿。
            词语 Words and Expressions
            1. 躺 v. lie down
        """.trimIndent()

        val title = "中文天天读 3 - 我很困"
        val cleaned = PodcastRssParser.cleanReadingChinaTranscript(raw, title)

        assertTrue(cleaned.startsWith("想睡觉的时候有人打扰你怎么办？"))
        assertFalse(cleaned.contains("我很困！\n想睡觉"))
        assertFalse(cleaned.contains("Words and Expressions"))
    }
}
