package com.matthieu.zi_app.domain.srs

/**
 * Standard 4-level review grade for flashcard practice and Spaced Repetition (SRS).
 */
enum class ReviewGrade(val value: Int) {
    /** Complete failure / forgot the character, syllable or meaning */
    AGAIN(1),

    /** Recalled with significant effort, hesitation, or tone inaccuracy */
    HARD(2),

    /** Correct recall with normal effort (default for automated quiz success) */
    GOOD(3),

    /** Instant, effortless recall with strong confidence */
    EASY(4);

    companion object {
        /**
         * Converts binary pass/fail quiz outcome into standard review grade.
         */
        @JvmStatic
        fun fromBinary(isCorrect: Boolean): ReviewGrade {
            return if (isCorrect) GOOD else AGAIN
        }

        @JvmStatic
        fun fromValue(value: Int): ReviewGrade {
            return entries.find { it.value == value } ?: GOOD
        }
    }
}
