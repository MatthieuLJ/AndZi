package com.matthieu.zi_app.domain.study

import com.matthieu.zi_app.ui.study.QuizType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveQuizPolicyTest {

    @Test
    fun testMinBoxForWritingProgression() {
        // 1 to 3 strokes -> Box 1+
        assertEquals(1, AdaptiveQuizPolicy.minBoxForWriting(1))
        assertEquals(1, AdaptiveQuizPolicy.minBoxForWriting(2))
        assertEquals(1, AdaptiveQuizPolicy.minBoxForWriting(3))

        // 4 to 6 strokes -> Box 2+
        assertEquals(2, AdaptiveQuizPolicy.minBoxForWriting(4))
        assertEquals(2, AdaptiveQuizPolicy.minBoxForWriting(6))

        // 7 to 9 strokes -> Box 3+
        assertEquals(3, AdaptiveQuizPolicy.minBoxForWriting(7))
        assertEquals(3, AdaptiveQuizPolicy.minBoxForWriting(9))

        // 10 to 12 strokes -> Box 4+
        assertEquals(4, AdaptiveQuizPolicy.minBoxForWriting(10))
        assertEquals(4, AdaptiveQuizPolicy.minBoxForWriting(12))

        // 13+ strokes -> Box 5+
        assertEquals(5, AdaptiveQuizPolicy.minBoxForWriting(13))
        assertEquals(5, AdaptiveQuizPolicy.minBoxForWriting(20))
    }

    @Test
    fun testNoWritingInBox0UnderAnyCircumstances() {
        // Even for 1-stroke characters with writing enabled, Box 0 must NEVER generate WRITING
        for (index in 0..10) {
            val quiz = AdaptiveQuizPolicy.selectQuizType(
                box = 0,
                isSingleChar = true,
                hasValidNonNeutralTone = true,
                hasUserEnglish = true,
                hasContainingWords = true,
                strokeCount = 1,
                index = index
            )
            assertFalse("Box 0 must never produce WRITING quiz", quiz == QuizType.WRITING)
        }
    }

    @Test
    fun testNoToneQuizAtBox6OrAbove() {
        // For box 6 to 9, TONE quiz must NEVER be generated, even if valid non-neutral tone exists
        for (box in 6..9) {
            for (index in 0..10) {
                val quiz = AdaptiveQuizPolicy.selectQuizType(
                    box = box,
                    isSingleChar = true,
                    hasValidNonNeutralTone = true,
                    hasUserEnglish = true,
                    hasContainingWords = true,
                    strokeCount = 4,
                    index = index
                )
                assertFalse("Box $box must never produce TONE quiz", quiz == QuizType.TONE)
            }
        }
    }

    @Test
    fun testNoToneQuizWhenToneIsNoneOrNeutral() {
        // When hasValidNonNeutralTone is false, TONE quiz must NEVER be generated for any box
        for (box in 0..5) {
            for (index in 0..10) {
                val quiz = AdaptiveQuizPolicy.selectQuizType(
                    box = box,
                    isSingleChar = true,
                    hasValidNonNeutralTone = false,
                    hasUserEnglish = true,
                    hasContainingWords = true,
                    strokeCount = 4,
                    index = index
                )
                assertFalse("Tone none/0 must never produce TONE quiz", quiz == QuizType.TONE)
            }
        }
    }

    @Test
    fun testWritingGatedByStrokeCount() {
        // Complex character with 12 strokes:
        // In Box 1, 2, 3: WRITING must NOT be present
        for (box in 1..3) {
            for (index in 0..10) {
                val quiz = AdaptiveQuizPolicy.selectQuizType(
                    box = box,
                    isSingleChar = true,
                    hasValidNonNeutralTone = true,
                    hasUserEnglish = true,
                    hasContainingWords = false,
                    strokeCount = 12,
                    index = index
                )
                assertFalse("12-stroke character in box $box must not get WRITING", quiz == QuizType.WRITING)
            }
        }

        // In Box 4+, 12-stroke character becomes eligible for WRITING
        val box4Quizzes = (0..5).map { index ->
            AdaptiveQuizPolicy.selectQuizType(
                box = 4,
                isSingleChar = true,
                hasValidNonNeutralTone = true,
                hasUserEnglish = true,
                hasContainingWords = false,
                strokeCount = 12,
                index = index
            )
        }
        assertTrue("12-stroke character in Box 4 should have WRITING available", box4Quizzes.contains(QuizType.WRITING))
    }

    @Test
    fun testMultiCharacterWordsNeverUseToneOrWriting() {
        for (box in 0..9) {
            for (index in 0..6) {
                val quiz = AdaptiveQuizPolicy.selectQuizType(
                    box = box,
                    isSingleChar = false,
                    hasValidNonNeutralTone = true,
                    hasUserEnglish = true,
                    hasContainingWords = false,
                    strokeCount = 2,
                    index = index
                )
                assertFalse(quiz == QuizType.TONE)
                assertFalse(quiz == QuizType.WRITING)
                assertTrue(quiz in listOf(QuizType.WORD_ASSEMBLY, QuizType.CLOZE, QuizType.ENGLISH))
            }
        }
    }
}

