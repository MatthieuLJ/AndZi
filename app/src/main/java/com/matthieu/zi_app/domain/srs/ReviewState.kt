package com.matthieu.zi_app.domain.srs

/**
 * Universal, engine-agnostic representation of a card's spaced repetition study state.
 */
data class ReviewState(
    /** Unique identifier for the word or character (e.g. "好" or "你好") */
    val cardId: String,

    /** Current box (0-9 for Leitner engines, or -1 if non-box engine) */
    val box: Int = 0,

    /** Next review expiration timestamp in milliseconds */
    val nextReviewTimestamp: Long = 0L,

    /** Total number of reviews completed for this card */
    val reviewCount: Int = 0,

    /** Total number of failed reviews (lapses) */
    val lapseCount: Int = 0,

    /** Consecutive correct answer streak */
    val streak: Int = 0,

    /** Timestamp when this card was first added to the study queue */
    val dateAdded: Long = 0L,

    /** Custom key-value metadata for extensible algorithms (e.g. stability, difficulty for FSRS) */
    val customData: Map<String, String> = emptyMap()
) {
    fun isDue(now: Long = System.currentTimeMillis()): Boolean {
        return box == 0 || nextReviewTimestamp <= now
    }
}
