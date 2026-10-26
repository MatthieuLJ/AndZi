package com.matthieu.zi_app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.LeitnerState
import com.matthieu.zi_app.Pinyin
import com.matthieu.zi_app.PinyinParser
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.preferences.UserPreferences
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
    private val dictionaryRepository: VocabularyRepository? = null,
    private val userPreferences: UserPreferences? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudyUiState(isLoading = true))
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private var testStepIndex = 0
    private var lastTestedHanzi: String? = null
    private var lastPreReviewState: LeitnerState? = null
    private val recentTestedWords = ArrayDeque<String>()

    init {
        startStudySession()
    }

    fun startStudySession() {
        viewModelScope.launch {
            testStepIndex = 0
            lastTestedHanzi = null
            lastPreReviewState = null
            recentTestedWords.clear()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    sessionStats = SessionStats(),
                    isSessionFinished = false
                )
            }
            loadNextCard(isInitialLoad = true)
        }
    }

    private suspend fun loadNextCard(isInitialLoad: Boolean) {
        try {
            val now = System.currentTimeMillis()
            val dueCandidates = studyRepository.getStudyQueue(limit = 20, now = now)

            val nextHanzi: String? = when {
                dueCandidates.isNotEmpty() -> {
                    // Priority 0: Not similar to last tested word AND not similar to recent words in session
                    val nonSimilarToRecent = dueCandidates.firstOrNull { candidate ->
                        candidate != lastTestedHanzi &&
                        !areWordsSimilar(candidate, lastTestedHanzi) &&
                        recentTestedWords.none { areWordsSimilar(candidate, it) }
                    }

                    // Priority 1: Not similar to immediately preceding word (consecutive tests do not share any character)
                    val nonSimilarToLast = dueCandidates.firstOrNull { candidate ->
                        candidate != lastTestedHanzi && !areWordsSimilar(candidate, lastTestedHanzi)
                    }

                    // Priority 2: Different word from last tested word
                    val differentFromLast = dueCandidates.firstOrNull { it != lastTestedHanzi }

                    // Fallback: First candidate in queue
                    nonSimilarToRecent ?: nonSimilarToLast ?: differentFromLast ?: dueCandidates.first()
                }
                isInitialLoad -> {
                    val allVoc = vocabularyRepository.getAllVocabulary()
                    if (allVoc.isNotEmpty()) {
                        allVoc.first().chinese
                    } else {
                        val distinctUser = vocabularyRepository.getAllDistinctHanzi()
                        if (distinctUser.isNotEmpty()) {
                            distinctUser.first()
                        } else {
                            dictionaryRepository?.getAllDistinctHanzi()?.firstOrNull()
                        }
                    }
                }
                else -> null
            }

            if (nextHanzi == null) {
                _uiState.update {
                    it.copy(
                        queue = emptyList(),
                        currentIndex = 0,
                        remainingDueCount = 0,
                        isAnswerRevealed = false,
                        isCorrect = null,
                        selectedOption = null,
                        assembledChars = emptyList(),
                        usedTileIndices = emptyList(),
                        isSessionFinished = true,
                        isLoading = false
                    )
                }
                return
            }

            val card = buildStudyCard(nextHanzi, testStepIndex)
            testStepIndex++
            lastTestedHanzi = nextHanzi
            recentTestedWords.addLast(nextHanzi)
            if (recentTestedWords.size > 5) {
                recentTestedWords.removeFirst()
            }
            val dueCount = studyRepository.getNumExpired(now)

            _uiState.update {
                it.copy(
                    queue = listOf(card),
                    currentIndex = 0,
                    remainingDueCount = dueCount,
                    isAnswerRevealed = false,
                    isCorrect = null,
                    selectedOption = null,
                    assembledChars = emptyList(),
                    usedTileIndices = emptyList(),
                    isSessionFinished = false,
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to load study card"
                )
            }
        }
    }

    private suspend fun buildStudyCard(hanzi: String, index: Int): StudyCard {
        val studyChars = (
            vocabularyRepository.getAllDistinctHanzi() +
            run {
                var idx = 0
                val chars = mutableListOf<String>()
                while (idx < hanzi.length) {
                    val cp = hanzi.codePointAt(idx)
                    chars.add(String(Character.toChars(cp)))
                    idx += Character.charCount(cp)
                }
                chars
            }
        ).distinct().toSet()

        val userEnglish = vocabularyRepository.getAllVocabulary()
            .flatMap { it.english }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val dictEnglish = if (userEnglish.size < 20 && dictionaryRepository != null) {
            dictionaryRepository.getRandomTranslations(limit = 40)
        } else emptyList()

        val allStudyEnglish = (userEnglish + dictEnglish).distinct().toSet()

        val localVocList = vocabularyRepository.getVocabularyForChinese(hanzi)
        val localVoc = localVocList.firstOrNull { it.english.isNotEmpty() } ?: localVocList.firstOrNull()

        val hasUserEnglish = localVoc != null && localVoc.english.isNotEmpty()
        val voc = if (localVoc != null) {
            localVoc
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

        val containingWords = if (isSingleChar) {
            vocabularyRepository.getWordsContainingCharacter(hanzi).filter { it.chinese.length > 1 }
        } else emptyList()

        // 1. Determine Quiz Type:
        val quizType = when {
            !isSingleChar -> {
                // Multi-character words NEVER use Tone quiz
                if (hasUserEnglish) {
                    when (index % 3) {
                        0 -> QuizType.WORD_ASSEMBLY
                        1 -> QuizType.CLOZE
                        else -> QuizType.ENGLISH
                    }
                } else {
                    if (index % 2 == 0) QuizType.WORD_ASSEMBLY else QuizType.CLOZE
                }
            }
            !hasUserEnglish -> {
                // Single character from char_pinyin (without user English translations)
                // MUST NEVER use QuizType.ENGLISH!
                when {
                    hasValidNonNeutralTone && containingWords.isNotEmpty() -> {
                        if (index % 2 == 0) QuizType.TONE else QuizType.CLOZE
                    }
                    hasValidNonNeutralTone -> QuizType.TONE
                    containingWords.isNotEmpty() -> QuizType.CLOZE
                    else -> QuizType.TONE
                }
            }
            else -> {
                // Single character WITH user English translations
                when {
                    hasValidNonNeutralTone -> {
                        if (index % 2 == 0) QuizType.TONE else QuizType.ENGLISH
                    }
                    else -> QuizType.ENGLISH
                }
            }
        }

        // 2. If Tone Quiz, find studied compound words demonstrating this character (study stack only)
        var contextWords = emptyList<String>()
        if (quizType == QuizType.TONE) {
            val exampleWords = vocabularyRepository.getCompoundWordsForCharPinyin(hanzi, voc.pinyin, limit = 4)
            contextWords = exampleWords.filter { it != hanzi && it.codePointCount(0, it.length) > 1 }
        }

        // 3. Generate MCQ distractors for English
        val mcqOptions = if (quizType == QuizType.ENGLISH) {
            val correct = voc.english.randomOrNull() ?: "Meaning"
            generateEnglishDistractors(correct, allStudyEnglish, excludedMeanings = voc.english)
        } else emptyList()

        // 4. Generate Cloze / Assembly slots
        var cardEnglish = voc.english
        var displayPinyin = voc.pinyin
        var relatedStudyWords = emptyList<Vocabulary>()
        val wordSlots = mutableListOf<String>()
        val scrambledTiles = mutableListOf<String>()
        var targetIdx = -1

        val distractorCount = getDistractorCountForBox(box, quizType)

        if (quizType == QuizType.CLOZE) {
            if (isSingleChar && containingWords.isNotEmpty()) {
                // Test filling the single character within a larger compound word!
                val comp = containingWords[index % containingWords.size]
                val compHanzi = comp.chinese
                var charIdx = 0
                while (charIdx < compHanzi.length) {
                    val cp = compHanzi.codePointAt(charIdx)
                    wordSlots.add(String(Character.toChars(cp)))
                    charIdx += Character.charCount(cp)
                }
                val idxInWord = wordSlots.indexOf(hanzi)
                targetIdx = if (idxInWord >= 0) idxInWord else 0
                val correctChar = wordSlots[targetIdx]
                scrambledTiles.addAll(generateCharDistractors(correctChar, distractorCount, studyChars))
                if (comp.english.isNotEmpty()) {
                    cardEnglish = comp.english
                }
                if (comp.pinyin.isNotBlank()) {
                    displayPinyin = comp.pinyin
                }
            } else {
                for (i in 0 until charCount) {
                    val codePoint = hanzi.codePointAt(i)
                    val ch = String(Character.toChars(codePoint))
                    wordSlots.add(ch)
                }
                if (wordSlots.isNotEmpty()) {
                    targetIdx = (0 until wordSlots.size).random()
                    val correctChar = wordSlots[targetIdx]
                    scrambledTiles.addAll(generateCharDistractors(correctChar, distractorCount, studyChars))
                }
            }

            val missingChar = if (targetIdx in wordSlots.indices) wordSlots[targetIdx] else hanzi
            val revealedWord = if (wordSlots.isNotEmpty()) wordSlots.joinToString("") else hanzi
            relatedStudyWords = (
                vocabularyRepository.getVocabularyForChinese(missingChar).filter { it.english.isNotEmpty() } +
                vocabularyRepository.getWordsContainingCharacter(missingChar)
            )
                .distinctBy { "${it.chinese}_${it.pinyin}" }
                .sortedBy { if (it.chinese == revealedWord) 1 else 0 }
        } else if (quizType == QuizType.WORD_ASSEMBLY) {
            for (i in 0 until charCount) {
                val codePoint = hanzi.codePointAt(i)
                val ch = String(Character.toChars(codePoint))
                wordSlots.add(ch)
            }
            val distractors = generateCharDistractorsForWord(wordSlots.toSet(), distractorCount, studyChars)
            scrambledTiles.addAll((wordSlots + distractors).shuffled())
        }

        return StudyCard(
            hanzi = hanzi,
            pinyin = voc.pinyin,
            syllable = syllable,
            tone = tone,
            english = cardEnglish,
            currentBox = box,
            quizType = quizType,
            mcqOptions = mcqOptions,
            wordSlots = wordSlots,
            scrambledTiles = scrambledTiles,
            clozeTargetIndex = targetIdx,
            contextWords = contextWords,
            displayPinyin = displayPinyin,
            relatedStudyWords = relatedStudyWords
        )
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
            val now = System.currentTimeMillis()
            lastPreReviewState = studyRepository.getLeitnerState(card.hanzi)
            val grade = if (isCorrect) ReviewGrade.GOOD else ReviewGrade.AGAIN
            studyRepository.recordReviewGrade(card.hanzi, grade, now)
            userPreferences?.recordStudySession(now)
            val dueCount = studyRepository.getNumExpired(now)

            _uiState.update { state ->
                val prevStats = state.sessionStats
                val newStats = prevStats.copy(
                    totalReviewed = prevStats.totalReviewed + 1,
                    correctCount = prevStats.correctCount + if (isCorrect) 1 else 0,
                    lapseCount = prevStats.lapseCount + if (!isCorrect) 1 else 0
                )
                state.copy(
                    remainingDueCount = dueCount,
                    isAnswerRevealed = true,
                    isCorrect = isCorrect,
                    selectedOption = selectedInput,
                    sessionStats = newStats
                )
            }
        }
    }

    fun markAnswerAsAcceptable() {
        val state = _uiState.value
        val card = state.currentCard ?: return
        if (!state.isAnswerRevealed || state.isCorrect != false) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            studyRepository.overrideReviewGrade(card.hanzi, lastPreReviewState, ReviewGrade.GOOD, now)
            userPreferences?.recordStudySession(now)
            val dueCount = studyRepository.getNumExpired(now)

            _uiState.update { current ->
                val prevStats = current.sessionStats
                val newStats = prevStats.copy(
                    correctCount = prevStats.correctCount + 1,
                    lapseCount = (prevStats.lapseCount - 1).coerceAtLeast(0)
                )
                current.copy(
                    remainingDueCount = dueCount,
                    isCorrect = true,
                    sessionStats = newStats
                )
            }
        }
    }

    fun rateCard(grade: ReviewGrade) {
        val card = _uiState.value.currentCard ?: return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            studyRepository.recordReviewGrade(card.hanzi, grade, now)
            userPreferences?.recordStudySession(now)
            loadNextCard(isInitialLoad = false)
        }
    }

    fun nextCard() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            loadNextCard(isInitialLoad = false)
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
        allStudyEnglish: Set<String> = emptySet(),
        excludedMeanings: Collection<String> = emptyList()
    ): List<String> {
        val excludedLower = (excludedMeanings + correct).map { it.trim().lowercase() }.toSet()
        val candidates = allStudyEnglish
            .filter { it.isNotBlank() && it.trim().lowercase() !in excludedLower }
            .toMutableList()

        if (candidates.size < 3) {
            val fallback = DEFAULT_FALLBACK_ENGLISH_DISTRACTORS.filter {
                it.isNotBlank() &&
                it.trim().lowercase() !in excludedLower &&
                candidates.none { c -> c.equals(it, ignoreCase = true) }
            }.shuffled()
            candidates.addAll(fallback)
        }

        val distractors = candidates.shuffled().take(3).toMutableList()
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
        fun extractCharacters(word: String): Set<String> {
            val chars = mutableSetOf<String>()
            var idx = 0
            while (idx < word.length) {
                val cp = word.codePointAt(idx)
                chars.add(String(Character.toChars(cp)))
                idx += Character.charCount(cp)
            }
            return chars
        }

        fun areWordsSimilar(wordA: String?, wordB: String?): Boolean {
            if (wordA.isNullOrEmpty() || wordB.isNullOrEmpty()) return false
            val charsA = extractCharacters(wordA)
            val charsB = extractCharacters(wordB)
            return charsA.any { it in charsB }
        }

        val DEFAULT_FALLBACK_ENGLISH_DISTRACTORS = listOf(
            "to see; to look",
            "water",
            "person; people",
            "big; large",
            "small; tiny",
            "to eat",
            "to go",
            "good; well",
            "friend",
            "book; letter",
            "sun; day",
            "moon; month",
            "mountain",
            "heart; mind",
            "tree; wood",
            "fire",
            "to speak; to say",
            "time; hour",
            "sky; heaven",
            "home; family"
        )

        fun provideFactory(
            studyRepository: StudyRepository,
            vocabularyRepository: VocabularyRepository,
            dictionaryRepository: VocabularyRepository? = null,
            userPreferences: UserPreferences? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StudyViewModel(studyRepository, vocabularyRepository, dictionaryRepository, userPreferences) as T
            }
        }
    }
}
