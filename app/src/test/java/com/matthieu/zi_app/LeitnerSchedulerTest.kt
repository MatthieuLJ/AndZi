package com.matthieu.zi_app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeitnerSchedulerTest {

    private val TWELVE_HOURS = 12L * 60 * 60 * 1000

    @Test
    fun testCalculateSimpleExpiryDelay() {
        assertEquals(0L, LeitnerScheduler.calculateSimpleExpiryDelay(0))
        assertEquals(TWELVE_HOURS * 2, LeitnerScheduler.calculateSimpleExpiryDelay(1))
        assertEquals(TWELVE_HOURS * 4, LeitnerScheduler.calculateSimpleExpiryDelay(2))
        assertEquals(TWELVE_HOURS * 8, LeitnerScheduler.calculateSimpleExpiryDelay(3))
        assertEquals(TWELVE_HOURS * 512, LeitnerScheduler.calculateSimpleExpiryDelay(9))
    }

    @Test
    fun testCalculateMultiplicand() {
        // When numTested - box <= 3, multiplicand is default 2.0
        assertEquals(2.0, LeitnerScheduler.calculateMultiplicand(box = 1, numTested = 3, numWrong = 0), 0.001)

        // Perfect score (0 wrong) -> multiplicand increases towards 2.2
        val highMult = LeitnerScheduler.calculateMultiplicand(box = 1, numTested = 10, numWrong = 0)
        assertEquals(2.2, highMult, 0.001)

        // 100% wrong -> multiplicand decreases towards 1.8
        val lowMult = LeitnerScheduler.calculateMultiplicand(box = 1, numTested = 11, numWrong = 10)
        assertEquals(1.8, lowMult, 0.001)

        // 50% wrong -> multiplicand remains 2.0
        val midMult = LeitnerScheduler.calculateMultiplicand(box = 1, numTested = 11, numWrong = 5)
        assertEquals(2.0, midMult, 0.001)
    }

    @Test
    fun testAdaptiveExpiryDelayWithOverdueBonus() {
        val now = 1_000_000_000L
        val lastExpOverdue = now - (24L * 60 * 60 * 1000) // 24 hours overdue

        val standardDelay = LeitnerScheduler.calculateAdaptiveExpiryDelay(
            box = 2,
            numTested = 2,
            numWrong = 0,
            lastExpiration = now + 1000L, // Not overdue
            now = now
        )
        val overdueDelay = LeitnerScheduler.calculateAdaptiveExpiryDelay(
            box = 2,
            numTested = 2,
            numWrong = 0,
            lastExpiration = lastExpOverdue, // 24h overdue -> +12h extra delay
            now = now
        )

        assertEquals(12L * 60 * 60 * 1000, overdueDelay - standardDelay)
    }

    @Test
    fun testOnCorrectReviewNewCard() {
        val now = 100_000L
        val newState = LeitnerScheduler.onCorrectReview("中", null, now = now)

        assertEquals("中", newState.hanzi)
        assertEquals(1, newState.box)
        assertEquals(1, newState.numTested)
        assertEquals(0, newState.numWrong)
        assertEquals(1, newState.streak)
        assertEquals(now, newState.dateAdded)
        assertEquals(now + LeitnerScheduler.calculateSimpleExpiryDelay(1), newState.expTime)
    }

    @Test
    fun testOnCorrectReviewProgressionAndStreakReduction() {
        val now = 100_000L
        val initial = LeitnerState(
            hanzi = "国",
            box = 2,
            expTime = now - 1000,
            numTested = 5,
            numWrong = 2,
            streak = 2,
            dateAdded = 50_000L
        )

        // 3rd consecutive answer (streak was 2): streak becomes 3, numWrong remains 2
        val step1 = LeitnerScheduler.onCorrectReview("国", initial, now = now)
        assertEquals(3, step1.box)
        assertEquals(6, step1.numTested)
        assertEquals(2, step1.numWrong)
        assertEquals(3, step1.streak)
        assertTrue(step1.expTime > now)
        assertEquals(50_000L, step1.dateAdded)

        // 4th consecutive answer (streak was 3 > 2): numWrong reduces by 1, streak resets to 0
        val step2 = LeitnerScheduler.onCorrectReview("国", step1, now = now)
        assertEquals(4, step2.box)
        assertEquals(7, step2.numTested)
        assertEquals(1, step2.numWrong)
        assertEquals(0, step2.streak)
    }

    @Test
    fun testOnCorrectReviewMaxBoxCap() {
        val now = 100_000L
        val box9 = LeitnerState(
            hanzi = "字",
            box = 9,
            expTime = now,
            numTested = 20,
            numWrong = 0,
            streak = 5,
            dateAdded = 10_000L
        )
        val next = LeitnerScheduler.onCorrectReview("字", box9, now = now)
        assertEquals(9, next.box)
    }

    @Test
    fun testOnIncorrectReviewDemotions() {
        val now = 100_000L

        // Low box (box 2 -> drops to 0)
        val stateBox2 = LeitnerState(hanzi = "人", box = 2, expTime = now, numTested = 3, numWrong = 0, streak = 2)
        val failBox2 = LeitnerScheduler.onIncorrectReview("人", stateBox2, now = now)
        assertEquals(0, failBox2.box)
        assertEquals(4, failBox2.numTested)
        assertEquals(1, failBox2.numWrong)
        assertEquals(0, failBox2.streak)
        assertEquals(now, failBox2.expTime) // Immediately ready for retry

        // High box (box 5 -> drops by 3 to box 2)
        val stateBox5 = LeitnerState(hanzi = "大", box = 5, expTime = now, numTested = 10, numWrong = 1, streak = 4)
        val failBox5 = LeitnerScheduler.onIncorrectReview("大", stateBox5, now = now)
        assertEquals(2, failBox5.box)
        assertEquals(11, failBox5.numTested)
        assertEquals(2, failBox5.numWrong)
        assertEquals(0, failBox5.streak)
        assertEquals(now, failBox5.expTime)
    }

    @Test
    fun testOnRestartStudy() {
        val now = 100_000L
        val state = LeitnerState(hanzi = "学", box = 4, expTime = now + 50000, numTested = 8, numWrong = 1, streak = 3)
        val restarted = LeitnerScheduler.onRestartStudy("学", state, now = now)

        assertEquals(0, restarted.box)
        assertEquals(now, restarted.expTime)
        assertEquals(9, restarted.numTested)
        assertEquals(2, restarted.numWrong)
        assertEquals(0, restarted.streak)
    }

    @Test
    fun testSelectWeightedIndex() {
        assertEquals(-1, LeitnerScheduler.selectWeightedIndex(0))
        assertEquals(0, LeitnerScheduler.selectWeightedIndex(10, randomFactor = 0.0))
        assertEquals(9, LeitnerScheduler.selectWeightedIndex(10, randomFactor = 1.0))
        // 0.5 squared = 0.25 -> index 2 of 10
        assertEquals(2, LeitnerScheduler.selectWeightedIndex(10, randomFactor = 0.5))
    }

    @Test
    fun testFormatDelayString() {
        assertEquals("45 seconds", LeitnerScheduler.formatDelayString(45 * 1000L))
        assertEquals("1 minute 30 seconds", LeitnerScheduler.formatDelayString(90 * 1000L))
        assertEquals("15 minutes", LeitnerScheduler.formatDelayString(15 * 60 * 1000L))
        assertEquals("1 hour 30 minutes", LeitnerScheduler.formatDelayString(90 * 60 * 1000L))
        assertEquals("5 hours", LeitnerScheduler.formatDelayString(5 * 60 * 60 * 1000L))
        assertEquals("1 day 6 hours", LeitnerScheduler.formatDelayString(30 * 60 * 60 * 1000L))
        assertEquals("7 days", LeitnerScheduler.formatDelayString(7 * 24 * 60 * 60 * 1000L))
    }

    @Test
    fun testIsExpired() {
        val now = 100_000L
        val box0 = LeitnerState(hanzi = "一", box = 0, expTime = now + 10_000)
        assertTrue(box0.isExpired(now))

        val expiredBox1 = LeitnerState(hanzi = "二", box = 1, expTime = now - 1)
        assertTrue(expiredBox1.isExpired(now))

        val futureBox1 = LeitnerState(hanzi = "三", box = 1, expTime = now + 10_000)
        assertFalse(futureBox1.isExpired(now))
    }
}
