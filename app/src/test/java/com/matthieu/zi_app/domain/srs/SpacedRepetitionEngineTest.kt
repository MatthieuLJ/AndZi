package com.matthieu.zi_app.domain.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpacedRepetitionEngineTest {

    private lateinit var engine: SpacedRepetitionEngine
    private val TWELVE_HOURS = 12L * 60 * 60 * 1000

    @Before
    fun setup() {
        engine = LeitnerSpacedRepetitionEngine()
    }

    @Test
    fun testReviewGradeConversion() {
        assertEquals(ReviewGrade.GOOD, ReviewGrade.fromBinary(true))
        assertEquals(ReviewGrade.AGAIN, ReviewGrade.fromBinary(false))
        assertEquals(ReviewGrade.AGAIN, ReviewGrade.fromValue(1))
        assertEquals(ReviewGrade.HARD, ReviewGrade.fromValue(2))
        assertEquals(ReviewGrade.GOOD, ReviewGrade.fromValue(3))
        assertEquals(ReviewGrade.EASY, ReviewGrade.fromValue(4))
    }

    @Test
    fun testInitialCardReviews() {
        val now = 1_000_000_000L

        // Initial AGAIN: stays in Box 0, due immediately
        val againRes = engine.calculateNextReview("好", null, ReviewGrade.AGAIN, now)
        assertEquals(0, againRes.updatedState.box)
        assertEquals(now, againRes.nextReviewTimestamp)
        assertEquals(0L, againRes.intervalMillis)
        assertEquals(1, againRes.updatedState.reviewCount)
        assertEquals(1, againRes.updatedState.lapseCount)

        // Initial GOOD: promoted to Box 1, 24h delay
        val goodRes = engine.calculateNextReview("好", null, ReviewGrade.GOOD, now)
        assertEquals(1, goodRes.updatedState.box)
        assertEquals(TWELVE_HOURS * 2, goodRes.intervalMillis)
        assertEquals(now + TWELVE_HOURS * 2, goodRes.nextReviewTimestamp)
        assertEquals(1, goodRes.updatedState.reviewCount)
        assertEquals(0, goodRes.updatedState.lapseCount)

        // Initial EASY: promoted directly to Box 2, 48h delay
        val easyRes = engine.calculateNextReview("好", null, ReviewGrade.EASY, now)
        assertEquals(2, easyRes.updatedState.box)
        assertEquals(TWELVE_HOURS * 4, easyRes.intervalMillis)
        assertEquals(now + TWELVE_HOURS * 4, easyRes.nextReviewTimestamp)
    }

    @Test
    fun testReviewGradesTransitions() {
        val now = 1_000_000_000L
        val box2State = ReviewState(
            cardId = "中",
            box = 2,
            nextReviewTimestamp = now,
            reviewCount = 3,
            lapseCount = 0,
            streak = 2,
            dateAdded = now - (48 * 3600 * 1000)
        )

        // HARD on Box 2: stays in Box 2, streak frozen at 2, interval is 0.8x of standard
        val hardRes = engine.calculateNextReview("中", box2State, ReviewGrade.HARD, now)
        assertEquals(2, hardRes.updatedState.box)
        assertEquals(2, hardRes.updatedState.streak)
        assertEquals(4, hardRes.updatedState.reviewCount)
        assertTrue(hardRes.intervalMillis < TWELVE_HOURS * 4)

        // AGAIN on Box 2: demoted to Box 0, due immediately, streak reset
        val failRes = engine.calculateNextReview("中", box2State, ReviewGrade.AGAIN, now)
        assertEquals(0, failRes.updatedState.box)
        assertEquals(0, failRes.updatedState.streak)
        assertEquals(1, failRes.updatedState.lapseCount)
        assertEquals(now, failRes.nextReviewTimestamp)

        // Demoting from high box (Box 5 -> drops by 3 to Box 2)
        val box5State = ReviewState(cardId = "国", box = 5, nextReviewTimestamp = now, reviewCount = 8, lapseCount = 0)
        val failBox5 = engine.calculateNextReview("国", box5State, ReviewGrade.AGAIN, now)
        assertEquals(2, failBox5.updatedState.box)
        assertEquals(0, failBox5.updatedState.streak)
    }

    @Test
    fun test30DayTrajectoryModelStudent() {
        var currentTime = 1_000_000_000L
        var state: ReviewState? = null

        val dayInMillis = 24L * 60 * 60 * 1000

        // Simulate 30 days of daily practice where user answers GOOD every time card is due
        var reviewCount = 0
        for (day in 1..30) {
            val now = currentTime + (day * dayInMillis)
            if (state == null || state.isDue(now)) {
                val result = engine.calculateNextReview("学", state, ReviewGrade.GOOD, now)
                state = result.updatedState
                reviewCount++
            }
        }

        // Over 30 days, card should have steadily progressed to Box 5
        assertEquals(5, state!!.box)
        assertEquals(0, state.lapseCount)
        assertEquals(reviewCount, state.reviewCount)
        // Next review should be scheduled beyond the 30-day window (on Day 32)
        assertTrue(state.nextReviewTimestamp > currentTime + 30 * dayInMillis)
    }

    @Test
    fun test365DayTrajectoryMasteryCap() {
        var currentTime = 1_000_000_000L
        var state: ReviewState? = null
        val dayInMillis = 24L * 60 * 60 * 1000

        // Simulate a full year (365 days)
        for (day in 1..365) {
            val now = currentTime + (day * dayInMillis)
            if (state == null || state.isDue(now)) {
                val result = engine.calculateNextReview("汉", state, ReviewGrade.GOOD, now)
                state = result.updatedState
            }
        }

        // Mastered card should reach and stay capped at Box 9
        assertEquals(9, state!!.box)
        assertEquals(0, state.lapseCount)
        // Interval for Box 9 is 256 days
        val nextDueInterval = state.nextReviewTimestamp - (currentTime + 365 * dayInMillis)
        assertTrue(nextDueInterval > 100 * dayInMillis)
    }

    @Test
    fun testIntermittentFailureAndStreakRecovery() {
        val now = 1_000_000_000L

        // Initial pass
        var state = engine.calculateNextReview("难", null, ReviewGrade.GOOD, now).updatedState
        assertEquals(1, state.box)

        // Failure on review 2
        state = engine.calculateNextReview("难", state, ReviewGrade.AGAIN, now + 1000).updatedState
        assertEquals(0, state.box)
        assertEquals(1, state.lapseCount)
        assertEquals(0, state.streak)

        // 3 consecutive good answers rebuild streak and reduce mistake penalty
        state = engine.calculateNextReview("难", state, ReviewGrade.GOOD, now + 2000).updatedState
        assertEquals(1, state.box)
        assertEquals(1, state.streak)
        assertEquals(1, state.lapseCount)

        state = engine.calculateNextReview("难", state, ReviewGrade.GOOD, now + 3000).updatedState
        assertEquals(2, state.box)
        assertEquals(2, state.streak)
        assertEquals(1, state.lapseCount)

        state = engine.calculateNextReview("难", state, ReviewGrade.GOOD, now + 4000).updatedState
        assertEquals(3, state.box)
        assertEquals(3, state.streak)
        assertEquals(1, state.lapseCount)

        // 4th consecutive correct answer (streak > 2): lapseCount decrements from 1 to 0!
        state = engine.calculateNextReview("难", state, ReviewGrade.GOOD, now + 5000).updatedState
        assertEquals(4, state.box)
        assertEquals(0, state.streak)
        assertEquals(0, state.lapseCount) // Penalty successfully cleared!
    }
}
