package com.matthieu.zi_app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.Pinyin
import com.matthieu.zi_app.PinyinParser
import com.matthieu.zi_app.Vocabulary
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
    private val vocabularyRepository: VocabularyRepository,
    private val dictionaryRepository: VocabularyRepository? = null
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
                    val allVoc = vocabularyRepository.search("", limit = limit).ifEmpty {
                        dictionaryRepository?.search("", limit = limit) ?: emptyList()
                    }
                    queueHanzis = allVoc.map { it.chinese }
                }

                // Gather all characters and English translations from the user's active study list
                val studyChars = (
                    vocabularyRepository.getAllDistinctHanzi() +
                    queueHanzis.flatMap { h ->
                        var idx = 0
                        val chars = mutableListOf<String>()
                        while (idx < h.length) {
                            val cp = h.codePointAt(idx)
                            chars.add(String(Character.toChars(cp)))
                            idx += Character.charCount(cp)
                        }
                        chars
                    }
                ).distinct().toSet()

                val allStudyEnglish = vocabularyRepository.search("", limit = 100)
                    .flatMap { it.english }
                    .distinct()
                    .toSet()

                val cards = mutableListOf<StudyCard>()
                for ((index, hanzi) in queueHanzis.withIndex()) {
                    var vocList = vocabularyRepository.getVocabularyForChinese(hanzi)
                    if (vocList.isEmpty() && dictionaryRepository != null) {
                        vocList = dictionaryRepository.getVocabularyForChinese(hanzi)
                    }

                    val voc = if (vocList.isNotEmpty()) {
                        vocList.first()
                    } else {
                        val localChars = vocabularyRepository.getCharPinyinsForHanzi(hanzi)
                        val dictChars = if (localChars.isEmpty() && dictionaryRepository != null) {
                            dictionaryRepository.getCharPinyinsForHanzi(hanzi)
                        } else emptyList()
                        val pinyin = (localChars.firstOrNull() ?: dictChars.firstOrNull())?.pinyin ?: ""
                        Vocabulary(chinese = hanzi, pinyin = pinyin, english = ArrayList())
                    }

                    val leitnerState = studyRepository.getLeitnerState(hanzi)
                    val box = leitnerState?.box ?: 0

                    val pinyins = if (voc.pinyin.isNotBlank()) {
                        PinyinParser.parse_string_to_pinyin(voc.pinyin) ?: emptyList()
                    } else emptyList()
                    val firstPinyin = pinyins.firstOrNull()
                    val tone = firstPinyin?.tone ?: 1
                    val syllable = firstPinyin?.radical ?: (if (voc.pinyin.isNotBlank()) voc.pinyin else hanzi)

                    val charCount = hanzi.codePointCount(0, hanzi.length)
                    val isSingleChar = (charCount == 1)
                    val hasValidNonNeutralTone = isSingleChar && (tone in 1..4)

                    // 1. Determine Quiz Type:
                    // Tone test is ONLY for single characters with non-neutral tone (tones 1..4)
                    val quizType = when {
                        !isSingleChar -> {
                            // Multi-character words NEVER use Tone quiz!
                            when {
                                voc.english.isNotEmpty() -> when (index % 3) {
                                    0 -> QuizType.WORD_ASSEMBLY
                                    1 -> QuizType.CLOZE
                                    else -> QuizType.ENGLISH
                                }
                                index % 2 == 0 -> QuizType.WORD_ASSEMBLY
                                else -> QuizType.CLOZE
                            }
                        }
                        hasValidNonNeutralTone && (voc.english.isEmpty() || index % 2 == 0) -> {
                            QuizType.TONE
                        }
                        voc.english.isNotEmpty() -> {
                            QuizType.ENGLISH
                        }
                        hasValidNonNeutralTone -> {
                            QuizType.TONE
                        }
                        else -> {
                            QuizType.ENGLISH
                        }
                    }

                    // 2. If Tone Quiz on a polyphonic character, find compound words demonstrating this tone
                    var contextWords = emptyList<String>()
                    if (quizType == QuizType.TONE) {
                        val localChars = vocabularyRepository.getCharPinyinsForHanzi(hanzi)
                        val dictChars = if (dictionaryRepository != null) {
                            dictionaryRepository.getCharPinyinsForHanzi(hanzi)
                        } else emptyList()
                        val allChars = (localChars + dictChars).distinctBy { it.pinyin }
                        val distinctTones = allChars.mapNotNull {
                            PinyinParser.parse_string_to_pinyin(it.pinyin)?.firstOrNull()?.tone
                        }.filter { it in 1..4 }.distinct()

                        if (distinctTones.size > 1 || allChars.size > 1) {
                            var exampleWords = vocabularyRepository.getCompoundWordsForCharPinyin(hanzi, voc.pinyin, limit = 4)
                            if (exampleWords.isEmpty() && dictionaryRepository != null) {
                                exampleWords = dictionaryRepository.getCompoundWordsForCharPinyin(hanzi, voc.pinyin, limit = 4)
                            }
                            contextWords = exampleWords.filter { it.codePointCount(0, it.length) > 1 }
                        }
                    }

                    // 3. Generate MCQ distractors for English or Cloze
                    val mcqOptions = if (quizType == QuizType.ENGLISH) {
                        val correct = voc.english.firstOrNull() ?: "Meaning"
                        generateEnglishDistractors(correct, allStudyEnglish)
                    } else emptyList()

                    // 4. Generate Cloze / Assembly slots
                    val wordSlots = mutableListOf<String>()
                    val scrambledTiles = mutableListOf<String>()
                    var targetIdx = -1

                    if (quizType == QuizType.CLOZE || quizType == QuizType.WORD_ASSEMBLY) {
                        for (i in 0 until charCount) {
                            val codePoint = hanzi.codePointAt(i)
                            val ch = String(Character.toChars(codePoint))
                            wordSlots.add(ch)
                        }
                        val distractorCount = getDistractorCountForBox(box, quizType)
                        if (quizType == QuizType.CLOZE && wordSlots.isNotEmpty()) {
                            targetIdx = (0 until wordSlots.size).random()
                            val correctChar = wordSlots[targetIdx]
                            scrambledTiles.addAll(generateCharDistractors(correctChar, distractorCount, studyChars))
                        } else {
                            val distractors = generateCharDistractorsForWord(wordSlots.toSet(), distractorCount, studyChars)
                            scrambledTiles.addAll((wordSlots + distractors).shuffled())
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
                            clozeTargetIndex = targetIdx,
                            contextWords = contextWords
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
                        assembledChars = emptyList(),
                        usedTileIndices = emptyList(),
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

    fun selectAssemblyTile(tileIndex: Int, tileChar: String) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return
        val currentUsed = _uiState.value.usedTileIndices
        if (tileIndex in currentUsed) return

        val updatedAssembled = _uiState.value.assembledChars + tileChar
        val updatedUsed = currentUsed + tileIndex

        if (updatedAssembled.size >= card.wordSlots.size) {
            val assembledWord = updatedAssembled.joinToString("")
            val isCorrect = (assembledWord == card.hanzi)
            _uiState.update { it.copy(assembledChars = updatedAssembled, usedTileIndices = updatedUsed) }
            processAnswer(card, isCorrect, assembledWord)
        } else {
            _uiState.update { it.copy(assembledChars = updatedAssembled, usedTileIndices = updatedUsed) }
        }
    }

    fun undoAssemblyTile() {
        if (_uiState.value.isAnswerRevealed) return
        val currentAssembled = _uiState.value.assembledChars
        val currentUsed = _uiState.value.usedTileIndices
        if (currentAssembled.isNotEmpty() && currentUsed.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    assembledChars = currentAssembled.dropLast(1),
                    usedTileIndices = currentUsed.dropLast(1)
                )
            }
        }
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
                    selectedOption = null,
                    assembledChars = emptyList(),
                    usedTileIndices = emptyList()
                )
            } else {
                state.copy(
                    isSessionFinished = true,
                    isAnswerRevealed = false,
                    assembledChars = emptyList(),
                    usedTileIndices = emptyList()
                )
            }
        }
    }

    private fun getDistractorCountForBox(box: Int, quizType: QuizType): Int {
        return when (quizType) {
            QuizType.CLOZE -> when {
                box <= 0 -> 2
                box <= 2 -> 3
                box <= 4 -> 4
                else -> 5
            }
            QuizType.WORD_ASSEMBLY -> when {
                box <= 0 -> 2
                box <= 2 -> 3
                box <= 4 -> 4
                else -> 5
            }
            else -> 3
        }
    }

    private fun generateEnglishDistractors(
        correct: String,
        allStudyEnglish: Set<String> = emptySet()
    ): List<String> {
        val candidates = allStudyEnglish.filter { !it.equals(correct, ignoreCase = true) }.shuffled()
        val distractors = candidates.take(3).toMutableList()
        distractors.add(correct)
        return distractors.shuffled()
    }

    private fun generateCharDistractors(
        correct: String,
        count: Int = 3,
        studyChars: Set<String> = emptySet()
    ): List<String> {
        val candidates = studyChars.filter { it != correct }.shuffled()
        val distractors = candidates.take(count).toMutableList()
        distractors.add(correct)
        return distractors.shuffled()
    }

    private fun generateCharDistractorsForWord(
        existingChars: Set<String>,
        count: Int = 3,
        studyChars: Set<String> = emptySet()
    ): List<String> {
        val candidates = studyChars.filter { !existingChars.contains(it) }.shuffled()
        return candidates.take(count)
    }

    companion object {
        fun provideFactory(
            studyRepository: StudyRepository,
            vocabularyRepository: VocabularyRepository,
            dictionaryRepository: VocabularyRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StudyViewModel(studyRepository, vocabularyRepository, dictionaryRepository) as T
            }
        }
    }
}
