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
        assertEquals(ParsedElementType.PINYIN_WITH_TONE, res[0].type)
        assertEquals("zen4", res[0].content)
        assertEquals(ParsedElementType.PINYIN_WITHOUT_TONE, res[1].type)
        assertEquals("fa", res[1].content)
        assertEquals(ParsedElementType.PINYIN_WITH_TONE, res[2].type)
        assertEquals("jian2", res[2].content)
    }

    @Test
    fun testMixedElements() {
        val res = PinyinParser.parse_mixed_string("中guo2不见dianhuazuiting不dong3fengfe")
        assertNotNull(res)
        assertEquals(12, res!!.size)
        assertEquals(ParsedElementType.CHINESE, res[0].type)
        assertEquals("中", res[0].content)
        assertEquals(ParsedElementType.PINYIN_WITHOUT_TONE, res[5].type)
        assertEquals("hua", res[5].content)
        assertEquals(ParsedElementType.PINYIN_WITH_TONE, res[9].type)
        assertEquals("dong3", res[9].content)
        assertEquals(ParsedElementType.INCOMPLETE_PINYIN, res[11].type)
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

    @Test
    fun testPinyinToneListsInvariants() {
        val lists = listOf(
            Pinyin.pinyin_list_notone,
            Pinyin.pinyin_list_tone1,
            Pinyin.pinyin_list_tone2,
            Pinyin.pinyin_list_tone3,
            Pinyin.pinyin_list_tone4,
            Pinyin.pinyin_list_tone5
        )
        val expectedSize = Pinyin.pinyin_list_notone.size
        for (list in lists) {
            assertEquals("All pinyin lists must match size", expectedSize, list.size)
        }

        val tone1Marks = setOf('ā', 'ē', 'ī', 'ō', 'ū', 'ǖ')
        val tone2Marks = setOf('á', 'é', 'í', 'ó', 'ú', 'ǘ')
        val tone3Marks = setOf('ǎ', 'ě', 'ǐ', 'ǒ', 'ǔ', 'ǚ')
        val tone4Marks = setOf('à', 'è', 'ì', 'ò', 'ù', 'ǜ')

        // Tone 1
        for (syll in Pinyin.pinyin_list_tone1) {
            if (syll == "r") continue
            val hasTone1 = syll.any { it in tone1Marks }
            val hasOtherTone = syll.any { it in tone2Marks || it in tone3Marks || it in tone4Marks }
            assertTrue("Tone 1 syllable '$syll' must have tone 1 mark", hasTone1)
            assertTrue("Tone 1 syllable '$syll' must not have other tone marks", !hasOtherTone)
        }

        // Tone 2
        for (syll in Pinyin.pinyin_list_tone2) {
            if (syll == "r") continue
            val hasTone2 = syll.any { it in tone2Marks }
            val hasOtherTone = syll.any { it in tone1Marks || it in tone3Marks || it in tone4Marks }
            assertTrue("Tone 2 syllable '$syll' must have tone 2 mark", hasTone2)
            assertTrue("Tone 2 syllable '$syll' must not have other tone marks", !hasOtherTone)
        }

        // Tone 3
        for (syll in Pinyin.pinyin_list_tone3) {
            if (syll == "r") continue
            val hasTone3 = syll.any { it in tone3Marks }
            val hasOtherTone = syll.any { it in tone1Marks || it in tone2Marks || it in tone4Marks }
            assertTrue("Tone 3 syllable '$syll' must have tone 3 mark", hasTone3)
            assertTrue("Tone 3 syllable '$syll' must not have other tone marks", !hasOtherTone)
        }

        // Tone 4
        for (syll in Pinyin.pinyin_list_tone4) {
            if (syll == "r") continue
            val hasTone4 = syll.any { it in tone4Marks }
            val hasOtherTone = syll.any { it in tone1Marks || it in tone2Marks || it in tone3Marks }
            assertTrue("Tone 4 syllable '$syll' must have tone 4 mark", hasTone4)
            assertTrue("Tone 4 syllable '$syll' must not have other tone marks", !hasOtherTone)
        }

        // Orthography: after j, q, x, y, ü is written without umlauts in standard Hanyu Pinyin
        val jqxyPrefixes = listOf("j", "q", "x", "y")
        for (t in 1..4) {
            val list = when (t) {
                1 -> Pinyin.pinyin_list_tone1
                2 -> Pinyin.pinyin_list_tone2
                3 -> Pinyin.pinyin_list_tone3
                else -> Pinyin.pinyin_list_tone4
            }
            for (syll in list) {
                if (jqxyPrefixes.any { syll.startsWith(it) }) {
                    val hasUmlaut = syll.any { it in setOf('ǖ', 'ǘ', 'ǚ', 'ǜ', 'ü') }
                    assertTrue("Syllable '$syll' starting with j/q/x/y must not contain umlauts", !hasUmlaut)
                }
            }
        }

        // Specific regression checks for the previously corrupted syllables
        assertTrue(Pinyin.pinyin_list_tone2.contains("ruí"))
        assertTrue(!Pinyin.pinyin_list_tone2.contains("ruī"))
        assertTrue(Pinyin.pinyin_list_tone2.contains("shuán"))
        assertTrue(!Pinyin.pinyin_list_tone2.contains("shuān"))

        assertTrue(Pinyin.pinyin_list_tone3.contains("liǎn"))
        assertTrue(!Pinyin.pinyin_list_tone3.contains("liān"))

        assertTrue(Pinyin.pinyin_list_tone4.contains("shuàn"))
        assertTrue(Pinyin.pinyin_list_tone4.contains("yù"))
        assertTrue(Pinyin.pinyin_list_tone4.contains("jù"))
        assertTrue(Pinyin.pinyin_list_tone4.contains("yùn"))
        assertTrue(Pinyin.pinyin_list_tone4.contains("jùn"))
        assertTrue(Pinyin.pinyin_list_tone4.contains("qùn"))
        assertTrue(Pinyin.pinyin_list_tone4.contains("xùn"))
        assertTrue(!Pinyin.pinyin_list_tone4.contains("yǜ"))
        assertTrue(!Pinyin.pinyin_list_tone4.contains("jǜ"))
        assertTrue(!Pinyin.pinyin_list_tone4.contains("yǜn"))
        assertTrue(!Pinyin.pinyin_list_tone4.contains("jǜn"))
        assertTrue(!Pinyin.pinyin_list_tone4.contains("qǜn"))
        assertTrue(!Pinyin.pinyin_list_tone4.contains("xǜn"))
    }

    @Test
    fun testToCodePointStrings() {
        val empty = "".toCodePointStrings()
        assertTrue(empty.isEmpty())

        val ascii = "hello".toCodePointStrings()
        assertEquals(listOf("h", "e", "l", "l", "o"), ascii)

        val bmp = "你好".toCodePointStrings()
        assertEquals(listOf("你", "好"), bmp)

        // Surrogate pair: 𠮷 (U+20BB7) has 2 chars (surrogate pair) in UTF-16
        val surrogateStr = "𠮷野家"
        assertEquals(3, surrogateStr.codePointCount(0, surrogateStr.length))
        assertEquals(4, surrogateStr.length) // 2 for 𠮷, 1 for 野, 1 for 家

        val codepoints = surrogateStr.toCodePointStrings()
        assertEquals(3, codepoints.size)
        assertEquals("𠮷", codepoints[0])
        assertEquals("野", codepoints[1])
        assertEquals("家", codepoints[2])
    }
}
