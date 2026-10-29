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
    private var lastTestedWord: String? = null
    private var lastPreReviewState: LeitnerState? = null
    private val recentTestedWords = ArrayDeque<String>()

    init {
        startStudySession()
    }

    fun startStudySession() {
        viewModelScope.launch {
            testStepIndex = 0
            lastTestedHanzi = null
            lastTestedWord = null
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
                        candidate != lastTestedWord &&
                        !areWordsSimilar(candidate, lastTestedHanzi) &&
                        !areWordsSimilar(candidate, lastTestedWord) &&
                        recentTestedWords.none { areWordsSimilar(candidate, it) }
                    }

                    // Priority 1: Not similar to immediately preceding word (consecutive tests do not share any character)
                    val nonSimilarToLast = dueCandidates.firstOrNull { candidate ->
                        candidate != lastTestedHanzi &&
                        candidate != lastTestedWord &&
                        !areWordsSimilar(candidate, lastTestedHanzi) &&
                        !areWordsSimilar(candidate, lastTestedWord)
                    }

                    // Priority 2: Different word from last tested word
                    val differentFromLast = dueCandidates.firstOrNull { candidate ->
                        candidate != lastTestedHanzi && candidate != lastTestedWord
                    }

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
            val revealedWord = card.revealedHanzi
            lastTestedWord = revealedWord
            recentTestedWords.addLast(revealedWord)
            if (nextHanzi != revealedWord) {
                recentTestedWords.addLast(nextHanzi)
            }
            while (recentTestedWords.size > 8) {
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
                        when (index % 3) {
                            0 -> QuizType.TONE
                            1 -> QuizType.CLOZE
                            else -> QuizType.PINYIN
                        }
                    }
                    hasValidNonNeutralTone -> {
                        if (index % 2 == 0) QuizType.TONE else QuizType.PINYIN
                    }
                    containingWords.isNotEmpty() -> QuizType.CLOZE
                    else -> QuizType.PINYIN
                }
            }
            else -> {
                // Single character WITH user English translations
                when {
                    hasValidNonNeutralTone -> {
                        when (index % 3) {
                            0 -> QuizType.TONE
                            1 -> QuizType.ENGLISH
                            else -> QuizType.PINYIN
                        }
                    }
                    else -> {
                        if (index % 2 == 0) QuizType.ENGLISH else QuizType.PINYIN
                    }
                }
            }
        }

        // 2. If Tone Quiz or Pinyin Quiz, find studied compound words demonstrating this character (study stack only)
        var contextWords = emptyList<String>()
        if (quizType == QuizType.TONE || quizType == QuizType.PINYIN) {
            val exampleWords = vocabularyRepository.getCompoundWordsForCharPinyin(hanzi, voc.pinyin, limit = 4)
            contextWords = exampleWords.filter { it != hanzi && it.codePointCount(0, it.length) > 1 }
        }

        // 3. Generate MCQ distractors for English or Pinyin
        val mcqOptions = when (quizType) {
            QuizType.ENGLISH -> {
                val correct = voc.english.randomOrNull() ?: "Meaning"
                generateEnglishDistractors(correct, allStudyEnglish, excludedMeanings = voc.english)
            }
            QuizType.PINYIN -> {
                generatePinyinDistractors(voc.pinyin, hanzi, box, studyChars)
            }
            else -> emptyList()
        }

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
                val missingCharBox = studyRepository.getLeitnerState(correctChar)?.box ?: box
                scrambledTiles.addAll(generateClozeDistractors(correctChar, missingCharBox, distractorCount, studyChars))
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
                    val missingCharBox = studyRepository.getLeitnerState(correctChar)?.box ?: box
                    scrambledTiles.addAll(generateClozeDistractors(correctChar, missingCharBox, distractorCount, studyChars))
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

    fun submitPinyinAnswer(selectedPinyin: String) {
        val card = _uiState.value.currentCard ?: return
        if (_uiState.value.isAnswerRevealed) return

        val prettyCorrect = PinyinParser.convert_to_pretty_version(card.pinyin)
        val isCorrect = selectedPinyin.equals(prettyCorrect, ignoreCase = true) ||
                selectedPinyin.equals(card.pinyin, ignoreCase = true)
        processAnswer(card, isCorrect, selectedPinyin)
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

    private suspend fun generatePinyinDistractors(
        correctPinyin: String,
        hanzi: String,
        box: Int,
        studyChars: Set<String> = emptySet(),
        targetOptionCount: Int = if (box >= 3) 8 else 6
    ): List<String> {
        val prettyCorrect = PinyinParser.convert_to_pretty_version(correctPinyin)
        val pinyins = PinyinParser.parse_string_to_pinyin(correctPinyin)
        val firstPinyin = pinyins?.firstOrNull()
        val targetRadical = (firstPinyin?.radical ?: correctPinyin.filter { !it.isDigit() }).lowercase()
        val targetTone = firstPinyin?.tone?.takeIf { it in 1..4 } ?: 1

        val numSyllables = (targetOptionCount / 2).coerceAtLeast(2)
        val neededDistractorSyllables = (numSyllables - 1).coerceAtLeast(1)

        // Generate 2 tones for the target syllable: correct tone + 1 alternate tone
        val targetAltTone = (1..4).filter { it != targetTone }.shuffled().first()
        val targetAltPretty = PinyinParser.convert_to_pretty_version("$targetRadical$targetAltTone")

        val targetPair = mutableListOf(prettyCorrect)
        if (targetAltPretty.isNotBlank() && !targetAltPretty.equals(prettyCorrect, ignoreCase = true)) {
            targetPair.add(targetAltPretty)
        }

        fun extractSyllable(raw: String): Pair<String, Int>? {
            val p = PinyinParser.parse_string_to_pinyin(raw)?.firstOrNull() ?: return null
            val rad = p.radical.lowercase()
            if (rad.isBlank() || rad == targetRadical) return null
            val t = if (p.tone in 1..4) p.tone else 1
            return Pair(rad, t)
        }

        // 1. Gather candidate distractor syllables from visually similar characters
        val similarChars = (
            vocabularyRepository.getSimilarCharacters(hanzi, limit = 20) +
            (dictionaryRepository?.getSimilarCharacters(hanzi, limit = 20) ?: emptyList())
        ).filter { it != hanzi }.distinct()

        val knownSimilarChars = similarChars.filter { studyChars.contains(it) }
        val unstudiedSimilarChars = similarChars.filter { !studyChars.contains(it) }

        val maxSimilarSyllables = when {
            box <= 1 -> (0..minOf(1, neededDistractorSyllables)).random() // Box 0-1: 0 or 1
            box in 2..4 -> (1..minOf(2, neededDistractorSyllables)).random() // Box 2-4: 1 or 2
            else -> (2..neededDistractorSyllables).random() // Box 5+: 2 to 3 from similar characters
        }

        val chosenDistractorSyllables = mutableMapOf<String, Int>() // radical -> baseTone

        if (maxSimilarSyllables > 0) {
            val charsToQuery = if (box >= 5) {
                (knownSimilarChars.shuffled() + unstudiedSimilarChars.shuffled()).take(maxSimilarSyllables * 2)
            } else {
                knownSimilarChars.shuffled().take(maxSimilarSyllables * 2)
            }

            val pinyinsFromVoc = if (charsToQuery.isNotEmpty()) {
                vocabularyRepository.getPinyinsForHanziList(charsToQuery)
            } else emptyList()

            val pinyinsFromDict = if (box >= 5 && charsToQuery.isNotEmpty() && dictionaryRepository != null) {
                dictionaryRepository.getPinyinsForHanziList(charsToQuery)
            } else emptyList()

            for (p in (pinyinsFromVoc + pinyinsFromDict).shuffled()) {
                if (chosenDistractorSyllables.size >= maxSimilarSyllables) break
                val syll = extractSyllable(p) ?: continue
                if (!chosenDistractorSyllables.containsKey(syll.first)) {
                    chosenDistractorSyllables[syll.first] = syll.second
                }
            }
        }

        // 2. Backfill syllables from general study vocabulary if needed
        if (chosenDistractorSyllables.size < neededDistractorSyllables) {
            val studyPinyins = vocabularyRepository.getAllDistinctPinyins(100).shuffled()
            for (p in studyPinyins) {
                if (chosenDistractorSyllables.size >= neededDistractorSyllables) break
                val syll = extractSyllable(p) ?: continue
                if (!chosenDistractorSyllables.containsKey(syll.first)) {
                    chosenDistractorSyllables[syll.first] = syll.second
                }
            }
        }

        // 3. Backfill syllables from dictionary repository or default fallback list
        if (chosenDistractorSyllables.size < neededDistractorSyllables && dictionaryRepository != null) {
            val dictPinyins = dictionaryRepository.getAllDistinctPinyins(100).shuffled()
            for (p in dictPinyins) {
                if (chosenDistractorSyllables.size >= neededDistractorSyllables) break
                val syll = extractSyllable(p) ?: continue
                if (!chosenDistractorSyllables.containsKey(syll.first)) {
                    chosenDistractorSyllables[syll.first] = syll.second
                }
            }
        }

        if (chosenDistractorSyllables.size < neededDistractorSyllables) {
            val fallbackPinyins = DEFAULT_FALLBACK_PINYIN_DISTRACTORS.shuffled()
            for (p in fallbackPinyins) {
                if (chosenDistractorSyllables.size >= neededDistractorSyllables) break
                val syll = extractSyllable(p) ?: continue
                if (!chosenDistractorSyllables.containsKey(syll.first)) {
                    chosenDistractorSyllables[syll.first] = syll.second
                }
            }
        }

        // Build paired tone options: each distinct syllable receives 2 tone variations
        // so no single syllable is given away by being the only one repeated with multiple tones
        val resultOptions = mutableSetOf<String>()
        resultOptions.addAll(targetPair)

        for ((rad, baseTone) in chosenDistractorSyllables) {
            val basePretty = PinyinParser.convert_to_pretty_version("$rad$baseTone")
            val altTone = (1..4).filter { it != baseTone }.shuffled().first()
            val altPretty = PinyinParser.convert_to_pretty_version("$rad$altTone")

            if (basePretty.isNotBlank()) resultOptions.add(basePretty)
            if (altPretty.isNotBlank()) resultOptions.add(altPretty)
        }

        // Ensure we meet targetOptionCount (safety backfill)
        if (resultOptions.size < targetOptionCount) {
            for (fb in DEFAULT_FALLBACK_PINYIN_DISTRACTORS) {
                if (resultOptions.size >= targetOptionCount) break
                val p = extractSyllable(fb) ?: continue
                val p1 = PinyinParser.convert_to_pretty_version("${p.first}${p.second}")
                val altT = (1..4).filter { it != p.second }.shuffled().first()
                val p2 = PinyinParser.convert_to_pretty_version("${p.first}$altT")
                if (p1.isNotBlank()) resultOptions.add(p1)
                if (resultOptions.size < targetOptionCount && p2.isNotBlank()) resultOptions.add(p2)
            }
        }

        val nonTargetOptions = (resultOptions - prettyCorrect).shuffled()
        val finalOptions = (listOf(prettyCorrect) + nonTargetOptions.take(targetOptionCount - 1)).shuffled()
        return finalOptions
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

    private suspend fun generateClozeDistractors(
        correct: String,
        missingCharBox: Int,
        count: Int = 3,
        studyChars: Set<String> = emptySet()
    ): List<String> {
        // Query precomputed visual similarities from char_similarity table
        val similarFromCedict = vocabularyRepository.getSimilarCharacters(correct, limit = 20)
            .filter { it != correct }

        val knownSimilar = similarFromCedict.filter { it in studyChars }
        val unstudiedSimilar = similarFromCedict.filter { it !in studyChars }
        val otherStudyChars = studyChars.filter { it != correct && it !in knownSimilar }.shuffled()

        // The count of similar characters we take should be somewhat random.
        // For not well known characters (box 0-1), this could even be 0.
        val numSimilarTarget = when {
            missingCharBox <= 1 -> (0..minOf(count, if (missingCharBox == 0) 1 else 2)).random()
            missingCharBox in 2..3 -> (1..minOf(count, 2)).random()
            missingCharBox == 4 -> (1..minOf(count, 3)).random()
            else -> (1..count).random()
        }

        val selectedDistractors = mutableListOf<String>()

        if (missingCharBox >= 5) {
            // Really well known (Box 5+):
            // Suggest similar characters, including ones from CEDICT that are not being studied yet!
            val similarPool = (knownSimilar.shuffled() + unstudiedSimilar.shuffled())
            selectedDistractors.addAll(similarPool.take(numSimilarTarget))
            if (selectedDistractors.size < count) {
                selectedDistractors.addAll(otherStudyChars.take(count - selectedDistractors.size))
            }
            // If study list doesn't have enough characters to fill count, backfill with remaining unstudied similar characters
            if (selectedDistractors.size < count) {
                val remainingUnstudied = unstudiedSimilar.filter { it !in selectedDistractors }
                selectedDistractors.addAll(remainingUnstudied.take(count - selectedDistractors.size))
            }
        } else {
            // Lower / intermediate boxes (Box 0-4):
            // Only suggest known characters from study list
            selectedDistractors.addAll(knownSimilar.shuffled().take(numSimilarTarget))
            if (selectedDistractors.size < count) {
                selectedDistractors.addAll(otherStudyChars.take(count - selectedDistractors.size))
            }
        }

        val finalTiles = selectedDistractors.take(count).toMutableList()
        finalTiles.add(correct)
        return finalTiles.shuffled()
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

        val DEFAULT_FALLBACK_PINYIN_DISTRACTORS = listOf(
            "hao3", "ni3", "da4", "xiao3", "shui3", "ren2", "tian1", "di4",
            "zhong1", "guo2", "shang4", "xia4", "yue4", "ri4", "shan1", "mu4"
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
