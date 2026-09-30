package com.matthieu.zi_app.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.data.local.dao.LeitnerDao
import com.matthieu.zi_app.data.local.dao.VocabularyDao
import com.matthieu.zi_app.data.local.entity.CharPinyinEntity
import com.matthieu.zi_app.data.local.entity.ChineseWordEntity
import com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity
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
class LeitnerDaoTest {

    private lateinit var db: AndZiDatabase
    private lateinit var leitnerDao: LeitnerDao
    private lateinit var vocDao: VocabularyDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AndZiDatabase.createInMemory(context)
        leitnerDao = db.leitnerDao()
        vocDao = db.vocabularyDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testLeitnerRecordCrud() = runBlocking {
        val now = 1_000_000L
        val record = LeitnerRecordEntity(
            hanzi = "水",
            box = 2,
            expTime = now + 100_000,
            numTested = 4,
            numWrong = 1,
            streak = 2,
            dateAdded = now
        )
        leitnerDao.insertOrUpdateRecord(record)

        val retrieved = leitnerDao.getRecord("水")
        assertNotNull(retrieved)
        assertEquals("水", retrieved?.hanzi)
        assertEquals(2, retrieved?.box)
        assertEquals(4, retrieved?.numTested)

        // Delete
        leitnerDao.deleteRecord("水")
        val afterDelete = leitnerDao.getRecord("水")
        assertEquals(null, afterDelete)
    }

    @Test
    fun testStudyQueueOrdering() = runBlocking {
        val now = 1_000_000L

        // Add characters to vocabulary
        vocDao.insertCharPinyin(CharPinyinEntity(hanzi = "一", pinyin = "yi1"))
        vocDao.insertCharPinyin(CharPinyinEntity(hanzi = "二", pinyin = "er4"))
        vocDao.insertWord(ChineseWordEntity(chinese = "三", pinyin = "san1", length = 1))

        // 一 has box 0 (due now)
        leitnerDao.insertOrUpdateRecord(LeitnerRecordEntity(hanzi = "一", box = 0, expTime = now - 500))
        // 二 has box 2, but expired earlier
        leitnerDao.insertOrUpdateRecord(LeitnerRecordEntity(hanzi = "二", box = 2, expTime = now - 2000))
        // 三 has box 1, expired
        leitnerDao.insertOrUpdateRecord(LeitnerRecordEntity(hanzi = "三", box = 1, expTime = now - 1000))

        val queue = leitnerDao.getStudyQueue(now, limit = 10)
        assertEquals(3, queue.size)
        // Ordered by forced_box DESC, exp_time ASC
        // Box 2 -> 二
        // Box 1 -> 三
        // Box 0 -> 一
        assertEquals("二", queue[0])
        assertEquals("三", queue[1])
        assertEquals("一", queue[2])
    }

    @Test
    fun testBoxCounts() = runBlocking {
        leitnerDao.insertOrUpdateRecord(LeitnerRecordEntity(hanzi = "A", box = 1, expTime = 0))
        leitnerDao.insertOrUpdateRecord(LeitnerRecordEntity(hanzi = "B", box = 1, expTime = 0))
        leitnerDao.insertOrUpdateRecord(LeitnerRecordEntity(hanzi = "C", box = 3, expTime = 0))

        var counts = leitnerDao.getBoxCounts().associate { it.box to it.count }
        assertEquals(2, counts[1])
        assertEquals(1, counts[3])

        // Add unstudied characters and words to vocabulary (should appear in Box 0)
        vocDao.insertCharPinyin(CharPinyinEntity(hanzi = "D", pinyin = "de5"))
        vocDao.insertWord(ChineseWordEntity(chinese = "EF", pinyin = "e1f1", length = 2))

        counts = leitnerDao.getBoxCounts().associate { it.box to it.count }
        assertEquals(2, counts[0]) // D and EF are unstudied -> Box 0
        assertEquals(2, counts[1]) // A and B -> Box 1
        assertEquals(1, counts[3]) // C -> Box 3

        // Reset all scores: delete all leitner records, everything falls back to Box 0
        leitnerDao.deleteAllRecords()
        counts = leitnerDao.getBoxCounts().associate { it.box to it.count }
        assertEquals(2, counts[0]) // D and EF in Box 0
        assertEquals(0, counts[1] ?: 0)
        assertEquals(0, counts[3] ?: 0)
    }
}
