package com.matthieu.zi_app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.ProgressDatabase
import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DatabaseBackupRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var andZiDb: AndZiDatabase
    private lateinit var progressDb: ProgressDatabase
    private lateinit var vocRepo: VocabularyRepository
    private lateinit var studyRepo: StudyRepository
    private lateinit var progressRepo: ProgressRepository
    private lateinit var backupRepo: DatabaseBackupRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext<Context>()

        andZiDb = Room.databaseBuilder(
            context,
            AndZiDatabase::class.java,
            AndZiDatabase.DATABASE_NAME
        ).allowMainThreadQueries().build()

        progressDb = Room.databaseBuilder(
            context,
            ProgressDatabase::class.java,
            ProgressDatabase.DATABASE_NAME
        ).allowMainThreadQueries().build()

        vocRepo = VocabularyRepository(andZiDb.vocabularyDao())
        studyRepo = StudyRepository(andZiDb.leitnerDao())
        progressRepo = ProgressRepository(progressDb.progressDao(), andZiDb.leitnerDao())
        backupRepo = DatabaseBackupRepository(context, andZiDb, progressDb)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        andZiDb.close()
        progressDb.close()
        context.deleteDatabase(AndZiDatabase.DATABASE_NAME)
        context.deleteDatabase(ProgressDatabase.DATABASE_NAME)
    }

    @Test
    fun testExportAndInspectStudyDatabaseWithScores() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("书", "shu1", arrayListOf("book")))
        vocRepo.addVocabulary(Vocabulary("苹果", "ping2guo3", arrayListOf("apple")))
        studyRepo.recordReviewResult("书", isCorrect = true)

        val outputStream = ByteArrayOutputStream()
        backupRepo.exportStudyDatabase(outputStream, includeScores = true)

        val bytes = outputStream.toByteArray()
        assertTrue(bytes.isNotEmpty())

        val inputStream = ByteArrayInputStream(bytes)
        val inspection = backupRepo.inspectIncomingStudyDatabase(inputStream)

        assertEquals(2, inspection.wordCount)
        assertEquals(2, inspection.translationCount)
        assertTrue(inspection.scoreCount >= 1)

        backupRepo.cleanupTempFile(inspection.tempFilePath)
    }

    @Test
    fun testExportStudyDatabaseWithoutScoresStripsScores() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("水", "shui3", arrayListOf("water")))
        studyRepo.recordReviewResult("水", isCorrect = true)

        val outputStream = ByteArrayOutputStream()
        backupRepo.exportStudyDatabase(outputStream, includeScores = false)

        val bytes = outputStream.toByteArray()
        val inputStream = ByteArrayInputStream(bytes)
        val inspection = backupRepo.inspectIncomingStudyDatabase(inputStream)

        assertEquals(1, inspection.wordCount)
        assertEquals(0, inspection.scoreCount)

        backupRepo.cleanupTempFile(inspection.tempFilePath)
    }

    @Test
    fun testMergeStudyDatabaseWithConflictingScoresKeepCurrent() = runBlocking {
        // Step 1: In a second database, prepare incoming backup data
        val otherDbFile = File(context.cacheDir, "other_test_db.db")
        if (otherDbFile.exists()) otherDbFile.delete()

        val otherDb = Room.databaseBuilder(
            context,
            AndZiDatabase::class.java,
            otherDbFile.absolutePath
        ).allowMainThreadQueries().build()

        val otherVocRepo = VocabularyRepository(otherDb.vocabularyDao())
        val otherStudyRepo = StudyRepository(otherDb.leitnerDao())

        otherVocRepo.addVocabulary(Vocabulary("猫", "mao1", arrayListOf("cat", "feline")))
        otherVocRepo.addVocabulary(Vocabulary("狗", "gou3", arrayListOf("dog")))
        // In incoming DB: "猫" is tested multiple times
        otherStudyRepo.recordReviewResult("猫", isCorrect = true)
        otherStudyRepo.recordReviewResult("猫", isCorrect = true)
        otherStudyRepo.recordReviewResult("狗", isCorrect = true)

        val backupOutput = ByteArrayOutputStream()
        val otherBackupRepo = DatabaseBackupRepository(context, otherDb, progressDb)
        otherBackupRepo.exportStudyDatabase(backupOutput, includeScores = true)
        otherDb.close()
        otherDbFile.delete()

        // Step 2: In primary DB, set "猫" to Box 1 with only 1 review and only "cat" translation
        vocRepo.addVocabulary(Vocabulary("猫", "mao1", arrayListOf("cat")))
        studyRepo.recordReviewResult("猫", isCorrect = false) // Box 0, tested 1
        val initialMaoState = studyRepo.getLeitnerState("猫")
        assertNotNull(initialMaoState)

        // Step 3: Inspect incoming backup
        val inspection = backupRepo.inspectIncomingStudyDatabase(ByteArrayInputStream(backupOutput.toByteArray()))
        assertEquals(2, inspection.wordCount)
        assertTrue(inspection.conflictingScoreCount >= 1)

        // Step 4: Merge with overwriteScoresOnConflict = false
        val mergeResult = backupRepo.mergeStudyDatabase(inspection, overwriteScoresOnConflict = false)
        assertTrue(mergeResult.wordsAdded >= 1) // "狗" was added
        assertTrue(mergeResult.translationsAdded >= 1) // "feline" was added to "猫"

        // Verify "猫" preserved its original score
        val postMergeMaoState = studyRepo.getLeitnerState("猫")
        assertNotNull(postMergeMaoState)
        assertEquals(initialMaoState!!.box, postMergeMaoState!!.box)
        assertEquals(initialMaoState.numTested, postMergeMaoState.numTested)

        // Verify "狗" was imported with its incoming score
        val gouState = studyRepo.getLeitnerState("狗")
        assertNotNull(gouState)
        assertTrue(gouState!!.numTested > 0)
    }

    @Test
    fun testMergeStudyDatabaseWithConflictingScoresOverwrite() = runBlocking {
        // Step 1: Create incoming database
        val otherDbFile = File(context.cacheDir, "other_test_db2.db")
        if (otherDbFile.exists()) otherDbFile.delete()

        val otherDb = Room.databaseBuilder(
            context,
            AndZiDatabase::class.java,
            otherDbFile.absolutePath
        ).allowMainThreadQueries().build()

        val otherVocRepo = VocabularyRepository(otherDb.vocabularyDao())
        val otherStudyRepo = StudyRepository(otherDb.leitnerDao())

        otherVocRepo.addVocabulary(Vocabulary("鸟", "niao3", arrayListOf("bird")))
        otherStudyRepo.recordReviewResult("鸟", isCorrect = true)
        otherStudyRepo.recordReviewResult("鸟", isCorrect = true)
        val incomingNiaoBox = otherStudyRepo.getLeitnerState("鸟")!!.box

        val backupOutput = ByteArrayOutputStream()
        val otherBackupRepo = DatabaseBackupRepository(context, otherDb, progressDb)
        otherBackupRepo.exportStudyDatabase(backupOutput, includeScores = true)
        otherDb.close()
        otherDbFile.delete()

        // Step 2: In primary DB, set "鸟" to Box 0
        vocRepo.addVocabulary(Vocabulary("鸟", "niao3", arrayListOf("bird")))
        studyRepo.recordReviewResult("鸟", isCorrect = false)

        // Step 3: Inspect and merge with overwriteScoresOnConflict = true
        val inspection = backupRepo.inspectIncomingStudyDatabase(ByteArrayInputStream(backupOutput.toByteArray()))
        backupRepo.mergeStudyDatabase(inspection, overwriteScoresOnConflict = true)

        // Step 4: Verify "鸟" score was updated to incoming value
        val postMergeNiaoState = studyRepo.getLeitnerState("鸟")
        assertNotNull(postMergeNiaoState)
        assertEquals(incomingNiaoBox, postMergeNiaoState!!.box)
    }

    @Test
    fun testProgressDatabaseExportAndImport() = runBlocking {
        val record1 = ProgressRecordEntity(
            date = 1000L,
            totalBox1 = 5,
            totalBox2 = 3,
            totalBox3 = 2,
            totalBox4 = 1,
            totalBox5 = 0,
            expiredBox2 = 1,
            expiredBox3 = 0,
            expiredBox4 = 0,
            expiredBox5 = 0,
            zoom = 0
        )
        val record2 = ProgressRecordEntity(
            date = 2000L,
            totalBox1 = 6,
            totalBox2 = 4,
            totalBox3 = 2,
            totalBox4 = 1,
            totalBox5 = 1,
            expiredBox2 = 0,
            expiredBox3 = 0,
            expiredBox4 = 0,
            expiredBox5 = 0,
            zoom = 0
        )
        progressDb.progressDao().insertProgressList(listOf(record1, record2))

        val output = ByteArrayOutputStream()
        backupRepo.exportProgressDatabase(output)
        val bytes = output.toByteArray()
        assertTrue(bytes.isNotEmpty())

        progressDb.progressDao().clearAll()
        assertEquals(0, progressDb.progressDao().getRecordCount())

        val importedCount = backupRepo.importProgressDatabase(ByteArrayInputStream(bytes))
        assertEquals(2, importedCount)
        assertEquals(2, progressDb.progressDao().getRecordCount())
    }
}
