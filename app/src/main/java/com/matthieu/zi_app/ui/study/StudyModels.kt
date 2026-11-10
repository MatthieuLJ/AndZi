package com.matthieu.zi_app.ui.study

import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.domain.study.quiz.QuizAction
import com.matthieu.zi_app.domain.study.quiz.QuizPayload
import com.matthieu.zi_app.domain.study.quiz.QuizSessionState
import com.matthieu.zi_app.domain.writing.HanziCharacter
import com.matthieu.zi_app.domain.writing.HanziStroke
import com.matthieu.zi_app.domain.writing.WritingScaffoldingLevel

enum class QuizType {
    TONE,           // Guess the tone of the character
    PINYIN,         // Syllable / Pinyin recognition
    ENGLISH,        // Multiple-choice English definitions
    CLOZE,          // Missing character fill-in-the-blank
    WORD_ASSEMBLY,  // Assemble characters to form the compound word
    WRITING,        // Stroke-by-stroke handwriting test
    PAIR_MATCH      // Multi-pair collocation matching
}

data class CollocationPair(
    val word: String,
    val pinyin: String,
    val english: List<String>,
    val firstChar: String,
    val secondChar: String
)

data class StudyCard(
    val hanzi: String,
    val pinyin: String,
    val syllable: String = "",
    val tone: Int = 1,
    val english: List<String> = emptyList(),
    val currentBox: Int = 0,
    val quizType: QuizType = QuizType.TONE,
    val payload: QuizPayload = QuizPayload.Empty,
    val mcqOptions: List<String> = (payload as? QuizPayload.OptionsProvider)?.options ?: emptyList(),
    val wordSlots: List<String> = (payload as? QuizPayload.SlotsProvider)?.slots ?: emptyList(),
    val scrambledTiles: List<String> = (payload as? QuizPayload.TilesProvider)?.tiles ?: emptyList(),
    val clozeTargetIndex: Int = (payload as? QuizPayload.ClozeTargetProvider)?.targetIndex ?: -1,
    val contextWords: List<String> = (payload as? QuizPayload.ContextWordsProvider)?.contextWords ?: emptyList(),
    val displayPinyin: String = (payload as? QuizPayload.DisplayPinyinProvider)?.displayPinyin?.takeIf { it.isNotBlank() } ?: pinyin,
    val relatedStudyWords: List<Vocabulary> = (payload as? QuizPayload.Cloze)?.relatedStudyWords
        ?: (payload as? QuizPayload.Writing)?.relatedStudyWords
        ?: emptyList(),
    val writingCharacter: HanziCharacter? = (payload as? QuizPayload.WritingProvider)?.character,
    val matchPairs: List<CollocationPair> = (payload as? QuizPayload.MatchPairsProvider)?.pairs ?: emptyList(),
    val leftMatchTiles: List<String> = (payload as? QuizPayload.MatchPairsProvider)?.leftTiles ?: emptyList(),
    val rightMatchTiles: List<String> = (payload as? QuizPayload.MatchPairsProvider)?.rightTiles ?: emptyList()
) {
    companion object {
        fun fromPayload(
            hanzi: String,
            pinyin: String,
            syllable: String,
            tone: Int,
            english: List<String>,
            currentBox: Int,
            quizType: QuizType,
            payload: QuizPayload
        ): StudyCard {
            val cardEnglish = (payload as? QuizPayload.Cloze)?.cardEnglish?.takeIf { it.isNotEmpty() }
                ?: (payload as? QuizPayload.WordAssembly)?.cardEnglish?.takeIf { it.isNotEmpty() }
                ?: (payload as? QuizPayload.Writing)?.cardEnglish?.takeIf { it.isNotEmpty() }
                ?: english
            return StudyCard(
                hanzi = hanzi,
                pinyin = pinyin,
                syllable = syllable,
                tone = tone,
                english = cardEnglish,
                currentBox = currentBox,
                quizType = quizType,
                payload = payload
            )
        }
    }
    val revealedHanzi: String
        get() = if ((quizType == QuizType.CLOZE || quizType == QuizType.WRITING) && wordSlots.isNotEmpty()) {
            wordSlots.joinToString("")
        } else if (quizType == QuizType.PAIR_MATCH && matchPairs.isNotEmpty()) {
            matchPairs.joinToString(" • ") { it.word }
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
    val selectedPinyinTone: Int? = null,
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
    val isContextWordsRevealed: Boolean = false,
    val selectedLeftMatchIndex: Int? = null,
    val selectedRightMatchIndex: Int? = null,
    val matchedWords: Set<String> = emptySet(),
    val failedMatchWords: Set<String> = emptySet(),
    val matchErrorShakeTrigger: Int = 0,
    val sessionState: QuizSessionState = QuizSessionState.Idle(),
    val newlyUnlockedBadges: List<com.matthieu.zi_app.domain.badges.BadgeId> = emptyList(),
    val recordIncreases: List<String> = emptyList(),
    val showCelebration: Boolean = false
) {
    val currentCard: StudyCard?
        get() = queue.getOrNull(currentIndex)
}
