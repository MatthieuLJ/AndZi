package com.matthieu.zi_app.domain.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EbisuSpacedRepetitionEngineTest {

    private lateinit var engine: EbisuSpacedRepetitionEngine

    @Before
    fun setup() {
        engine = EbisuSpacedRepetitionEngine(targetRecall = 0.80)
    }

    @Test
    fun testInitialReviewAndGrading() {
        val now = 1_000_000_000L

        // Initial AGAIN: due immediately
        val againResult = engine.calculateNextReview("好", null, ReviewGrade.AGAIN, now)
        assertEquals(now, againResult.nextReviewTimestamp)
        assertEquals(0L, againResult.intervalMillis)
        assertEquals(1, againResult.updatedState.reviewCount)
        assertEquals(1, againResult.updatedState.lapseCount)
        assertNotNull(againResult.updatedState.customData[EbisuSpacedRepetitionEngine.KEY_T0])

        // Initial GOOD: scheduled into future
        val goodResult = engine.calculateNextReview("好", null, ReviewGrade.GOOD, now)
        assertTrue(goodResult.intervalMillis > 0L)
        assertEquals(now + goodResult.intervalMillis, goodResult.nextReviewTimestamp)
        assertEquals(1, goodResult.updatedState.reviewCount)
        assertEquals(0, goodResult.updatedState.lapseCount)

        // Initial EASY: scheduled further than GOOD
        val easyResult = engine.calculateNextReview("好", null, ReviewGrade.EASY, now)
        assertTrue(easyResult.intervalMillis > goodResult.intervalMillis)
    }

    @Test
    fun testCardReset() {
        val now = 1_000_000_000L
        val currentState = ReviewState(
            cardId = "学",
            box = 4,
            nextReviewTimestamp = now + 100_000L,
            reviewCount = 5,
            lapseCount = 1,
            streak = 4
        )

        val resetResult = engine.resetCard("学", currentState, now)
        assertEquals(0, resetResult.updatedState.box)
        assertEquals(0, resetResult.updatedState.reviewCount)
        assertEquals(0, resetResult.updatedState.lapseCount)
        assertEquals(now, resetResult.nextReviewTimestamp)
        assertEquals(0L, resetResult.intervalMillis)
    }

    @Test
    fun testProgressiveReviewIntervalGrowth() {
        var now = 1_000_000_000L
        var state: ReviewState? = null

        val intervals = mutableListOf<Long>()
        for (i in 1..6) {
            val res = engine.calculateNextReview("汉", state, ReviewGrade.GOOD, now)
            state = res.updatedState
            intervals.add(res.intervalMillis)
            now = res.nextReviewTimestamp
        }

        // Each successful review at the scheduled horizon should yield an increasing interval
        for (i in 1 until intervals.size) {
            assertTrue(
                "Interval $i (${intervals[i]}) should be greater than ${intervals[i - 1]}",
                intervals[i] > intervals[i - 1]
            )
        }
    }
}
