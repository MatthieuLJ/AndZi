package com.matthieu.zi_app.domain.study.quiz

import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.writing.HanziCharacter
import com.matthieu.zi_app.ui.study.QuizType
import com.matthieu.zi_app.ui.study.StudyCard

/**
 * Information needed to determine whether a quiz type is eligible for a card.
 */
data class QuizEligibilityContext(
    val box: Int,
    val isSingleChar: Boolean,
    val hasValidNonNeutralTone: Boolean,
    val hasUserEnglish: Boolean,
    val hasContainingWords: Boolean,
    val strokeCount: Int?,
    val canPairMatch: Boolean
)

/**
 * Context and repositories passed to build a quiz payload.
 */
data class QuizBuildContext(
    val box: Int,
    val index: Int,
    val studyChars: Set<String>,
    val containingWords: List<Vocabulary>,
    val allStudyEnglish: Set<String>,
    val strokeData: HanziCharacter?,
    val matchCompanions: List<Vocabulary>,
    val vocabularyRepository: VocabularyRepository,
    val dictionaryRepository: VocabularyRepository?,
    val studyRepository: StudyRepository
)

/**
 * Common strategy interface for each study test mode.
 */
interface QuizStrategy {
    val quizType: QuizType

    /**
     * Checks if this quiz type is eligible for the given word / character and study state.
     */
    fun isEligible(context: QuizEligibilityContext): Boolean

    /**
     * Builds the quiz payload for the study card.
     */
    suspend fun buildPayload(
        target: Vocabulary,
        context: QuizBuildContext
    ): QuizPayload

    /**
     * Contextual prompt string displayed above the card.
     */
    fun getPromptText(card: StudyCard, isRevealed: Boolean): String
}

