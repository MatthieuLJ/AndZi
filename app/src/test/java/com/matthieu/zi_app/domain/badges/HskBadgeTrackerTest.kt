package com.matthieu.zi_app.domain.badges

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.entity.HskVocabEntity
import com.matthieu.zi_app.data.preferences.UserPreferences
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HskBadgeTrackerTest {

    private lateinit var andZiDb: AndZiDatabase
    private lateinit var vocRepo: VocabularyRepository
    private lateinit var studyRepo: StudyRepository
    private lateinit var userPrefs: UserPreferences
    private lateinit var hskTracker: HskBadgeTracker

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        andZiDb = AndZiDatabase.createInMemory(context)
        vocRepo = VocabularyRepository(andZiDb.vocabularyDao())
        studyRepo = StudyRepository(andZiDb.leitnerDao())

        val prefs = context.getSharedPreferences(UserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        userPrefs = UserPreferences(context)

        hskTracker = HskBadgeTracker(
            studyRepository = studyRepo,
            dictionaryRepository = vocRepo,
            userPreferences = userPrefs,
            minBox = 3
        )
    }

    @After
    fun teardown() {
        andZiDb.close()
    }

    @Test
    fun testHskLevelProgressWithBoxThreshold() = runBlocking {
        // Insert 3 HSK 1 words
        vocRepo.insertHskVocab(
            listOf(
                HskVocabEntity(hanzi = "我", hskLevel = 1, freqRank = 1),
                HskVocabEntity(hanzi = "你", hskLevel = 1, freqRank = 2),
                HskVocabEntity(hanzi = "他", hskLevel = 1, freqRank = 3)
            )
        )

        // Add to study list (restartStudy initializes card at box 0)
        studyRepo.restartStudy("我", 1000L) // box 0
        studyRepo.restartStudy("你", 1000L) // box 0
        studyRepo.restartStudy("他", 1000L) // box 0

        // In box 0, mastered should be 0
        var progress = hskTracker.getHskProgress()
        assertEquals(3, progress[1]?.totalWords)
        assertEquals(0, progress[1]?.box3PlusWords)
        assertFalse(progress[1]?.isMastered == true)

        // Advance "我" to Box 1, "你" to Box 2 -> still < Box 3
        studyRepo.recordReviewGrade("我", ReviewGrade.GOOD, 2000L) // box 1
        studyRepo.recordReviewGrade("你", ReviewGrade.GOOD, 2000L) // box 1
        studyRepo.recordReviewGrade("你", ReviewGrade.GOOD, 3000L) // box 2

        progress = hskTracker.getHskProgress()
        assertEquals(0, progress[1]?.box3PlusWords)
        assertFalse(progress[1]?.isMastered == true)

        // Advance "我" to Box 3
        studyRepo.recordReviewGrade("我", ReviewGrade.GOOD, 3000L) // box 2
        studyRepo.recordReviewGrade("我", ReviewGrade.GOOD, 4000L) // box 3

        progress = hskTracker.getHskProgress()
        assertEquals(1, progress[1]?.box3PlusWords)
        assertFalse(progress[1]?.isMastered == true)

        // Advance "你" and "他" to Box 3+
        studyRepo.recordReviewGrade("你", ReviewGrade.GOOD, 4000L) // box 3
        studyRepo.recordReviewGrade("他", ReviewGrade.EASY, 2000L) // box 2
        studyRepo.recordReviewGrade("他", ReviewGrade.GOOD, 3000L) // box 3

        progress = hskTracker.getHskProgress()
        assertEquals(3, progress[1]?.box3PlusWords)
        assertTrue(progress[1]?.isMastered == true)
    }

    @Test
    fun testUnlockHskBadgeAndStickinessWhenCardDrops() = runBlocking {
        // Insert 2 HSK 1 words
        vocRepo.insertHskVocab(
            listOf(
                HskVocabEntity(hanzi = "水", hskLevel = 1, freqRank = 1),
                HskVocabEntity(hanzi = "火", hskLevel = 1, freqRank = 2)
            )
        )

        studyRepo.restartStudy("水", 1000L)
        studyRepo.restartStudy("火", 1000L)

        // Promote both to Box 3
        for (i in 1..3) {
            studyRepo.recordReviewGrade("水", ReviewGrade.GOOD, 1000L + i * 1000L)
            studyRepo.recordReviewGrade("火", ReviewGrade.GOOD, 1000L + i * 1000L)
        }

        assertFalse(userPrefs.isBadgeUnlocked(BadgeId.HSK_1))

        // Check and unlock badges
        val newlyUnlocked = hskTracker.checkAndUnlockHskBadges(5000L)
        assertEquals(1, newlyUnlocked.size)
        assertEquals(BadgeId.HSK_1, newlyUnlocked.first())
        assertTrue(userPrefs.isBadgeUnlocked(BadgeId.HSK_1))

        // Subsequent check returns empty (already unlocked)
        val secondCheck = hskTracker.checkAndUnlockHskBadges(6000L)
        assertTrue(secondCheck.isEmpty())

        // "Once acquired, that badge sticks even if cards go back down."
        // Lapse "水" back to Box 0 with AGAIN grade
        studyRepo.recordReviewGrade("水", ReviewGrade.AGAIN, 7000L)
        val recordWater = studyRepo.getLeitnerState("水")
        assertEquals(0, recordWater?.box)

        // Badge is STILL unlocked!
        assertTrue(userPrefs.isBadgeUnlocked(BadgeId.HSK_1))

        val progress = hskTracker.getHskProgress()
        val summary = userPrefs.getBadgesSummary(hskProgress = progress)
        val hsk1BadgeItem = summary.badges.first { it.badge == BadgeId.HSK_1 }
        assertTrue(hsk1BadgeItem.isUnlocked)
        assertEquals(2, hsk1BadgeItem.currentProgress)
        assertEquals(2, hsk1BadgeItem.target)
    }
}
