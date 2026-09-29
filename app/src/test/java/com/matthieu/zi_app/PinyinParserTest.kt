package com.matthieu.zi_app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinParserTest {

    @Test
    fun testSimple() {
        val res = PinyinParser.parse_string("ni3hao3")
        assertNotNull(res)
        assertEquals(2, res!!.size)
        assertEquals("ni3", res[0])
        assertEquals("hao3", res[1])
    }

    @Test
    fun testNoTone() {
        val res = PinyinParser.parse_string("nihao")
        assertNotNull(res)
        assertEquals(2, res!!.size)
        assertEquals("ni5", res[0])
        assertEquals("hao5", res[1])
    }

    @Test
    fun testTrickier() {
        val res = PinyinParser.parse_string("nangong")
        assertNotNull(res)
        assertEquals(2, res!!.size)
        assertEquals("nan5", res[0])
        assertEquals("gong5", res[1])
    }

    @Test
    fun testWithApostrophe() {
        val res = PinyinParser.parse_string("xi'an")
        assertNotNull(res)
        assertEquals(2, res!!.size)
        assertEquals("xi5", res[0])
        assertEquals("an5", res[1])
    }

    @Test
    fun testElementsSimple() {
        val res = PinyinParser.parse_string_to_elements("zènfajian2")
        assertNotNull(res)
        assertEquals(3, res!!.size)
        assertEquals(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, res[0].type)
        assertEquals("zen4", res[0].content)
        assertEquals(HanziDB.StringElement.type_t.PINYIN_WITHOUT_TONE, res[1].type)
        assertEquals("fa", res[1].content)
        assertEquals(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, res[2].type)
        assertEquals("jian2", res[2].content)
    }

    @Test
    fun testMixedElements() {
        val res = PinyinParser.parse_mixed_string("中guo2不见dianhuazuiting不dong3fengfe")
        assertNotNull(res)
        assertEquals(12, res!!.size)
        assertEquals(HanziDB.StringElement.type_t.CHINESE, res[0].type)
        assertEquals("中", res[0].content)
        assertEquals(HanziDB.StringElement.type_t.PINYIN_WITHOUT_TONE, res[5].type)
        assertEquals("hua", res[5].content)
        assertEquals(HanziDB.StringElement.type_t.PINYIN_WITH_TONE, res[9].type)
        assertEquals("dong3", res[9].content)
        assertEquals(HanziDB.StringElement.type_t.INCOMPLETE_PINYIN, res[11].type)
        assertEquals("fe", res[11].content)
    }

    @Test
    fun testCaching() {
        PinyinParser.parse_mixed_string("ma")
        PinyinParser.parse_mixed_string("mama")
        PinyinParser.parse_mixed_string("mamah")
        PinyinParser.parse_mixed_string("mamahuh")
        val res = PinyinParser.parse_mixed_string("mamahuhu")
        assertNotNull(res)
        assertEquals(4, res!!.size)
    }

    @Test
    fun testPrettyAndHtmlConversion() {
        val pretty = PinyinParser.convert_to_pretty_version("ni3hao3")
        assertEquals("nǐhǎo", pretty)

        val html = PinyinParser.convert_to_html_version("ni3hao3")
        assertEquals("<font color=\"green\">nǐ</font><font color=\"green\">hǎo</font>", html)

        val lame = PinyinParser.convert_to_lame_version("nǐhǎo")
        assertEquals("ni3hao3", lame)
    }

    @Test
    fun testConvertChineseToHtml() {
        val pinyins = PinyinParser.parse_string_to_pinyin("ni3hao3")
        assertNotNull(pinyins)
        val html = PinyinParser.convert_chinese_to_html_version("你好", pinyins!!)
        assertEquals("<font color=\"green\">你</font><font color=\"green\">好</font>", html)
    }

    @Test
    fun testInvalidInputs() {
        val empty = PinyinParser.parse_string("")
        assertNull(empty)

        val prettyEmpty = PinyinParser.convert_to_pretty_version("")
        assertEquals("", prettyEmpty)

        // Non-pinyin character results in empty list
        val nonPinyinChars = PinyinParser.parse_mixed_string("你好!")
        assertNotNull(nonPinyinChars)
        assertTrue(nonPinyinChars!!.isEmpty())

        // Letters that cannot be segmented into valid pinyin return null
        val invalidPinyin = PinyinParser.parse_mixed_string("hello")
        assertNull(invalidPinyin)
    }
}
