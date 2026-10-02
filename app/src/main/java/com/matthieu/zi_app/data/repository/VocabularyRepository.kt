package com.matthieu.zi_app.data.repository

import com.matthieu.zi_app.PinyinParser
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.dao.VocabularyDao
import com.matthieu.zi_app.data.local.entity.CharPinyinEntity
import com.matthieu.zi_app.data.local.entity.ChineseWordEntity
import com.matthieu.zi_app.data.local.entity.EnglishTranslationEntity
import com.matthieu.zi_app.data.local.entity.HskVocabEntity
import com.matthieu.zi_app.data.local.entity.WordSegmentEntity
import com.matthieu.zi_app.data.local.model.WordWithDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VocabularyRepository(
    private val vocabularyDao: VocabularyDao
) {

    suspend fun addVocabulary(vocabulary: Vocabulary): Long = withContext(Dispatchers.IO) {
        val ch = vocabulary.chinese.trim()
        val pi = vocabulary.pinyin.trim()
        if (ch.isEmpty() || pi.isEmpty()) return@withContext -1L

        // 1. Ensure or insert Chinese word
        val word = vocabularyDao.getWord(ch, pi)
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
                    val charPinyin = vocabularyDao.getCharPinyin(hanzi, pinyinSyllable)
                    val charPinyinId = charPinyin?.charPinyinId ?: vocabularyDao.insertCharPinyin(
                        CharPinyinEntity(hanzi = hanzi, pinyin = pinyinSyllable)
                    )

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

        // 2. Insert English translations
        for (en in vocabulary.english) {
            val cleanEn = en.trim()
            if (cleanEn.isEmpty()) continue
            val existingTranslations = vocabularyDao.getTranslationsForWord(wordId)
            val existing = existingTranslations.find { it.translation == cleanEn }
            if (existing == null) {
                vocabularyDao.insertTranslation(
                    EnglishTranslationEntity(cwordId = wordId, translation = cleanEn)
                )
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

    suspend fun getCharPinyinsForHanzi(hanzi: String): List<CharPinyinEntity> = withContext(Dispatchers.IO) {
        vocabularyDao.getCharPinyinsForHanzi(hanzi)
    }

    suspend fun search(query: String, limit: Int = 50): List<Vocabulary> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()

        val results = mutableListOf<WordWithDetails>()
        val byChinese = vocabularyDao.searchByChinesePrefix(cleanQuery, limit)
        results.addAll(byChinese)

        if (results.size < limit && cleanQuery.isNotEmpty()) {
            val lameQuery = PinyinParser.convert_to_lame_version(cleanQuery)
            val pinyinQuery = if (lameQuery.isNotEmpty()) lameQuery else cleanQuery
            val byPinyin = vocabularyDao.searchByPinyinPrefix(pinyinQuery, limit - results.size)
            results.addAll(byPinyin)
            if (pinyinQuery != cleanQuery && results.size < limit) {
                val byPinyinRaw = vocabularyDao.searchByPinyinPrefix(cleanQuery, limit - results.size)
                results.addAll(byPinyinRaw)
            }
        }

        if (results.size < limit && cleanQuery.isNotEmpty()) {
            val byEnglish = vocabularyDao.searchByEnglish(cleanQuery, limit - results.size)
            results.addAll(byEnglish)
        }

        results.distinctBy { it.word.cwordId }.map { toVocabulary(it) }
    }

    suspend fun getAllVocabulary(): List<Vocabulary> = withContext(Dispatchers.IO) {
        vocabularyDao.getAllWordsWithDetails()
            .map { toVocabulary(it) }
            .filter { it.english.isNotEmpty() }
    }

    suspend fun deleteWord(chinese: String, pinyin: String? = null) = withContext(Dispatchers.IO) {
        if (pinyin.isNullOrBlank()) {
            vocabularyDao.deleteWordsForChinese(chinese)
        } else {
            vocabularyDao.deleteWord(chinese, pinyin)
        }
        if (chinese.codePointCount(0, chinese.length) == 1) {
            vocabularyDao.deleteCharPinyin(chinese)
        }
    }

    suspend fun deleteCharacter(hanzi: String) = withContext(Dispatchers.IO) {
        vocabularyDao.deleteCharPinyin(hanzi)
    }

    suspend fun deleteTranslation(cwordId: Long, translation: String) = withContext(Dispatchers.IO) {
        vocabularyDao.deleteTranslation(cwordId, translation)
    }

    suspend fun addTranslation(chinese: String, pinyin: String, translation: String): Long = withContext(Dispatchers.IO) {
        val cleanTrans = translation.trim()
        if (cleanTrans.isEmpty()) return@withContext -1L

        val word = vocabularyDao.getWord(chinese.trim(), pinyin.trim())
        val wordId = if (word == null) {
            addVocabulary(Vocabulary(chinese = chinese, pinyin = pinyin, english = arrayListOf(cleanTrans)))
        } else {
            val existing = vocabularyDao.getTranslationsForWord(word.cwordId)
            if (existing.none { it.translation == cleanTrans }) {
                vocabularyDao.insertTranslation(EnglishTranslationEntity(cwordId = word.cwordId, translation = cleanTrans))
            }
            word.cwordId
        }
        wordId
    }

    suspend fun removeTranslation(chinese: String, pinyin: String, translation: String) = withContext(Dispatchers.IO) {
        val word = vocabularyDao.getWord(chinese.trim(), pinyin.trim()) ?: return@withContext
        vocabularyDao.deleteTranslation(word.cwordId, translation.trim())
    }

    suspend fun setTranslations(chinese: String, pinyin: String, translations: List<String>) = withContext(Dispatchers.IO) {
        val word = vocabularyDao.getWord(chinese.trim(), pinyin.trim())
        if (word == null) {
            if (translations.isNotEmpty()) {
                addVocabulary(Vocabulary(chinese = chinese, pinyin = pinyin, english = ArrayList(translations)))
            }
            return@withContext
        }
        vocabularyDao.deleteAllTranslationsForWord(word.cwordId)
        for (trans in translations) {
            val clean = trans.trim()
            if (clean.isNotEmpty()) {
                vocabularyDao.insertTranslation(EnglishTranslationEntity(cwordId = word.cwordId, translation = clean))
            }
        }
    }

    suspend fun getAllDistinctHanzi(): List<String> = withContext(Dispatchers.IO) {
        vocabularyDao.getAllDistinctHanzi()
    }

    suspend fun getDistinctHanziCount(): Int = withContext(Dispatchers.IO) {
        vocabularyDao.getDistinctHanziCount()
    }

    suspend fun getWordCount(): Int = withContext(Dispatchers.IO) {
        vocabularyDao.getWordCount()
    }

    suspend fun getCompoundWordsForCharPinyin(hanzi: String, pinyin: String, limit: Int = 4): List<String> = withContext(Dispatchers.IO) {
        val direct = vocabularyDao.getCompoundWordsForCharPinyin(hanzi, pinyin, limit)
        if (direct.isNotEmpty()) return@withContext direct

        // Fallback: search words containing the character and match pinyin syllables
        val containing = vocabularyDao.getWordsContainingCharacter(hanzi)
        val cleanPinyin = pinyin.trim().lowercase()
        val radicalOnly = cleanPinyin.filter { !it.isDigit() }
        containing
            .filter { (it.word.length ?: 0) > 1 && (it.word.pinyin.contains(cleanPinyin) || it.word.pinyin.contains(radicalOnly)) }
            .map { it.word.chinese }
            .distinct()
            .take(limit)
    }

    suspend fun getComposedWords(knownChars: List<String>, limit: Int = 200): List<ChineseWordEntity> = withContext(Dispatchers.IO) {
        if (knownChars.isEmpty()) emptyList() else vocabularyDao.getComposedWords(knownChars, limit)
    }

    suspend fun getHskCandidates(maxLevel: Int, limit: Int = 300): List<HskVocabEntity> = withContext(Dispatchers.IO) {
        vocabularyDao.getHskCandidates(maxLevel, limit)
    }

    suspend fun getHskVocabForWords(words: List<String>): List<HskVocabEntity> = withContext(Dispatchers.IO) {
        if (words.isEmpty()) emptyList() else vocabularyDao.getHskVocabForWords(words)
    }

    suspend fun getHskVocabForWord(hanzi: String): HskVocabEntity? = withContext(Dispatchers.IO) {
        vocabularyDao.getHskVocabForWord(hanzi)
    }

    suspend fun insertHskVocab(entities: List<HskVocabEntity>) = withContext(Dispatchers.IO) {
        vocabularyDao.insertHskVocab(entities)
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
