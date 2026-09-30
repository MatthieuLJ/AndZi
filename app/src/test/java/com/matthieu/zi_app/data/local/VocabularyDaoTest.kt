package com.matthieu.zi_app.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.data.local.dao.VocabularyDao
import com.matthieu.zi_app.data.local.entity.CharPinyinEntity
import com.matthieu.zi_app.data.local.entity.ChineseWordEntity
import com.matthieu.zi_app.data.local.entity.EnglishTranslationEntity
import com.matthieu.zi_app.data.local.entity.WordSegmentEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VocabularyDaoTest {

    private lateinit var db: AndZiDatabase
    private lateinit var dao: VocabularyDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AndZiDatabase.createInMemory(context)
        dao = db.vocabularyDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndRetrieveWordWithDetails() = runBlocking {
        // 1. Insert CharPinyins
        val charId1 = dao.insertCharPinyin(CharPinyinEntity(hanzi = "你", pinyin = "ni3"))
        val charId2 = dao.insertCharPinyin(CharPinyinEntity(hanzi = "好", pinyin = "hao3"))
        assertTrue(charId1 > 0)
        assertTrue(charId2 > 0)

        // 2. Insert Word
        val wordId = dao.insertWord(ChineseWordEntity(chinese = "你好", pinyin = "ni3hao3", length = 2))
        assertTrue(wordId > 0)

        // 3. Insert Segments
        dao.insertSegments(
            listOf(
                WordSegmentEntity(cwordId = wordId, charPinyinId = charId1, pos = 0),
                WordSegmentEntity(cwordId = wordId, charPinyinId = charId2, pos = 1)
            )
        )

        // 4. Insert Translations
        val engId = dao.insertTranslation(EnglishTranslationEntity(cwordId = wordId, translation = "Hello; Hi"))
        assertTrue(engId > 0)

        // 5. Query
        val details = dao.getWordsWithDetailsForChinese("你好")
        assertEquals(1, details.size)
        assertEquals("你好", details[0].word.chinese)
        assertEquals("ni3hao3", details[0].word.pinyin)
        assertEquals(1, details[0].translations.size)
        assertEquals("Hello; Hi", details[0].translations[0].translation)
        assertEquals(2, details[0].characters.size)
    }

    @Test
    fun testSearchQueries() = runBlocking {
        val wordId1 = dao.insertWord(ChineseWordEntity(chinese = "中国", pinyin = "zhong1guo2", length = 2))
        val wordId2 = dao.insertWord(ChineseWordEntity(chinese = "中国人", pinyin = "zhong1guo2ren2", length = 3))
        dao.insertTranslation(EnglishTranslationEntity(cwordId = wordId1, translation = "China"))
        dao.insertTranslation(EnglishTranslationEntity(cwordId = wordId2, translation = "Chinese person"))

        // Prefix Chinese search
        val byChinese = dao.searchByChinesePrefix("中")
        assertEquals(2, byChinese.size)

        // Prefix Pinyin search
        val byPinyin = dao.searchByPinyinPrefix("zhong1")
        assertEquals(2, byPinyin.size)

        // English search
        val byEng = dao.searchByEnglish("person")
        assertEquals(1, byEng.size)
        assertEquals("中国人", byEng[0].word.chinese)

        // Containment search
        val containing = dao.getWordsContainingCharacter("人")
        assertEquals(1, containing.size)
        assertEquals("中国人", containing[0].word.chinese)
    }

    @Test
    fun testCascadeDeletion() = runBlocking {
        val wordId = dao.insertWord(ChineseWordEntity(chinese = "大", pinyin = "da4", length = 1))
        dao.insertTranslation(EnglishTranslationEntity(cwordId = wordId, translation = "big"))

        val before = dao.getTranslationsForWord(wordId)
        assertEquals(1, before.size)

        dao.deleteWordById(wordId)

        val after = dao.getTranslationsForWord(wordId)
        assertTrue(after.isEmpty())
    }
}
