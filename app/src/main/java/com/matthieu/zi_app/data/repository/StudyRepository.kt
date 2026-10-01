package com.matthieu.zi_app.data.repository

import com.matthieu.zi_app.LeitnerState
import com.matthieu.zi_app.data.local.dao.BoxCount
import com.matthieu.zi_app.data.local.dao.LeitnerDao
import com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity
import com.matthieu.zi_app.domain.srs.EbisuSpacedRepetitionEngine
import com.matthieu.zi_app.domain.srs.ReviewGrade
import com.matthieu.zi_app.domain.srs.ReviewState
import com.matthieu.zi_app.domain.srs.SpacedRepetitionEngine
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

    suspend fun recordReviewResult(
        hanzi: String,
        isCorrect: Boolean,
        now: Long = System.currentTimeMillis()
    ): LeitnerState = withContext(Dispatchers.IO) {
        val grade = ReviewGrade.fromBinary(isCorrect)
        val reviewState = recordReviewGrade(hanzi, grade, now)
        toLeitnerState(reviewState)
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
        leitnerDao.getAllRecords().map { toDomainState(it) }
    }

    suspend fun getAllReviewStates(): List<ReviewState> = withContext(Dispatchers.IO) {
        leitnerDao.getAllRecords().map { toReviewState(it) }
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
