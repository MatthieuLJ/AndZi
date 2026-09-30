package com.matthieu.zi_app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity
import kotlinx.coroutines.flow.Flow

data class BoxCount(
    val box: Int,
    val count: Int
)

@Dao
interface LeitnerDao {

    @Query("SELECT * FROM leitner WHERE hanzi = :hanzi LIMIT 1")
    suspend fun getRecord(hanzi: String): LeitnerRecordEntity?

    @Query("SELECT * FROM leitner WHERE hanzi = :hanzi LIMIT 1")
    fun getRecordFlow(hanzi: String): Flow<LeitnerRecordEntity?>

    @Query("SELECT * FROM leitner")
    suspend fun getAllRecords(): List<LeitnerRecordEntity>

    @Query("SELECT * FROM leitner")
    fun getAllRecordsFlow(): Flow<List<LeitnerRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRecord(record: LeitnerRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRecords(records: List<LeitnerRecordEntity>)

    @Query("DELETE FROM leitner WHERE hanzi = :hanzi")
    suspend fun deleteRecord(hanzi: String)

    @Query("DELETE FROM leitner")
    suspend fun deleteAllRecords()

    @Query("SELECT MIN(exp_time) FROM leitner WHERE exp_time > :now")
    suspend fun getEarliestExpiringTime(now: Long): Long?

    @Query("""
        SELECT chinese FROM (
            SELECT char_pinyin.hanzi AS chinese, 
                   IFNULL(leitner.exp_time, 0) AS exp_time, 
                   IFNULL(leitner.box, 0) AS forced_box 
            FROM char_pinyin 
            LEFT JOIN leitner ON leitner.hanzi = char_pinyin.hanzi 
            WHERE (leitner.box IS NULL) OR (leitner.box = 0) OR (leitner.exp_time < :now) 
            UNION 
            SELECT cword.chinese AS chinese,
                   IFNULL(leitner.exp_time, 0) AS exp_time, 
                   IFNULL(leitner.box, 0) AS forced_box 
            FROM cword 
            LEFT JOIN leitner ON leitner.hanzi = cword.chinese 
            WHERE (leitner.box IS NULL) OR (leitner.box = 0) OR (leitner.exp_time < :now)
        ) 
        GROUP BY chinese ORDER BY forced_box DESC, exp_time ASC LIMIT :limit
    """)
    suspend fun getStudyQueue(now: Long, limit: Int): List<String>

    @Query("""
        SELECT COUNT(DISTINCT chinese) FROM (
            SELECT cword.chinese AS chinese FROM cword 
            LEFT JOIN leitner ON leitner.hanzi = cword.chinese 
            WHERE (leitner.exp_time < :now) OR (leitner.box IS NULL) OR (leitner.box = 0) 
            UNION 
            SELECT char_pinyin.hanzi AS chinese FROM char_pinyin 
            LEFT JOIN leitner ON leitner.hanzi = char_pinyin.hanzi 
            WHERE (leitner.exp_time < :now) OR (leitner.box IS NULL) OR (leitner.box = 0)
        )
    """)
    suspend fun getNumExpired(now: Long): Int

    @Query("""
        SELECT COUNT(DISTINCT chinese) FROM (
            SELECT cword.chinese AS chinese FROM cword 
            LEFT JOIN leitner ON leitner.hanzi = cword.chinese 
            WHERE (leitner.exp_time < :now) OR (leitner.box IS NULL) OR (leitner.box = 0) 
            UNION 
            SELECT char_pinyin.hanzi AS chinese FROM char_pinyin 
            LEFT JOIN leitner ON leitner.hanzi = char_pinyin.hanzi 
            WHERE (leitner.exp_time < :now) OR (leitner.box IS NULL) OR (leitner.box = 0)
        )
    """)
    fun getNumExpiredFlow(now: Long): Flow<Int>

    @Query("SELECT box, COUNT(*) as count FROM leitner GROUP BY box")
    suspend fun getBoxCounts(): List<BoxCount>
}
