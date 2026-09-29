package com.matthieu.zi_app

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Immutable domain entity representing Leitner spaced repetition review state for a character or word.
 */
data class LeitnerState(
    val hanzi: String,
    val box: Int = 0,
    val expTime: Long = 0L,
    val numTested: Int = 0,
    val numWrong: Int = 0,
    val streak: Int = 0,
    val dateAdded: Long = 0L
) {
    init {
        require(box in 0..9) { "Box must be between 0 and 9, got $box" }
        require(numTested >= 0) { "numTested must be >= 0, got $numTested" }
        require(numWrong >= 0) { "numWrong must be >= 0, got $numWrong" }
        require(streak >= 0) { "streak must be >= 0, got $streak" }
    }

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return box == 0 || expTime <= now
    }
}

/**
 * Pure calculation and state transition engine for Leitner-based Spaced Repetition (SRS).
 */
object LeitnerScheduler {

    const val MIN_BOX: Int = 0
    const val MAX_BOX: Int = 9
    const val BASE_DELAY_MS: Long = 12L * 60 * 60 * 1000 // 12 hours

    /**
     * Calculates the baseline review delay for a given box using standard exponential scaling (2^box).
     */
    @JvmStatic
    fun calculateSimpleExpiryDelay(box: Int): Long {
        if (box <= MIN_BOX) return 0L
        val clampedBox = min(box, MAX_BOX)
        return (BASE_DELAY_MS * 2.0.pow(clampedBox.toDouble())).toLong()
    }

    /**
     * Calculates the adaptive multiplicand base (between 1.8 and 2.2) based on past accuracy.
     */
    @JvmStatic
    fun calculateMultiplicand(box: Int, numTested: Int, numWrong: Int): Double {
        var multiplicand = 2.0
        val diff = numTested - box
        if (diff > 3) {
            val errorRate = numWrong.toDouble() / diff.toDouble()
            multiplicand += (0.5 - errorRate) * 8.0 / 20.0
        }
        return multiplicand
    }

    /**
     * Calculates the next review interval taking into account overdue bonus and adaptive multiplicand.
     *
     * @param box Target box after promotion/review.
     * @param numTested Cumulative count of reviews including current one.
     * @param numWrong Cumulative count of errors (after streak adjustments).
     * @param lastExpiration Timestamp of previous expiration.
     * @param now Current timestamp in milliseconds.
     */
    @JvmStatic
    @JvmOverloads
    fun calculateAdaptiveExpiryDelay(
        box: Int,
        numTested: Int,
        numWrong: Int,
        lastExpiration: Long,
        now: Long = System.currentTimeMillis()
    ): Long {
        if (box <= MIN_BOX) return 0L
        val clampedBox = min(box, MAX_BOX)

        val extraDelay = if (lastExpiration != 0L && lastExpiration < now) {
            (now - lastExpiration) / 2
        } else {
            0L
        }

        val multiplicand = calculateMultiplicand(clampedBox, numTested, numWrong)
        return (BASE_DELAY_MS * multiplicand.pow(clampedBox.toDouble()) + extraDelay).toLong()
    }

    /**
     * Calculates the updated Leitner state after a correct review answer.
     *
     * Rules:
     * - Box increases by +1 (capped at 9).
     * - If streak > 2, numWrong is decremented by 1 (down to 0) and streak resets to 0.
     * - Otherwise streak increments by 1.
     * - Expiry time set to now + adaptive delay.
     */
    @JvmStatic
    @JvmOverloads
    fun onCorrectReview(
        hanzi: String,
        currentState: LeitnerState?,
        now: Long = System.currentTimeMillis()
    ): LeitnerState {
        if (currentState == null) {
            return LeitnerState(
                hanzi = hanzi,
                box = 1,
                expTime = now + calculateSimpleExpiryDelay(1),
                numTested = 1,
                numWrong = 0,
                streak = 1,
                dateAdded = now
            )
        }

        val newBox = min(MAX_BOX, currentState.box + 1)
        val currentStreak = currentState.streak
        val (newWrong, newStreak) = if (currentStreak > 2) {
            Pair(max(0, currentState.numWrong - 1), 0)
        } else {
            Pair(currentState.numWrong, currentStreak + 1)
        }
        val newNumTested = currentState.numTested + 1
        val delay = calculateAdaptiveExpiryDelay(
            box = newBox,
            numTested = newNumTested,
            numWrong = newWrong,
            lastExpiration = currentState.expTime,
            now = now
        )

        return LeitnerState(
            hanzi = hanzi,
            box = newBox,
            expTime = now + delay,
            numTested = newNumTested,
            numWrong = newWrong,
            streak = newStreak,
            dateAdded = if (currentState.dateAdded != 0L) currentState.dateAdded else now
        )
    }

    /**
     * Calculates the updated Leitner state after an incorrect review answer.
     *
     * Rules:
     * - Demotion: If box > 2, box decreases by -3; otherwise resets to 0.
     * - numWrong is incremented by 1.
     * - streak is reset to 0.
     * - Expiry time is set to now (immediately available for retry).
     */
    @JvmStatic
    @JvmOverloads
    fun onIncorrectReview(
        hanzi: String,
        currentState: LeitnerState?,
        now: Long = System.currentTimeMillis()
    ): LeitnerState {
        if (currentState == null) {
            return LeitnerState(
                hanzi = hanzi,
                box = 0,
                expTime = now,
                numTested = 1,
                numWrong = 1,
                streak = 0,
                dateAdded = now
            )
        }

        val newBox = if (currentState.box > 2) currentState.box - 3 else 0
        val newNumTested = currentState.numTested + 1
        val newWrong = currentState.numWrong + 1

        return LeitnerState(
            hanzi = hanzi,
            box = newBox,
            expTime = now,
            numTested = newNumTested,
            numWrong = newWrong,
            streak = 0,
            dateAdded = if (currentState.dateAdded != 0L) currentState.dateAdded else now
        )
    }

    /**
     * Completely resets study progress for a character to Box 0, expTime = now, streak = 0.
     */
    @JvmStatic
    @JvmOverloads
    fun onRestartStudy(
        hanzi: String,
        currentState: LeitnerState?,
        now: Long = System.currentTimeMillis()
    ): LeitnerState {
        if (currentState == null) {
            return LeitnerState(
                hanzi = hanzi,
                box = 0,
                expTime = now,
                numTested = 1,
                numWrong = 1,
                streak = 0,
                dateAdded = now
            )
        }

        return LeitnerState(
            hanzi = hanzi,
            box = 0,
            expTime = now,
            numTested = currentState.numTested + 1,
            numWrong = currentState.numWrong + 1,
            streak = 0,
            dateAdded = if (currentState.dateAdded != 0L) currentState.dateAdded else now
        )
    }

    /**
     * Selects an index from a queue of size [queueSize] with quadratic weighting toward the head (index 0).
     */
    @JvmStatic
    @JvmOverloads
    fun selectWeightedIndex(queueSize: Int, randomFactor: Double = Math.random()): Int {
        if (queueSize <= 0) return -1
        val clampedRandom = randomFactor.coerceIn(0.0, 1.0)
        val index = (queueSize * clampedRandom.pow(2.0)).toInt()
        return index.coerceIn(0, queueSize - 1)
    }

    /**
     * Formats a millisecond delay into a human-readable duration string (days, hours, minutes, seconds).
     */
    @JvmStatic
    fun formatDelayString(delayMillis: Long): String {
        val seconds = delayMillis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days >= 2 -> "$days days"
            days >= 1 -> {
                val remHours = hours - 24
                "1 day $remHours hours"
            }
            hours >= 2 -> "$hours hours"
            hours >= 1 -> {
                val remMinutes = minutes - 60
                "1 hour $remMinutes minutes"
            }
            minutes >= 2 -> "$minutes minutes"
            minutes >= 1 -> {
                val remSeconds = seconds - 60
                "1 minute $remSeconds seconds"
            }
            else -> "$seconds seconds"
        }
    }
}
