package com.matthieu.zi_app.data.repository

import com.matthieu.zi_app.LeitnerScheduler
import com.matthieu.zi_app.LeitnerState
import com.matthieu.zi_app.data.local.dao.BoxCount
import com.matthieu.zi_app.data.local.dao.LeitnerDao
import com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity
import com.matthieu.zi_app.domain.srs.EbisuSpacedRepetitionEngine
import com.matthieu.zi_app.domain.srs.ReviewGrade
import com.matthieu.zi_app.domain.srs.ReviewState
import com.matthieu.zi_app.domain.srs.SpacedRepetitionEngine
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class StudyRepository(
    private val leitnerDao: LeitnerDao,
    private val srsEngine: SpacedRepetitionEngine = EbisuSpacedRepetitionEngine()
) {

    suspend fun getStudyQueue(limit: Int = 30, now: Long = System.currentTimeMillis()): List<String> = withContext(Dispatchers.IO) {
        leitnerDao.getStudyQueue(now, limit)
    }

    suspend fun recordReviewGrade(
        hanzi: String,
        grade: ReviewGrade,
        now: Long = System.currentTimeMillis()
    ): ReviewState = withContext(Dispatchers.IO) {
        val currentRecord = leitnerDao.getRecord(hanzi)
        val currentState = currentRecord?.let { toReviewState(it) }

        val scheduleResult = srsEngine.calculateNextReview(hanzi, currentState, grade, now)
        leitnerDao.insertOrUpdateRecord(toEntity(scheduleResult.updatedState))
        scheduleResult.updatedState
    }

    suspend fun overrideReviewGrade(
        hanzi: String,
        previousState: LeitnerState?,
        grade: ReviewGrade,
        now: Long = System.currentTimeMillis()
    ): ReviewState = withContext(Dispatchers.IO) {
        val baseState = previousState?.let {
            ReviewState(
                cardId = it.hanzi,
                box = it.box,
                nextReviewTimestamp = it.expTime,
                reviewCount = it.numTested,
                lapseCount = it.numWrong,
                streak = it.streak,
                dateAdded = it.dateAdded
            )
        }
        val scheduleResult = srsEngine.calculateNextReview(hanzi, baseState, grade, now)
        leitnerDao.insertOrUpdateRecord(toEntity(scheduleResult.updatedState))
        scheduleResult.updatedState
    }

    suspend fun recordReviewResult(
        hanzi: String,
        isCorrect: Boolean,
        now: Long = System.currentTimeMillis()
    ): LeitnerState = withContext(Dispatchers.IO) {
        val grade = ReviewGrade.fromBinary(isCorrect)
        val reviewState = recordReviewGrade(hanzi, grade, now)
        toLeitnerState(reviewState)
    }

    suspend fun recordGuidedWritingResult(
        hanzi: String,
        strokeCount: Int,
        now: Long = System.currentTimeMillis()
    ): LeitnerState = withContext(Dispatchers.IO) {
        val currentRecord = leitnerDao.getRecord(hanzi)
        val currentBox = currentRecord?.box ?: 0
        val currentStreak = currentRecord?.streak ?: 0
        val numTested = (currentRecord?.numTested ?: 0) + 1
        val numWrong = currentRecord?.numWrong ?: 0

        val (newBox, newStreak, newNumWrong) = when {
            strokeCount <= 3 -> Triple(maxOf(0, currentBox - 1), 0, numWrong + 1)
            else -> Triple(currentBox, currentStreak, numWrong)
        }
        val oneDayMillis = 24L * 60 * 60 * 1000
        val newExpTime = now + oneDayMillis

        val updatedRecord = LeitnerRecordEntity(
            hanzi = hanzi,
            box = newBox,
            expTime = newExpTime,
            numTested = numTested,
            numWrong = newNumWrong,
            streak = newStreak,
            dateAdded = currentRecord?.dateAdded ?: now
        )
        leitnerDao.insertOrUpdateRecord(updatedRecord)
        toDomainState(updatedRecord)
    }

    suspend fun restartStudy(
        hanzi: String,
        now: Long = System.currentTimeMillis()
    ): LeitnerState = withContext(Dispatchers.IO) {
        val currentRecord = leitnerDao.getRecord(hanzi)
        val currentState = currentRecord?.let { toReviewState(it) }
        val scheduleResult = srsEngine.resetCard(hanzi, currentState, now)
        leitnerDao.insertOrUpdateRecord(toEntity(scheduleResult.updatedState))
        toLeitnerState(scheduleResult.updatedState)
    }

    /**
     * Resets the expiry timer for an exposed character or word without modifying its mastery box.
     * This ensures recently encountered items are postponed according to their current mastery level
     * without artificially promoting them as if they passed a direct active test.
     */
    suspend fun refreshCardTimer(
        hanzi: String,
        now: Long = System.currentTimeMillis()
    ): LeitnerState = withContext(Dispatchers.IO) {
        val currentRecord = leitnerDao.getRecord(hanzi)
        val box = currentRecord?.box ?: 0
        val delay = if (box > 0) {
            LeitnerScheduler.calculateAdaptiveExpiryDelay(
                box = box,
                numTested = currentRecord?.numTested ?: 0,
                numWrong = currentRecord?.numWrong ?: 0,
                lastExpiration = currentRecord?.expTime ?: now,
                now = now
            )
        } else {
            LeitnerScheduler.BASE_DELAY_MS
        }
        val updatedRecord = LeitnerRecordEntity(
            hanzi = hanzi,
            box = box,
            expTime = now + delay,
            numTested = currentRecord?.numTested ?: 0,
            numWrong = currentRecord?.numWrong ?: 0,
            streak = currentRecord?.streak ?: 0,
            dateAdded = currentRecord?.dateAdded ?: now
        )
        leitnerDao.insertOrUpdateRecord(updatedRecord)
        toDomainState(updatedRecord)
    }

    suspend fun refreshCardTimers(
        hanziList: Collection<String>,
        now: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val distinct = hanziList.distinct()
        for (hanzi in distinct) {
            refreshCardTimer(hanzi, now)
        }
    }

    suspend fun getLeitnerState(hanzi: String): LeitnerState? = withContext(Dispatchers.IO) {
        leitnerDao.getRecord(hanzi)?.let { toDomainState(it) }
    }

    fun getLeitnerStateFlow(hanzi: String): Flow<LeitnerState?> {
        return leitnerDao.getRecordFlow(hanzi).map { record ->
            record?.let { toDomainState(it) }
        }
    }

    suspend fun getNumExpired(now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        leitnerDao.getNumExpired(now)
    }

    fun getNumExpiredFlow(now: Long = System.currentTimeMillis()): Flow<Int> {
        return leitnerDao.getNumExpiredFlow(now)
    }

    suspend fun getBoxCounts(): List<BoxCount> = withContext(Dispatchers.IO) {
        leitnerDao.getBoxCounts()
    }

    suspend fun resetAllScores() = withContext(Dispatchers.IO) {
        leitnerDao.deleteAllRecords()
    }

    suspend fun deleteRecord(hanzi: String) = withContext(Dispatchers.IO) {
        leitnerDao.deleteRecord(hanzi)
    }

    suspend fun getAllRecords(): List<LeitnerState> = withContext(Dispatchers.IO) {
        leitnerDao.getAllStudyRecords().map { toDomainState(it) }
    }

    suspend fun getAllReviewStates(): List<ReviewState> = withContext(Dispatchers.IO) {
        leitnerDao.getAllStudyRecords().map { toReviewState(it) }
    }

    private fun toReviewState(entity: LeitnerRecordEntity): ReviewState {
        return ReviewState(
            cardId = entity.hanzi,
            box = entity.box,
            nextReviewTimestamp = entity.expTime,
            reviewCount = entity.numTested,
            lapseCount = entity.numWrong,
            streak = entity.streak,
            dateAdded = entity.dateAdded
        )
    }

    private fun toLeitnerState(state: ReviewState): LeitnerState {
        return LeitnerState(
            hanzi = state.cardId,
            box = state.box,
            expTime = state.nextReviewTimestamp,
            numTested = state.reviewCount,
            numWrong = state.lapseCount,
            streak = state.streak,
            dateAdded = state.dateAdded
        )
    }

    private fun toEntity(state: ReviewState): LeitnerRecordEntity {
        return LeitnerRecordEntity(
            hanzi = state.cardId,
            box = state.box,
            expTime = state.nextReviewTimestamp,
            numTested = state.reviewCount,
            numWrong = state.lapseCount,
            streak = state.streak,
            dateAdded = state.dateAdded
        )
    }

    private fun toDomainState(entity: LeitnerRecordEntity): LeitnerState {
        return LeitnerState(
            hanzi = entity.hanzi,
            box = entity.box,
            expTime = entity.expTime,
            numTested = entity.numTested,
            numWrong = entity.numWrong,
            streak = entity.streak,
            dateAdded = entity.dateAdded
        )
    }
}
