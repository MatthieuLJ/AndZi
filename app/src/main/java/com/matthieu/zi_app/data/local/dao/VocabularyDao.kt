package com.matthieu.zi_app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.matthieu.zi_app.data.local.entity.CharPinyinEntity
import com.matthieu.zi_app.data.local.entity.CharSimilarityEntity
import com.matthieu.zi_app.data.local.entity.CharacterStrokeEntity
import com.matthieu.zi_app.data.local.entity.ChineseWordEntity
import com.matthieu.zi_app.data.local.entity.EnglishTranslationEntity
import com.matthieu.zi_app.data.local.entity.HskVocabEntity
import com.matthieu.zi_app.data.local.entity.WordSegmentEntity
import com.matthieu.zi_app.data.local.model.WordWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {

    // --- Character / Pinyin ---
    @Query("SELECT * FROM char_pinyin WHERE hanzi = :hanzi AND pinyin = :pinyin LIMIT 1")
    suspend fun getCharPinyin(hanzi: String, pinyin: String): CharPinyinEntity?

    @Query("SELECT * FROM char_pinyin WHERE hanzi = :hanzi")
    suspend fun getCharPinyinsForHanzi(hanzi: String): List<CharPinyinEntity>

    @Query("SELECT DISTINCT hanzi FROM char_pinyin")
    suspend fun getAllDistinctHanzi(): List<String>

    @Query("SELECT COUNT(DISTINCT hanzi) FROM char_pinyin")
    suspend fun getDistinctHanziCount(): Int

    @Query("SELECT DISTINCT pinyin FROM char_pinyin WHERE hanzi IN (:hanziList)")
    suspend fun getPinyinsForHanziList(hanziList: List<String>): List<String>

    @Query("SELECT DISTINCT pinyin FROM char_pinyin LIMIT :limit")
    suspend fun getAllDistinctPinyins(limit: Int = 100): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCharPinyin(entity: CharPinyinEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCharPinyins(entities: List<CharPinyinEntity>): List<Long>

    @Query("DELETE FROM char_pinyin WHERE hanzi = :hanzi")
    suspend fun deleteCharPinyin(hanzi: String)

    // --- Chinese Words ---
    @Query("SELECT * FROM cword WHERE chinese = :chinese AND pinyin = :pinyin LIMIT 1")
    suspend fun getWord(chinese: String, pinyin: String): ChineseWordEntity?

    @Query("SELECT * FROM cword WHERE chinese = :chinese")
    suspend fun getWordsForChinese(chinese: String): List<ChineseWordEntity>

    @Query("SELECT * FROM cword WHERE pinyin = :pinyin")
    suspend fun getWordsForPinyin(pinyin: String): List<ChineseWordEntity>

    @Query("SELECT COUNT(*) FROM cword")
    suspend fun getWordCount(): Int

    @Query("SELECT DISTINCT chinese FROM cword")
    suspend fun getAllDistinctWords(): List<String>

    @Query("SELECT COUNT(*) FROM cword")
    fun getWordCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWord(entity: ChineseWordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWords(entities: List<ChineseWordEntity>): List<Long>

    @Query("DELETE FROM cword WHERE cword_id = :cwordId")
    suspend fun deleteWordById(cwordId: Long)

    @Query("DELETE FROM cword WHERE chinese = :chinese AND pinyin = :pinyin")
    suspend fun deleteWord(chinese: String, pinyin: String)

    @Query("DELETE FROM cword WHERE chinese = :chinese")
    suspend fun deleteWordsForChinese(chinese: String)

    // --- Word Segments ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSegments(segments: List<WordSegmentEntity>)

    @Query("SELECT * FROM cword_segment WHERE cword_id = :cwordId ORDER BY pos ASC")
    suspend fun getSegmentsForWord(cwordId: Long): List<WordSegmentEntity>

    // --- English Translations ---
    @Query("SELECT * FROM eword WHERE cword_id = :cwordId")
    suspend fun getTranslationsForWord(cwordId: Long): List<EnglishTranslationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTranslation(entity: EnglishTranslationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTranslations(entities: List<EnglishTranslationEntity>): List<Long>

    @Query("DELETE FROM eword WHERE eng_id = :engId")
    suspend fun deleteTranslationById(engId: Long)

    @Query("DELETE FROM eword WHERE cword_id = :cwordId AND translation = :translation")
    suspend fun deleteTranslation(cwordId: Long, translation: String)

    @Query("DELETE FROM eword WHERE cword_id = :cwordId")
    suspend fun deleteAllTranslationsForWord(cwordId: Long)

    @Query("SELECT DISTINCT translation FROM eword ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomTranslations(limit: Int): List<String>

    // --- Search & Composite Queries ---
    @Transaction
    @Query("SELECT * FROM cword ORDER BY cword_id DESC")
    suspend fun getAllWordsWithDetails(): List<WordWithDetails>

    @Transaction
    @Query("SELECT * FROM cword WHERE chinese = :chinese")
    suspend fun getWordsWithDetailsForChinese(chinese: String): List<WordWithDetails>

    @Transaction
    @Query("""
        SELECT c.* FROM cword c
        LEFT JOIN hsk_vocab h ON h.hanzi = c.chinese
        WHERE c.chinese LIKE '%' || :hanzi || '%' AND c.chinese != :hanzi
        ORDER BY
            COALESCE(h.hsk_level, 99) ASC,
            COALESCE(h.freq_rank, 999999) ASC,
            c.length ASC,
            c.cword_id ASC
    """)
    suspend fun getWordsContainingCharacter(hanzi: String): List<WordWithDetails>

    @Transaction
    @Query("""
        SELECT c.* FROM cword c
        LEFT JOIN hsk_vocab h ON h.hanzi = c.chinese
        WHERE c.chinese LIKE :prefix || '%'
        ORDER BY
            CASE WHEN c.chinese = :prefix THEN 0 ELSE 1 END,
            COALESCE(h.hsk_level, 99) ASC,
            COALESCE(h.freq_rank, 999999) ASC,
            c.length ASC,
            c.cword_id ASC
        LIMIT :limit
    """)
    suspend fun searchByChinesePrefix(prefix: String, limit: Int = 50): List<WordWithDetails>

    @Transaction
    @Query("""
        SELECT c.* FROM cword c
        LEFT JOIN hsk_vocab h ON h.hanzi = c.chinese
        WHERE c.pinyin LIKE :prefix || '%'
        ORDER BY
            CASE WHEN c.pinyin = :prefix THEN 0 ELSE 1 END,
            COALESCE(h.hsk_level, 99) ASC,
            COALESCE(h.freq_rank, 999999) ASC,
            c.length ASC,
            c.cword_id ASC
        LIMIT :limit
    """)
    suspend fun searchByPinyinPrefix(prefix: String, limit: Int = 50): List<WordWithDetails>

    @Transaction
    @Query("""
        SELECT c.* FROM cword c
        LEFT JOIN hsk_vocab h ON h.hanzi = c.chinese
        WHERE c.pinyin GLOB :rawPrefixGlob OR (:syllablePrefix != '' AND c.pinyin GLOB :syllablePrefix)
        ORDER BY
            CASE
                WHEN c.length = 1 AND (:exactSingle != '' AND c.pinyin GLOB :exactSingle) THEN 0
                WHEN c.length > 1 AND (:exactWord != '' AND c.pinyin GLOB :exactWord) THEN 1
                WHEN :syllablePrefix != '' AND c.pinyin GLOB :syllablePrefix THEN 2
                ELSE 3
            END ASC,
            COALESCE(h.hsk_level, 99) ASC,
            COALESCE(h.freq_rank, 999999) ASC,
            c.length ASC,
            c.cword_id ASC
        LIMIT :limit
    """)
    suspend fun searchByPinyin(
        exactSingle: String,
        exactWord: String,
        syllablePrefix: String,
        rawPrefixGlob: String,
        limit: Int = 50
    ): List<WordWithDetails>

    @Transaction
    @Query("""
        SELECT c.* FROM cword c
        INNER JOIN eword e ON e.cword_id = c.cword_id
        LEFT JOIN hsk_vocab h ON h.hanzi = c.chinese
        WHERE e.translation LIKE '%' || :query || '%'
        GROUP BY c.cword_id
        ORDER BY
            MIN(
                CASE
                    WHEN LOWER(e.translation) = LOWER(:query) THEN 0
                    WHEN LOWER(e.translation) LIKE LOWER(:query) || '%' THEN 1
                    ELSE 2
                END
            ) ASC,
            COALESCE(h.hsk_level, 99) ASC,
            COALESCE(h.freq_rank, 999999) ASC,
            c.length ASC,
            c.cword_id ASC
        LIMIT :limit
    """)
    suspend fun searchByEnglish(query: String, limit: Int = 50): List<WordWithDetails>

    @Query("""
        SELECT DISTINCT c.chinese FROM cword c
        INNER JOIN cword_segment cs ON cs.cword_id = c.cword_id
        INNER JOIN char_pinyin cp ON cp.char_pinyin_id = cs.char_pinyin_id
        LEFT JOIN hsk_vocab h ON h.hanzi = c.chinese
        WHERE cp.hanzi = :hanzi AND (cp.pinyin = :pinyin OR cp.pinyin LIKE :pinyin || '%') AND c.length > 1
        ORDER BY
            COALESCE(h.hsk_level, 99) ASC,
            COALESCE(h.freq_rank, 999999) ASC,
            c.length ASC
        LIMIT :limit
    """)
    suspend fun getCompoundWordsForCharPinyin(hanzi: String, pinyin: String, limit: Int = 4): List<String>

    // --- Recommendation & HSK Vocabulary Queries ---
    @Query("""
        SELECT c.* FROM cword c
        JOIN cword_segment cs ON c.cword_id = cs.cword_id
        JOIN char_pinyin cp ON cs.char_pinyin_id = cp.char_pinyin_id
        WHERE cp.hanzi IN (:knownChars) AND c.length > 1 AND c.length <= 4
        GROUP BY c.cword_id
        HAVING count(DISTINCT cs.pos) = c.length
        LIMIT :limit
    """)
    suspend fun getComposedWords(knownChars: List<String>, limit: Int = 200): List<ChineseWordEntity>

    @Query("""
        SELECT * FROM hsk_vocab
        WHERE hsk_level <= :maxLevel
        ORDER BY hsk_level ASC, freq_rank ASC
        LIMIT :limit
    """)
    suspend fun getHskCandidates(maxLevel: Int, limit: Int = 300): List<HskVocabEntity>

    @Query("SELECT * FROM hsk_vocab WHERE hanzi IN (:words)")
    suspend fun getHskVocabForWords(words: List<String>): List<HskVocabEntity>

    @Query("SELECT * FROM hsk_vocab WHERE hanzi = :hanzi LIMIT 1")
    suspend fun getHskVocabForWord(hanzi: String): HskVocabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHskVocab(entities: List<HskVocabEntity>)

    // --- Character Similarity ---
    @Query("""
        SELECT similar_hanzi FROM char_similarity
        WHERE hanzi = :hanzi
        ORDER BY score DESC
        LIMIT :limit
    """)
    suspend fun getSimilarCharacters(hanzi: String, limit: Int = 10): List<String>

    @Query("""
        SELECT * FROM char_similarity
        WHERE hanzi = :hanzi
        ORDER BY score DESC
        LIMIT :limit
    """)
    suspend fun getSimilarCharacterDetails(hanzi: String, limit: Int = 10): List<CharSimilarityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharSimilarities(entities: List<CharSimilarityEntity>)

    // --- Character Strokes ---
    @Query("SELECT * FROM character_strokes WHERE hanzi = :hanzi LIMIT 1")
    suspend fun getCharacterStroke(hanzi: String): CharacterStrokeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacterStrokes(entities: List<CharacterStrokeEntity>)
}
