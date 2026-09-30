package com.matthieu.zi_app.domain.srs

/**
 * Pluggable interface for Spaced Repetition (SRS) scheduling algorithms.
 *
 * Implementations calculate next review intervals, state transitions, and study queue ordering
 * based on user recall grades.
 */
interface SpacedRepetitionEngine {

    /** Unique programmatic identifier (e.g. "leitner", "fsrs-5", "sm2") */
    val engineId: String

    /** Human-readable display title (e.g. "Adaptive Leitner System") */
    val displayName: String

    /** Brief description of the scheduling algorithm and mechanics */
    val description: String

    /**
     * Evaluates a completed review and calculates the updated review state and next review interval.
     *
     * @param cardId Unique identifier for the character/word.
     * @param currentState Existing review state (or null if first time tested).
     * @param grade User review rating (AGAIN, HARD, GOOD, EASY).
     * @param now Current timestamp in milliseconds.
     */
    fun calculateNextReview(
        cardId: String,
        currentState: ReviewState?,
        grade: ReviewGrade,
        now: Long = System.currentTimeMillis()
    ): ReviewScheduleResult

    /**
     * Resets a card's study progress completely back to the unlearned state.
     *
     * @param cardId Unique identifier for the character/word.
     * @param currentState Existing review state (or null).
     * @param now Current timestamp in milliseconds.
     */
    fun resetCard(
        cardId: String,
        currentState: ReviewState?,
        now: Long = System.currentTimeMillis()
    ): ReviewScheduleResult
}
