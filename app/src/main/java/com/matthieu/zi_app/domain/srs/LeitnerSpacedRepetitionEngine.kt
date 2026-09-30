package com.matthieu.zi_app.domain.srs

import com.matthieu.zi_app.LeitnerScheduler
import com.matthieu.zi_app.LeitnerState
import kotlin.math.max
import kotlin.math.min

/**
 * Adaptive Leitner 0-9 box Spaced Repetition Engine.
 *
 * Mechanics:
 * - 10 boxes (Box 0: unlearned/retry -> Box 9: mastered)
 * - Base interval: 12h * multiplicand^box (+ overdue bonus)
 * - Multiplicand dynamically scales between 1.8 and 2.2 based on historical accuracy
 * - Consecutive streak of 3 correct reviews reduces past mistake penalty
 */
class LeitnerSpacedRepetitionEngine : SpacedRepetitionEngine {

    override val engineId: String = "leitner"
    override val displayName: String = "Adaptive Leitner System"
    override val description: String = "10-box Leitner scheduling with error-penalty recovery and overdue scaling."

    override fun calculateNextReview(
        cardId: String,
        currentState: ReviewState?,
        grade: ReviewGrade,
        now: Long
    ): ReviewScheduleResult {
        return when (grade) {
            ReviewGrade.AGAIN -> handleAgain(cardId, currentState, now)
            ReviewGrade.HARD -> handleHard(cardId, currentState, now)
            ReviewGrade.GOOD -> handleGood(cardId, currentState, now)
            ReviewGrade.EASY -> handleEasy(cardId, currentState, now)
        }
    }

    override fun resetCard(
        cardId: String,
        currentState: ReviewState?,
        now: Long
    ): ReviewScheduleResult {
        val domainState = currentState?.let { toLeitnerState(it) }
        val nextDomainState = LeitnerScheduler.onRestartStudy(cardId, domainState, now)
        val updatedState = toReviewState(nextDomainState, currentState)

        return ReviewScheduleResult(
            updatedState = updatedState,
            intervalMillis = 0L,
            nextReviewTimestamp = now,
            formattedInterval = "0 seconds"
        )
    }

    private fun handleAgain(
        cardId: String,
        currentState: ReviewState?,
        now: Long
    ): ReviewScheduleResult {
        val domainState = currentState?.let { toLeitnerState(it) }
        val nextDomainState = LeitnerScheduler.onIncorrectReview(cardId, domainState, now)
        val updatedState = toReviewState(nextDomainState, currentState)

        return ReviewScheduleResult(
            updatedState = updatedState,
            intervalMillis = 0L,
            nextReviewTimestamp = now,
            formattedInterval = "0 seconds"
        )
    }

    private fun handleGood(
        cardId: String,
        currentState: ReviewState?,
        now: Long
    ): ReviewScheduleResult {
        val domainState = currentState?.let { toLeitnerState(it) }
        val nextDomainState = LeitnerScheduler.onCorrectReview(cardId, domainState, now)
        val updatedState = toReviewState(nextDomainState, currentState)
        val interval = updatedState.nextReviewTimestamp - now

        return ReviewScheduleResult(
            updatedState = updatedState,
            intervalMillis = interval,
            nextReviewTimestamp = updatedState.nextReviewTimestamp,
            formattedInterval = LeitnerScheduler.formatDelayString(interval)
        )
    }

    private fun handleHard(
        cardId: String,
        currentState: ReviewState?,
        now: Long
    ): ReviewScheduleResult {
        if (currentState == null) {
            // First time seeing the card, HARD is treated as passing to Box 1 with simple delay
            return handleGood(cardId, null, now)
        }

        // Maintain current box without promotion, keep streak frozen, scale delay by 0.8
        val currentBox = max(1, currentState.box)
        val newNumTested = currentState.reviewCount + 1
        val baseDelay = LeitnerScheduler.calculateAdaptiveExpiryDelay(
            box = currentBox,
            numTested = newNumTested,
            numWrong = currentState.lapseCount,
            lastExpiration = currentState.nextReviewTimestamp,
            now = now
        )
        val hardDelay = (baseDelay * 0.8).toLong()
        val nextExp = now + hardDelay

        val updatedState = currentState.copy(
            box = currentBox,
            nextReviewTimestamp = nextExp,
            reviewCount = newNumTested,
            streak = currentState.streak // streak does not increase on HARD
        )

        return ReviewScheduleResult(
            updatedState = updatedState,
            intervalMillis = hardDelay,
            nextReviewTimestamp = nextExp,
            formattedInterval = LeitnerScheduler.formatDelayString(hardDelay)
        )
    }

    private fun handleEasy(
        cardId: String,
        currentState: ReviewState?,
        now: Long
    ): ReviewScheduleResult {
        if (currentState == null) {
            // First time seeing card, EASY promotes immediately to Box 2
            val delay = LeitnerScheduler.calculateSimpleExpiryDelay(2)
            val updatedState = ReviewState(
                cardId = cardId,
                box = 2,
                nextReviewTimestamp = now + delay,
                reviewCount = 1,
                lapseCount = 0,
                streak = 2,
                dateAdded = now
            )
            return ReviewScheduleResult(
                updatedState = updatedState,
                intervalMillis = delay,
                nextReviewTimestamp = now + delay,
                formattedInterval = LeitnerScheduler.formatDelayString(delay)
            )
        }

        // Normal promotion + bonus streak (+2) + 1.2x delay bonus
        val newBox = min(LeitnerScheduler.MAX_BOX, currentState.box + 1)
        val (newWrong, newStreak) = if (currentState.streak + 2 > 2) {
            Pair(max(0, currentState.lapseCount - 1), 0)
        } else {
            Pair(currentState.lapseCount, currentState.streak + 2)
        }
        val newNumTested = currentState.reviewCount + 1
        val baseDelay = LeitnerScheduler.calculateAdaptiveExpiryDelay(
            box = newBox,
            numTested = newNumTested,
            numWrong = newWrong,
            lastExpiration = currentState.nextReviewTimestamp,
            now = now
        )
        val easyDelay = (baseDelay * 1.2).toLong()
        val nextExp = now + easyDelay

        val updatedState = currentState.copy(
            box = newBox,
            nextReviewTimestamp = nextExp,
            reviewCount = newNumTested,
            lapseCount = newWrong,
            streak = newStreak
        )

        return ReviewScheduleResult(
            updatedState = updatedState,
            intervalMillis = easyDelay,
            nextReviewTimestamp = nextExp,
            formattedInterval = LeitnerScheduler.formatDelayString(easyDelay)
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

    private fun toReviewState(domain: LeitnerState, original: ReviewState?): ReviewState {
        return ReviewState(
            cardId = domain.hanzi,
            box = domain.box,
            nextReviewTimestamp = domain.expTime,
            reviewCount = domain.numTested,
            lapseCount = domain.numWrong,
            streak = domain.streak,
            dateAdded = if (domain.dateAdded != 0L) domain.dateAdded else (original?.dateAdded ?: 0L),
            customData = original?.customData ?: emptyMap()
        )
    }
}
