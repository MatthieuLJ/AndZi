package com.matthieu.zi_app.data.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastAttributionsTest {

    @Test
    fun testAllDefaultChannelsHaveAttributions() {
        val attributions = PodcastAttributions.attributions
        assertEquals(4, attributions.size)

        val slowChinese = attributions.find { it.title == "Slow Chinese" }
        assertNotNull(slowChinese)
        assertEquals("慢速中文", slowChinese?.chineseTitle)
        assertTrue(slowChinese!!.license.contains("Creative Commons"))
        assertTrue(slowChinese.website.isNotEmpty())

        val readingChina = attributions.find { it.title == "Reading China" }
        assertNotNull(readingChina)
        assertEquals("中文天天读", readingChina?.chineseTitle)

        val maomi = attributions.find { it.title == "MaoMi Chinese" }
        assertNotNull(maomi)
        assertEquals("猫咪中文", maomi?.chineseTitle)
        assertEquals("https://maomichinese.com", maomi?.website)

        val teatime = attributions.find { it.title == "TeaTime Chinese" }
        assertNotNull(teatime)
        assertEquals("茶歇中文", teatime?.chineseTitle)
        assertEquals("https://teatimechinese.com", teatime?.website)
    }

    @Test
    fun testLookupByEnglishOrChineseTitle() {
        val byEnglish = PodcastAttributions.getAttributionForChannel("Slow Chinese")
        assertNotNull(byEnglish)
        assertEquals("Slow Chinese", byEnglish?.title)

        val byChinese = PodcastAttributions.getAttributionForChannel("慢速中文")
        assertNotNull(byChinese)
        assertEquals("Slow Chinese", byChinese?.title)

        val caseInsensitive = PodcastAttributions.getAttributionForChannel("slow chinese")
        assertNotNull(caseInsensitive)
        assertEquals("Slow Chinese", caseInsensitive?.title)

        val withSpaces = PodcastAttributions.getAttributionForChannel("  TeaTime Chinese  ")
        assertNotNull(withSpaces)
        assertEquals("TeaTime Chinese", withSpaces?.title)
    }

    @Test
    fun testLookupNonExistentOrNullReturnsNull() {
        assertNull(PodcastAttributions.getAttributionForChannel(null))
        assertNull(PodcastAttributions.getAttributionForChannel(""))
        assertNull(PodcastAttributions.getAttributionForChannel("Non Existent Channel"))
    }
}

