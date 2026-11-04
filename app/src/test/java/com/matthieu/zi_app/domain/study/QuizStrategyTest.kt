package com.matthieu.zi_app.domain.study

import com.matthieu.zi_app.domain.study.quiz.ClozeQuizStrategy
import com.matthieu.zi_app.domain.study.quiz.EnglishQuizStrategy
import com.matthieu.zi_app.domain.study.quiz.PairMatchQuizStrategy
import com.matthieu.zi_app.domain.study.quiz.PinyinQuizStrategy
import com.matthieu.zi_app.domain.study.quiz.QuizEligibilityContext
import com.matthieu.zi_app.domain.study.quiz.QuizStrategyRegistry
import com.matthieu.zi_app.domain.study.quiz.ToneQuizStrategy
import com.matthieu.zi_app.domain.study.quiz.WordAssemblyQuizStrategy
import com.matthieu.zi_app.domain.study.quiz.WritingQuizStrategy
import com.matthieu.zi_app.ui.study.QuizType
import com.matthieu.zi_app.ui.study.StudyCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizStrategyTest {

    @Test
    fun testStrategyRegistryContainsAllTypes() {
        for (type in QuizType.entries) {
            val strategy = QuizStrategyRegistry.getStrategy(type)
            assertEquals(type, strategy.quizType)
        }
    }

    @Test
    fun testToneStrategyEligibility() {
        // Eligible: single char, non-neutral tone, box < 6
        val eligibleCtx = QuizEligibilityContext(
            box = 1,
            isSingleChar = true,
            hasValidNonNeutralTone = true,
            hasUserEnglish = true,
            hasContainingWords = false,
            strokeCount = null,
            canPairMatch = false
        )
        assertTrue(ToneQuizStrategy.isEligible(eligibleCtx))

        // Ineligible: box >= 6
        assertFalse(ToneQuizStrategy.isEligible(eligibleCtx.copy(box = 6)))

        // Ineligible: neutral tone
        assertFalse(ToneQuizStrategy.isEligible(eligibleCtx.copy(hasValidNonNeutralTone = false)))

        // Ineligible: multi-character word
        assertFalse(ToneQuizStrategy.isEligible(eligibleCtx.copy(isSingleChar = false)))
    }

    @Test
    fun testWritingStrategyEligibility() {
        // Ineligible without stroke data
        val noStrokes = QuizEligibilityContext(
            box = 3,
            isSingleChar = true,
            hasValidNonNeutralTone = true,
            hasUserEnglish = true,
            hasContainingWords = false,
            strokeCount = null,
            canPairMatch = false
        )
        assertFalse(WritingQuizStrategy.isEligible(noStrokes))

        // Ineligible in Box 0
        val box0 = noStrokes.copy(box = 0, strokeCount = 1)
        assertFalse(WritingQuizStrategy.isEligible(box0))

        // Ineligible when box is below complexity requirement (e.g. 10 strokes requires Box 4+)
        val highStrokesLowBox = noStrokes.copy(box = 2, strokeCount = 10)
        assertFalse(WritingQuizStrategy.isEligible(highStrokesLowBox))

        // Eligible when box meets complexity threshold (10 strokes at Box 4)
        val highStrokesSufficientBox = noStrokes.copy(box = 4, strokeCount = 10)
        assertTrue(WritingQuizStrategy.isEligible(highStrokesSufficientBox))

        // Ineligible for multi-character compound words
        assertFalse(WritingQuizStrategy.isEligible(highStrokesSufficientBox.copy(isSingleChar = false)))
    }

    @Test
    fun testEnglishStrategyEligibility() {
        val withEnglish = QuizEligibilityContext(
            box = 1,
            isSingleChar = true,
            hasValidNonNeutralTone = true,
            hasUserEnglish = true,
            hasContainingWords = false,
            strokeCount = null,
            canPairMatch = false
        )
        assertTrue(EnglishQuizStrategy.isEligible(withEnglish))
        assertFalse(EnglishQuizStrategy.isEligible(withEnglish.copy(hasUserEnglish = false)))
    }

    @Test
    fun testClozeStrategyEligibility() {
        // Single character: requires containing words
        val singleWithContext = QuizEligibilityContext(
            box = 1,
            isSingleChar = true,
            hasValidNonNeutralTone = true,
            hasUserEnglish = true,
            hasContainingWords = true,
            strokeCount = null,
            canPairMatch = false
        )
        assertTrue(ClozeQuizStrategy.isEligible(singleWithContext))
        assertFalse(ClozeQuizStrategy.isEligible(singleWithContext.copy(hasContainingWords = false)))

        // Multi-character word: always eligible
        val multiWord = singleWithContext.copy(isSingleChar = false, hasContainingWords = false)
        assertTrue(ClozeQuizStrategy.isEligible(multiWord))
    }

    @Test
    fun testWordAssemblyStrategyEligibility() {
        val single = QuizEligibilityContext(
            box = 1,
            isSingleChar = true,
            hasValidNonNeutralTone = true,
            hasUserEnglish = true,
            hasContainingWords = false,
            strokeCount = null,
            canPairMatch = false
        )
        assertFalse(WordAssemblyQuizStrategy.isEligible(single))
        assertTrue(WordAssemblyQuizStrategy.isEligible(single.copy(isSingleChar = false)))
    }

    @Test
    fun testPairMatchStrategyEligibility() {
        val multi = QuizEligibilityContext(
            box = 1,
            isSingleChar = false,
            hasValidNonNeutralTone = false,
            hasUserEnglish = true,
            hasContainingWords = false,
            strokeCount = null,
            canPairMatch = true
        )
        assertTrue(PairMatchQuizStrategy.isEligible(multi))
        assertFalse(PairMatchQuizStrategy.isEligible(multi.copy(canPairMatch = false)))
        assertFalse(PairMatchQuizStrategy.isEligible(multi.copy(isSingleChar = true)))
    }

    @Test
    fun testPinyinStrategyEligibility() {
        val single = QuizEligibilityContext(
            box = 0,
            isSingleChar = true,
            hasValidNonNeutralTone = false,
            hasUserEnglish = false,
            hasContainingWords = false,
            strokeCount = null,
            canPairMatch = false
        )
        assertTrue(PinyinQuizStrategy.isEligible(single))
        assertFalse(PinyinQuizStrategy.isEligible(single.copy(isSingleChar = false)))
    }

    @Test
    fun testStrategyPromptTexts() {
        val dummyCard = StudyCard(hanzi = "你", pinyin = "ni3", tone = 3)
        assertEquals("Swipe or tap the correct tone", ToneQuizStrategy.getPromptText(dummyCard, isRevealed = false))
        assertEquals("", ToneQuizStrategy.getPromptText(dummyCard, isRevealed = true))

        assertEquals("Select the English meaning", EnglishQuizStrategy.getPromptText(dummyCard, isRevealed = false))
        assertEquals("Fill in the missing character", ClozeQuizStrategy.getPromptText(dummyCard, isRevealed = false))
        assertEquals("Assemble the compound word", WordAssemblyQuizStrategy.getPromptText(dummyCard, isRevealed = false))
        assertEquals("Match the characters to form words", PairMatchQuizStrategy.getPromptText(dummyCard, isRevealed = false))
    }
}

