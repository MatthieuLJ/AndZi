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
    fun testSearchSortingByHskAndFrequency() = runBlocking {
        // Insert words in arbitrary insertion order
        val idObscure = dao.insertWord(ChineseWordEntity(chinese = "学究", pinyin = "xue2jiu1", length = 2))
        val idHsk3 = dao.insertWord(ChineseWordEntity(chinese = "学院", pinyin = "xue2yuan4", length = 2))
        val idHsk1HighFreq = dao.insertWord(ChineseWordEntity(chinese = "学习", pinyin = "xue2xi2", length = 2))
        val idHsk1Exact = dao.insertWord(ChineseWordEntity(chinese = "学", pinyin = "xue2", length = 1))
        val idHsk1MedFreq = dao.insertWord(ChineseWordEntity(chinese = "学生", pinyin = "xue2sheng1", length = 2))

        dao.insertTranslation(EnglishTranslationEntity(cwordId = idObscure, translation = "pedant; bookworm"))
        dao.insertTranslation(EnglishTranslationEntity(cwordId = idHsk3, translation = "college; institute; academy"))
        dao.insertTranslation(EnglishTranslationEntity(cwordId = idHsk1HighFreq, translation = "to learn; to study"))
        dao.insertTranslation(EnglishTranslationEntity(cwordId = idHsk1Exact, translation = "to learn; to study; science"))
        dao.insertTranslation(EnglishTranslationEntity(cwordId = idHsk1MedFreq, translation = "student; pupil"))

        // Insert HSK and frequency metadata
        dao.insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "学", hskLevel = 1, freqRank = 50, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "学习", hskLevel = 1, freqRank = 80, isChar = 0),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "学生", hskLevel = 1, freqRank = 120, isChar = 0),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "学院", hskLevel = 3, freqRank = 1500, isChar = 0)
                // "学究" is non-HSK (defaults to hskLevel = 99, freqRank = 999999)
            )
        )

        // 1. Search by Chinese prefix "学":
        // Order: Exact match ("学") -> HSK 1 freq 80 ("学习") -> HSK 1 freq 120 ("学生") -> HSK 3 ("学院") -> Non-HSK ("学究")
        val chineseResults = dao.searchByChinesePrefix("学")
        assertEquals(5, chineseResults.size)
        assertEquals("学", chineseResults[0].word.chinese)
        assertEquals("学习", chineseResults[1].word.chinese)
        assertEquals("学生", chineseResults[2].word.chinese)
        assertEquals("学院", chineseResults[3].word.chinese)
        assertEquals("学究", chineseResults[4].word.chinese)

        // 2. Search by Pinyin prefix "xue2":
        val pinyinResults = dao.searchByPinyinPrefix("xue2")
        assertEquals(5, pinyinResults.size)
        assertEquals("学", pinyinResults[0].word.chinese) // Exact pinyin match "xue2"
        assertEquals("学习", pinyinResults[1].word.chinese)
        assertEquals("学生", pinyinResults[2].word.chinese)
        assertEquals("学院", pinyinResults[3].word.chinese)
        assertEquals("学究", pinyinResults[4].word.chinese)

        // 3. Search by English "study":
        val engResults = dao.searchByEnglish("study")
        assertEquals(2, engResults.size)
        // Both "学" and "学习" are HSK 1, "学" has freqRank 50 (higher frequency than "学习" at 80)
        assertEquals("学", engResults[0].word.chinese)
        assertEquals("学习", engResults[1].word.chinese)
    }

    @Test
    fun testSearchByPinyinRanking() = runBlocking {
        dao.insertWord(ChineseWordEntity(chinese = "行", pinyin = "xing2", length = 1))
        dao.insertWord(ChineseWordEntity(chinese = "先", pinyin = "xian1", length = 1))
        dao.insertWord(ChineseWordEntity(chinese = "西", pinyin = "xi1", length = 1))
        dao.insertWord(ChineseWordEntity(chinese = "系", pinyin = "xi4", length = 1))
        dao.insertWord(ChineseWordEntity(chinese = "喜欢", pinyin = "xi3huan5", length = 2))

        dao.insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "行", hskLevel = 1, freqRank = 9, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "先", hskLevel = 1, freqRank = 42, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "喜欢", hskLevel = 1, freqRank = 46, isChar = 0),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "系", hskLevel = 1, freqRank = 65, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "西", hskLevel = 1, freqRank = 165, isChar = 1)
            )
        )

        // Exact single "xi[1-5]" characters come first (系, 西), then compound "xi[1-5]*" (喜欢), then others (行, 先)
        val results = dao.searchByPinyin(
            exactSingle = "xi[1-5]",
            exactWord = "xi[1-5]",
            syllablePrefix = "xi[1-5]*",
            rawPrefixGlob = "xi*",
            limit = 10
        )
        assertEquals(5, results.size)
        assertEquals("系", results[0].word.chinese)
        assertEquals("西", results[1].word.chinese)
        assertEquals("喜欢", results[2].word.chinese)
        assertEquals("行", results[3].word.chinese)
        assertEquals("先", results[4].word.chinese)
    }

    @Test
    fun testWordsContainingCharacterSortedByHsk() = runBlocking {
        val id1 = dao.insertWord(ChineseWordEntity(chinese = "大体", pinyin = "da4ti3", length = 2))
        val id2 = dao.insertWord(ChineseWordEntity(chinese = "大人", pinyin = "da4ren2", length = 2))
        val id3 = dao.insertWord(ChineseWordEntity(chinese = "大家", pinyin = "da4jia1", length = 2))

        dao.insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "大家", hskLevel = 1, freqRank = 30, isChar = 0),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "大人", hskLevel = 3, freqRank = 500, isChar = 0),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "大体", hskLevel = 5, freqRank = 2000, isChar = 0)
            )
        )

        val containing = dao.getWordsContainingCharacter("大")
        assertEquals(3, containing.size)
        assertEquals("大家", containing[0].word.chinese)
        assertEquals("大人", containing[1].word.chinese)
        assertEquals("大体", containing[2].word.chinese)
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
