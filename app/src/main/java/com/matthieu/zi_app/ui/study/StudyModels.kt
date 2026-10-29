package com.matthieu.zi_app.ui.study

import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.domain.writing.HanziCharacter
import com.matthieu.zi_app.domain.writing.HanziStroke
import com.matthieu.zi_app.domain.writing.WritingScaffoldingLevel

enum class QuizType {
    TONE,           // Guess the tone of the character
    PINYIN,         // Syllable / Pinyin recognition
    ENGLISH,        // Multiple-choice English definitions
    CLOZE,          // Missing character fill-in-the-blank
    WORD_ASSEMBLY,  // Assemble characters to form the compound word
    WRITING         // Stroke-by-stroke handwriting test
}

data class StudyCard(
    val hanzi: String,
    val pinyin: String,
    val syllable: String = "",
    val tone: Int = 1,
    val english: List<String> = emptyList(),
    val currentBox: Int = 0,
    val quizType: QuizType = QuizType.TONE,
    val mcqOptions: List<String> = emptyList(),
    val wordSlots: List<String> = emptyList(),
    val scrambledTiles: List<String> = emptyList(),
    val clozeTargetIndex: Int = -1,
    val contextWords: List<String> = emptyList(),
    val displayPinyin: String = pinyin,
    val relatedStudyWords: List<Vocabulary> = emptyList(),
    val writingCharacter: HanziCharacter? = null
) {
    val revealedHanzi: String
        get() = if ((quizType == QuizType.CLOZE || quizType == QuizType.WRITING) && wordSlots.isNotEmpty()) {
            wordSlots.joinToString("")
        } else {
            hanzi
        }

    val missingCharacter: String
        get() = if (clozeTargetIndex in wordSlots.indices) {
            wordSlots[clozeTargetIndex]
        } else {
            hanzi
        }
}

data class SessionStats(
    val totalReviewed: Int = 0,
    val correctCount: Int = 0,
    val lapseCount: Int = 0
) {
    val accuracyPercentage: Int
        get() = if (totalReviewed > 0) ((correctCount.toFloat() / totalReviewed) * 100).toInt() else 0
}

data class StudyUiState(
    val queue: List<StudyCard> = emptyList(),
    val currentIndex: Int = 0,
    val remainingDueCount: Int = 0,
    val isAnswerRevealed: Boolean = false,
    val isCorrect: Boolean? = null,
    val selectedOption: String? = null,
    val isSessionFinished: Boolean = false,
    val sessionStats: SessionStats = SessionStats(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val assembledChars: List<String> = emptyList(),
    val usedTileIndices: List<Int> = emptyList(),
    val completedStrokes: List<HanziStroke> = emptyList(),
    val currentStrokeIndex: Int = 0,
    val scaffoldingLevel: WritingScaffoldingLevel = WritingScaffoldingLevel.TEST,
    val hasUsedWritingGuide: Boolean = false,
    val writingErrorShakeTrigger: Int = 0,
    val isContextWordsRevealed: Boolean = false
) {
    val currentCard: StudyCard?
        get() = queue.getOrNull(currentIndex)
}
