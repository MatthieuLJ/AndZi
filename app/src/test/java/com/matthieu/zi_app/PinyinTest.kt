package com.matthieu.zi_app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinTest {

    @Test
    fun testNumericTones() {
        val p1 = Pinyin("ni3")
        assertTrue(Pinyin.verify_pinyin("ni3"))
        assertEquals("ni3", p1.get_lame())
        assertEquals("ni", p1.get_radical())
        assertEquals(3, p1.get_tone())
        assertEquals("nǐ", p1.get_pretty())
        assertEquals("<font color=\"green\">nǐ</font>", p1.get_color())
        assertTrue(p1.tone_is_given())

        val p2 = Pinyin("hao3")
        assertEquals("hao3", p2.get_lame())
        assertEquals("hao", p2.get_radical())
        assertEquals(3, p2.get_tone())
        assertEquals("hǎo", p2.get_pretty())

        val p3 = Pinyin("ma1")
        assertEquals(1, p3.get_tone())
        assertEquals("<font color=\"red\">mā</font>", p3.get_color())

        val p4 = Pinyin("ma2")
        assertEquals(2, p4.get_tone())
        assertEquals("<font color=\"#ff8000\">má</font>", p4.get_color())

        val p5 = Pinyin("ma4")
        assertEquals(4, p5.get_tone())
        assertEquals("<font color=\"blue\">mà</font>", p5.get_color())

        val p0 = Pinyin("ma0")
        assertEquals(5, p0.get_tone())
        assertEquals("ma0", p0.get_lame())
        assertEquals("ma", p0.get_pretty())
    }

    @Test
    fun testToneMarkedVowels() {
        val p1 = Pinyin("zhōng")
        assertEquals("zhong1", p1.get_lame())
        assertEquals("zhong", p1.get_radical())
        assertEquals(1, p1.get_tone())
        assertEquals("zhōng", p1.get_pretty())

        val p2 = Pinyin("guó")
        assertEquals("guo2", p2.get_lame())
        assertEquals("guo", p2.get_radical())
        assertEquals(2, p2.get_tone())

        val p3 = Pinyin("lǚ")
        assertEquals("lv3", p3.get_lame())
        assertEquals("lv", p3.get_radical())
        assertEquals(3, p3.get_tone())
    }

    @Test
    fun testNeutralToneNoDigit() {
        val p = Pinyin("de")
        assertEquals("de5", p.get_lame())
        assertEquals("de", p.get_radical())
        assertEquals(5, p.get_tone())
        assertFalse(p.tone_is_given())
    }

    @Test
    fun testIncompletePinyin() {
        val inc1 = Pinyin("zh", true)
        assertEquals("zh", inc1.get_lame())

        val inc2 = Pinyin("zhō", true)
        assertEquals("zho", inc2.get_lame())
        assertEquals(1, inc2.tone)

        val empty = Pinyin("", true)
        assertEquals("", empty.get_lame())
    }

    @Test
    fun testVerifyPinyin() {
        assertTrue(Pinyin.verify_pinyin("ni3"))
        assertTrue(Pinyin.verify_pinyin("hao3"))
        assertTrue(Pinyin.verify_pinyin("zhong1"))
        assertTrue(Pinyin.verify_pinyin("nv3"))
        assertTrue(Pinyin.verify_pinyin("lv4"))
        assertTrue(Pinyin.verify_pinyin("er2"))
        assertTrue(Pinyin.verify_pinyin("r5"))

        assertFalse(Pinyin.verify_pinyin("xyz"))
        assertFalse(Pinyin.verify_pinyin("hello"))
        assertFalse(Pinyin.verify_pinyin(""))
        assertFalse(Pinyin.verify_pinyin("123"))
    }

    @Test
    fun testEqualityAndHashCode() {
        val p1 = Pinyin("ni3")
        val p2 = Pinyin("nǐ")
        val p3 = Pinyin("ni2")

        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())
        assertNotEquals(p1, p3)
    }
}
