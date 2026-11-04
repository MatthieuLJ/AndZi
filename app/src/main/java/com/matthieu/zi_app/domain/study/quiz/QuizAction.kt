package com.matthieu.zi_app.domain.study.quiz

import com.matthieu.zi_app.domain.writing.HanziPoint
import com.matthieu.zi_app.domain.writing.WritingScaffoldingLevel

/**
 * Unified sealed hierarchy of user actions and events emitted from quiz UI components.
 */
sealed interface QuizAction {
    data class ToneSelected(val tone: Int) : QuizAction
    data class EnglishSelected(val option: String) : QuizAction
    data class ClozeSelected(val character: String) : QuizAction
    data class PinyinSyllableSelected(val syllable: String) : QuizAction
    data class PinyinToneSelected(val tone: Int) : QuizAction
    data class AssemblyTileSelected(val index: Int, val character: String) : QuizAction
    data object UndoAssemblyTile : QuizAction
    data class MatchLeftSelected(val index: Int) : QuizAction
    data class MatchRightSelected(val index: Int) : QuizAction
    data class WritingStrokeDrawn(val points: List<HanziPoint>) : QuizAction
    data class SetWritingScaffolding(val level: WritingScaffoldingLevel) : QuizAction
    data object UndoWritingStroke : QuizAction
    data object ClearWritingCanvas : QuizAction
    data object SurrenderWriting : QuizAction
    data object RevealContextWords : QuizAction
}

