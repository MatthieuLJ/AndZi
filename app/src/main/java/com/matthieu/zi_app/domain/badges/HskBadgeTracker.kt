package com.matthieu.zi_app.domain.badges

import com.matthieu.zi_app.data.preferences.UserPreferences
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HskLevelProgressInfo(
    val level: Int,
    val totalWords: Int,
    val box3PlusWords: Int,
    val isMastered: Boolean
)

class HskBadgeTracker(
    private val studyRepository: StudyRepository,
    private val dictionaryRepository: VocabularyRepository,
    private val userPreferences: UserPreferences,
    private val minBox: Int = DEFAULT_MIN_BOX
) {
    companion object {
        const val DEFAULT_MIN_BOX = 3

        val DEFAULT_HSK_COUNTS = mapOf(
            1 to 579,
            2 to 865,
            3 to 1095,
            4 to 1114,
            5 to 1210,
            6 to 1598
        )
    }

    suspend fun getHskProgress(): Map<Int, HskLevelProgressInfo> = withContext(Dispatchers.IO) {
        val totalCounts = try {
            val counts = dictionaryRepository.getHskLevelCounts()
            if (counts.isNotEmpty()) counts else DEFAULT_HSK_COUNTS
        } catch (e: Exception) {
            DEFAULT_HSK_COUNTS
        }

        val studyRecords = studyRepository.getAllRecords()
        val masteredHanzi = studyRecords.filter { it.box >= minBox }.map { it.hanzi }.distinct()
        val hskEntities = try {
            dictionaryRepository.getHskVocabForWords(masteredHanzi)
        } catch (e: Exception) {
            emptyList()
        }

        val masteredPerLevel = mutableMapOf<Int, MutableSet<String>>()
        for (entity in hskEntities) {
            if (entity.hskLevel in 1..6) {
                masteredPerLevel.getOrPut(entity.hskLevel) { mutableSetOf() }.add(entity.hanzi)
            }
        }

        (1..6).associateWith { lvl ->
            val total = totalCounts[lvl] ?: DEFAULT_HSK_COUNTS[lvl] ?: 0
            val mastered = masteredPerLevel[lvl]?.size ?: 0
            val isMastered = total > 0 && mastered >= total
            HskLevelProgressInfo(
                level = lvl,
                totalWords = total,
                box3PlusWords = mastered,
                isMastered = isMastered
            )
        }
    }

    suspend fun checkAndUnlockHskBadges(now: Long = System.currentTimeMillis()): List<BadgeId> = withContext(Dispatchers.IO) {
        val progressMap = getHskProgress()
        val newlyUnlocked = mutableListOf<BadgeId>()

        for ((level, info) in progressMap) {
            val badge = BadgeId.fromHskLevel(level) ?: continue
            // Condition: all words reached Box 3+ (total > 0 && mastered >= total)
            if (info.isMastered && !userPreferences.isBadgeUnlocked(badge)) {
                if (userPreferences.unlockBadge(badge, now)) {
                    newlyUnlocked.add(badge)
                }
            }
        }
        newlyUnlocked
    }
}
