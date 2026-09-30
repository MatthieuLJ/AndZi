package com.matthieu.zi_app.data.repository

import com.matthieu.zi_app.PinyinParser
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.dao.LessonDao
import com.matthieu.zi_app.data.local.dao.VocabularyDao
import com.matthieu.zi_app.data.local.entity.CharPinyinEntity
import com.matthieu.zi_app.data.local.entity.ChineseWordEntity
import com.matthieu.zi_app.data.local.entity.EnglishTranslationEntity
import com.matthieu.zi_app.data.local.entity.LessonWordCrossRef
import com.matthieu.zi_app.data.local.entity.WordSegmentEntity
import com.matthieu.zi_app.data.local.model.WordWithDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class VocabularyRepository(
    private val vocabularyDao: VocabularyDao,
    private val lessonDao: LessonDao
) {

    suspend fun addVocabulary(vocabulary: Vocabulary, lessonName: String? = null): Long = withContext(Dispatchers.IO) {
        val ch = vocabulary.chinese.trim()
        val pi = vocabulary.pinyin.trim()
        if (ch.isEmpty() || pi.isEmpty()) return@withContext -1L

        // 1. Ensure or insert Chinese word
        var word = vocabularyDao.getWord(ch, pi)
        val wordId = if (word == null) {
            val length = ch.codePointCount(0, ch.length)
            val newId = vocabularyDao.insertWord(ChineseWordEntity(chinese = ch, pinyin = pi, length = length))
            // Parse and insert segments
            val pinyins = PinyinParser.parse_string_to_pinyin(pi)
            if (pinyins != null) {
                var charIdx = 0
                var pos = 0
                while (charIdx < ch.length && pos < pinyins.size) {
                    val codePoint = ch.codePointAt(charIdx)
                    val hanzi = ch.substring(charIdx, charIdx + Character.charCount(codePoint))
                    val pinyinSyllable = pinyins[pos].lame

                    // Ensure char_pinyin entry
                    var charPinyin = vocabularyDao.getCharPinyin(hanzi, pinyinSyllable)
                    val charPinyinId = if (charPinyin == null) {
                        vocabularyDao.insertCharPinyin(CharPinyinEntity(hanzi = hanzi, pinyin = pinyinSyllable))
                    } else {
                        charPinyin.charPinyinId
                    }

                    vocabularyDao.insertSegments(
                        listOf(WordSegmentEntity(cwordId = newId, charPinyinId = charPinyinId, pos = pos))
                    )
                    pos++
                    charIdx += Character.charCount(codePoint)
                }
            }
            newId
        } else {
            word.cwordId
        }

        // 2. Insert English translations and optional lesson link
        var lessonId: Long? = null
        if (!lessonName.isNullOrEmpty()) {
            val lesson = lessonDao.getLessonByName(lessonName)
            lessonId = lesson?.lessonId ?: lessonDao.insertLesson(
                com.matthieu.zi_app.data.local.entity.LessonEntity(name = lessonName)
            )
        }

        for (en in vocabulary.english) {
            val cleanEn = en.trim()
            if (cleanEn.isEmpty()) continue
            val existingTranslations = vocabularyDao.getTranslationsForWord(wordId)
            val existing = existingTranslations.find { it.translation == cleanEn }
            val engId = existing?.engId ?: vocabularyDao.insertTranslation(
                EnglishTranslationEntity(cwordId = wordId, translation = cleanEn)
            )
            if (lessonId != null && engId > 0) {
                lessonDao.assignWordToLesson(LessonWordCrossRef(engId = engId, lessonId = lessonId))
            }
        }

        wordId
    }

    suspend fun getVocabularyForChinese(chinese: String): List<Vocabulary> = withContext(Dispatchers.IO) {
        val details = vocabularyDao.getWordsWithDetailsForChinese(chinese)
        details.map { toVocabulary(it) }
    }

    suspend fun getWordsContainingCharacter(hanzi: String): List<Vocabulary> = withContext(Dispatchers.IO) {
        val details = vocabularyDao.getWordsContainingCharacter(hanzi)
        details.map { toVocabulary(it) }
    }

    fun getVocabularyInLessonFlow(lessonId: Long): Flow<List<Vocabulary>> {
        return vocabularyDao.getWordsInLessonFlow(lessonId).map { list ->
            list.map { toVocabulary(it) }
        }
    }

    suspend fun getVocabularyInLessons(lessonIds: List<Long>): List<Vocabulary> = withContext(Dispatchers.IO) {
        vocabularyDao.getWordsInLessons(lessonIds).map { toVocabulary(it) }
    }

    suspend fun getVocabularyWithoutLesson(): List<Vocabulary> = withContext(Dispatchers.IO) {
        vocabularyDao.getWordsWithoutLesson().map { toVocabulary(it) }
    }

    suspend fun search(query: String, limit: Int = 50): List<Vocabulary> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<WordWithDetails>()
        val byChinese = vocabularyDao.searchByChinesePrefix(cleanQuery, limit)
        results.addAll(byChinese)

        if (results.size < limit) {
            val byPinyin = vocabularyDao.searchByPinyinPrefix(cleanQuery, limit - results.size)
            results.addAll(byPinyin)
        }

        if (results.size < limit) {
            val byEnglish = vocabularyDao.searchByEnglish(cleanQuery, limit - results.size)
            results.addAll(byEnglish)
        }

        results.distinctBy { it.word.cwordId }.map { toVocabulary(it) }
    }

    suspend fun deleteWord(chinese: String, pinyin: String) = withContext(Dispatchers.IO) {
        vocabularyDao.deleteWord(chinese, pinyin)
    }

    suspend fun deleteTranslation(cwordId: Long, translation: String) = withContext(Dispatchers.IO) {
        vocabularyDao.deleteTranslation(cwordId, translation)
    }

    suspend fun getDistinctHanziCount(): Int = withContext(Dispatchers.IO) {
        vocabularyDao.getDistinctHanziCount()
    }

    suspend fun getWordCount(): Int = withContext(Dispatchers.IO) {
        vocabularyDao.getWordCount()
    }

    private fun toVocabulary(details: WordWithDetails): Vocabulary {
        val engList = ArrayList(details.translations.map { it.translation })
        return Vocabulary(
            chinese = details.word.chinese,
            pinyin = details.word.pinyin,
            english = engList
        )
    }
}
