package com.matthieu.zi_app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyTest {

    @Test
    fun testConstructors() {
        val voc1 = Vocabulary("你好", "ni3hao3")
        assertEquals("你好", voc1.chinese)
        assertEquals("ni3hao3", voc1.pinyin)
        assertTrue(voc1.english.isEmpty())

        val voc2 = Vocabulary("你好", "ni3hao3", "hello")
        assertEquals(1, voc2.english.size)
        assertEquals("hello", voc2.english[0])

        val voc3 = Vocabulary("好", "hao3", arrayListOf("good", "well"))
        assertEquals(2, voc3.english.size)
        assertEquals("good", voc3.english[0])
        assertEquals("well", voc3.english[1])
    }

    @Test
    fun testSameWord() {
        val voc1 = Vocabulary("长", "chang2", "long")
        val voc2 = Vocabulary("长", "chang2", "length")
        val voc3 = Vocabulary("长", "zhang3", "to grow")
        val voc4 = Vocabulary("短", "duan3", "short")

        // Same hanzi and pinyin even with different translations
        assertTrue(voc1.same_word(voc2))
        // Same hanzi but different pinyin
        assertFalse(voc1.same_word(voc3))
        // Different hanzi
        assertFalse(voc1.same_word(voc4))
        // Null
        assertFalse(voc1.same_word(null))
    }

    @Test
    fun testDataClassEquality() {
        val voc1 = Vocabulary("你好", "ni3hao3", arrayListOf("hello"))
        val voc2 = Vocabulary("你好", "ni3hao3", arrayListOf("hello"))
        val voc3 = Vocabulary("你好", "ni3hao3", arrayListOf("hi"))

        assertEquals(voc1, voc2)
        assertEquals(voc1.hashCode(), voc2.hashCode())
        assertNotEquals(voc1, voc3)
    }
}
