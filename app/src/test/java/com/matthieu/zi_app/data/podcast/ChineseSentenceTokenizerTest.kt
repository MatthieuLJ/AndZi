package com.matthieu.zi_app.data.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseSentenceTokenizerTest {

    @Test
    fun testEmptySentence() {
        val tokens = ChineseSentenceTokenizer.tokenize("")
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun testOnlyPunctuation() {
        val sentence = "！？，。……"
        val tokens = ChineseSentenceTokenizer.tokenize(sentence)
        assertEquals(1, tokens.size)
        assertEquals(sentence, tokens[0].text)
        assertFalse(tokens[0].isHanzi)
        assertEquals(0, tokens[0].startIndex)
        assertEquals(sentence.length, tokens[0].endIndex)
    }

    @Test
    fun testSingleCharactersWithoutKnownWords() {
        val sentence = "不用刀和叉。"
        val tokens = ChineseSentenceTokenizer.tokenize(sentence)
        // Expected: "不", "用", "刀", "和", "叉", "。"
        assertEquals(6, tokens.size)
        assertEquals("不", tokens[0].text); assertTrue(tokens[0].isHanzi); assertFalse(tokens[0].isCompound)
        assertEquals("用", tokens[1].text); assertTrue(tokens[1].isHanzi); assertFalse(tokens[1].isCompound)
        assertEquals("刀", tokens[2].text); assertTrue(tokens[2].isHanzi); assertFalse(tokens[2].isCompound)
        assertEquals("和", tokens[3].text); assertTrue(tokens[3].isHanzi); assertFalse(tokens[3].isCompound)
        assertEquals("叉", tokens[4].text); assertTrue(tokens[4].isHanzi); assertFalse(tokens[4].isCompound)
        assertEquals("。", tokens[5].text); assertFalse(tokens[5].isHanzi)

        // Verify string reconstruction
        assertEquals(sentence, tokens.joinToString("") { it.text })
    }

    @Test
    fun testCompoundWordForwardMaximumMatching() {
        val sentence = "中国人吃饭的时候不用刀和叉。"
        val knownWords = setOf("中国人", "中国", "吃饭", "时候", "不用")
        val tokens = ChineseSentenceTokenizer.tokenize(sentence, knownWords)

        // Expected:
        // "中国人" (compound)
        // "吃饭" (compound)
        // "的" (single)
        // "时候" (compound)
        // "不用" (compound)
        // "刀" (single)
        // "和" (single)
        // "叉" (single)
        // "。" (punct)
        val texts = tokens.map { it.text }
        assertEquals(listOf("中国人", "吃饭", "的", "时候", "不用", "刀", "和", "叉", "。"), texts)

        assertTrue(tokens[0].isCompound)
        assertTrue(tokens[1].isCompound)
        assertFalse(tokens[2].isCompound)
        assertTrue(tokens[3].isCompound)
        assertTrue(tokens[4].isCompound)
        assertFalse(tokens[5].isCompound)
        assertFalse(tokens[8].isHanzi)

        // Verify offsets
        for (token in tokens) {
            assertEquals(token.text, sentence.substring(token.startIndex, token.endIndex))
        }
        assertEquals(sentence, tokens.joinToString("") { it.text })
    }

    @Test
    fun testMixedEnglishDigitsAndChinese() {
        val sentence = "Slow Chinese 慢速中文 #12: 茶马古道！"
        val knownWords = setOf("慢速", "中文", "慢速中文", "茶马古道")
        val tokens = ChineseSentenceTokenizer.tokenize(sentence, knownWords)

        val texts = tokens.map { it.text }
        assertEquals(listOf("Slow Chinese ", "慢速中文", " #12: ", "茶马古道", "！"), texts)

        for (token in tokens) {
            assertEquals(token.text, sentence.substring(token.startIndex, token.endIndex))
        }
        assertEquals(sentence, tokens.joinToString("") { it.text })
    }
}
