package com.matthieu.zi_app.domain.srs

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Continuous Bayesian Spaced Repetition Engine implementing the Ebisu memory model.
 *
 * Tracks (alpha, beta, t0) in customData and schedules reviews based on target retention decay.
 */
class EbisuSpacedRepetitionEngine(
    val targetRecall: Double = EbisuMath.DEFAULT_TARGET_RECALL
) : SpacedRepetitionEngine {

    override val engineId: String = "ebisu"
    override val displayName = "Ebisu Bayesian Model"
    override val description: String = "Continuous Bayesian estimation of memory decay and recall probability."

    override fun calculateNextReview(
        cardId: String,
        currentState: ReviewState?,
        grade: ReviewGrade,
        now: Long
    ): ReviewScheduleResult {
        val (alpha, beta, t0, lastReview) = decodeEbisuState(currentState, now)
        val elapsed = max(1000L, now - lastReview).toDouble()

        val isSuccess = grade != ReviewGrade.AGAIN
        val bayesianUpdated = EbisuMath.updateRecall(alpha, beta, t0, elapsed, grade)

        val (finalT0, virtualBox) = if (currentState == null) {
            when (grade) {
                ReviewGrade.AGAIN -> Pair(computeAdaptiveT0(0, 1, 1, 0), 0)
                ReviewGrade.HARD -> Pair(computeAdaptiveT0(1, 1, 0, 1), 1)
                ReviewGrade.GOOD -> Pair(computeAdaptiveT0(1, 1, 0, 1), 1)
                ReviewGrade.EASY -> Pair(computeAdaptiveT0(2, 1, 0, 2), 2)
            }
        } else {
            val newReviews = currentState.reviewCount + 1
            val newLapses = currentState.lapseCount + if (isSuccess) 0 else 1
            val newStreak = if (isSuccess) currentState.streak + 1 else 0
            when (grade) {
                ReviewGrade.AGAIN -> {
                    val demotedBox = if (currentState.box > 2) currentState.box - 3 else 0
                    Pair(computeAdaptiveT0(demotedBox, newReviews, newLapses, 0), demotedBox)
                }
                ReviewGrade.HARD -> {
                    val targetBox = max(1, currentState.box)
                    val adaptiveT0 = computeAdaptiveT0(targetBox, newReviews, newLapses, newStreak)
                    Pair(max(adaptiveT0, bayesianUpdated.t0 * 0.8), targetBox)
                }
                ReviewGrade.GOOD -> {
                    val promotedBox = min(9, currentState.box + 1)
                    val adaptiveT0 = computeAdaptiveT0(promotedBox, newReviews, newLapses, newStreak)
                    Pair(max(adaptiveT0, bayesianUpdated.t0), promotedBox)
                }
                ReviewGrade.EASY -> {
                    val promotedBox = min(9, currentState.box + 2)
                    val adaptiveT0 = computeAdaptiveT0(promotedBox, newReviews, newLapses, newStreak)
                    Pair(max(adaptiveT0, bayesianUpdated.t0 * 1.25), promotedBox)
                }
            }
        }

        val nextIntervalMillis = if (grade == ReviewGrade.AGAIN) {
            0L // Retry immediately on lapse
        } else {
            max(
                3600L * 1000L, // Minimum 1 hour for passing reviews
                EbisuMath.timeForRecall(
                    alpha = bayesianUpdated.alpha,
                    beta = bayesianUpdated.beta,
                    t0 = finalT0,
                    targetRecall = targetRecall
                ).toLong()
            )
        }

        val nextExp = if (grade == ReviewGrade.AGAIN) now else (now + nextIntervalMillis)

        val updatedState = ReviewState(
            cardId = cardId,
            box = virtualBox,
            nextReviewTimestamp = nextExp,
            reviewCount = (currentState?.reviewCount ?: 0) + 1,
            lapseCount = (currentState?.lapseCount ?: 0) + if (isSuccess) 0 else 1,
            streak = if (isSuccess) (currentState?.streak ?: 0) + 1 else 0,
            dateAdded = currentState?.dateAdded?.takeIf { it != 0L } ?: now,
            customData = mapOf(
                KEY_ALPHA to bayesianUpdated.alpha.toString(),
                KEY_BETA to bayesianUpdated.beta.toString(),
                KEY_T0 to finalT0.toString(),
                KEY_LAST_REVIEW to now.toString()
            )
        )

        return ReviewScheduleResult(
            updatedState = updatedState,
            intervalMillis = nextIntervalMillis,
            nextReviewTimestamp = nextExp,
            formattedInterval = formatInterval(nextIntervalMillis)
        )
    }

    override fun resetCard(
        cardId: String,
        currentState: ReviewState?,
        now: Long
    ): ReviewScheduleResult {
        val updatedState = ReviewState(
            cardId = cardId,
            box = 0,
            nextReviewTimestamp = now,
            reviewCount = 0,
            lapseCount = 0,
            streak = 0,
            dateAdded = now,
            customData = mapOf(
                KEY_ALPHA to EbisuMath.DEFAULT_ALPHA.toString(),
                KEY_BETA to EbisuMath.DEFAULT_BETA.toString(),
                KEY_T0 to EbisuMath.DEFAULT_T0_MILLIS.toString(),
                KEY_LAST_REVIEW to now.toString()
            )
        )

        return ReviewScheduleResult(
            updatedState = updatedState,
            intervalMillis = 0L,
            nextReviewTimestamp = now,
            formattedInterval = "0 seconds"
        )
    }

    private fun decodeEbisuState(
        state: ReviewState?,
        now: Long
    ): EbisuParams {
        if (state == null) {
            return EbisuParams(
                alpha = EbisuMath.DEFAULT_ALPHA,
                beta = EbisuMath.DEFAULT_BETA,
                t0 = EbisuMath.DEFAULT_T0_MILLIS,
                lastReview = now
            )
        }

        val custom = state.customData
        val alpha = custom[KEY_ALPHA]?.toDoubleOrNull() ?: EbisuMath.DEFAULT_ALPHA
        val beta = custom[KEY_BETA]?.toDoubleOrNull() ?: EbisuMath.DEFAULT_BETA
        val t0 = custom[KEY_T0]?.toDoubleOrNull() ?: run {
            computeAdaptiveT0(state.box, state.reviewCount, state.lapseCount, state.streak)
        }
        val lastReview = custom[KEY_LAST_REVIEW]?.toLongOrNull() ?: run {
            inferLastReviewTimestamp(state, alpha, beta, t0, targetRecall, now)
        }

        return EbisuParams(alpha, beta, t0, lastReview)
    }

    private fun mapT0ToVirtualBox(t0Millis: Double): Int {
        val hours = t0Millis / (3600 * 1000.0)
        val box = (ln(max(1.0, hours / 12.0)) / ln(2.0)).toInt()
        return box.coerceIn(0, 9)
    }

    private fun mapBoxToT0(box: Int): Double {
        return computeAdaptiveT0(box, 0, 0, 0)
    }

    private fun formatInterval(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "$days day${if (days > 1) "s" else ""}"
            hours > 0 -> "$hours hour${if (hours > 1) "s" else ""}"
            minutes > 0 -> "$minutes min${if (minutes > 1) "s" else ""}"
            else -> "$seconds sec${if (seconds != 1L) "s" else ""}"
        }
    }

    private data class EbisuParams(
        val alpha: Double,
        val beta: Double,
        val t0: Double,
        val lastReview: Long
    )

    companion object {
        const val KEY_ALPHA = "ebisu_a"
        const val KEY_BETA = "ebisu_b"
        const val KEY_T0 = "ebisu_t0"
        const val KEY_LAST_REVIEW = "last_review"

        fun computeAdaptiveT0(
            box: Int,
            reviewCount: Int,
            lapseCount: Int,
            streak: Int
        ): Double {
            val clampedBox = box.coerceIn(0, 9)
            val multiplicand = if (reviewCount > 0) {
                val correctCount = (reviewCount - lapseCount).coerceAtLeast(0)
                val successRate = (correctCount.toDouble() / reviewCount.toDouble()).coerceIn(0.0, 1.0)
                1.8 + (0.4 * successRate)
            } else {
                2.0
            }
            val streakBoost = 1.0 + (streak.coerceIn(0, 5) * 0.04)
            return (12.0 * Math.pow(multiplicand, clampedBox.toDouble()) * streakBoost) * 3600.0 * 1000.0
        }

        fun inferLastReviewTimestamp(
            state: ReviewState,
            alpha: Double,
            beta: Double,
            t0: Double,
            targetRecall: Double = EbisuMath.DEFAULT_TARGET_RECALL,
            now: Long = System.currentTimeMillis()
        ): Long {
            if (state.box > 0 && state.nextReviewTimestamp > 0L) {
                val scheduledInterval = max(
                    3600L * 1000L,
                    EbisuMath.timeForRecall(alpha, beta, t0, targetRecall).toLong()
                )
                val inferred = state.nextReviewTimestamp - scheduledInterval
                if (inferred > 0L) {
                    return min(now, inferred)
                }
            }
            return if (state.dateAdded > 0L) min(now, state.dateAdded) else (now - 12 * 3600 * 1000L)
        }
    }
}
