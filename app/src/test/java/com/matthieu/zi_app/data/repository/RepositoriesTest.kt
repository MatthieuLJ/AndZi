package com.matthieu.zi_app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.ProgressDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoriesTest {

    private lateinit var andZiDb: AndZiDatabase
    private lateinit var progressDb: ProgressDatabase
    private lateinit var vocRepo: VocabularyRepository
    private lateinit var studyRepo: StudyRepository
    private lateinit var progressRepo: ProgressRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        andZiDb = AndZiDatabase.createInMemory(context)
        progressDb = ProgressDatabase.createInMemory(context)

        vocRepo = VocabularyRepository(andZiDb.vocabularyDao())
        studyRepo = StudyRepository(andZiDb.leitnerDao())
        progressRepo = ProgressRepository(progressDb.progressDao(), andZiDb.leitnerDao())
    }

    @After
    fun teardown() {
        andZiDb.close()
        progressDb.close()
    }

    @Test
    fun testVocabularyRepositoryAddAndSearch() = runBlocking {
        val voc = Vocabulary("谢谢", "xie4xie5", arrayListOf("thanks", "thank you"))
        val wordId = vocRepo.addVocabulary(voc)
        assertTrue(wordId > 0)

        val retrieved = vocRepo.getVocabularyForChinese("谢谢")
        assertEquals(1, retrieved.size)
        assertEquals("谢谢", retrieved[0].chinese)
        assertEquals("xie4xie5", retrieved[0].pinyin)
        assertEquals(2, retrieved[0].english.size)

        // Search by English
        val searchRes = vocRepo.search("thanks")
        assertEquals(1, searchRes.size)
        assertEquals("谢谢", searchRes[0].chinese)
    }

    @Test
    fun testVocabularyRepositorySearchSortedByHskAndFrequency() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("他", "ta1", arrayListOf("he", "him")))
        vocRepo.addVocabulary(Vocabulary("塌", "ta1", arrayListOf("to collapse", "to fall down")))
        vocRepo.addVocabulary(Vocabulary("她", "ta1", arrayListOf("she", "her")))
        vocRepo.addVocabulary(Vocabulary("它们", "ta1men5", arrayListOf("they (for inanimate things)")))

        vocRepo.insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "他", hskLevel = 1, freqRank = 10, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "她", hskLevel = 1, freqRank = 15, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "它们", hskLevel = 3, freqRank = 500, isChar = 0),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "塌", hskLevel = 6, freqRank = 3000, isChar = 1)
            )
        )

        // Search with exact 1-syllable tone "ta1" (exact pinyin matches before multi-syllable prefix)
        val res1 = vocRepo.search("ta1")
        assertEquals(4, res1.size)
        assertEquals("他", res1[0].chinese) // exact "ta1", HSK 1, freq 10
        assertEquals("她", res1[1].chinese) // exact "ta1", HSK 1, freq 15
        assertEquals("塌", res1[2].chinese) // exact "ta1", HSK 6
        assertEquals("它们", res1[3].chinese) // prefix "ta1men5", HSK 3

        // Search with toneless prefix "ta" (exact 1-syllable "ta1" matches before multi-syllable "ta1men5")
        val res2 = vocRepo.search("ta")
        assertEquals(4, res2.size)
        assertEquals("他", res2[0].chinese) // exact "ta1", HSK 1, freq 10
        assertEquals("她", res2[1].chinese) // exact "ta1", HSK 1, freq 15
        assertEquals("塌", res2[2].chinese) // exact "ta1", HSK 6, freq 3000
        assertEquals("它们", res2[3].chinese) // prefix "ta1men5", HSK 3, freq 500

        // Search with tone mark "tā"
        val res3 = vocRepo.search("tā")
        assertEquals(4, res3.size)
        assertEquals("他", res3[0].chinese)
        assertEquals("她", res3[1].chinese)
        assertEquals("塌", res3[2].chinese)
        assertEquals("它们", res3[3].chinese)
    }

    @Test
    fun testPinyinExactMatchBeforeDifferentPinyinSyllables() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("西", "xi1", arrayListOf("west")))
        vocRepo.addVocabulary(Vocabulary("系", "xi4", arrayListOf("system; department")))
        vocRepo.addVocabulary(Vocabulary("喜欢", "xi3huan5", arrayListOf("to like")))
        vocRepo.addVocabulary(Vocabulary("行", "xing2", arrayListOf("all right; capable")))
        vocRepo.addVocabulary(Vocabulary("先", "xian1", arrayListOf("early; first")))
        vocRepo.addVocabulary(Vocabulary("想", "xiang3", arrayListOf("to think")))
        vocRepo.addVocabulary(Vocabulary("下", "xia4", arrayListOf("down; below")))

        vocRepo.insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "行", hskLevel = 1, freqRank = 9, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "想", hskLevel = 1, freqRank = 18, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "下", hskLevel = 1, freqRank = 40, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "先", hskLevel = 1, freqRank = 45, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "喜欢", hskLevel = 1, freqRank = 46, isChar = 0),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "系", hskLevel = 1, freqRank = 65, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "西", hskLevel = 1, freqRank = 165, isChar = 1)
            )
        )

        // Searching "xi" must return exact "xi" characters first (系, 西), then compound "xi..." (喜欢), then other syllables (行, 想, 下, 先)
        val results = vocRepo.search("xi")
        assertEquals(7, results.size)
        assertEquals("系", results[0].chinese)
        assertEquals("西", results[1].chinese)
        assertEquals("喜欢", results[2].chinese)
        assertEquals("行", results[3].chinese)
        assertEquals("想", results[4].chinese)
        assertEquals("下", results[5].chinese)
        assertEquals("先", results[6].chinese)
    }

    @Test
    fun testMultiSyllablePinyinSearch() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("学习", "xue2xi2", arrayListOf("to study")))
        vocRepo.addVocabulary(Vocabulary("学生", "xue2sheng5", arrayListOf("student")))
        vocRepo.addVocabulary(Vocabulary("西安", "xi1an1", arrayListOf("Xi'an")))

        vocRepo.insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "学生", hskLevel = 1, freqRank = 122, isChar = 0),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "学习", hskLevel = 1, freqRank = 124, isChar = 0),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "西安", hskLevel = 99, freqRank = 2360, isChar = 0)
            )
        )

        // Searching "xuexi" without tones should rank "学习" first
        val xuexiResults = vocRepo.search("xuexi")
        assertEquals(2, xuexiResults.size)
        assertEquals("学习", xuexiResults[0].chinese)
        assertEquals("学生", xuexiResults[1].chinese)

        // Searching "xi'an" should rank "西安" first
        val xianResults = vocRepo.search("xi'an")
        assertEquals(1, xianResults.size)
        assertEquals("西安", xianResults[0].chinese)

        // Searching "xi an" with spaces should also rank "西安" first
        val xiSpaceAnResults = vocRepo.search("xi an")
        assertEquals(1, xiSpaceAnResults.size)
        assertEquals("西安", xiSpaceAnResults[0].chinese)
    }

    @Test
    fun testStudyRepositoryFlow() = runBlocking {
        val now = 100_000L

        // 1. Initial review: correct -> box 1
        val state1 = studyRepo.recordReviewResult("学", isCorrect = true, now = now)
        assertEquals(1, state1.box)
        assertEquals(1, state1.numTested)
        assertEquals(0, state1.numWrong)
        assertEquals(1, state1.streak)

        // 2. Second review: correct -> box 2
        val state2 = studyRepo.recordReviewResult("学", isCorrect = true, now = now + 1000)
        assertEquals(2, state2.box)
        assertEquals(2, state2.numTested)
        assertEquals(0, state2.numWrong)
        assertEquals(2, state2.streak)

        // 3. Third review: incorrect -> drops to box 0, resets streak
        val state3 = studyRepo.recordReviewResult("学", isCorrect = false, now = now + 2000)
        assertEquals(0, state3.box)
        assertEquals(3, state3.numTested)
        assertEquals(1, state3.numWrong)
        assertEquals(0, state3.streak)
    }

    @Test
    fun testProgressRepositorySnapshotAndDownsampling() = runBlocking {
        val minDate = 1_000_000L
        val maxDate = 2_000_000L

        for (i in 0..50) {
            val date = minDate + i * ((maxDate - minDate) / 50)
            progressRepo.recordProgressSnapshot(
                totalBox1 = 10,
                totalBox2 = 20,
                totalBox3 = 30,
                totalBox4 = 40,
                totalBox5 = 50,
                expiredBox2 = 0,
                expiredBox3 = 0,
                expiredBox4 = 0,
                expiredBox5 = 0,
                date = date
            )
        }

        val all = progressRepo.getAllProgress()
        assertEquals(51, all.size)

        // Distribute zoom levels
        progressRepo.distributeDownsampling(minDate, maxDate, maxNum = 10, maxZoom = 2)

        val zoom0Records = progressRepo.getProgressInRange(minDate, maxDate, maxZoom = 0)
        assertTrue(zoom0Records.size in 1..15)
    }
}
