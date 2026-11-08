package com.matthieu.zi_app.domain.srs

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Mathematical utilities and Bayesian posterior update algorithms for the Ebisu spaced repetition model.
 *
 * Ebisu models memory recall as a Beta distribution over recall probability at reference horizon t0.
 */
object EbisuMath {

    const val DEFAULT_ALPHA = 4.0
    const val DEFAULT_BETA = 4.0
    const val DEFAULT_T0_MILLIS = 43200000.0 // 12 hours in milliseconds
    const val MIN_T0_MILLIS = 60000.0 // 1 minute minimum horizon
    const val DEFAULT_TARGET_RECALL = 0.80 // 80% recall threshold

    data class EbisuState(
        val alpha: Double,
        val beta: Double,
        val t0: Double
    )

    /**
     * Lanczos approximation of ln(Gamma(x)) with g=7, N=9.
     * Accurate to ~1e-15 across positive domain.
     */
    fun logGamma(x: Double): Double {
        if (x <= 0.0) return 0.0
        val p = doubleArrayOf(
            0.99999999999980993,
            676.5203681218851,
            -1259.1392167224028,
            771.32342877765313,
            -176.61502916214059,
            12.507343278686905,
            -0.138571095836526,
            9.9843695780195716e-6,
            1.5056327351493116e-7
        )
        val g = 7.0
        if (x < 0.5) {
            return ln(PI / sin(PI * x)) - logGamma(1.0 - x)
        }
        val z = x - 1.0
        var a = p[0]
        val t = z + g + 0.5
        for (i in 1 until p.size) {
            a += p[i] / (z + i)
        }
        return 0.5 * ln(2.0 * PI) + (z + 0.5) * ln(t) - t + ln(a)
    }

    /**
     * Natural log of the Beta function B(a, b) = Gamma(a) * Gamma(b) / Gamma(a + b).
     */
    fun logBeta(a: Double, b: Double): Double {
        return logGamma(a) + logGamma(b) - logGamma(a + b)
    }

    /**
     * Calculates the expected recall probability at elapsed time t since last review.
     * E[p(t)] = B(alpha + t / t0, beta) / B(alpha, beta)
     *
     * @param alpha Beta shape parameter alpha
     * @param beta Beta shape parameter beta
     * @param t0 Reference half-life horizon (in milliseconds or days)
     * @param elapsed Elapsed duration (same units as t0)
     */
    fun predictRecall(alpha: Double, beta: Double, t0: Double, elapsed: Double): Double {
        if (elapsed <= 0.0) return 1.0
        val safeT0 = max(MIN_T0_MILLIS, t0)
        val delta = elapsed / safeT0
        val logProb = logBeta(alpha + delta, beta) - logBeta(alpha, beta)
        return max(0.0, min(1.0, exp(logProb)))
    }

    /**
     * Performs a Bayesian posterior update of (alpha, beta, t0) given the review grade and elapsed time.
     */
    fun updateRecall(
        alpha: Double,
        beta: Double,
        t0: Double,
        elapsed: Double,
        grade: ReviewGrade
    ): EbisuState {
        val safeT0 = max(MIN_T0_MILLIS, t0)
        val safeElapsed = max(1000.0, elapsed)
        val delta = safeElapsed / safeT0

        val (mu1, mu2) = when (grade) {
            ReviewGrade.AGAIN -> {
                // Failure: Likelihood is (1 - p^delta)
                val logB_ab = logBeta(alpha, beta)
                val logB_adb = logBeta(alpha + delta, beta)
                val logB_a1b = logBeta(alpha + 1.0, beta)
                val logB_ad1b = logBeta(alpha + delta + 1.0, beta)
                val logB_a2b = logBeta(alpha + 2.0, beta)
                val logB_ad2b = logBeta(alpha + delta + 2.0, beta)

                val m0 = exp(logB_ab) - exp(logB_adb)
                val m1 = exp(logB_a1b) - exp(logB_ad1b)
                val m2 = exp(logB_a2b) - exp(logB_ad2b)

                val m1Norm = if (m0 > 1e-12) max(0.01, min(0.99, m1 / m0)) else 0.1
                val m2Norm = if (m0 > 1e-12) max(0.0001, min(0.99, m2 / m0)) else 0.02
                Pair(m1Norm, m2Norm)
            }
            ReviewGrade.HARD -> {
                // Hard: Remembered with struggle -> compute intermediate moments between failure & success
                val succMu1 = (alpha + delta) / (alpha + delta + beta)
                val succMu2 = ((alpha + delta + 1.0) * (alpha + delta)) / ((alpha + delta + beta + 1.0) * (alpha + delta + beta))
                Pair(succMu1 * 0.9, succMu2 * 0.85)
            }
            ReviewGrade.GOOD -> {
                // Success: Likelihood is p^delta
                val succMu1 = (alpha + delta) / (alpha + delta + beta)
                val succMu2 = ((alpha + delta + 1.0) * (alpha + delta)) / ((alpha + delta + beta + 1.0) * (alpha + delta + beta))
                Pair(succMu1, succMu2)
            }
            ReviewGrade.EASY -> {
                // Easy: Full success + boosted precision
                val succMu1 = (alpha + delta) / (alpha + delta + beta)
                val succMu2 = ((alpha + delta + 1.0) * (alpha + delta)) / ((alpha + delta + beta + 1.0) * (alpha + delta + beta))
                Pair(min(0.999, succMu1 * 1.05), min(0.998, succMu2 * 1.08))
            }
        }

        // Recenter to target half-life (E[p] = 0.5)
        val newT0 = if (mu1 in 0.001..0.999) {
            val eta = ln(0.5) / ln(mu1)
            val baseT0 = max(MIN_T0_MILLIS, safeT0 * eta)
            when (grade) {
                ReviewGrade.AGAIN -> max(MIN_T0_MILLIS, baseT0 * 0.6) // Reduce horizon on lapse
                ReviewGrade.HARD -> max(MIN_T0_MILLIS, baseT0 * 0.85)
                ReviewGrade.GOOD -> baseT0
                ReviewGrade.EASY -> baseT0 * 1.3 // Extra boost for easy words
            }
        } else {
            safeT0
        }

        // Moment match posterior alpha and beta from moments (mu1, mu2)
        val variance = mu2 - mu1 * mu1
        val (postAlpha, postBeta) = if (variance > 1e-7 && mu1 in 0.001..0.999) {
            val meanFactor = (mu1 * (1.0 - mu1) / variance) - 1.0
            if (meanFactor > 0.01) {
                Pair(
                    (mu1 * meanFactor).coerceIn(0.1, 100.0),
                    ((1.0 - mu1) * meanFactor).coerceIn(0.1, 100.0)
                )
            } else {
                Pair(DEFAULT_ALPHA, DEFAULT_BETA)
            }
        } else {
            Pair(DEFAULT_ALPHA, DEFAULT_BETA)
        }

        return EbisuState(
            alpha = postAlpha,
            beta = postBeta,
            t0 = newT0
        )
    }

    /**
     * Solves for the duration t from now until expected recall drops to targetRecall.
     * Uses binary search with monotonic predictRecall.
     */
    fun timeForRecall(
        alpha: Double,
        beta: Double,
        t0: Double,
        targetRecall: Double = DEFAULT_TARGET_RECALL
    ): Double {
        if (targetRecall >= 1.0) return 0.0
        if (targetRecall <= 0.0) return t0 * 100.0

        val safeT0 = max(MIN_T0_MILLIS, t0)
        var low = 0.0
        var high = safeT0 * 50.0

        for (i in 0 until 30) {
            val mid = (low + high) / 2.0
            val recall = predictRecall(alpha, beta, safeT0, mid)
            if (recall > targetRecall) {
                low = mid
            } else {
                high = mid
            }
        }

        return (low + high) / 2.0
    }
}
