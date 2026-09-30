package com.matthieu.zi_app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.Pinyin
import com.matthieu.zi_app.PinyinParser
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StudyViewModel(
    private val studyRepository: StudyRepository,
    private val vocabularyRepository: VocabularyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyUiState(isLoading = true))
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    init {
        startStudySession()
    }

    fun startStudySession(limit: Int = 30) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val now = System.currentTimeMillis()
                var queueHanzis = studyRepository.getStudyQueue(limit, now)

                // Fallback: If no cards are overdue, fetch search sample from dictionary/vocab
                if (queueHanzis.isEmpty()) {
                    val allVoc = vocabularyRepository.search("", limit = limit)
                    queueHanzis = allVoc.map { it.chinese }
                }

                val cards = mutableListOf<StudyCard>()
                for ((index, hanzi) in queueHanzis.withIndex()) {
                    val vocList = vocabularyRepository.getVocabularyForChinese(hanzi)
                    val voc = vocList.firstOrNull() ?: continue
                    val leitnerState = studyRepository.getLeitnerState(hanzi)
                    val box = leitnerState?.box ?: 0

                    val pinyins = PinyinParser.parse_string_to_pinyin(voc.pinyin) ?: emptyList()
                    val firstPinyin = pinyins.firstOrNull()
                    val tone = firstPinyin?.tone ?: 1
                    val syllable = firstPinyin?.radical ?: voc.pinyin

                    // Alternate quiz formats based on card index and character length
                    val charCount = hanzi.codePointCount(0, hanzi.length)
                    val quizType = when {
                        charCount > 1 && index % 3 == 0 -> QuizType.CLOZE
                        charCount > 1 && index % 3 == 1 -> QuizType.WORD_ASSEMBLY
                        voc.english.isNotEmpty() && index % 2 == 1 -> QuizType.ENGLISH
                        else -> QuizType.TONE
                    }

                    // Generate MCQ distractors for English or Cloze
                    val mcqOptions = if (quizType == QuizType.ENGLISH) {
                        val correct = voc.english.firstOrNull() ?: "Meaning"
                        generateEnglishDistractors(correct)
                    } else emptyList()

                    // Generate Cloze / Assembly slots
                    val wordSlots = mutableListOf<String>()
                    val scrambledTiles = mutableListOf<String>()
                    var targetIdx = -1

                    if (quizType == QuizType.CLOZE || quizType == QuizType.WORD_ASSEMBLY) {
                        for (i in 0 until charCount) {
                            val codePoint = hanzi.codePointAt(i)
                            val ch = String(Character.toChars(codePoint))
                            wordSlots.add(ch)
                        }
                        if (quizType == QuizType.CLOZE && wordSlots.isNotEmpty()) {
                            targetIdx = (0 until wordSlots.size).random()
                            val correctChar = wordSlots[targetIdx]
                            scrambledTiles.addAll(generateCharDistractors(correctChar))
                        } else {
                            scrambledTiles.addAll(wordSlots.shuffled())
                        }
                    }

                    cards.add(
                        StudyCard(
                            hanzi = hanzi,
                            pinyin = voc.pinyin,
                            syllable = syllable,
                            tone = tone,
                            english = voc.english,
                            currentBox = box,
                            quizType = quizType,
                            mcqOptions = mcqOptions,
                            wordSlots = wordSlots,
                            scrambledTiles = scrambledTiles,
                            clozeTargetIndex = targetIdx
                        )
                    )
                }

                _uiState.update {
                    it.copy(
                        queue = cards,
                        currentIndex = 0,
                        isAnswerRevealed = false,
                        isCorrect = null,
                        selectedOption = null,
                        isSessionFinished = cards.isEmpty(),
                        sessionStats = SessionStats(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load study cards"
                    )
                }
            }
        }
    }

    fun submitToneAnswer(selectedTone: Int) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return

        val isCorrect = (selectedTone == card.tone)
        processAnswer(card, isCorrect, selectedTone.toString())
    }

    fun submitEnglishAnswer(selectedMeaning: String) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return

        val isCorrect = card.english.any { it.equals(selectedMeaning, ignoreCase = true) }
        processAnswer(card, isCorrect, selectedMeaning)
    }

    fun submitClozeAnswer(selectedChar: String) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return

        val targetChar = if (card.clozeTargetIndex in card.wordSlots.indices) {
            card.wordSlots[card.clozeTargetIndex]
        } else ""

        val isCorrect = (selectedChar == targetChar)
        processAnswer(card, isCorrect, selectedChar)
    }

    private fun processAnswer(card: StudyCard, isCorrect: Boolean, selectedInput: String) {
        viewModelScope.launch {
            val grade = if (isCorrect) ReviewGrade.GOOD else ReviewGrade.AGAIN
            studyRepository.recordReviewGrade(card.hanzi, grade)

            _uiState.update { state ->
                val prevStats = state.sessionStats
                val newStats = prevStats.copy(
                    totalReviewed = prevStats.totalReviewed + 1,
                    correctCount = prevStats.correctCount + if (isCorrect) 1 else 0,
                    lapseCount = prevStats.lapseCount + if (!isCorrect) 1 else 0
                )
                state.copy(
                    isAnswerRevealed = true,
                    isCorrect = isCorrect,
                    selectedOption = selectedInput,
                    sessionStats = newStats
                )
            }
        }
    }

    fun rateCard(grade: ReviewGrade) {
        val card = _uiState.value.currentCard ?: return
        viewModelScope.launch {
            studyRepository.recordReviewGrade(card.hanzi, grade)
            nextCard()
        }
    }

    fun nextCard() {
        _uiState.update { state ->
            val nextIdx = state.currentIndex + 1
            if (nextIdx < state.queue.size) {
                state.copy(
                    currentIndex = nextIdx,
                    isAnswerRevealed = false,
                    isCorrect = null,
                    selectedOption = null
                )
            } else {
                state.copy(
                    isSessionFinished = true,
                    isAnswerRevealed = false
                )
            }
        }
    }

    private fun generateEnglishDistractors(correct: String): List<String> {
        val fallbackDistractors = listOf(
            "water, liquid", "to learn, to study", "bright, clear",
            "friend, companion", "to eat, food", "large, big",
            "person, human", "time, hour", "to speak, words"
        ).filter { !it.equals(correct, ignoreCase = true) }

        val distractors = fallbackDistractors.shuffled().take(3).toMutableList()
        distractors.add(correct)
        return distractors.shuffled()
    }

    private fun generateCharDistractors(correct: String): List<String> {
        val fallbackChars = listOf("学", "字", "明", "大", "中", "国", "人", "水", "火", "木", "天", "地")
            .filter { it != correct }
        val distractors = fallbackChars.shuffled().take(3).toMutableList()
        distractors.add(correct)
        return distractors.shuffled()
    }

    companion object {
        fun provideFactory(
            studyRepository: StudyRepository,
            vocabularyRepository: VocabularyRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StudyViewModel(studyRepository, vocabularyRepository) as T
            }
        }
    }
}
