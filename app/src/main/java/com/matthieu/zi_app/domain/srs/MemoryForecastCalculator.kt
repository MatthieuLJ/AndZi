package com.matthieu.zi_app.domain.srs

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

data class MemoryHealthData(
    val strongCount: Int = 0,
    val maturingCount: Int = 0,
    val dueCount: Int = 0,
    val totalRetainedWords: Float = 0f,
    val totalActiveWords: Int = 0,
    val retentionPercentage: Float = 100f
)

data class ForecastPoint(
    val futureOffsetMillis: Long,
    val retentionPercentage: Float,
    val expectedRetainedWords: Float,
    val label: String
)

data class WorkloadBucket(
    val label: String,
    val minOffsetMillis: Long,
    val maxOffsetMillis: Long,
    val count: Int
)

data class MemoryForecastData(
    val points: List<ForecastPoint> = emptyList(),
    val dueBuckets: List<WorkloadBucket> = emptyList(),
    val targetRetention: Float = 80f,
    val totalCards: Int = 0
)

object MemoryForecastCalculator {

    private const val DAY_MILLIS = 24L * 3600 * 1000L

    /**
     * Computes the real-time memory health breakdown for a collection of review states.
     */
    fun calculateMemoryHealth(
        states: List<ReviewState>,
        now: Long = System.currentTimeMillis()
    ): MemoryHealthData {
        if (states.isEmpty()) {
            return MemoryHealthData()
        }

        var strong = 0
        var maturing = 0
        var due = 0
        var totalRetained = 0.0

        for (state in states) {
            val recall = calculateCurrentRecall(state, now)
            totalRetained += recall

            when {
                state.box == 0 || state.nextReviewTimestamp <= now || recall < 0.70 -> due++
                recall >= 0.90 -> strong++
                else -> maturing++
            }
        }

        val totalCount = states.size
        val avgRetention = if (totalCount > 0) ((totalRetained / totalCount) * 100.0).toFloat() else 100f

        return MemoryHealthData(
            strongCount = strong,
            maturingCount = maturing,
            dueCount = due,
            totalRetainedWords = totalRetained.toFloat(),
            totalActiveWords = totalCount,
            retentionPercentage = avgRetention
        )
    }

    /**
     * Generates the continuous forgetting curve points (log-scaled) and future due workload histogram buckets.
     */
    fun calculateForecast(
        states: List<ReviewState>,
        now: Long = System.currentTimeMillis(),
        maxHorizonDays: Int = 90,
        samplePointsCount: Int = 60
    ): MemoryForecastData {
        if (states.isEmpty()) {
            return MemoryForecastData()
        }

        val totalCount = states.size
        val maxOffsetMillis = maxHorizonDays.toLong() * DAY_MILLIS
        val tauMillis = 0.5 * DAY_MILLIS.toDouble()

        // 1. Generate Logarithmically Spaced Interpolation Points
        val points = mutableListOf<ForecastPoint>()
        for (i in 0 until samplePointsCount) {
            val fraction = i.toDouble() / (samplePointsCount - 1).toDouble()
            // Invert log mapping: deltaT = tau * (exp(fraction * ln(1 + max / tau)) - 1)
            val offsetMillis = (tauMillis * (exp(fraction * ln(1.0 + maxOffsetMillis / tauMillis)) - 1.0)).toLong()

            var sumRecall = 0.0
            for (state in states) {
                sumRecall += calculateRecallAtFutureOffset(state, offsetMillis, now)
            }

            val avgRetention = ((sumRecall / totalCount) * 100.0).toFloat()
            val label = formatOffsetLabel(offsetMillis)

            points.add(
                ForecastPoint(
                    futureOffsetMillis = offsetMillis,
                    retentionPercentage = avgRetention,
                    expectedRetainedWords = sumRecall.toFloat(),
                    label = label
                )
            )
        }

        // 2. Generate Due Workload Buckets (Intervals: Today, +1d, +3d, +7d, +14d, +30d, +90d)
        val bucketDefinitions = listOf(
            Triple("Today", 0L, 1L * DAY_MILLIS),
            Triple("+1d", 1L * DAY_MILLIS, 3L * DAY_MILLIS),
            Triple("+3d", 3L * DAY_MILLIS, 7L * DAY_MILLIS),
            Triple("+7d", 7L * DAY_MILLIS, 14L * DAY_MILLIS),
            Triple("+14d", 14L * DAY_MILLIS, 30L * DAY_MILLIS),
            Triple("+30d", 30L * DAY_MILLIS, 90L * DAY_MILLIS)
        )

        val dueBuckets = bucketDefinitions.map { (label, minOffset, maxOffset) ->
            val count = states.count { state ->
                val dueTimestamp = state.nextReviewTimestamp
                if (minOffset == 0L && state.box == 0) {
                    true
                } else {
                    val relativeDue = dueTimestamp - now
                    relativeDue in minOffset until maxOffset
                }
            }
            WorkloadBucket(
                label = label,
                minOffsetMillis = minOffset,
                maxOffsetMillis = maxOffset,
                count = count
            )
        }

        return MemoryForecastData(
            points = points,
            dueBuckets = dueBuckets,
            targetRetention = 80f,
            totalCards = totalCount
        )
    }

    private fun calculateCurrentRecall(state: ReviewState, now: Long): Double {
        return calculateRecallAtFutureOffset(state, 0L, now)
    }

    private fun calculateRecallAtFutureOffset(state: ReviewState, futureOffsetMillis: Long, now: Long): Double {
        if (state.box == 0) return 0.0
        val custom = state.customData
        val alpha = custom[EbisuSpacedRepetitionEngine.KEY_ALPHA]?.toDoubleOrNull() ?: EbisuMath.DEFAULT_ALPHA
        val beta = custom[EbisuSpacedRepetitionEngine.KEY_BETA]?.toDoubleOrNull() ?: EbisuMath.DEFAULT_BETA
        val t0 = custom[EbisuSpacedRepetitionEngine.KEY_T0]?.toDoubleOrNull() ?: run {
            (12.0 * Math.pow(2.0, state.box.coerceIn(0, 9).toDouble())) * 3600.0 * 1000.0
        }
        val lastReview = custom[EbisuSpacedRepetitionEngine.KEY_LAST_REVIEW]?.toLongOrNull() ?: run {
            if (state.dateAdded > 0L) state.dateAdded else (now - 12 * 3600 * 1000L)
        }

        val totalElapsed = max(0L, (now - lastReview) + futureOffsetMillis).toDouble()
        return EbisuMath.predictRecall(alpha, beta, t0, totalElapsed)
    }

    private fun formatOffsetLabel(offsetMillis: Long): String {
        val days = offsetMillis / DAY_MILLIS
        return when {
            days == 0L -> "Today"
            days < 7L -> "+${days}d"
            days < 30L -> "+${days / 7}w"
            else -> "+${days / 30}m"
        }
    }
}
