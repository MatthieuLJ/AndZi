package com.matthieu.zi_app.domain.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EbisuMathTest {

    @Test
    fun testLogGammaAndLogBeta() {
        // Gamma(5) = 4! = 24 -> ln(24) ≈ 3.1780538303479458
        val lg5 = EbisuMath.logGamma(5.0)
        assertTrue(abs(lg5 - 3.1780538303479458) < 1e-10)

        // Beta(1, 1) = 1 -> ln(1) = 0
        val lb11 = EbisuMath.logBeta(1.0, 1.0)
        assertTrue(abs(lb11 - 0.0) < 1e-10)

        // Beta(2, 3) = 1/12 -> ln(1/12) ≈ -2.484906649788
        val lb23 = EbisuMath.logBeta(2.0, 3.0)
        assertTrue(abs(lb23 - (-2.484906649788)) < 1e-8)
    }

    @Test
    fun testPredictRecallProperties() {
        val a = 4.0
        val b = 4.0
        val t0 = 24.0 * 3600 * 1000.0 // 24 hours

        // At elapsed = 0: recall probability is 1.0 (100%)
        val r0 = EbisuMath.predictRecall(a, b, t0, 0.0)
        assertEquals(1.0, r0, 1e-9)

        // At elapsed = t0: recall probability should be 0.5 (50% half-life)
        val rHalf = EbisuMath.predictRecall(a, b, t0, t0)
        assertEquals(0.5, rHalf, 1e-3)

        // Monotonic decrease: r(1h) > r(6h) > r(24h) > r(72h)
        val r1h = EbisuMath.predictRecall(a, b, t0, 1.0 * 3600 * 1000.0)
        val r6h = EbisuMath.predictRecall(a, b, t0, 6.0 * 3600 * 1000.0)
        val r72h = EbisuMath.predictRecall(a, b, t0, 72.0 * 3600 * 1000.0)

        assertTrue(r0 > r1h)
        assertTrue(r1h > r6h)
        assertTrue(r6h > rHalf)
        assertTrue(rHalf > r72h)
        assertTrue(r72h > 0.0)
    }

    @Test
    fun testTimeForRecall() {
        val a = 4.0
        val b = 4.0
        val t0 = 24.0 * 3600 * 1000.0

        val target = 0.80
        val duration = EbisuMath.timeForRecall(a, b, t0, target)

        // Computed duration should produce predicted recall ≈ target
        val actualRecall = EbisuMath.predictRecall(a, b, t0, duration)
        assertEquals(target, actualRecall, 1e-2)
    }

    @Test
    fun testUpdateRecallBehaviors() {
        val a = 4.0
        val b = 4.0
        val t0 = 24.0 * 3600 * 1000.0
        val elapsed = 12.0 * 3600 * 1000.0

        // 1. GOOD review expands horizon
        val goodState = EbisuMath.updateRecall(a, b, t0, elapsed, ReviewGrade.GOOD)
        assertTrue(goodState.t0 > t0)

        // 2. EASY review expands horizon even further than GOOD
        val easyState = EbisuMath.updateRecall(a, b, t0, elapsed, ReviewGrade.EASY)
        assertTrue(easyState.t0 > goodState.t0)

        // 3. HARD review expands horizon less than GOOD
        val hardState = EbisuMath.updateRecall(a, b, t0, elapsed, ReviewGrade.HARD)
        assertTrue(hardState.t0 < goodState.t0)

        // 4. AGAIN (failure) contracts horizon
        val failState = EbisuMath.updateRecall(a, b, t0, elapsed, ReviewGrade.AGAIN)
        assertTrue(failState.t0 < t0)
    }
}
