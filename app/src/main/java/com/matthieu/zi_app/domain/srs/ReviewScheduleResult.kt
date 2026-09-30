package com.matthieu.zi_app.domain.srs

/**
 * Result returned by a SpacedRepetitionEngine after evaluating a card review.
 */
data class ReviewScheduleResult(
    /** Updated study state to persist in database */
    val updatedState: ReviewState,

    /** Interval until next review in milliseconds */
    val intervalMillis: Long,

    /** Expiration timestamp in milliseconds when card will next become due */
    val nextReviewTimestamp: Long,

    /** Human-readable delay string (e.g. "1 day 6 hours") */
    val formattedInterval: String
)
