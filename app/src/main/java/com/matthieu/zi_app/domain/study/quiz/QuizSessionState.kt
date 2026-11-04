package com.matthieu.zi_app.domain.study.quiz

import com.matthieu.zi_app.domain.writing.HanziStroke
import com.matthieu.zi_app.domain.writing.WritingScaffoldingLevel

/**
 * Sealed hierarchy representing the transient, interactive state of a study quiz session.
 */
sealed interface QuizSessionState {
    val isContextWordsRevealed: Boolean get() = false

    data class Idle(
        override val isContextWordsRevealed: Boolean = false
    ) : QuizSessionState

    data class OptionSelected(
        val option: String,
        override val isContextWordsRevealed: Boolean = false
    ) : QuizSessionState

    data class PinyinInProgress(
        val selectedSyllable: String? = null,
        val selectedTone: Int? = null,
        override val isContextWordsRevealed: Boolean = false
    ) : QuizSessionState

    data class AssemblyInProgress(
        val assembledChars: List<String> = emptyList(),
        val usedTileIndices: List<Int> = emptyList(),
        override val isContextWordsRevealed: Boolean = false
    ) : QuizSessionState

    data class WritingInProgress(
        val completedStrokes: List<HanziStroke> = emptyList(),
        val currentStrokeIndex: Int = 0,
        val scaffoldingLevel: WritingScaffoldingLevel = WritingScaffoldingLevel.TEST,
        val hasUsedWritingGuide: Boolean = false,
        val errorShakeTrigger: Int = 0,
        override val isContextWordsRevealed: Boolean = false
    ) : QuizSessionState

    data class PairMatchInProgress(
        val selectedLeftIndex: Int? = null,
        val selectedRightIndex: Int? = null,
        val matchedWords: Set<String> = emptySet(),
        val failedMatchWords: Set<String> = emptySet(),
        val errorShakeTrigger: Int = 0,
        override val isContextWordsRevealed: Boolean = false
    ) : QuizSessionState
}

